#!/usr/bin/env python3
"""Verify live fault injection and post-reset HTTP recovery. Standard library only."""
import argparse
import csv
import json
import pathlib
import statistics
import time
import urllib.error
import urllib.request

HOST = "http://127.0.0.1"
PORTS = {"orders": 18081, "payments": 18082, "inventory": 18083}
TOKEN = "local-experiment-only"
SCENARIOS = [
    ("orders_error", "orders", "orders", "ERROR", 0),
    ("payments_error", "payments", "orders", "ERROR", 0),
    ("inventory_latency", "inventory", "orders", "LATENCY", 900),
]

def request(url, data=None, authorized=False, timeout=8):
    headers = {"Content-Type": "application/json"}
    if authorized:
        headers["X-Lab-Token"] = TOKEN
    req = urllib.request.Request(url, data=json.dumps(data).encode() if data is not None else None, headers=headers)
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as result:
            code = result.status
            body = result.read().decode()
    except urllib.error.HTTPError as err:
        code = err.code
        body = err.read().decode()
    except (urllib.error.URLError, TimeoutError) as err:
        code, body = 0, str(err)
    return {"status": code, "latency_ms": round(1000 * (time.monotonic() - start), 2), "body": body}

def url(service, path):
    return HOST + ":" + str(PORTS[service]) + path

def fault(service, mode, delay):
    result = request(url(service, "/lab/admin/fault"),
                     {"mode": mode, "delayMs": delay}, authorized=True)
    if result["status"] != 200:
        raise RuntimeError("Fault update rejected: " + repr(result))
    return json.loads(result["body"])["revision"]

def observations(service, scenario, phase, n, output):
    values = []
    for attempt in range(n):
        obs = request(url(service, "/api/work"))
        output.append({"scenario": scenario, "phase": phase, "attempt": attempt + 1,
                       "status": obs["status"], "latency_ms": obs["latency_ms"]})
        values.append(obs)
    return values

def wait_until_ready(timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if all(request(url(s, "/actuator/health"), timeout=3)["status"] == 200 for s in PORTS):
                if request(url("orders", "/api/work"))["status"] == 200:
                    return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Live lab did not become healthy within " + str(timeout) + " seconds")

def median_ms(rows):
    return statistics.median(r["latency_ms"] for r in rows)

def run_scenario(name, target, probe, mode, delay, samples, observations_out):
    # A full fault reset between scenarios, checked before and after each trial.
    for s in PORTS:
        fault(s, "NONE", 0)
    baseline = observations(probe, name, "baseline", samples, observations_out)
    if not all(p["status"] == 200 for p in baseline):
        raise AssertionError(name + ": baseline unhealthy")
    fault(target, mode, delay)
    try:
        degraded = observations(probe, name, "degraded", samples, observations_out)
        if mode == "ERROR":
            if not all(p["status"] >= 500 for p in degraded):
                raise AssertionError(name + ": expected real downstream HTTP failures")
        elif median_ms(degraded) - median_ms(baseline) < 400:
            raise AssertionError(name + ": latency fault did not produce 400ms median increase")
    finally:
        restore_start = time.monotonic()
        fault(target, "NONE", 0)
    restored = observations(probe, name, "restored", samples, observations_out)
    if not all(p["status"] == 200 for p in restored):
        raise AssertionError(name + ": post-remediation service HTTP checks failed")
    if mode == "LATENCY" and median_ms(restored) > median_ms(baseline) + 450:
        raise AssertionError(name + ": delay persisted after fault reset")
    restore_ms = round(1000 * (time.monotonic() - restore_start), 2)
    return {"scenario": name, "target": target, "probe": probe, "injection": mode,
            "baseline_median_ms": median_ms(baseline),
            "degraded_median_ms": median_ms(degraded),
            "restored_median_ms": median_ms(restored),
            "observed_reset_and_probe_ms": restore_ms,
            "status": "verified"}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="lab/results")
    parser.add_argument("--samples", type=int, default=6)
    args = parser.parse_args()
    if args.samples < 3:
        parser.error("--samples must be at least 3")
    dest = pathlib.Path(args.output)
    dest.mkdir(parents=True, exist_ok=True)
    trace = []
    cases = []
    try:
        wait_until_ready()
        for spec in SCENARIOS:
            cases.append(run_scenario(*spec, args.samples, trace))
    finally:
        # Attempt cleanup even if a verification assertion fails.
        for svc in PORTS:
            try:
                fault(svc, "NONE", 0)
            except Exception:
                pass
        if trace:
            with (dest / "http_observations.csv").open("w", newline="") as fp:
                writer = csv.DictWriter(fp, fieldnames=["scenario", "phase", "attempt", "status", "latency_ms"])
                writer.writeheader()
                writer.writerows(trace)
        (dest / "recovery_verification.json").write_text(
            json.dumps({"mode": "live-container-http-fault-reset",
                        "limitations": "Manual fault reset, not AI remediation; elapsed time is reset-and-probe time, not end-to-end autonomous MTTR.",
                        "scenarios": cases}, indent=2) + "\n")
    print(json.dumps(cases, indent=2))

if __name__ == "__main__":
    main()
