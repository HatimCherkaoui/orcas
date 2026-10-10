# Execution correlation and quality of service

## Start the ready-to-use stack

```sh
docker compose --profile prod up --build -d
```

Open `http://localhost:5601/app/dashboards#/view/orcas-quality-of-service`.
On a cold Colima start, Elasticsearch and Kibana can each take several minutes.
Compose allows a startup grace period and waits for authenticated Elasticsearch
cluster readiness before running initialization. If an earlier invocation stopped
on an unhealthy dependency, rerun the command above; persisted data is retained.
The initialization services install the indexed identifier mappings, ingest pipeline,
data views, saved execution search, and dashboard. Imports overwrite these specific
saved-object IDs on subsequent starts. Existing application records are preserved.

## Workflow quality first

The **Orcas workflow quality** dashboard selects **Service** and **Workflow name**.
Leave workflow names unselected to compare all workflows in the selected service.
The first charts summarize all selected workflows, followed by comparisons by name:
total workflow duration (mean, p50, p95, p99), completion
counts, success/failure percentages, and completions per minute. Subsequent rows
show mean/percentile latency and outcome rates for steps, REST, Kafka publish and
consume separately, database operations, and criteria evaluations.

Workflow completion measurements are emitted only after the engine finishes a
workflow. Total duration starts at the persisted workflow creation time, including
queueing and automatic retries, and survives process restarts. Counts measure
**terminal transitions**, not distinct workflow IDs: a manual replay that finishes
again contributes another terminal transition. Failed intermediate operations do
not themselves count as failed workflows. Historical traces from before this
instrumentation have no completion measurement. For an existing local Compose
database, restore them from durable `FINISH` audit records with:

```sh
python3 scripts/backfill-observability.py --before <instrumentation-rollout-time-in-UTC>
```

The import uses the recorded creation/finish timestamps, labels its source
`measurementSource: jdbc.audit`, skips workflows with native completion telemetry,
and uses deterministic audit IDs so repeated runs do not add duplicate measurements.
It exports only workflow details and correlation identifiers, not business inputs.
Use `--service` to set the service that executed the historical workflows.
Custom state stores can implement `WorkflowStateStore.startedAt` to enable total
workflow duration and completion measurements. Custom telemetry implementations
opt in with `WorkflowTelemetry.recordsCompletions()`. With completion telemetry
disabled, the engine does not query persistence for a start time.

Success and failure are **rates**, expressed as percentages; duration has
percentiles. Operation rates include retry attempts and nested instrumentation.
HTTP client duration measures response header latency; step duration also includes
subsequent response processing. Charts show the top 10 workflow/operation groups;
select a workflow name to focus on one group. Counts use compact notation and
latency uses ms/s/min; hover for exact counts and rates.

Use **Orcas execution investigation** at
`http://localhost:5601/app/dashboards#/view/orcas-execution-investigation`
for request, correlation, transaction, trace, and workflow ID selectors and
chronological trace/log tables. The saved **Execution events by identifier** search
is also available in Discover. Filter `eventType` to inspect `kafkaEvent`,
`RestCall`, `Database`, `Workflow`, `Step`, `Criteria`, `Log`, or `Dashboard`.
Logs retain their originating scope as `relatedEventType`. The KQL bar supports
combined queries, for example `workflowId: "your-id" and eventType: "Database"`.

Use **Orcas service resources** at
`http://localhost:5601/app/dashboards#/view/orcas-service-resources`
for shared CPU, JVM memory, and database connection usage by service. These
resources are shared with business code and cannot be attributed to one workflow.
CPU is an interval average; memory and active connections show the interval maximum.

Older trace operation names are normalized from the exported `Name` field. To
repair data indexed before that normalization, apply the updated ingest pipeline
with Elasticsearch `_update_by_query?pipeline=orcas-correlation` to traces missing
`operation` (or containing `<unspecified span name>`).

## Identifier contract

`CorrelationIdentifiers` computes missing `requestId`, `correlationId`,
`transactionId`, and `traceId`. HTTP aliases are `x-request-id`, `x-correlation-id`,
`x-transaction-id`, and `x-trace-id`. Values are bounded and validated; invalid
values are replaced. A valid W3C version-00 `traceparent` takes precedence over an
independent trace ID. `tracestate` is validated.

Only registered identifiers and validated trace context are captured from HTTP or
Kafka headers. W3C baggage is parsed for registered identifier entries only; its
raw header and unrelated entries are not persisted. Authorization, cookies, and
other headers are excluded. Metadata intentionally added by application code is
preserved. IDs survive context replacement, Kafka delivery, asynchronous execution,
automatic retries, manual replays, and dashboard metadata replacement.

IDs are propagated in HTTP/Kafka headers and allowlisted W3C baggage. Generated
identifiers are returned in HTTP response headers and exposed to browser callers.
The workflow details view and metadata drawer offer individual copy actions.
Docker builds default the Kibana link to localhost; set `KIBANA_PUBLIC_URL` for
another public URL, or `VITE_KIBANA_URL` when building the dashboard directly.

Register extensions before accepting traffic, using the same shared application
configuration in runtime and management services:

```java
CorrelationIdentifiers.register("businessTransactionId", "x-business-transaction-id",
    () -> UUID.randomUUID().toString());
Metadata metadata = CorrelationIdentifiers.fromHeaders(incomingHeaders);
metadata.withIdentifier("businessTransactionId", "business-42");
String id = metadata.identifier("x-business-transaction-id");
Map<String, String> identifiers = metadata.identifiers();
```

Registered extensions are indexed as keyword fields and exposed by the metadata
API. `WorkflowContext.of(input, headers)` applies the transport allowlist; use
`new WorkflowContext(input, new Metadata(codeValues))` for explicit code metadata.

## Instrumentation and resource limits

The observability starter installs scoped MDC, OTel workflow/step/criteria spans,
HTTP ingress correlation, JDBC acquisition/query/commit/rollback spans, and a
Logback OTel appender when one is not already directly attached to the root logger.
The Kafka and REST adapters propagate IDs even without the observability starter.
Spring Kafka observations and framework child spans are enriched by a correlation
span processor. Arbitrary custom operations can use the `WorkflowTelemetry` bean
and a `try (var operation = telemetry.begin(...))` scope.

JDBC instrumentation wraps the existing pooled datasource; it does not create
another pool or query the database for tracing metadata. SQL text and bound
parameters are not exported. Set `workflow.orchestrator.observability.jdbc=false`
to disable this wrapper when another JDBC agent already instruments queries.

Unique execution IDs are indexed on logs/traces and attached to OTel metric
exemplars at recording time. They are deliberately excluded from metric labels to
avoid one time series per request. In the pinned ECS exporter, per-execution metric
investigation uses indexed trace `durationMs` and `outcome`; aggregate metric
series are searchable by service, workflow name and event type, rather than execution ID.

The Collector is pinned to 0.123.0 and uses ECS mapping compatible with the pinned
Elasticsearch/Kibana 8.15.0 stack. Collector memory and batches are bounded.
Histogram export uses delta temporality. Micrometer exports JVM, CPU, HTTP and
pool metrics; the OTel SDK exports `orcas.operation.duration` and
`orcas.operation.count`. Both applications default to 100% tracing for the POC. Dashboard counts and
percentiles use indexed completion/operation spans; sampling or dropped exports
will reduce the measured population. Keep completion spans unsampled for exact
completion counts.
The stack uses the existing local POC credentials (`elastic` / `adminpassword`);
`ELASTIC_PASSWORD` can override the initial stack credentials.

Application settings:

```yaml
workflow.orchestrator.observability:
  enabled: true
  jdbc: true
  metrics-export: true
  metrics-endpoint: http://localhost:4318/v1/metrics
  metrics-interval-seconds: 30
management.opentelemetry.tracing.export.otlp.endpoint: http://localhost:4318/v1/traces
management.opentelemetry.logging.export.otlp.endpoint: http://localhost:4318/v1/logs
management.otlp.metrics.export.url: http://localhost:4318/v1/metrics
management.otlp.metrics.export.aggregation-temporality: delta
```

The example and management applications accept `OTEL_TRACES_ENDPOINT`,
`OTEL_LOGS_ENDPOINT`, `OTEL_METRICS_ENDPOINT`, and `OTEL_METRICS_ENABLED`.
For custom Logback configurations using an async wrapper around an OTel appender,
disable or replace the `workflowOpenTelemetryLogs` bean to avoid duplicate export.

## Verification

```sh
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./verify.sh
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -pl workflow-orchestrator-observability-autoconfigure -am \
  -Pobservability-stack -Dtest=ObservabilityStackIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The optional stack test starts isolated Elasticsearch, Collector and Kibana
containers. It verifies export and normalization of every category, metric export,
identifier search, and saved-object import. Normal verification also covers HTTP
→ Kafka → persisted workflow → outbound REST identity continuity, malformed IDs,
metadata privacy, extension registration, async scope restoration, and JDBC errors.

The example Compose stack bounds Kafka's heap to 256 MiB, application heaps to
256 MiB each, and Kibana's Node heap to 512 MiB for local Colima usage. Override
`KAFKA_HEAP_OPTS`, `ORCAS_JAVA_TOOL_OPTIONS`, and `KIBANA_NODE_OPTIONS` for a
deployment's workload. These are local POC defaults, not a production capacity guarantee.
