#!/usr/bin/env python3
"""Generate real HTTP requests and report status/latency observations."""
import argparse
import csv
import json
import pathlib
import statistics
import time
import urllib.error
import urllib.request

PORTS = {"orders": 18081, "payments": 18082, "inventory": 18083}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--service", choices=PORTS, default="orders")
    ap.add_argument("--requests", type=int, default=50)
    ap.add_argument("--interval-ms", type=float, default=100)
    ap.add_argument("--output", default="lab/results/workload")
    args = ap.parse_args()
    if args.requests < 1 or args.interval_ms < 0:
        ap.error("requests must be positive; interval cannot be negative")
    dest = pathlib.Path(args.output)
    dest.mkdir(parents=True, exist_ok=True)
    endpoint = "http://127.0.0.1:" + str(PORTS[args.service]) + "/api/work"
    records = []
    for n in range(args.requests):
        start = time.monotonic()
        try:
            with urllib.request.urlopen(endpoint, timeout=8) as response:
                code = response.status
                response.read()
        except urllib.error.HTTPError as err:
            code = err.code
        except (TimeoutError, urllib.error.URLError):
            code = 0
        records.append({"request": n + 1, "status": code,
                        "latency_ms": round((time.monotonic() - start) * 1000, 2)})
        if args.interval_ms:
            time.sleep(args.interval_ms / 1000)
    samples = sorted(r["latency_ms"] for r in records)
    p95 = samples[max(0, int(0.95 * (len(samples) - 1)))]
    summary = {"service": args.service, "requests": len(records),
               "success_count": sum(200 <= r["status"] < 300 for r in records),
               "failure_count": sum(not 200 <= r["status"] < 300 for r in records),
               "p50_ms": statistics.median(samples), "p95_ms": p95}
    with (dest / "requests.csv").open("w", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=["request", "status", "latency_ms"])
        writer.writeheader()
        writer.writerows(records)
    (dest / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))

if __name__ == "__main__":
    main()
