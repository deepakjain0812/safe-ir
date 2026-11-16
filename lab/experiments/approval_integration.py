#!/usr/bin/env python3
"""End-to-end SAFE-IR lab authorization regression suite; requires running Docker lab."""
import json
import os
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18080/lab/recovery"
FAULT = "http://127.0.0.1:18082/lab/admin/fault"
LAB = os.environ.get("SAFEIR_LAB_TOKEN", "local-experiment-only")
REVIEWER = os.environ.get("SAFEIR_LAB_REVIEWER_TOKEN", "")
TTL = int(os.environ.get("SAFEIR_TEST_TTL_SECONDS", "45"))
if not REVIEWER or REVIEWER == LAB:
    raise SystemExit("Set a separate SAFEIR_LAB_REVIEWER_TOKEN")
if not 25 <= TTL <= 90:
    raise SystemExit("Set SAFEIR_TEST_TTL_SECONDS between 25 and 90 and configure matching Compose TTL")

def call(url, data=None, reviewer=False):
    headers = {"X-Reviewer-Token": REVIEWER} if reviewer else {"X-Lab-Token": LAB}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(),
                                 headers=headers, method="POST" if data is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=65) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        return exc.code, json.loads(raw) if raw else {}

def require(condition, detail):
    if not condition:
        raise AssertionError(detail)

def fault(mode, delay=0):
    status, result = call(FAULT, {"mode": mode, "delayMs": delay})
    require(status == 200, ("fault", status, result))

def capture(action=None):
    payload = {"service": "payments", "testAction": action} if action else {"service": "payments", "useOllama": False}
    status, result = call(BASE + "/capture", payload)
    require(status == 200 and result.get("captureId"), ("capture", status, result))
    return result["captureId"]

def execute(cid):
    status, result = call(BASE + "/execute", {"captureId": cid, "guarded": True})
    require(status == 200, ("execute", status, result))
    return result

def approval(cid):
    return call(BASE + "/approval/" + cid, reviewer=True)

def decide(cid, approve):
    return call(BASE + "/approval/decide", {
        "captureId": cid, "approve": approve, "reason": "Automated disposable lab verification"
    }, reviewer=True)

def pending_scaling():
    fault("LATENCY", 1800)
    cid = capture("SCALE_UP")
    result = execute(cid)
    require(result["decision"] == "REQUIRE_APPROVAL" and not result["applied"], ("pending", result))
    status, record = approval(cid)
    require(status == 200 and record["status"] == "PENDING", ("pending record", status, record))
    return cid

try:
    print("A: ALLOW and verified fault reset", flush=True)
    fault("ERROR")
    cid = capture()
    result = execute(cid)
    require(result["action"] == "RESTART_SERVICE" and result["decision"] == "ALLOW"
            and result["applied"] and result["verified"] and result["after"]["failed"] == 0, result)
    print("PASS A", flush=True)

    print("C: DENY destructive action", flush=True)
    fault("ERROR")
    cid = capture("DELETE_DATABASE")
    result = execute(cid)
    require(result["decision"] == "DENY" and not result["applied"] and not result["verified"], result)
    print("PASS C", flush=True)

    print("B1: REQUIRE_APPROVAL then reviewer rejection and duplicate protection", flush=True)
    cid = pending_scaling()
    status, result = decide(cid, False)
    require(status == 200 and result["approval"]["status"] == "REJECTED"
            and not result["applied"], (status, result))
    status, _ = decide(cid, True)
    require(status == 409, ("duplicate", status))
    print("PASS B1", flush=True)

    print("B2: approval invalidated when measured evidence changes", flush=True)
    cid = pending_scaling()
    fault("NONE")
    status, result = decide(cid, True)
    require(status == 200 and result["approval"]["status"] == "INVALIDATED"
            and not result["applied"], (status, result))
    print("PASS B2", flush=True)

    print("B3: approved request never executes unsupported SCALE_UP", flush=True)
    cid = pending_scaling()
    status, result = decide(cid, True)
    require(status == 200 and result["approval"]["status"] == "APPROVED"
            and not result["applied"] and not result["verified"], (status, result))
    print("PASS B3", flush=True)

    print("B4: expired request cannot be approved", flush=True)
    cid = pending_scaling()
    time.sleep(TTL + 2)
    status, result = decide(cid, True)
    require(status == 409 and result.get("error") == "Approval expired", (status, result))
    status, record = approval(cid)
    require(status == 200 and record["status"] == "EXPIRED", (status, record))
    print("PASS B4", flush=True)
    print("PASS: all SAFE-IR live authorization scenarios", flush=True)
finally:
    fault("NONE")
