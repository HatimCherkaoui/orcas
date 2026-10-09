# First POC

The development version is `0.6.0-SNAPSHOT`. This POC exercises the workflow runtime,
persisted dashboard state, manual replay, transient retries and circuit breaker recovery.

## Build and package

```bash
./verify.sh
mvn -Drelease -Dgpg.skip=true -DskipTests package
python3 scripts/package-poc.py
```

The ZIP and its SHA-256 checksum are written under `target/`. It contains the public
source tree, library binaries with source/Javadoc JARs, the executable example and
management JARs, and the built dashboard. `manifest.sha256` records the contents.
Local agent instructions, Git metadata, credentials, caches and test reports are excluded.
Unzip it and run Compose from its `source/` directory to rebuild the demonstration stack:

```bash
docker compose --profile prod up -d --build
./example-app/demo.sh
```

## Scenarios

`OrderWorkflowIntegrationTest` uses Kafka, PostgreSQL and WireMock Testcontainers:

| Scenario | Expected result |
| --- | --- |
| 50 concurrent orders | 50 orders and payments, with successful workflows |
| HTTP 502, then success | Automatic replay; payment created after success |
| HTTP 429 → 503 → 504 → success | Three scheduled retries, then success |
| Connection reset, then success | Network failure automatically replayed |
| Persistent HTTP 503 | Retries exhaust; step and workflow fail |
| HTTP 400 or 500 | Step and workflow fail without automatic replay or payment creation |
| Refund gateway fails twice | Breaker suspends, waits for cooldown, probes half-open and recovers |
| Manual workflow or step retry | Dashboard API replays the failed step through Kafka |

Provider mappings live in `wiremock/mappings`. Numeric amounts select payment scenarios;
refunds use sequential WireMock scenario states. Tests assert request counts and durable
workflow states, as well as business data. The dashboard step panel exposes the failure
category, status/exception code and brief message.

Local Docker throughput is a regression baseline. EC2/RDS sizing requires measurements
with the actual instance classes, connection limits, payloads and business services.
