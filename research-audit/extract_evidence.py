import csv, hashlib, io, json, sys, zipfile
from collections import Counter
from xml.etree import ElementTree as ET

EXPECTED = "15784e565a8eb26b2892c7937129c7660b115a31f85d11b42ab99dea898278f7"

def extract(path):
    with open(path, "rb") as handle:
        data = handle.read()
    if hashlib.sha256(data).hexdigest() != EXPECTED:
        raise ValueError("Original release ZIP checksum mismatch")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        prefix = "safe-ir-supplement/"
        rows = list(csv.DictReader(io.StringIO(archive.read(prefix+"results-offline/runs.csv").decode())))
        groups = {}
        for mode in sorted({r["mode"] for r in rows}):
            subset = [r for r in rows if r["mode"] == mode]
            counts = Counter(r["scenario"] for r in subset)
            assert len(set(counts.values())) == 1
            repeats = next(iter(counts.values()))
            for scenario in counts:
                outcomes = {(r["recovered"], r["unsafeExecuted"], r["decision"]) for r in subset if r["scenario"] == scenario}
                assert len(outcomes) == 1
            groups[mode] = {
                "runs": len(subset),
                "recovered_scenarios": sum(r["recovered"] == "True" for r in subset)//repeats,
                "unsafe_scenarios": sum(r["unsafeExecuted"] == "True" for r in subset)//repeats,
                "approval_scenarios": sum(r["decision"] == "REQUIRE_APPROVAL" for r in subset)//repeats,
                "denied_scenarios": sum(r["decision"] == "DENY" for r in subset)//repeats,
                "unresolved_scenarios": sum(r["recovered"] == "False" for r in subset)//repeats,
            }
        tests = []
        for name in archive.namelist():
            if name.startswith(prefix+"test-reports/TEST-") and name.endswith(".xml"):
                root = ET.fromstring(archive.read(name))
                tests.append({k: int(root.attrib[k]) for k in ("tests","failures","errors","skipped")})
        smoke = archive.read(prefix+"smoke-check.txt").decode().strip()
        if smoke != "Four HTTP integration checks passed":
            raise ValueError("Unexpected smoke-check artifact")
        return {"runs": len(rows), "scenarios": len({r["scenario"] for r in rows}), "modes": len(groups),
                "repeats": len(rows)//(len(groups)*len({r["scenario"] for r in rows})),
                "outcomes": groups, "junit_tests": sum(t["tests"] for t in tests),
                "junit_passed": sum(t["tests"]-t["failures"]-t["errors"]-t["skipped"] for t in tests),
                "http_checks_reported_passed": 4}

if __name__ == "__main__":
    print(json.dumps(extract(sys.argv[1]), indent=2, sort_keys=True))
