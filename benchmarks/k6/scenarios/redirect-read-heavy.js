/**
 * k6 scenario: redirect-read-heavy
 *
 * Workload: ~100:1 redirects to creates, Zipf(s=1) key popularity over SEED_ROWS seeded links,
 * open-model load (arrival-rate executors) so slow responses cannot throttle the offered load
 * (avoids coordinated omission).
 *
 * MODE (env):
 *   measure    constant rate for DURATION (default)
 *   saturation ramps the rate up to find the knee; pick the measure RATE at ~70% of it
 *
 * Other env: BASE_URL, RATE (redirects/s), DURATION (measure only), SEED_ROWS (must match
 * benchmarks/scripts/seed.sql). run-bench.sh also runs this file as a discarded warm-up: same
 * scenario in measure mode with the warm-up RATE and DURATION.
 * Run via: make bench VERSION=v0-naive  (see benchmarks/scripts/run-bench.sh)
 */

import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const MODE = __ENV.MODE || "measure";
const RATE = parseInt(__ENV.RATE || "500", 10);
const SEED_ROWS = parseInt(__ENV.SEED_ROWS || "10000000", 10);
const DURATION = __ENV.DURATION || "5m";
const WRITE_DIVISOR = 100; // 100:1 read:write

// ─── Key distribution ─────────────────────────────────────────────────────────
// Zipf with s=1: P(rank=k) ~ 1/k, so CDF ~ ln(k)/ln(N) and rank = floor(N^u) for u~U(0,1).
// Ranks are then scattered over ids with a bijection (multiplier coprime with SEED_ROWS) so
// the hot keys are not physically adjacent rows, which would flatter the database.
const SCATTER = 1000003;
function gcd(a, b) {
  return b === 0 ? a : gcd(b, a % b);
}
if (gcd(SCATTER, SEED_ROWS) !== 1) {
  throw new Error(`SEED_ROWS=${SEED_ROWS} must be coprime with ${SCATTER} (1000003 is prime, so avoid multiples of it)`);
}

const ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
function base62(n) {
  if (n === 0) return "0";
  let s = "";
  while (n > 0) {
    s = ALPHABET[n % 62] + s;
    n = Math.floor(n / 62);
  }
  return s;
}

// mulberry32: small seedable PRNG, one stream per VU (k6's Math.random cannot be seeded).
// Streams are deterministic per VU; which VU serves which request is not.
let rng = null;
function rand() {
  if (rng === null) {
    let a = (0x9e3779b9 ^ (__VU * 2654435761)) >>> 0;
    rng = () => {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  return rng();
}

function zipfCode() {
  const rank = Math.min(SEED_ROWS, Math.floor(Math.pow(SEED_ROWS, rand()))); // 1..N
  const id = ((rank * SCATTER) % SEED_ROWS) + 1; // 1..N, bijective
  return base62(id);
}

// ─── Load shape ───────────────────────────────────────────────────────────────
function constant(rate, duration, exec, vus, maxVus) {
  return {
    executor: "constant-arrival-rate",
    rate,
    timeUnit: "1s",
    duration,
    preAllocatedVUs: vus,
    maxVUs: maxVus,
    exec,
  };
}

function ramp(peak, exec, vus, maxVus) {
  const steps = 10; // linear ramp to peak, 30s per step
  const stages = [];
  for (let i = 1; i <= steps; i++) {
    stages.push({ target: Math.max(1, Math.round((peak * i) / steps)), duration: "30s" });
  }
  return { executor: "ramping-arrival-rate", startRate: 1, timeUnit: "1s", stages, preAllocatedVUs: vus, maxVUs: maxVus, exec };
}

function scenarios() {
  const createRate = Math.max(1, Math.round(RATE / WRITE_DIVISOR));
  if (MODE === "saturation") {
    // RATE is the peak of the ramp here.
    return { redirects: ramp(RATE, "redirect", 100, 1000), creates: ramp(createRate, "create", 10, 100) };
  }
  return {
    redirects: constant(RATE, DURATION, "redirect", 100, 1000),
    creates: constant(createRate, DURATION, "create", 10, 100),
  };
}

export const options = {
  scenarios: scenarios(),
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max", "count"],
  thresholds: {
    // Thresholds only gate the exit code; the baseline just records what it gets. The empty-ish
    // bounds exist so k6 reports per-endpoint sub-metrics in the summary.
    "http_req_duration{name:redirect}": ["p(99)<60000"],
    "http_req_duration{name:create}": ["p(99)<60000"],
    "http_req_failed{name:redirect}": ["rate<1"],
    "http_req_failed{name:create}": ["rate<1"],
    dropped_iterations: ["count>=0"],
  },
};

// ─── Scenarios ────────────────────────────────────────────────────────────────
export function redirect() {
  const res = http.get(`${BASE_URL}/${zipfCode()}`, {
    redirects: 0, // measure the redirect response, not the destination
    tags: { name: "redirect" },
    responseCallback: http.expectedStatuses(302),
  });
  check(res, { "redirect is 302": (r) => r.status === 302 });
}

export function create() {
  const res = http.post(
    `${BASE_URL}/api/v1/links`,
    JSON.stringify({ longUrl: `https://example.com/article/${__VU}-${__ITER}-${Date.now()}` }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "create" },
      responseCallback: http.expectedStatuses(201),
    }
  );
  check(res, { "create is 201": (r) => r.status === 201 });
}
