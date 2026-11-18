#!/usr/bin/env bash
# SAFE-IR evidence collector — synthetic pilot only
set -euo pipefail
OUT=safe-ir-supplement
APP_PID=""
cleanup() { if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi; }
trap cleanup EXIT
fail() { echo "ERROR: $*" >&2; exit 1; }
[ -f pom.xml ] && [ -f experiments/run_experiments.py ] || fail "Run from repository root"
for c in git java mvn python3 curl zip; do command -v "$c" >/dev/null || fail "Missing tool: $c"; done
java -version 2>&1 | grep -q 'version "21' || fail "Java 21 required"
[ ! -e "$OUT" ] && [ ! -e "$OUT.zip" ] || fail "Previous evidence exists; archive it before rerunning"
[ -z "$(git status --porcelain)" ] || fail "Commit or stash changes before evidence collection"
python3 - <<'PY' || fail "Port 8080 is busy"
import socket
s=socket.socket()
try: s.bind(('127.0.0.1',8080))
finally: s.close()
PY
COMMIT="$(git rev-parse HEAD)"
MODEL="$(printenv OLLAMA_MODEL || true)"
[ -n "$MODEL" ] || MODEL=llama3.2:3b
OLLAMA="$(printenv OLLAMA_URL || true)"
[ -n "$OLLAMA" ] || OLLAMA=http://localhost:11434
REPEATS="$(printenv REPEATS_OFFLINE || true)"
[ -n "$REPEATS" ] || REPEATS=10
case "$REPEATS" in *[!0-9]*|0) fail "REPEATS_OFFLINE must be a positive integer";; esac
mkdir -p "$OUT/test-reports" "$OUT/results-offline"
{ date -u +%Y-%m-%dT%H:%M:%SZ; echo "git_commit=$COMMIT"; uname -a; java -version 2>&1; mvn -v; python3 --version; } > "$OUT/ENVIRONMENT.txt"
echo "==> Maven tests"
mvn -B clean test > "$OUT/test-reports/mvn-test.log" 2>&1 || fail "mvn test failed — inspect test-reports/mvn-test.log"
python3 - "$OUT/test-reports" <<'PY' || fail "JUnit XML report missing"
import glob,shutil,sys
reports=glob.glob('target/surefire-reports/TEST-*.xml')
assert reports, "No Surefire XML reports"
for path in glob.glob('target/surefire-reports/*.xml')+glob.glob('target/surefire-reports/*.txt'):
 shutil.copy2(path,sys.argv[1])
PY
echo "==> Packaging"
mvn -B -DskipTests package > "$OUT/mvn-package.log" 2>&1 || fail "mvn package failed"
JAR="$(find target -maxdepth 1 -name 'safe-ir-*.jar' ! -name '*.original' | head -n 1)"
[ -n "$JAR" ] || fail "No runnable JAR"
OLLAMA_MODEL="$MODEL" OLLAMA_URL="$OLLAMA" java -jar "$JAR" > "$OUT/application.log" 2>&1 &
APP_PID=$!
UP=0
for _ in $(seq 1 60); do
 if curl -fsS -m 2 http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then UP=1; break; fi
 kill -0 "$APP_PID" 2>/dev/null || break
 sleep 1
done
[ "$UP" -eq 1 ] || fail "Application health check failed"
curl -fsS http://localhost:8080/actuator/health > "$OUT/health.json"
curl -fsS http://localhost:8080/api/v1/scenarios > "$OUT/scenarios.json"
echo "==> Offline experiments"
python3 experiments/run_experiments.py --repeats "$REPEATS" --out "$OUT/results-offline" > "$OUT/results-offline/runner.log" 2>&1 || fail "Offline experiment failed"
python3 - "$OUT/results-offline/runs.csv" "$REPEATS" <<'PY' || fail "CSV missing or incomplete"
import csv,sys
with open(sys.argv[1],newline='') as f: rows=list(csv.DictReader(f))
assert len(rows)==18*int(sys.argv[2])
PY
[ -s "$OUT/results-offline/summary.json" ] || fail "Summary JSON missing"
python3 experiments/verify_policy.py > "$OUT/smoke-check.txt" 2>&1 || fail "HTTP smoke check failed"
OLLAMA_STATUS="skipped (not available)"
if curl -fsS -m 3 "$OLLAMA/api/tags" > "$OUT/ollama-tags.json" 2>/dev/null; then
 if python3 - "$OUT/ollama-tags.json" "$MODEL" <<'PY'
import json,sys
with open(sys.argv[1]) as f: tags=json.load(f)
sys.exit(0 if any(m.get('name')==sys.argv[2] or m.get('model')==sys.argv[2] for m in tags.get('models',[])) else 1)
PY
 then
  mkdir "$OUT/results-ollama"
  mv "$OUT/ollama-tags.json" "$OUT/results-ollama/ollama-tags.json"
  echo "model: $MODEL" > "$OUT/results-ollama/MODEL.txt"
  if python3 experiments/run_experiments.py --ollama --repeats 3 --out "$OUT/results-ollama" > "$OUT/results-ollama/runner.log" 2>&1; then
   [ -s "$OUT/results-ollama/runs.csv" ] && [ -s "$OUT/results-ollama/summary.json" ] || fail "Incomplete Ollama output"
   OLLAMA_STATUS="completed: $MODEL"
  else
   OLLAMA_STATUS="failed (check results-ollama/runner.log)"
  fi
 else
  OLLAMA_STATUS="skipped (model not pulled)"
  rm "$OUT/ollama-tags.json"
 fi
else
 rm -f "$OUT/ollama-tags.json"
fi
cleanup
APP_PID=""
cp collect_evidence.sh "$OUT/collect_evidence.sh"
{ echo "# SAFE-IR synthetic pilot evidence"; echo "Commit: $COMMIT"; echo "Ollama: $OLLAMA_STATUS"; echo "Artifacts: JUnit XML, logs, CSV/JSON synthetic results, HTTP smoke checks and environment."; echo "WARNING: no real remediation occurs. Repeated seeds are not independent trials and elapsedMillis is not MTTR."; } > "$OUT/README.md"
( cd "$OUT" && find . -type f ! -name MANIFEST.sha256 -print0 | sort -z | xargs -0 sha256sum > MANIFEST.sha256 )
zip -qr "$OUT.zip" "$OUT"
echo "Created $OUT.zip; source commit $COMMIT; Ollama $OLLAMA_STATUS"
