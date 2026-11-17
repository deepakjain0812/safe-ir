# Published study evidence guide

Scope: original v0.1.0 synthetic preprint only. The later live lab is not evidence for this paper.

| Claim group | Evidence |
|---|---|
| Experimental design and scenario counts | [Historical results](research-audit/results/historical.json), original ZIP member `safe-ir-supplement/results-offline/runs.csv` |
| Recovery, unsafe action, approval, denial and unresolved outcomes by mode | [Machine-readable ledger](PAPER-NUMBERS.csv), JSON pointers identify each exact count |
| Unit test counts | Original ZIP members under `safe-ir-supplement/test-reports/TEST-*.xml` |
| Reported HTTP checks | Original ZIP member `safe-ir-supplement/smoke-check.txt`; this is a reported check count, not a new execution |
| Fixed policy/scenario constants and external study references | [Audit configuration](osaudit.toml), with source citations; not measured outcomes |

[Extraction instrument](research-audit/extract_evidence.py) checks the original ZIP checksum and recounts deterministic outcomes. Repeats are not independent samples. RULES and UNGUARDED share the same proposal generator offline. Recovery is a synthetic oracle, not measured restoration.

The ledger covers the verified historical result fields; it is not a certified exhaustive transcription of every printed paper number. See the audit configuration for non-outcome numerical values.

[Original evidence archive](research-audit/inputs/safe-ir-supplement.zip) · [Audit reproduction](research-audit/README.md)
