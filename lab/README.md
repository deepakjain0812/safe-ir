# SAFE-IR v0.2.0 — Live Recovery Lab (Milestone 1)

This module extends the existing SAFE-IR v0.1.0 **synthetic policy prototype** with disposable Spring Boot services and real HTTP measurements. It is *not* a production remediation system, does not execute LLM proposals, and does not validate an ECRA safety benefit yet.

## Architecture

- **Orders** (loopback port 18081) invokes Payments via Docker networking.
- **Payments** (loopback port 18082) invokes Inventory.
- **Inventory** (loopback port 18083) handles requests without a downstream dependency.
- Each is the *same* Java 21/Spring Boot application image, configured with a different `LAB_ROLE` and downstream URL. They run as distinct isolated Docker containers.
- `GET /api/work` makes actual synchronous HTTP requests. A downstream HTTP failure causes the caller to return HTTP 502. All three expose `GET /actuator/health` and `GET /lab/metrics`.
- `POST /lab/admin/fault` injects a real, bounded in-process error or latency effect. The lab script performs a reset and then measures **new** HTTP responses, rather than looking up an expected outcome in the old simulator.

## Start

Requirements: Docker Compose v2, Docker Engine, Python 3.10+; JDK 21 and Maven 3.9+ if running JUnit locally.

From repository root:

```sh
mvn -f lab/pom.xml clean test
docker compose -f docker-compose.lab.yml up --build -d
python3 lab/experiments/verify_recovery.py --output lab/results/verification
python3 lab/experiments/workload.py --service orders --requests 50 --output lab/results/workload
docker compose -f docker-compose.lab.yml down -v
```

On macOS and Linux with Docker Desktop, these loopback-only ports should be available to the host: 18081, 18082, 18083. The Docker network is a dedicated bridge with loopback-only published ports; do not connect production resources to it. The application containers have read-only root filesystems, a non-root user, and no Docker socket mount.

## Inspect real service behavior

```sh
curl http://127.0.0.1:18081/api/work
curl http://127.0.0.1:18082/lab/metrics
curl http://127.0.0.1:18083/actuator/health
```

### Inject and reset a fault

This deliberately uses an explicit lab-only static token; it is **not a production authentication mechanism**.

```sh
curl -X POST http://127.0.0.1:18082/lab/admin/fault \
  -H 'X-Lab-Token: local-experiment-only' -H 'Content-Type: application/json' \
  -d '{"mode":"ERROR","delayMs":0}'
curl -i http://127.0.0.1:18081/api/work
curl -X POST http://127.0.0.1:18082/lab/admin/fault \
  -H 'X-Lab-Token: local-experiment-only' -H 'Content-Type: application/json' \
  -d '{"mode":"NONE","delayMs":0}'
curl -i http://127.0.0.1:18081/api/work
```

When Payments returns HTTP 503, Orders returns HTTP 502; after resetting Payments to NONE, Orders returns HTTP 200 again. This is **real HTTP application behavior** within the disposable lab. Spring Actuator stays healthy during application-level faults; **Actuator health alone is not used as proof of recovery**.

## Reproducible verification

`verify_recovery.py` first confirms that the services respond, then performs three scenarios:

1. Orders injected 503; Orders client detects failure and then restoration.
2. Payments injected 503; Orders detects the downstream failure, then restoration.
3. Inventory injected +900 ms latency; Orders observes the additional latency, followed by latency returning to near baseline.

For each scenario, the tool resets all services, collects baseline HTTP samples, injects a fault, collects degraded samples, resets the fault, and collects restored samples. It checks actual HTTP status codes and measured latency. Results are saved as `http_observations.csv` and `recovery_verification.json`.

`workload.py` generates separate sequential client requests, reporting HTTP statuses and p50/p95 measured request durations in `requests.csv` and `summary.json`.

The code intentionally does not use the v0.1.0 synthetic recovery oracle. It also does not instruct any model to operate the services.

## Research boundaries

- This milestone validates a **fault injection and verification harness**, not autonomous AI incident recovery.
- A fault is remediated by **an explicit trusted reset of the lab fault flag**, not restart of a real failed process or AI-selected recovery. This is a deliberately narrow first step.
- `observed_reset_and_probe_ms` includes the admin reset and follow-up probes; it is **not** full MTTR measured from fault onset.
- The client request latency samples are actual observed HTTP durations but may vary with CI runner resource contention.
- There is no ECRA-on/off comparison in this lab yet, no real Ollama proposal, no external telemetry backend, no approval workflow, and no evidence of production performance.
- Further milestones should add fault classes, pre-registered independent safety oracles, signed/allowlisted remediation, tracing/metrics exports, matched proposal replay, and independently sampled workloads.

The CI workflow `.github/workflows/live-lab.yml` runs Maven tests, builds the Docker lab, validates recovery, and uploads logs/results. **Do not cite success until that workflow completes successfully.**
