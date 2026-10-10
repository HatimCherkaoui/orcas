# Workflow Orchestrator

A modular workflow engine for Java 25 and Spring Boot 4.

The design follows one rule: **the application declares workflows; infrastructure is supplied by the modules it chooses**.

## Modules

| Artifact | Purpose | Use when |
|---|---|---|
| `workflow-orchestrator-core` | Engine, context, routing DSL and extension contracts | Always |
| `workflow-orchestrator-spring-boot-autoconfigure` | Spring IoC integration and default engine wiring | Using Spring Boot |
| `workflow-orchestrator-spring-boot-starter` | Core Spring Boot convenience bundle | You want the standard setup |
| `workflow-orchestrator-rest` | REST annotations and contracts | Defining HTTP workflow steps |
| `workflow-orchestrator-rest-autoconfigure` / `-starter` | WebClient implementation and auto-configuration | Calling external HTTP APIs |
| `workflow-orchestrator-resilience` | Resilience annotations and contracts | Adding circuit breakers/fallbacks |
| `workflow-orchestrator-resilience-autoconfigure` / `-starter` | Resilience4j integration | Using the standard resilience adapter |
| `workflow-orchestrator-jdbc-autoconfigure` / `-starter` | JDBC workflow state store | Persisting workflow state in SQL |
| `workflow-orchestrator-kafka-autoconfigure` / `-starter` | Kafka events and listeners | Event-driven workflows |
| `workflow-orchestrator-observability-autoconfigure` / `-starter` | MDC and operational hooks | Adding execution tracing/log context |
| `workflow-orchestrator-service` | Framework-neutral query/admin API | Building operational tooling |
| `workflow-orchestrator-service-autoconfigure` / `-starter` | Spring MVC + JDBC operational API | Exposing the service API over HTTP |
| `workflow-orchestrator-dashboard-service` | Dashboard backend contracts | Connecting a dashboard to external messaging |
| `workflow-orchestrator-dashboard-service-autoconfigure` / `-starter` | Dashboard backend integration | Running the bundled dashboard service |
| `workflow-orchestrator-dashboard` | React dashboard application | Using the graphical UI |

Feature modules have their own API where useful. The `*-starter` artifacts are optional convenience bundles; applications can import only the adapters they need. The umbrella Spring Boot starter includes the standard integrations.

## Define a workflow

Names normally come from annotations, so workflow classes do not repeat configuration already present in the source code:

```java
@Workflow
final class OrderWorkflow {
    @WorkflowStep
    Order validate(Order order) {
        return order;
    }

    @WorkflowStep
    CompletionStage<Order> reserve(Order order) {
        return inventory.reserve(order);
    }
}
```

Functional steps are first-class too:

```java
var step = Steps.step(context -> validate(context.businessInput()));
```

Built-in Spring configuration supplies the registry, executor, publisher and extension beans. Application code stays focused on workflow behavior.

## Select only what you use

Use the BOM to keep versions aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.hatimcherkaoui</groupId>
            <artifactId>workflow-orchestrator-bom</artifactId>
            <version>0.6.0-SNAPSHOT</version>
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

Mappers and response consumers are resolved through Spring IoC. Constructor-injected application beans work without manual reflection or factory configuration.

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

Use JDK 25 or 26, Maven 3.9.6+, Node.js 20+ and Docker (including Colima).

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
`com.github.orcas`. The current `0.6.0-SNAPSHOT` is a development POC, not a published
Maven Central release. Install it locally with `mvn install` before using the BOM example.

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
- [Changes](CHANGELOG.md)
- [GitHub issues](https://github.com/HatimCherkaoui/orcas/issues)
- Maintainer: [Hatim Cherkaoui](https://github.com/HatimCherkaoui)

Licensed under [Apache 2.0](LICENSE).

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
