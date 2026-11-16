# Milestone 2: ECRA and restricted live replay (evaluation harness)

This is an **engineering integration** of existing SAFE-IR ECRA and Ollama proposal logic with the Milestone 1 Docker lab. It is not proof that ECRA improves real-world incident safety.

## Requirements

- Docker Engine, Docker Compose v2, Java 21, Maven 3.9+, Python 3.10+.
- Use only a disposable local test environment. Never add these services to a production network.
- Ollama is optional. **GitHub Actions intentionally runs the deterministic rule-based proposal path**, not LLM inference.

## Start the lab and collect matched replay evidence

```sh
mvn -B -ntp clean test
docker compose -f docker-compose.lab.yml up --build -d
python3 lab/experiments/live_policy_replay.py --output lab/results/live-policy
docker compose -f docker-compose.lab.yml down -v
```

The recovery controller is bound to **127.0.0.1:18080**; Orders, Payments, and Inventory remain on ports 18081, 18082, and 18083 respectively. The controller is disabled in the normal SAFE-IR application unless `SAFEIR_LAB_ENABLED=true`. Its REST API requires the static lab-only `X-Lab-Token`; the token is deliberately non-secret and must never be used to secure a deployed service.

### APIs

- `POST /lab/recovery/capture`, JSON `{"service":"payments","useOllama":false}`: gather five actual HTTP observations from Payments, convert status/latency into the existing ECRA incident format, generate a proposal **once**, and return a capture ID.
- `POST /lab/recovery/execute`, JSON `{"captureId":"<uuid>","guarded":true}`: reuse exactly that captured proposal; fresh telemetry is collected for that arm, and ECRA authorizes or blocks it. With `guarded:false`, ECRA is not applied but the same hardcoded lab safety boundary still applies.
- Captures expire after 10 minutes; each capture ID can be used once per arm (BASELINE, ECRA). The runner independently resets injected faults before each arm.
- The executor supports only an injected fault flag reset on one of the three preapproved lab services, **for a captured RESTART_SERVICE proposal, with complete HTTP observations and an active failure**. This is intentionally NOT a real container restart. NO_OP never mutates; SCALE_UP, PURGE_QUEUE, DELETE_DATABASE and any other action cannot execute.
- No arbitrary hostnames, shell commands, Docker API privileges, external credentials, or unrestricted deployment endpoints are accepted.

### Run optional Ollama locally

Ollama is not required to run integration CI. To try actual model proposal generation, you can run it locally and enable the Compose profile:

```sh
docker compose -f docker-compose.lab.yml --profile ollama up --build -d
docker compose -f docker-compose.lab.yml exec ollama ollama pull llama3.2:3b
curl -s -X POST http://127.0.0.1:18080/lab/recovery/capture \
 -H 'X-Lab-Token: local-experiment-only' \
 -H 'Content-Type: application/json' \
 -d '{"service":"payments","useOllama":true}'
```

Inject a Payments fault *before* capture if you want the proposal to reflect an active failure. Never count optional Ollama proposals as tested in GitHub Actions until a separate measured run with model version, hash, runtime and output trace exists.

### Research limitations

- Five HTTP probes are only a coarse signal. The current ECRA rule checks errorRate > 0.2 and telemetry completeness; the policy has not been recalibrated against real workloads.
- Real HTTP status and latency data feed the ECRA model, but there is no full distributed tracing backend or queue-depth instrumentation.
- ECRA's RESTART_SERVICE maps to a **lab reset operation**. The action semantics are narrower than production restart.
- The comparison is a **paired proposal smoke test** with sequentially reset fault states, not a controlled statistical experiment.
- The CI baseline and ECRA arms share a hardcoded allowlisted executor. Consequently their zero unsafe executions, if observed, do not establish ECRA superiority.
- Production recovery performance, independent unsafe-action labels, model variability, cost, randomization, statistical power, and approval workflows remain outside this milestone.
- A verified flag is based on post-reset HTTP probes, not on a synthetic outcome oracle. The Python runner independently verifies the Orders request chain as well.

See `lab/results/live-policy/replay.json` from the workflow's uploaded artifacts. Do not claim benchmark findings until CI passes and the experimental design is strengthened.

## Extended safety and fault matrix (PR #5 extension)

Run:
```sh
python3 -m unittest discover -s lab/experiments -p 'test_*.py' -v
python3 lab/experiments/expanded_replay.py --output lab/results/expanded
```

The matrix records three real HTTP error faults (Orders, Payments, Inventory)
and two measured latency injections. Each captured proposal is replayed once
to each arm after resetting and reproducing the same injected fault. A separate
Python rubric, `independent_safety.py`, defines incorrect mutation using a
**pre-registered scenario target** rather than ECRA's numeric thresholds.
Adversarial injections of DELETE_DATABASE, PURGE_QUEUE and SCALE_UP are exercised
under both arms. The hardcoded executor refuses them regardless of ECRA, so a
lack of unsafe execution does NOT demonstrate ECRA improves safety over baseline.

Important: the rubric is independent of ECRA source code but is still written
by the project's author, not third-party adjudicated. Some latency scenarios
remain degraded because scaling is deliberately unsupported. Such cases are
reported as failures to restore, not counted as recovered.

### Real Ollama run (separate, explicitly activated GitHub workflow)

The `Ollama model integration` workflow is an **opt-in GitHub Actions workflow**.
It starts the Ollama profile, pulls `llama3.2:1b` (override supported by
the lab Compose model variable), performs actual model-backed proposals in the
five live fault scenarios, records each model's returned action and explanation,
and runs the independent rubric. It records the Ollama version, model details,
and immutable model digest in CI artifacts. This job may be slow and requires
outbound access to the model registry. Do not claim model results unless that
workflow completes and its artifacts are inspected.

Neither the rule-based CI nor the Ollama job provides a statistical comparison:
same captured proposal, but fixed scenarios, sequential replay, one model and
author-defined safety rubric.
