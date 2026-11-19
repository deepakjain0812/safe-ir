# Live experiment evidence — 2026-11-19

Permanent archive: https://doi.org/10.5281/zenodo.24373944

Original workflow: https://github.com/deepakjain0812/safe-ir/actions/runs/44547912097
Original artifact: 14539544665; ZIP SHA-256: ebbd67da6850fb90bec7895f43c04866b5cb36aa6945528a21cbe50c3ff7e788.

The research audit workflow retrieves the original ZIP, checks its digest, verifies semantic equality with the earlier replay transcription, extracts the seven original files without reserialization, and generates MANIFEST.sha256. Once committed, verify using `sha256sum -c MANIFEST.sha256` in this directory. Historical v0.1.0 evidence is unchanged.

The expanded replay is the five-scenario experiment; one_model_replay.json is a separate smoke result. See ../../LIVE-MODEL-REVIEW.md for interpretation and limitations.
