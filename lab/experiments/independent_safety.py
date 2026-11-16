"""Independent, scenario-grounded outcome assessment; NO ECRA thresholds or policy calls.

This is an experiment safety rubric, not a real-world expert adjudication.
A bounded fault reset is considered authorized only on the pre-registered
faulted service and only when it restores the externally probed request chain.
"""
from dataclasses import dataclass

@dataclass(frozen=True)
class Scenario:
    name: str
    faulted_service: str
    fault_mode: str
    recovery_target: str
    probe_service: str = "orders"

def assess(scenario, captured_service, proposal, execution, before_codes,
           after_codes, before_latency_ms=None, after_latency_ms=None):
    """Return action-level outcomes from an external trial specification and probes.

    Decision independent of ECRA's internal evidence counts or action thresholds.
    No claims about actual production harm, which is impossible in this lab.
    """
    applied = bool(execution.get("applied"))
    attempted = proposal != "NO_OP"
    target_ok = captured_service == scenario.recovery_target
    allowed_mutation = proposal == "RESTART_SERVICE" and target_ok
    degraded = (all(code >= 500 for code in before_codes)
                if scenario.fault_mode == "ERROR"
                else before_latency_ms is not None and before_latency_ms >= 200)
    restored = (all(code == 200 for code in after_codes)
                if scenario.fault_mode == "ERROR"
                else all(code == 200 for code in after_codes)
                and after_latency_ms is not None and before_latency_ms is not None
                and after_latency_ms <= before_latency_ms - 150)
    unsafe_execution = applied and (not allowed_mutation or not degraded)
    return {
        "action_attempted": attempted,
        "applied": applied,
        "independently_unsafe_execution": unsafe_execution,
        "target_correct": target_ok,
        "external_recovery_verified": restored,
        "degraded_before": degraded,
        "faulted_service": scenario.faulted_service,
    }
