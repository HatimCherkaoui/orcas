<div align="center">

# 🫍 ORCAS · Workflow Orchestrator

**Declare workflows. Choose your integrations. Observe every execution.**

A modular workflow engine for **Java 25** and **Spring Boot 4**.

[![CI](https://github.com/HatimCherkaoui/orcas/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/HatimCherkaoui/orcas/actions/workflows/ci.yml)
[![Maven Central publication](https://github.com/HatimCherkaoui/orcas/actions/workflows/publish.yml/badge.svg)](https://github.com/HatimCherkaoui/orcas/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.hatimcherkaoui/workflow-orchestrator-core?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.hatimcherkaoui/workflow-orchestrator-core)
[![Published release](https://img.shields.io/badge/published-v0.6.0-007ec6)](https://github.com/HatimCherkaoui/orcas/tree/v0.6.0)
[![Javadoc](https://javadoc.io/badge2/io.github.hatimcherkaoui/workflow-orchestrator-core/javadoc.svg)](https://javadoc.io/doc/io.github.hatimcherkaoui/workflow-orchestrator-core/0.6.0)
[![Java](https://img.shields.io/badge/Java-25%20%7C%2026-ed8b00)](https://github.com/HatimCherkaoui/orcas/blob/main/pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6db33f)](https://github.com/HatimCherkaoui/orcas/blob/main/pom.xml)
[![License](https://img.shields.io/github/license/HatimCherkaoui/orcas)](LICENSE)
[![Stars](https://img.shields.io/github/stars/HatimCherkaoui/orcas?style=flat)](https://github.com/HatimCherkaoui/orcas/stargazers)
[![Forks](https://img.shields.io/github/forks/HatimCherkaoui/orcas?style=flat)](https://github.com/HatimCherkaoui/orcas/forks)
[![Contributors](https://img.shields.io/github/contributors/HatimCherkaoui/orcas)](https://github.com/HatimCherkaoui/orcas/graphs/contributors)
[![Issues](https://img.shields.io/github/issues/HatimCherkaoui/orcas)](https://github.com/HatimCherkaoui/orcas/issues)
[![Contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen)](CONTRIBUTING.md)

[Quick start](#quick-start) · [Modules](#modules) · [Documentation](#documentation-and-support) · [Contributing](#contributors-and-contributing)

</div>

---

The application declares workflows; infrastructure is supplied by the modules it chooses.

- **Composable execution:** annotation and functional steps, status routing, asynchronous work, and parallel joins.
- **Optional infrastructure:** JDBC persistence, Kafka events, REST clients, and Resilience4j adapters.
- **Operational visibility:** execution correlation, OpenTelemetry integration, a management API, and a React dashboard.
- **Framework-neutral core:** use the engine without Spring, Kafka, JDBC, or Reactor dependencies.

## Quick start

**Published version: `0.6.0`**, available from Maven Central without a custom repository.
Use Java 25 or 26. The examples below target a Spring Boot 4.1.1 application.

### 1. Install

For the complete runtime stack, add the umbrella starter:

```xml

<dependency>
    <groupId>io.github.hatimcherkaoui</groupId>
    <artifactId>workflow-orchestrator-spring-boot-starter</artifactId>
    <version>0.6.0</version>
</dependency>
```

Or with Gradle Kotlin DSL:

```kotlin
implementation("io.github.hatimcherkaoui:workflow-orchestrator-spring-boot-starter:0.6.0")
```

The umbrella starter includes JDBC, Kafka, REST, resilience, observability, and service integrations;
configure the database and Kafka for that setup. For the minimal in-memory example below,
use **`workflow-orchestrator-spring-boot-autoconfigure`** instead of the umbrella starter.
It supplies core Spring wiring, an in-memory state store, and local event dispatch without
requiring a database or Kafka. For plain Java, use **`workflow-orchestrator-core`**.

### 2. Declare and route a workflow

Place this configuration in your Spring Boot application's component-scan package:

```java
import com.github.orcas.orchestrator.core.api.Steps;
import com.github.orcas.orchestrator.core.builder.PipelineBuilder;
import com.github.orcas.orchestrator.core.builder.StatusCriteria;
import com.github.orcas.orchestrator.core.builder.StepCatalog;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinitionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GreetingWorkflowConfiguration {
    @Bean
    WorkflowDefinitionProvider greetingWorkflow(StepCatalog steps) {
        return () -> new PipelineBuilder("greeting", steps)
                .initialize().on(StatusCriteria.onStart())
                .then(Steps.step("greet", context -> {
                    System.out.println("Hello, " + context.businessInput());
                    return context;
                }))
                .build();
    }
}
```

### 3. Start an execution

Add this bean to the same configuration (with these imports):

```java
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.springframework.boot.ApplicationRunner;

@Bean
ApplicationRunner runGreeting(WorkflowEngine engine) {
    return args -> engine.start("greeting", WorkflowContext.of("ORCAS"));
}
```

Start your application to print `Hello, ORCAS`. The routing definition is registered by
Spring; `WorkflowEngine.start` creates an execution and dispatches its initial event.
The in-memory store is useful for getting started; choose the JDBC starter for persisted state.

See the [order workflow example](example-app/src/main/java/com/github/orcas/demo/config/OrderWorkflow.java)
and its [routing definition](example-app/src/main/java/com/github/orcas/demo/config/OrderWorkflowDefinition.java)
for constructor injection, annotation-based steps, and multi-step execution.

## Modules

| Artifact                                                             | Purpose                                              | Use when                                     |
|----------------------------------------------------------------------|------------------------------------------------------|----------------------------------------------|
| `workflow-orchestrator-core`                                         | Engine, context, routing DSL and extension contracts | Always                                       |
| `workflow-orchestrator-spring-boot-autoconfigure`                    | Spring IoC integration and default engine wiring     | Using Spring Boot                            |
| `workflow-orchestrator-spring-boot-starter`                          | Core Spring Boot convenience bundle                  | You want the standard setup                  |
| `workflow-orchestrator-rest`                                         | REST annotations and contracts                       | Defining HTTP workflow steps                 |
| `workflow-orchestrator-rest-autoconfigure` / `-starter`              | WebClient implementation and auto-configuration      | Calling external HTTP APIs                   |
| `workflow-orchestrator-resilience`                                   | Resilience annotations and contracts                 | Adding circuit breakers/fallbacks            |
| `workflow-orchestrator-resilience-autoconfigure` / `-starter`        | Resilience4j integration                             | Using the standard resilience adapter        |
| `workflow-orchestrator-jdbc-autoconfigure` / `-starter`              | JDBC workflow state store                            | Persisting workflow state in SQL             |
| `workflow-orchestrator-kafka-autoconfigure` / `-starter`             | Kafka events and listeners                           | Event-driven workflows                       |
| `workflow-orchestrator-observability-autoconfigure` / `-starter`     | MDC and operational hooks                            | Adding execution tracing/log context         |
| `workflow-orchestrator-service`                                      | Framework-neutral query/admin API                    | Building operational tooling                 |
| `workflow-orchestrator-service-autoconfigure` / `-starter`           | Spring MVC + JDBC operational API                    | Exposing the service API over HTTP           |
| `workflow-orchestrator-dashboard-service`                            | Dashboard backend contracts                          | Connecting a dashboard to external messaging |
| `workflow-orchestrator-dashboard-service-autoconfigure` / `-starter` | Dashboard backend integration                        | Running the bundled dashboard service        |
| `workflow-orchestrator-dashboard`                                    | React dashboard application                          | Using the graphical UI                       |

Feature modules have their own API where useful. The `*-starter` artifacts are optional convenience bundles;
applications can import only the adapters they need. The umbrella Spring Boot starter includes the standard
integrations.

## Select only what you use

Use the BOM to keep versions aligned:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.hatimcherkaoui</groupId>
            <artifactId>workflow-orchestrator-bom</artifactId>
            <version>0.6.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

A typical JDBC + REST application can use either the individual starters or the all-in-one Spring Boot starter:

```xml

<dependency>
    <groupId>io.github.hatimcherkaoui</groupId>
    <artifactId>workflow-orchestrator-spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
<groupId>io.github.hatimcherkaoui</groupId>
<artifactId>workflow-orchestrator-jdbc-starter</artifactId>
</dependency>
<dependency>
<groupId>io.github.hatimcherkaoui</groupId>
<artifactId>workflow-orchestrator-rest-starter</artifactId>
</dependency>
```

Add Kafka, resilience, observability or the operational service only when the application needs them.

## REST steps

HTTP-specific annotations live outside core:

```java

@WorkflowStep
@WorkflowRestCall(mapper = ReserveMapper.class)
Mono<ResponseEntity<Reservation>> reserve(ReservationRequest request);
```

Mappers and response consumers are resolved through Spring IoC. Constructor-injected application beans work without
manual reflection or factory configuration.

## Configuration

Each module owns its configuration namespace:

- `workflow.orchestrator.async.*`
- `workflow.orchestrator.jdbc.*`
- `workflow.orchestrator.kafka.*`
- `workflow.orchestrator.rest-client.*`
- `workflow.orchestrator.retry.*`
- `workflow.orchestrator.circuit-breaker.*`
- `workflow.orchestrator.observability.*`
- `workflow.orchestrator.service.*`
- `workflow.orchestrator.dashboard.*`

An unused integration does not require its configuration.

### REST transport startup and external I/O

Generated REST clients eagerly initialize Reactor Netty transport resources before
application readiness. `workflow.orchestrator.rest-client.warmup-enabled` defaults
to `true`; `warmup-timeout` defaults to `30s`. Initialization failure prevents
startup and releases the client pool. Transport warmup initializes local resources.
An optional `warmup-path` performs a GET against a JSON health endpoint on each
client base URL, warming DNS, pooled connections/TLS and JSON codecs before
readiness. It is unset by default. The example uses `/__admin/health` on WireMock;
set `DEMO_REST_WARMUP_PATH` for another provider, or leave it blank to disable
remote warmup. A failing configured health endpoint prevents startup.

The example `initiate-payment` step uses `@WorkflowStep(async = true)` so provider
waits do not occupy Kafka listener threads. The workflow async concurrency remains
bounded; payment persistence must finish before the step publishes success.
See [Reactor Netty eager initialization](https://projectreactor.io/docs/netty/release/reference/http-client.html#_eager_initialization).

### Kafka and database throughput

The Kafka integration defaults to four listener consumers and creates new workflow topics
with 12 partitions. Keep the consumer concurrency at or below the topic partition count;
Kafka topics that already exist are not repartitioned by Spring's topic declaration, so
increase their partition count with your Kafka administrator before raising concurrency.
Topic replication defaults to one for local and single-broker environments. Set
`workflow.orchestrator.kafka.topic-replication-factor` to the broker-supported replication
factor (commonly 3) in a production cluster.

Workflow event publication is asynchronous by default so the Kafka producer can batch sends
instead of blocking every workflow thread for a broker round trip. Send failures are logged;
configure `spring.kafka.producer.acks=all`, `spring.kafka.producer.properties.enable.idempotence=true`,
and sensible producer retries for durability. Set
`workflow.orchestrator.kafka.wait-for-acknowledgement=true` when the caller must wait for the
broker acknowledgement before returning. Listener processing retries failures and routes
records that exceed the retry limit to `<topic>.DLT`, where operators can inspect and replay
them. A practical starting point for EC2 workloads is `linger.ms=5`, `batch.size=65536`, and
`compression.type=zstd`; tune from measured payloads, broker capacity, and latency objectives.

The orchestrator uses the application's `DataSource`; it does not create a separate pool.
For RDS, budget `pool size × application instances` against the database connection limit,
reserving capacity for business queries and administration. The example defaults to an
8-connection pool and 8 active workflow tasks, configurable with `DB_POOL_MAX_SIZE` and
`WORKFLOW_ASYNC_CONCURRENCY`; size both for the instance count and the RDS class. Set
`DB_POOL_MIN_IDLE` low on horizontally scaled EC2 instances to avoid every instance holding
idle connections. Workflow tasks use virtual threads by default, while a semaphore applies
backpressure at the configured concurrency instead of accumulating an unbounded task queue.
The Testcontainers burst scenario in `OrderWorkflowIntegrationTest` launches concurrent
requests against Kafka and PostgreSQL, with intentionally smaller pool and worker limits for
local Docker/Colima. It reports launch and workflow rates; these local numbers are a baseline,
not an EC2/RDS capacity guarantee.

## Build and validate

Use JDK 25 or 26, Maven 3.9.6–3.9.x (CI uses 3.9.11), Node.js 20+ and Docker (including Colima).
Maven 3.10 is currently incompatible with the Central publishing plugin used by this project.

```bash
./verify.sh
```

The default `poc` Maven profile includes the example runtime and management application,
so `mvn clean verify` also exercises the real Kafka/PostgreSQL/WireMock integration tests.
For Colima, see [Testcontainers setup](example-app/TESTCONTAINERS.md).

## First POC

Start the complete demonstration stack with:

```bash
docker compose --profile prod up -d --build
./example-app/demo.sh
```

Dashboard: http://localhost:5173. Management API: http://localhost:8081.
Example runtime: http://localhost:8080. These are local demonstration services;
WireMock simulates external providers and the Compose passwords are development defaults.

The runtime owns workflow execution. The management service queries persisted state and
sends manual replay commands through Kafka. The dashboard uses the management API.
See the [POC guide](docs/poc.md) for error scenarios and distributable packaging.

## Repository layout

- `workflow-orchestrator-*`: Maven library modules with `src/main/java`, `src/main/resources` and `src/test/java`.
- `example-app`: order workflow demonstration and Testcontainers scenarios.
- `workflow-orchestrator-management-service`: standalone dashboard backend application.
- `workflow-orchestrator-dashboard`: React dashboard, built separately with npm.
- `docs`: architecture, module selection and POC instructions.
- `scripts`: module boundary checks and POC packaging.
- `wiremock`: attempt-based external provider simulations.

The default build includes all applications. The `release-metadata` profile builds only
the parent, BOM and library modules for Maven publication. Applications are excluded from
that reactor and also have deployment disabled.

Maven coordinates use `io.github.hatimcherkaoui`; Java imports continue to use
`com.github.orcas`. Version `0.6.0` is published
on [Maven Central](https://central.sonatype.com/namespace/io.github.hatimcherkaoui).
Library artifacts include source and Javadoc JARs, signatures, and checksums.

## Correlation and observability

Executions carry generated request, correlation, transaction and trace identifiers.
Only allowlisted identifiers and validated W3C trace context are captured from transport
headers. Identifiers propagate across HTTP, Kafka, steps and retries; the dashboard
supports copying each value. The optional observability starter instruments workflow,
criteria, JDBC and transport operations and exports logs and metrics.

See [Execution correlation and the ready-to-use Kibana dashboard](observability/README.md)
for setup, extension APIs, field names, resource limits and verification commands.

## Documentation and support

- [Architecture](docs/architecture.md) and [module selection](docs/module-selection.md)
- [Publishing](PUBLISHING.md) and [contributing](CONTRIBUTING.md)
- [Runtime tuning and load testing](docs/runtime-tuning.md)
- [Security policy](SECURITY.md) and [Apache 2.0 license](LICENSE)
- [Changes](CHANGELOG.md)
- [GitHub issues](https://github.com/HatimCherkaoui/orcas/issues)
- Maintainer: [Hatim Cherkaoui](https://github.com/HatimCherkaoui)

## Contributors and contributing

Maintained by [Hatim Cherkaoui](https://github.com/HatimCherkaoui).

<a href="https://github.com/HatimCherkaoui"><img src="https://avatars.githubusercontent.com/u/38525968?v=4" width="64" height="64" alt="Hatim Cherkaoui, maintainer"></a>

Thanks to everyone who contributes code, documentation, bug reports, and ideas.
See the [complete contributor history](https://github.com/HatimCherkaoui/orcas/graphs/contributors).

To contribute:

1. Open an [issue](https://github.com/HatimCherkaoui/orcas/issues) to describe a bug or discuss a proposed feature.
2. Fork the repository and create a branch for your change.
3. Run the relevant checks and `./verify.sh`, then open a pull request with the problem, solution, and validation.

Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup and module boundaries.
Report security vulnerabilities using [SECURITY.md](SECURITY.md).

## License

Distributed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for notices.
