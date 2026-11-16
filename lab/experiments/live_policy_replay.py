#!/usr/bin/env python3
"""Matched replay against live HTTP lab. Never sends an arbitrary operational command."""
import argparse
import json
import pathlib
import time
import urllib.error
import urllib.request

HOST = "http://127.0.0.1"
TOKEN = "local-experiment-only"
CONTROLLER = HOST + ":18080"
PORTS = {"orders": 18081, "payments": 18082, "inventory": 18083}


def call(url, data=None, token=False, timeout=15):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Lab-Token"] = TOKEN
    req = urllib.request.Request(
        url, data=json.dumps(data).encode() if data is not None else None, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            code, payload = resp.status, resp.read().decode()
    except urllib.error.HTTPError as exc:
        code, payload = exc.code, exc.read().decode()
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError("HTTP request failed: " + url + ": " + str(exc)) from exc
    try:
        body = json.loads(payload) if payload else {}
    except json.JSONDecodeError:
        body = {"raw": payload}
    return code, body


def admin_fault(service, mode):
    code, data = call(HOST + ":" + str(PORTS[service]) + "/lab/admin/fault",
                      {"mode": mode, "delayMs": 0}, token=True)
    if code != 200:
        raise RuntimeError("Fault API did not accept reset/injection: " + str(data))


def wait_ready(seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if all(call(HOST + ":" + str(port) + "/actuator/health")[0] == 200
                   for port in list(PORTS.values()) + [18080]):
                if call(HOST + ":18081/api/work")[0] == 200:
                    return
        except RuntimeError:
            pass
        time.sleep(1)
    raise RuntimeError("Lab controller and services not ready")


def externally_verified_recovery():
    # Independent of ECRA and the Java controller's reported verified field.
    samples = [call(HOST + ":18081/api/work")[0] for _ in range(5)]
    return all(code == 200 for code in samples), samples


def compare_payment_fault():
    for service in PORTS:
        admin_fault(service, "NONE")
    admin_fault("payments", "ERROR")
    try:
        degraded, _ = call(HOST + ":18081/api/work")
        if degraded < 500:
            raise AssertionError("Injected payment failure did not propagate to Orders")
        status, captured = call(CONTROLLER + "/lab/recovery/capture",
                                {"service": "payments", "useOllama": False}, token=True)
        if status != 200 or captured.get("action") != "RESTART_SERVICE":
            raise AssertionError("Expected rules proposal for live payment failure: " + str(captured))

        results = []
        for guarded in (False, True):
            # Crucial: identical captured proposal but independently reset faults/observation.
            admin_fault("payments", "ERROR")
            before, _ = call(HOST + ":18081/api/work")
            if before < 500:
                raise AssertionError("Each replay arm must start from the same failing state")
            status, response = call(CONTROLLER + "/lab/recovery/execute",
                                    {"captureId": captured["captureId"], "guarded": guarded},
                                    token=True)
            if status != 200:
                raise AssertionError("Execution endpoint rejected replay: " + str(response))
            ok, codes = externally_verified_recovery()
            if not ok or not response["applied"] or not response["verified"]:
                raise AssertionError("Observed recovery verification failed for arm " + str(guarded))
            results.append({"arm": "ECRA" if guarded else "BASELINE",
                            "proposal": captured["action"],
                            "proposal_source": captured["proposalSource"],
                            "decision": response["decision"],
                            "applied": response["applied"],
                            "controller_verified": response["verified"],
                            "external_verified": ok,
                            "external_http_codes": codes,
                            "elapsedMillis": response["elapsedMillis"]})
        if results[0]["proposal"] != results[1]["proposal"]:
            raise AssertionError("Proposal mismatch between replay arms")
        return {"scenario": "payments_error", "captureId": captured["captureId"],
                "captureEvidence": captured["evidence"], "arms": results}
    finally:
        for service in PORTS:
            admin_fault(service, "NONE")


def denial_check():
    for service in PORTS:
        admin_fault(service, "NONE")
    # A healthy service should yield NO_OP and no mutation under ECRA.
    code, capture = call(CONTROLLER + "/lab/recovery/capture",
                         {"service": "payments", "useOllama": False}, token=True)
    if code != 200 or capture.get("action") != "NO_OP":
        raise AssertionError("Expected NO_OP for a healthy service")
    code, result = call(CONTROLLER + "/lab/recovery/execute",
                        {"captureId": capture["captureId"], "guarded": True}, token=True)
    if code != 200 or result["applied"]:
        raise AssertionError("NO_OP should never mutate the live lab")
    return {"scenario": "healthy_noop", "captured": capture, "result": result}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", default="lab/results/live-policy")
    args = ap.parse_args()
    directory = pathlib.Path(args.output)
    directory.mkdir(parents=True, exist_ok=True)
    wait_ready()
    outcome = {
        "kind": "disposable-live-http-integration-check",
        "not_a_research_conclusion": True,
        "limitations": [
            "Rule-based proposals only in CI; Ollama requires optional manual setup",
            "Recovery uses clearing a lab-injected error flag, not container restart",
            "Two replay arms share proposal but are run sequentially, not randomized",
            "No independent safety outcome rubric or production MTTR",
            "ECRA and baseline executor share an allowlisted safety boundary"
        ],
        "checks": [compare_payment_fault(), denial_check()]
    }
    (directory / "replay.json").write_text(json.dumps(outcome, indent=2) + "\n")
    print(json.dumps(outcome, indent=2))


if __name__ == "__main__":
    main()
