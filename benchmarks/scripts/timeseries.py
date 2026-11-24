#!/usr/bin/env python3
"""Turn a k6 --out json(.gz) file into per-bucket latency rows (timeseries.csv).

Usage: timeseries.py RAW.json.gz OUT.csv [BUCKET_SECONDS=10]
One row per (bucket, endpoint): requests, req/s, p50/p95/p99/max latency (ms), failed, dropped.
In a saturation ramp the offered rate rises with time, so the knee is where p99 jumps or
dropped starts climbing. Only the standard library is used.
"""
import calendar
import csv
import gzip
import json
import sys
import time
from collections import defaultdict

raw, out = sys.argv[1], sys.argv[2]
bucket_s = int(sys.argv[3]) if len(sys.argv) > 3 else 10

SCENARIO_TO_NAME = {"redirects": "redirect", "creates": "create"}
durations = defaultdict(list)   # (bucket, name) -> [ms]
failed = defaultdict(int)       # (bucket, name) -> count
dropped = defaultdict(int)      # (bucket, name) -> count
epoch_cache = {}
t0 = None


def epoch(ts):
    # "2026-11-24T10:33:05.123456789+07:00": seconds resolution is enough, the offset is constant.
    key = ts[:19]
    v = epoch_cache.get(key)
    if v is None:
        v = epoch_cache[key] = calendar.timegm(time.strptime(key, "%Y-%m-%dT%H:%M:%S"))
    return v


with gzip.open(raw, "rt") if raw.endswith(".gz") else open(raw) as f:
    for line in f:
        # Cheap pre-filter: skip json parsing for the metrics we do not need.
        if '"type":"Point"' not in line:
            continue
        if '"http_req_duration"' in line:
            kind = "d"
        elif '"http_req_failed"' in line:
            kind = "f"
        elif '"dropped_iterations"' in line:
            kind = "x"
        else:
            continue
        p = json.loads(line)
        data = p["data"]
        tags = data.get("tags", {})
        name = tags.get("name") or SCENARIO_TO_NAME.get(tags.get("scenario"), "?")
        t = epoch(data["time"])
        if t0 is None:
            t0 = t
        b = ((t - t0) // bucket_s) * bucket_s
        if kind == "d":
            durations[(b, name)].append(data["value"])
        elif kind == "f":
            failed[(b, name)] += int(data["value"])
        else:
            dropped[(b, name)] += int(data["value"])


def pct(sorted_vals, q):
    return sorted_vals[min(len(sorted_vals) - 1, int(q * len(sorted_vals)))]


keys = sorted(set(durations) | set(dropped))
with open(out, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["t_start_s", "name", "requests", "req_per_s", "p50_ms", "p95_ms", "p99_ms", "max_ms", "failed", "dropped"])
    for k in keys:
        vals = sorted(durations.get(k, []))
        if vals:
            row = [len(vals), round(len(vals) / bucket_s, 1), *(round(pct(vals, q), 2) for q in (0.5, 0.95, 0.99)), round(vals[-1], 2)]
        else:
            row = [0, 0, "", "", "", ""]
        w.writerow([k[0], k[1], *row, failed.get(k, 0), dropped.get(k, 0)])
print(f"timeseries: {len(keys)} rows -> {out}")
