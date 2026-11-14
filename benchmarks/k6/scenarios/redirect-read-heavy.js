/**
 * k6 load test scenario: redirect-read-heavy
 *
 * Simulates a realistic URL shortener workload:
 * - ~100:1 read-to-write ratio
 * - Zipf-like key distribution (hot short codes get most traffic)
 * - constant-arrival-rate to avoid coordinated omission
 *
 * Run via Makefile: make bench VERSION=v0-naive
 *
 * Pre-requisites:
 *   - Stack must be running: make up
 *   - Short codes must be seeded (see benchmarks/scripts/seed.sh)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

// ─── Custom metrics ───────────────────────────────────────────────────────────
const redirectErrors = new Rate("redirect_errors");
const redirectLatency = new Trend("redirect_latency_ms", true);

// ─── Configuration ────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// Seed these short codes before running the benchmark.
// Generate with: benchmarks/scripts/seed.sh
const SHORT_CODES = JSON.parse(open("../data/short_codes.json") || "[]");

// Zipf-like distribution: first 10% of codes receive ~60% of traffic
function zipfCode() {
  const r = Math.random();
  if (r < 0.6) {
    // hot tier: top 10%
    return SHORT_CODES[Math.floor(Math.random() * Math.max(1, SHORT_CODES.length * 0.1))];
  } else if (r < 0.9) {
    // warm tier: next 30%
    const offset = Math.floor(SHORT_CODES.length * 0.1);
    return SHORT_CODES[offset + Math.floor(Math.random() * Math.max(1, SHORT_CODES.length * 0.3))];
  } else {
    // cold tier: remaining 60%
    const offset = Math.floor(SHORT_CODES.length * 0.4);
    return SHORT_CODES[offset + Math.floor(Math.random() * Math.max(1, SHORT_CODES.length * 0.6))];
  }
}

// ─── Scenarios ────────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    redirects: {
      executor: "constant-arrival-rate",
      rate: __ENV.REDIRECT_RATE || 500,   // requests/second — tune to ~70% saturation
      timeUnit: "1s",
      duration: __ENV.DURATION || "5m",
      preAllocatedVUs: 50,
      maxVUs: 200,
      exec: "redirect",
    },
    creates: {
      executor: "constant-arrival-rate",
      rate: __ENV.CREATE_RATE || 5,       // 100:1 read-to-write
      timeUnit: "1s",
      duration: __ENV.DURATION || "5m",
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: "create",
    },
  },
  thresholds: {
    // These will be meaningful after Phase 1 optimizations.
    // Phase 0 baseline: just capture what we get.
    "http_req_duration{scenario:redirects}": ["p(99)<5000"], // very lenient for baseline
    redirect_errors: ["rate<0.05"],
  },
};

// ─── Scenario: redirect ───────────────────────────────────────────────────────
export function redirect() {
  if (SHORT_CODES.length === 0) {
    console.warn("No short codes loaded. Run seed script first.");
    return;
  }

  const code = zipfCode();
  const start = Date.now();
  const res = http.get(`${BASE_URL}/s/${code}`, {
    redirects: 0, // measure the redirect response, not the destination
    tags: { name: "redirect" },
  });
  redirectLatency.add(Date.now() - start);

  const ok = check(res, {
    "redirect status is 301 or 302": (r) => r.status === 301 || r.status === 302,
  });
  redirectErrors.add(!ok);
}

// ─── Scenario: create ────────────────────────────────────────────────────────
export function create() {
  const payload = JSON.stringify({
    longUrl: `https://example.com/article/${Math.random().toString(36).slice(2)}`,
  });

  const res = http.post(`${BASE_URL}/api/v1/links`, payload, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "create" },
  });

  check(res, {
    "create status is 201": (r) => r.status === 201,
  });
}
