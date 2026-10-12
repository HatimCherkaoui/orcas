# Runtime tuning and load testing

[Back to the project README](../README.md)

### Faster Compose builds and verification

Compose builds both backend JARs once through `Dockerfile.backend`, using two
Maven reactor workers and a persistent BuildKit dependency cache. The frontend
build runs independently and caches npm downloads. Context allowlists exclude
tests, local output, IDE files and documentation from image builds. Runtime
images contain only their application JAR or static frontend assets.

Image packaging skips test compilation; run `./verify.sh` before publishing.
Verification runs Maven modules with two workers and frontend checks concurrently,
stopping the other task on failure. Test methods within a module stay sequential
because integration scenarios share database and Kafka state. Set `MAVEN_THREADS`
to tune concurrency and `VERIFY_TIMEOUT_SECONDS` to change the 900-second limit.
Image builds stop after 300 seconds; Docker build arguments `MAVEN_THREADS` and
`BUILD_TIMEOUT_SECONDS` are configurable. Maven uses `package`/`verify`, avoiding
unnecessary local-repository installs and clean rebuilds.

For a bounded build and startup, run `python3 scripts/compose-up.py prod`.
It requires Buildx, stops on the first failed command, caps the whole build at
450 seconds and startup at 600 seconds (configurable with
`COMPOSE_BUILD_TIMEOUT_SECONDS` / `COMPOSE_START_TIMEOUT_SECONDS`).

In IntelliJ's Docker Compose run configuration, use `docker-compose.yml` and the
chosen profile with **Build images** enabled. Remove any redundant Maven
`clean install` before-launch tasks; Compose builds the JARs itself. Existing
personal IDE configurations are not checked into this repository.

On a Colima VM with only 4 GB RAM, the full production stack competes with the
Maven builder for memory. For a rebuild that changes Java sources, temporarily
stop the two application containers, then run the bounded launcher (which
restarts them):

```sh
docker compose --profile prod stop example-app workflow-management-service
python3 scripts/compose-up.py prod
```

Volumes and infrastructure services are retained. This is a local development
workflow; schedule downtime appropriately when using it on a deployed instance.


### Colima resource tuning

The included Compose stack targets a small local VM. Kafka uses a 256 MiB heap,
a 32 MiB cleaner deduplication buffer, two network threads and four I/O threads.
The example runtime uses two status consumers and one replay consumer; management
publishes replay commands but does not consume runtime topics. Existing topic
partitions and consumer offsets are preserved. Producer acknowledgements and
idempotence remain enabled.

Workflow listener settings under `workflow.orchestrator.kafka`:

| Setting | Default | Purpose |
| --- | --- | --- |
| `concurrency` | 2 | Status consumers per application |
| `replay-concurrency` | 1 | Independent replay consumers |
| `max-poll-records` | 25 | Limit work between polls |
| `fetch-max-bytes` | 4194304 | Fetch response target per consumer |
| `max-partition-fetch-bytes` | 1048576 | Fetch target per partition |
| `consumers-enabled` | true | Disable consumers in management-only deployments |
| `observation-enabled` | false | Optional Spring Kafka observation in addition to Orcas spans |

Fetch byte limits are targets: Kafka can return an oversized first batch to make
progress. Size payloads and producer buffers accordingly. Small poll batches
reduce the risk of long synchronous workflow steps exceeding the poll interval;
configure `spring.kafka.consumer.properties.max.poll.interval.ms` for the maximum
processing time of an entire poll batch, including downstream timeouts/retries.

Compose overrides include `WORKFLOW_KAFKA_CONCURRENCY`, `WORKFLOW_ASYNC_CONCURRENCY`,
`DB_POOL_MAX_SIZE`, `KAFKA_HEAP_OPTS`, `KAFKA_JVM_PERFORMANCE_OPTS`,
`KAFKA_NETWORK_THREADS`, `KAFKA_IO_THREADS`, `KAFKA_RETENTION_HOURS`,
`ORCAS_JAVA_TOOL_OPTIONS`, and `ES_JAVA_OPTS`. The local broker retention defaults
to seven days; increase it for longer outage recovery windows. JVM processor counts
are bounded to two to avoid excessive worker and garbage collector threads.
WireMock keeps at most 1,000 journal entries. Application producer buffers use
8 MiB and LZ4 compression. Database pools default to six connections for the
Compose example and four for management.

Use `--profile dev` when Elasticsearch and Kibana are unnecessary. The full
`prod` demonstration also hosts both observability services; on a two CPU,
4 GiB VM they compete with workflow execution. These settings reduce resource
contention but do not provide production cluster redundancy or a throughput
guarantee. Scale runtime consumers, pools and broker resources together after
measuring latency and consumer lag on the target infrastructure.


### Load testing the full Compose stack

With `docker compose --profile prod up -d` healthy, run:

```sh
python3 scripts/compose-load.py --stages 50:10,200:25   --output /private/tmp/orcas-compose-prod-load.json
```

This creates an isolated inventory SKU and 250 orders using the seeded customer,
then launches 250 payment-success callbacks. It leaves the
orders and workflow history available for dashboard investigation. Unique request
IDs and a shared correlation ID isolate each run from existing records. No tables,
Kafka offsets or WireMock scenarios are reset.

The script verifies successful persisted workflows, unique orders, payment records
and confirmed business states. It reports HTTP and workflow duration mean/p50/p95/p99,
throughput, and sampled container CPU/memory. `--timeout` sets the completion deadline
per leg; HTTP requests have a 65-second timeout. Completion polling every three seconds
makes elapsed throughput conservative. Results measure this Compose deployment and
its mock services; they are not a cloud capacity estimate.

With `--verify-traces`, the load test also requires exactly one successful native
Elasticsearch completion per workflow and reports its end-to-end latency, including
Kafka delivery to the terminal-event consumer. The database duration uses the
terminal event timestamp; completion trace latency additionally includes final
consumer processing. Set `ELASTIC_PASSWORD` for a customized Elasticsearch password.
Nested executor submissions run on their current worker to preserve the concurrency
bound without blocking on their own permits. `RUNNING_ASYNC` is an active state and
does not produce workflow completion records.

The local broker uses a JVM-free Kafka protocol health probe, a 30-second broker
session lease and a 10-second controller request timeout. Relative CPU shares favor
Kafka over analytics during contention. Publishers await Kafka’s definitive result,
bounded by `delivery.timeout.ms=30000`, with request and buffer waits of 10 seconds.
See the [Kafka broker configuration](https://kafka.apache.org/43/configuration/broker-configs/).

The 10 October 2026 full-profile load run on Colima (2 CPUs, 4 GiB) completed
500/500 workflows and confirmed 250 orders and payments, with zero final status
consumer lag. At 25 concurrent clients, persisted workflow p95 duration was 28.2s
for orders and 13.0s for callbacks. Full observability validation failed: the
2,048-span OpenTelemetry queue overflowed, reporting 16,555 dropped spans; only
315/500 completion traces reached Elasticsearch. That run exposed a telemetry export limitation, addressed by the queue and
indexing changes below; validate completeness for each new load cohort. These results are a local burst
measurement with WireMock, not a production capacity guarantee.

### Persisted transition and telemetry cost

Workflow creation and step snapshot persistence each use one PostgreSQL command,
including their audit rows. JDBC step results persist their step snapshot and workflow context in one
retried transaction. Context and metadata updates, audit snapshots and the workflow
timestamp share one PostgreSQL command. Status transitions also combine the step
mutation, workflow status, metadata and their audit rows in one command. Step-context reads join the workflow name
instead of borrowing another connection inside the result mapper. Custom state
stores retain the default `saveStepResult` fallback.

Standalone orchestration writes use a JDBC transaction manager, avoiding Hibernate
session creation for each transition. Writes inside an existing business transaction
still use the application's manager and roll back with the business operation.
Set `workflow.orchestrator.jdbc.local-transactions=false` when a custom transaction
manager must handle standalone state writes too.

The Compose runtime uses four status consumers, four bounded async workers and six
database connections. The library consumer default remains two. Producer linger is
zero by default in the example apps (`KAFKA_PRODUCER_LINGER_MS` can override it).
Application spans export in batches up to 2,048 with an 8,192-span queue and a
500 ms scheduling delay, configured through `OTEL_SPAN_BATCH_SIZE`,
`OTEL_SPAN_QUEUE_SIZE` and `OTEL_SPAN_SCHEDULE_DELAY`. The collector batches up to
2,048 records, waiting at most five seconds for small batches. These bounded settings reduce request overhead and absorb bursts;
exporter queues still require monitoring. The collector uses synchronous bulk export
with two consumers and a bounded, disk-backed queue of 128 batches per signal.
This removes the legacy buffer handoff's five-second timeout and survives collector
restarts while Elasticsearch catches up. All SQL mutations remain audited and
Kafka acknowledgement is still required before progressing workflows.

The full profile reserves a 512 MiB Elasticsearch heap to reduce indexing GC
pressure. Backend, database and observability health probes run every 30 seconds; HTTP probes have explicit
connection and response deadlines, avoiding abandoned curl processes under load.
Metrics use `metrics-orcas-runtime` with a dedicated template that preserves dotted
field names (`subobjects: false`); this prevents conflicts between names such as
`records.lag` and `records.lag.max`. Historical streams remain accessible through
the `metrics-orcas-*` data view. See [Elastic's metrics mapping guidance](https://www.elastic.co/guide/en/elasticsearch/reference/8.15/subobjects.html).
`--trace-timeout` defaults to 120 seconds to allow asynchronous indexing to catch up
after the last business workflow finishes; it does not change measured duration.

SQL execution, commit and rollback spans remain enabled. Pool acquisition spans
are optional (`workflow.orchestrator.observability.jdbc-connection-spans=true`);
Hikari metrics retain connection saturation and wait measurements without a span
for every borrow. Identifier alias lookups and registered-key sets are cached.
Kafka fetch limits are applied to each actual listener container, since Spring
does not copy consumer properties from the listener factory configuration.

Trace and log indices retain complete OTLP payloads in `_source`, while indexing
normalized identifiers, workflow, operation, duration, outcome and event type.
Raw `Attributes`, `Resource`, `Scope`, `Link` and `Event` objects are not indexed.
Use root `httpStatusCode`, `dbOperation`, `exceptionType` and `exceptionMessage`
fields for searches. Templates use a 10-second refresh interval to reduce segment
creation; allow this delay when investigating recent executions. ECS metrics also
normalize flat `service.name` fields, and bucketless timers retain sum and count
without requiring percentile buckets. Percentiles require bucketed histograms or
individual duration spans.
