# SAFE-IR v0.1.0 research audit

Status: preliminary evidence verification; official osaudit not yet run.

The November 2026 preprint describes the synthetic v0.1.0 release, not the later live lab. Original source commit: bfb0f5a89c212634c268e2e42adbe85cb07190c8.

- [Original supplement](https://github.com/deepakjain0812/safe-ir/releases/download/v0.1.0/safe-ir-supplement.zip)
- Supplement SHA-256: 15784e565a8eb26b2892c7937129c7660b115a31f85d11b42ab99dea898278f7
- Evidence DOI: https://doi.org/10.5281/zenodo.24206339
- Paper DOI: https://doi.org/10.5281/zenodo.24285121
- Supplied paper PDF SHA-256: 895c1beffc458b1b0837443644235389670d4eaa8ba0395b130e4b26ce12d178

All 16 internal manifest entries verified against the supplied supplement. Recounted original CSV: RULES 60 runs, 50 recovered, 20 unsafe; UNGUARDED 60 runs, 50 recovered, 20 unsafe; SAFE_IR 60 runs, 20 recovered, zero unsafe, 20 held for approval and 20 denied. The two Surefire XML reports each contain five passing tests, no errors, failures or skips. The smoke-check artifact reports four passing checks.

These are historical, deterministic synthetic observations, not independent trials or production restoration measurements. No retrospective preregistration or historical osaudit provenance is asserted.

Remaining: stage the exact paper and original evidence, create a reproducible extraction instrument and audit configuration, check all paper numbers and citations, run the official auditor, resolve findings, and verify endorsement eligibility. Do not interpret this preliminary verification as an official audit pass.
