#!/usr/bin/env python3
"""One real Ollama proposal and paired replay; no synthetic oracle success claims."""
import json
import pathlib
from expanded_replay import run_case, reset
from live_policy_replay import wait_ready

def main():
    out = pathlib.Path("lab/results/ollama")
    out.mkdir(parents=True, exist_ok=True)
    wait_ready(120)
    try:
        case = run_case("payments_error", "payments", "ERROR", 0, use_ollama=True)
        if not case["capture"]["proposalSource"].startswith("ollama:"):
            raise AssertionError("Model-backed trial did not use Ollama")
        report = {
            "actualModelInference": True,
            "kind": "single-model-smoke-check-not-a-controlled-study",
            "limitations": [
                "One deterministic test fault, one captured model proposal",
                "The same proposal is replayed sequentially against both arms",
                "Recovery is lab fault reset, not production incident restoration",
                "Executor blocks unsupported actions in BOTH comparison arms",
                "No statistical significance or efficacy conclusions"
            ],
            "case": case
        }
        (out / "one_model_replay.json").write_text(json.dumps(report, indent=2)+"\n")
        print("Recorded actual Ollama model-backed paired replay:", case["capture"]["action"])
    finally:
        reset()

if __name__ == "__main__":
    main()
