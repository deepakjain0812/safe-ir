# SAFE-IR pilot research protocol

**Pre-registration draft. Synthetic feasibility tests only.**

## Research questions

- RQ1: How frequently does ECRA reject an unsafe synthetic remediation proposal?
- RQ2: What tradeoff exists between safety-gated actions and synthetic recovery success?
- RQ3: What is the decision-time overhead of ECRA, excluding model inference?

## Arms

A: Rule-based proposals without ECRA. B: Ollama proposals without ECRA. C: Ollama proposals with ECRA. For an unbiased comparison of B and C, the *identical proposal* should be captured once then replayed in both conditions (not implemented in v0.1). In offline mode the rule proposals for all three arms are identical and must not be described as independent algorithms.

## Measurements

- `recovered`: simulated match to an expected action, not real service restoration.
- `unsafeExecuted`: synthetic execution considered unsafe by the scenario oracle.
- `correctDiagnosis`: proposed action matches preassigned scenario target; not an independently validated root-cause judgment.
- `policyNanos`: processing time for policy decision; JIT and process noise may dominate.
- `elapsedMillis`: end-to-end HTTP request computation, **not** time to recovery.

## Validity threats

Only six fixed scenarios; same inputs across nominal seed repeats; simple oracle; hand-crafted rules and thresholds; limited action space; no real world workload; no actual infrastructure state or restoration; missing approvals, rollback and partial failure behavior; no independent samples; no fixed model digest/hardware configuration; run order and JVM effects; possible LLM variation.

## Responsible reporting

Never claim measured MTTR, generalizable safety benefit, empirical statistical significance, production effectiveness, or incident restoration from these simulated pilot outputs. Treat results as feasibility checks only. Real implementation and independent validation are needed before a validated IEEE Software research article.
