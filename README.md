# 🐋 Orcas — Workflow Orchestrator

**Java 25 · Spring Boot 4**

> Like an orca pod, Orcas is built to be **lightweight**, **reliable**, **resilient** and **scalable** — a small, dependency-conscious workflow engine that coordinates sequential, parallel and asynchronous steps across your services, backed by JDBC (no JPA/Hibernate) and Kafka, with first-class observability and a modern interactive dashboard.

Orcas is a *library*, not a platform: it ships as a Spring Boot starter you drop into your own application. It never creates or owns your `DataSource`, never forces JPA/Hibernate, and every infrastructure bean it registers can be replaced with your own implementation of the same contract.

---

## Contents

- [Why Orcas](#why-orcas)
- [Features](#features)
- [How it works](#how-it-works)
- [Technologies used](#technologies-used)
- [Project modules](#project-modules)
- [Quick start (Docker Compose)](#quick-start-docker-compose)
- [Annotation-first application API](#annotation-first-application-api)
- [Functional infrastructure steps](#functional-infrastructure-steps)
- [Retry, replay & circuit breaking](#retry-replay--circuit-breaking)
- [Logging & tracing](#logging--tracing)
- [Operational REST API](#operational-rest-api)
- [The dashboard](#the-dashboard)
- [Contributing](#contributing)
- [Credits](#credits)

---

## Why Orcas

Workflow/orchestration engines tend to be heavy: they bring their own database schema management, their own UI server, their own opinionated persistence layer, and they're hard to embed into an existing Spring Boot application without a big rewrite.

Orcas takes the opposite approach:

- **Lightweight** — core module has essentially zero dependencies beyond SLF4J; the Spring Boot starter reuses beans your application already has (`DataSource`, `NamedParameterJdbcTemplate`, Kafka producer/consumer factories) instead of creating parallel infrastructure.
- **Reliable** — every workflow/step transition is persisted through JDBC before it's considered committed; Kafka is used to drive asynchronous/distributed step execution with at-least-once semantics.
- **Resilient** — built-in retry policies (global and per-step), automatic error classification (`REPLAYABLE` vs `SUSPEND`), and optional Resilience4j circuit breakers around flaky external calls, with manual replay from the dashboard or the REST API when a step suspends.
- **Scalable** — steps run on virtual threads (Java 25) by default; parallel branches and async steps are first-class citizens; Kafka lets orchestration fan out across multiple application instances.

## Features

- ✅ **Annotation-first API** — `@Workflow` / `@WorkflowStep` turn plain Spring beans into orchestrated workflows; `@LaunchWorkflow` starts a workflow straight from a controller method with zero boilerplate.
- ✅ **Sequential, parallel, async & join steps** — declare complex routing (fan-out/fan-in, conditional branches) through a small fluent `PipelineBuilder` DSL or annotations.
- ✅ **HTTP steps out of the box** — annotate a Spring HTTP Service interface (`@GetExchange`/`@PostExchange`, ...) with `@WorkflowRestClient`/`@WorkflowStep` and it becomes a managed workflow step, backed by a pooled, timeout-aware `WebClient`.
- ✅ **JDBC persistence, no JPA/Hibernate** — workflow/step state and audit history are persisted through plain JDBC using your existing `DataSource`.
- ✅ **Kafka-driven execution** — step status transitions flow through Kafka, enabling distributed, at-least-once workflow processing across instances.
- ✅ **Automatic retry & replay** — global and per-step retry policies for transient failures; exhausted retries suspend the workflow for manual or automatic replay.
- ✅ **Circuit breakers** — `@WorkflowCircuitBreaker` wraps a step with a configurable Resilience4j circuit breaker and a `SUSPEND`/`REPLAY` fallback strategy.
- ✅ **Structured logging & distributed tracing** — every log line is enriched with `workflowId`/`workflow`/`step` MDC fields plus `traceId`/`spanId` (Micrometer Tracing + OpenTelemetry), and can be switched to JSON (ECS/Logstash) for Kibana/Grafana/Loki ingestion, with log levels adjustable live via env vars or the Actuator `/loggers` endpoint.
- ✅ **Metrics & health** — Micrometer + Prometheus endpoint and Actuator health/liveness/readiness probes, ready for Grafana dashboards and Kubernetes probes.
- ✅ **Optional operational REST API** — a small JDBC-backed service exposing workflow/step/context/metadata/log endpoints and Kafka topic/consumer-group introspection, with no extra persistence dependency.
- ✅ **Modern interactive dashboard** — a React + Vite single-page app that renders every pipeline as an interactive, GitHub-Actions-style graph (sequential paths, parallel lanes, async branches, explicit joins), lets you click any step to inspect its input/output/logs in a floating panel, replay failed/suspended steps, browse Kafka topics/consumer groups, and filter/paginate executions — all with a compact, collapsible-sidebar UI.

## How it works

1. **You describe a workflow** either declaratively (`@Workflow`/`@WorkflowStep` methods on a Spring bean) or with the `PipelineBuilder` DSL, wiring steps together with `StatusCriteria` (e.g. *"when step A succeeds, run step B"*, *"run B and C in parallel, then join into D"*).
2. **A workflow is launched** — either directly (`WorkflowEngine.start(...)`), or declaratively from a controller with `@LaunchWorkflow`, or by publishing a triggering event.
3. **The engine persists state through JDBC** — a new `workflow` row and initial `workflow_step` row(s) are written using your application's own `DataSource`/`NamedParameterJdbcTemplate`, with no JPA/Hibernate layer in between.
4. **Step execution is dispatched** — synchronous steps run inline; `AsyncStep`s and Kafka-driven transitions are dispatched onto a virtual-thread executor, publishing `RUNNING` immediately and the eventual result as a new status event on the configured Kafka topic.
5. **Status events drive the next steps** — a Kafka consumer picks up step-status events, evaluates the registered `StatusCriteria` for every downstream step, and triggers the next step(s) — including joining parallel branches once all of them report `SUCCESS`.
6. **Failures are classified automatically** — transient errors (timeouts, connection failures, HTTP 408/425/429/5xx) are `REPLAYABLE` and retried according to the configured policy (global or per-step); everything else `SUSPEND`s the workflow for manual/automatic replay. An optional circuit breaker can short-circuit a chronically failing external dependency before it even gets there.
7. **Every transition is observable** — each step execution and status transition is logged with correlated MDC fields and a distributed trace span, and recorded to `workflow`/`workflow_step`/`workflow_step_context`/`workflow_log` tables that the operational REST API and the dashboard read from directly — so what you see in the graph is exactly what happened, with full input/output/audit history per step.

```
┌─────────────┐     start      ┌────────────────┐     status event      ┌───────────────┐
│  Your app   │ ─────────────▶ │ WorkflowEngine │ ─────────────────────▶ │  Kafka topic  │
│ (@Workflow, │                │  (virtual      │                        │ workflow.status│
│ controllers)│ ◀───────────── │   threads)     │ ◀───────────────────── │               │
└─────────────┘   JDBC (state) └────────────────┘   consumer dispatch    └───────────────┘
       │                                │
       ▼                                ▼
 PostgreSQL (workflow/step/context/log)   Micrometer Tracing + structured logs
       │                                       │
       ▼                                       ▼
 Operational REST API  ───────────────▶  React dashboard (interactive graph)
```

## Technologies used

**Core engine**
- Java 25 (virtual threads for async/parallel step execution)
- Spring Boot 4.1 / Spring Framework (auto-configuration, `@ConfigurationProperties`, HTTP Service Client / `WebClient`)
- Spring JDBC (`NamedParameterJdbcTemplate`) — no JPA/Hibernate
- Apache Kafka (Spring for Apache Kafka) — status-driven, distributed step execution
- Resilience4j 2.4.0 (`resilience4j-spring-boot4`, circuit breaker, reactor support)
- SLF4J — logging facade only; the core module stays framework-agnostic

**Observability**
- Micrometer Tracing + OpenTelemetry bridge (`micrometer-tracing-bridge-otel`) — distributed traces exportable via OTLP to an OpenTelemetry Collector, Grafana Tempo, Jaeger, etc.
- Micrometer + `micrometer-registry-prometheus` — application metrics, Grafana-ready
- Spring Boot Actuator — health/liveness/readiness probes, runtime log-level control (`/actuator/loggers`), metrics/Prometheus endpoints
- Spring Boot structured logging (ECS / Logstash JSON formats) — ships log lines Filebeat/Logstash/Promtail can ingest straight into Elasticsearch/Kibana or Loki/Grafana, correlated via MDC (`workflowId`, `workflow`, `step`) and trace/span IDs

**Dashboard**
- React 19 + Vite 8
- `@xyflow/react` (React Flow) — the interactive workflow graph
- `lucide-react` — icon set
- Hand-rolled, dependency-light component library (no CSS framework) for the floating panels, forms, tables and layout chrome

**Infrastructure / delivery**
- PostgreSQL — workflow/step/context/log persistence
- Apache Kafka — status-event bus
- WireMock + Testcontainers — integration testing of HTTP steps
- Docker & Docker Compose — one-command local stack (Postgres, Kafka, example app, dashboard)
- Maven (multi-module reactor build)

## Project modules

| Module | Purpose |
|---|---|
| `workflow-orchestrator-core` | Framework-agnostic engine: `WorkflowEngine`, `PipelineBuilder`, `StepCatalog`, status criteria, error classification. Depends only on SLF4J. |
| `workflow-orchestrator-spring-boot-starter` | Auto-configuration wiring the core engine to your Spring Boot app's JDBC `DataSource` and Kafka beans; annotation support (`@Workflow`, `@WorkflowStep`, `@LaunchWorkflow`, `@WorkflowRestClient`, `@WorkflowCircuitBreaker`); retry/circuit-breaker configuration. |
| `workflow-orchestrator-service-starter` | Optional JDBC-backed REST API for operational dashboards (workflow/step/context/metadata/log endpoints, Kafka topic/consumer-group introspection). |
| `example-app` | A runnable Spring Boot application demonstrating annotated workflows, HTTP steps, retry/circuit-breaker configuration, structured logging/tracing, and the full Docker Compose stack. |
| `workflow-orchestrator-dashboard` | The React/Vite single-page dashboard — interactive workflow graph, pipeline list, Kafka admin. |

## Quick start (Docker Compose)

Run the complete example stack (PostgreSQL, Kafka, the Spring Boot example app, and the React dashboard):

```bash
docker compose up --build
```

Then open:

- **Dashboard** — http://localhost:5173
- **Example API** — http://localhost:8080
- **PostgreSQL** — `localhost:5432` (`workflow` / `workflow`)
- **Kafka** (from the host) — `localhost:29092`

The containers communicate internally using `postgres:5432` and `kafka:9092`. The Spring Boot example application does not create infrastructure — Compose supplies the PostgreSQL and Kafka services.

Stop the stack:

```bash
docker compose down
```

Remove persisted PostgreSQL/Kafka data as well:

```bash
docker compose down -v
```

## Annotation-first application API

The starter removes most orchestration boilerplate from application code.

### Workflow classes and method steps

A class annotated with `@Workflow` is discovered automatically. Methods annotated with `@WorkflowStep` are registered as workflow steps; the method can accept either no argument, `PipelineContext`, `Metadata`, or the business input.

```java
@Workflow("order-pipeline")
public class OrderWorkflow {
    @WorkflowStep("extract-order")
    public StepResult extract(PipelineContext context) {
        // application logic only
        return StepResult.success(context);
    }

    @WorkflowStep("notify")
    public StepResult notifyCustomer(PipelineContext context) {
        return StepResult.success(context);
    }
}
```

The builder can reference annotation names directly:

```java
new PipelineBuilder(OrderWorkflow.class, steps)
    .initialize().on(StatusCriteria.onStart()).then("extract-order")
    .sequential().when(StatusCriteria.status("extract-order", Status.SUCCESS)).then("notify")
    .build();
```

The previous class-based `WorkflowStep` API remains supported.

### Start workflows directly from controllers

No `WorkflowEngine` field is required:

```java
@PostMapping("/orders")
@LaunchWorkflow("order-pipeline")
public ResponseEntity<Void> create(@RequestHeader HttpHeaders headers,
                                   @RequestBody Order order) {
    return ResponseEntity.accepted().build();
}
```

Incoming HTTP headers are copied to workflow metadata.

### HTTP interface workflow steps

Spring HTTP Service interfaces can be registered as workflow steps:

```java
@WorkflowRestClient(baseUrl = "${customer.api.base-url}")
public interface CustomerClient {
    @WorkflowStep("customer-call")
    @GetExchange("/customers/{id}")
    ResponseEntity<Customer> get(@PathVariable("id") String id);
}
```

The starter creates the WebClient-backed proxy and the workflow step automatically. `ResponseEntity` responses expose status and response headers through metadata. `Mono` and `Flux` return types are supported; reactive streams are collected into the workflow context when a step completes.

The HTTP client supports connection pooling, connect/response timeouts, default headers, bearer/basic authentication, metadata propagation, cache controls and application-provided `WebClientCustomizer` beans, built on Spring's HTTP Service Client / `WebClient` adapter model.

## Functional infrastructure steps

The stable AOP model is extended with small functional adapters:

```java
Steps.rest(
    "customer-call",
    ctx -> new CustomerRequest(ctx.workflowContext().businessInput()),
    customerClient::get,
    (ctx, response) -> response
);

Steps.load(
    "persist-order",
    ctx -> ctx.workflowContext().businessInput(),
    repository::saveAll
);
```

A step receives `StepExecutionContext`, which exposes the original workflow context and the persisted parent step context. Infrastructure steps store their input/output independently in `workflow_step_context`.

REST clients can also be auto-registered:

```java
@WorkflowRestClient(baseUrl = "${customer.url}")
interface CustomerClient {
    @WorkflowStep(value = "customer-call", mapper = OrderIdMapper.class)
    @GetExchange("/customers/{id}")
    ResponseEntity<Customer> get(@PathVariable String id);
}
```

The mapper is intentionally the only application-specific adapter. Database loaders accept any application writer: JPA repositories, `JdbcTemplate`, batch writers, stored procedures, or custom persistence code.

## Retry, replay & circuit breaking

```yaml
workflow:
  orchestrator:
    retry:
      enabled: true
      max-attempts: 5
      delay: 5s
      steps:
        notify:
          max-attempts: 10
          delay: 2s
```

Global values are defaults; `steps.<alias>` overrides them for retryable workflow exceptions. Criteria-not-matched events use the same Kafka retry policy. Async steps publish `RUNNING` immediately and their eventual result as a new status event.

Errors are classified as `REPLAYABLE` or `SUSPEND`. Timeouts, connection failures and transient HTTP statuses such as 408, 425, 429 and 5xx are replayable by default. Non-replayable failures suspend the current activity. When retries are exhausted, the workflow is persisted as `SUSPENDED`. Suspended steps can be replayed from the dashboard or through:

```text
POST /api/orchestrator/workflows/{workflowId}/steps/{stepName}/replay
```

Workflow methods can also opt into a configurable circuit breaker:

```java
@WorkflowStep("customer-call")
@WorkflowCircuitBreaker(name = "customer", fallback = "REPLAY", retryDelaySeconds = 30)
public StepResult callCustomer(PipelineContext context) {
    // external call
    return StepResult.success(context);
}
```

Circuit-breaker instances are configurable under `workflow.orchestrator.circuit-breaker`. `fallback: SUSPEND` is the safe default; `fallback: REPLAY` schedules a workflow-step replay after the configured delay.

## Logging & tracing

Every log line emitted while a workflow step runs carries `workflowId`/`workflow`/`step` MDC fields, plus `traceId`/`spanId` contributed by Micrometer Tracing once a request/consumer record is traced — making it trivial to correlate logs, metrics and traces for a single workflow execution across all three observability pillars.

Log levels are fully controllable at runtime, without a restart:

- via environment variables at startup, e.g. `LOGGING_LEVEL_COM_GITHUB_ORCAS_ORCHESTRATOR=DEBUG`
- via the Actuator endpoint while running:

  ```bash
  curl -X POST localhost:8080/actuator/loggers/com.github.orcas.orchestrator \
       -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
  # GET the same URL to read the effective level
  ```

Structured (JSON) logging is built into Spring Boot: set `LOG_FORMAT=ecs` (Elastic Common Schema, ready for Filebeat/Logstash → Elasticsearch/Kibana) or `LOG_FORMAT=logstash` (Logstash JSON format, also Kibana/Loki friendly) to switch the console appender from human-readable text to line-delimited JSON:

```bash
LOG_FORMAT=ecs java -jar app.jar
```

Leave it unset for a human-readable console during local development.

Traces are exported over OTLP/HTTP (`OTEL_EXPORTER_OTLP_ENDPOINT`, default `http://localhost:4318/v1/traces`) to any OpenTelemetry Collector, Grafana Tempo/Alloy, Jaeger, etc.; sampling is tunable via `TRACING_SAMPLING_PROBABILITY`. Metrics are exposed at `/actuator/prometheus` for scraping by Prometheus and visualization in Grafana.

## Operational REST API

`workflow-orchestrator-service-starter` adds a small JDBC-backed REST service for operational dashboards. It has no JPA/Hibernate dependency and reuses the application's `DataSource`/`NamedParameterJdbcTemplate`.

Endpoints are rooted at `/api/orchestrator` by default:

- `GET /workflows` with workflow/status/stepStatus/metadata/date-range filters and pagination
- `GET /workflows/{workflowId}`
- `GET /workflows/{workflowId}/steps`
- `GET /workflows/{workflowId}/context`
- `GET /workflows/{workflowId}/metadata`
- log endpoints for workflow, step, context and metadata
- PATCH/PUT endpoints for operational modifications
- `/kafka/topics` and `/kafka/consumer-groups` when Kafka is available

## The dashboard

The dashboard (`workflow-orchestrator-dashboard/`) is a React + Vite single-page app, orca-themed (black / white / ocean blue), built around one idea: **the workflow graph is the product**.

- Workflow execution renders as an interactive, GitHub-Actions-style graph — sequential steps form the main path, parallel branches appear as separate lanes, async steps are visually distinct, and joins are explicit.
- Click any step to open a floating panel with its overview, input, output and audit log, without ever leaving or scrolling the graph. Failed/suspended steps expose a one-click **Replay step** action.
- The collapsible sidebar (hover to reveal the toggle arrow) doubles as the full navbar and houses the Orcas trademark/footer.
- The workflows list and Kafka admin pages are compact, paginated/filterable (including a date-range picker), and built on a small shared form-field library with Material-style floating labels.

## Contributing

Contributions are very welcome — Orcas grows better with more use cases, more steps types, and more eyes on resilience edge cases.

1. **Fork** the repository.
2. Create a feature branch (`git checkout -b feature/my-improvement`).
3. Make your changes, keeping modules dependency-light and documented.
4. Run the relevant build/tests (`mvn clean install` for Java modules, `npm run build` for the dashboard).
5. **Push** your branch and open a **pull request** describing what changed and why.

Bug reports, design discussions and feature ideas are just as welcome as code — feel free to open an issue first if you'd like to discuss an approach before implementing it.

## Credits

Created and maintained by **[Hatim Cherkaoui](https://github.com/HatimCherkaoui)**.
