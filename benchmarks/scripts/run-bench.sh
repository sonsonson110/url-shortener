#!/usr/bin/env bash
# One command per run: clean stack -> migrate -> seed -> warm up -> measure -> save -> tear down.
# Usage: VERSION=v0-naive benchmarks/scripts/run-bench.sh      (normally via: make bench VERSION=...)
# Env:   MODE=measure|saturation  RATE=500  SEED_ROWS=10000000  WARMUP_DURATION=2m  DURATION=5m
#
# The app image is built from the CURRENT working tree. Check out the version's tag/commit first;
# the commit and dirty flag are recorded in env.txt so a result can be traced to its code.
set -euo pipefail

VERSION=${VERSION:?set VERSION, e.g. VERSION=v0-naive}
MODE=${MODE:-measure}
RATE=${RATE:-500}
SEED_ROWS=${SEED_ROWS:-10000000}
WARMUP_DURATION=${WARMUP_DURATION:-2m}
DURATION=${DURATION:-5m}
export MODE RATE SEED_ROWS DURATION WARMUP_DURATION

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TS=$(date +%Y%m%dT%H%M%S)
if [[ "$MODE" == "measure" ]]; then LABEL=""; else LABEL="-$MODE"; fi
export BENCH_OUT="$ROOT/benchmarks/results/$VERSION/$TS$LABEL"
BENCH_UID=$(id -u); BENCH_GID=$(id -g)
export BENCH_UID BENCH_GID
mkdir -p "$BENCH_OUT"

COMPOSE=(docker compose -f "$ROOT/deploy/docker-compose.bench.yml")
PSQL=("${COMPOSE[@]}" exec -T postgres psql -U urlshortener -d urlshortener -v ON_ERROR_STOP=1)
K6_ENV=(-e MODE -e RATE -e SEED_ROWS -e DURATION -e WARMUP_DURATION)

STATS_PID=""
cleanup() {
  if [[ -n "$STATS_PID" ]]; then kill "$STATS_PID" 2>/dev/null || true; fi
  # Scoped to the bench compose project only (name: urlshortener-bench); owner-approved.
  "${COMPOSE[@]}" --profile load down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

echo "== [$VERSION] starting clean stack"
"${COMPOSE[@]}" up -d --build --wait postgres app

echo "== seeding $SEED_ROWS rows"
SEED_START=$(date +%s)
"${PSQL[@]}" -v rows="$SEED_ROWS" < "$ROOT/benchmarks/scripts/seed.sql" > "$BENCH_OUT/seed.log"
SEED_SECONDS=$(( $(date +%s) - SEED_START ))
"${PSQL[@]}" -qc "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" > /dev/null

{
  echo "version=$VERSION"
  echo "mode=$MODE rate=$RATE seed_rows=$SEED_ROWS warmup=$WARMUP_DURATION duration=$DURATION"
  echo "seed_seconds=$SEED_SECONDS (setup time, not a benchmark result)"
  echo "git_commit=$(git -C "$ROOT" rev-parse HEAD)"
  echo "git_dirty=$([[ -n "$(git -C "$ROOT" status --porcelain -- . ':!benchmarks/results')" ]] && echo yes || echo no)"
  echo "date=$(date -Is)"
  echo "kernel=$(uname -sr)"
  echo "cpu=$(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | xargs) cores=$(nproc)"
  echo "mem=$(free -h | awk '/Mem:/ {print $2}')"
  echo "docker=$(docker version --format '{{.Server.Version}}')"
  echo "postgres=$("${PSQL[@]}" -Atc 'select version()')"
  echo "links_rows=$("${PSQL[@]}" -Atc 'select count(*) from links')"
  echo "links_size=$("${PSQL[@]}" -Atc "select pg_size_pretty(pg_total_relation_size('links'))")"
} > "$BENCH_OUT/env.txt"
"${COMPOSE[@]}" config > "$BENCH_OUT/compose.resolved.yml"

run_k6() { "${COMPOSE[@]}" --profile load run --rm -T "${K6_ENV[@]}" k6 "$@"; }

if [[ "$MODE" != "saturation" ]]; then
  echo "== warm-up ($WARMUP_DURATION, results discarded)"
  MODE=warmup run_k6 run /scripts/scenarios/redirect-read-heavy.js > "$BENCH_OUT/warmup.log" 2>&1 || true
fi

"${PSQL[@]}" -qc "SELECT pg_stat_statements_reset()" > /dev/null

(while true; do
   # Only this project's containers (the dev stack may be running too).
   docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' \
     $(docker ps -q --filter label=com.docker.compose.project=urlshortener-bench) \
     | sed "s/^/$(date +%s),/" >> "$BENCH_OUT/docker-stats.csv"
   sleep 5
 done) &
STATS_PID=$!

echo "== k6 $MODE"
K6_STATUS=0
run_k6 run --summary-export /results/summary.json /scripts/scenarios/redirect-read-heavy.js \
  2>&1 | tee "$BENCH_OUT/k6-stdout.log" || K6_STATUS=${PIPESTATUS[0]}

kill "$STATS_PID" 2>/dev/null || true; STATS_PID=""

"${PSQL[@]}" -c "\\copy (SELECT calls, round(total_exec_time::numeric,1) AS total_ms, round(mean_exec_time::numeric,3) AS mean_ms, rows, left(query,120) AS query FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 15) TO STDOUT WITH CSV HEADER" \
  > "$BENCH_OUT/pg_stat_statements.csv"
"${COMPOSE[@]}" exec -T app wget -qO- http://localhost:8080/actuator/prometheus > "$BENCH_OUT/app-metrics.prom" || true

echo "== done: $BENCH_OUT (k6 exit status $K6_STATUS)"
exit "$K6_STATUS"
