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
            <groupId>com.github.orcas</groupId>
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
    <groupId>com.github.orcas</groupId>
    <artifactId>workflow-orchestrator-spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>com.github.orcas</groupId>
    <artifactId>workflow-orchestrator-jdbc-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.github.orcas</groupId>
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

## Build

Java 25 and Maven 3.9.6+ are required.

```bash
mvn clean verify
mvn -pl example-app -am verify
cd workflow-orchestrator-dashboard && npm ci && npm run build
```

See `DESIGN.md` for the dependency rules and `PUBLISHING.md` for Maven Central release steps.

## Run the order example

From the repository root:

```bash
# Requires JDK 25 or JDK 26, Maven 3.9.6+, and Docker.
mvn -version

# Start PostgreSQL, Kafka and WireMock for the local example.
docker compose -f example-app/docker-compose.yml up -d

# Build the example and every library module it depends on.
mvn -pl example-app -am clean package -DskipTests

# Run the packaged Spring Boot application.
JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED" \
  java -jar example-app/target/app.jar
```

The example uses the defaults from `example-app/src/main/resources/application.yml`:
PostgreSQL on `localhost:5432`, Kafka on `localhost:9092`, and WireMock on
`localhost:8089`. The Compose file above starts those three services.

In another terminal, exercise the example workflow:

```bash
./example-app/demo.sh
```

For the complete Docker development stack, including PostgreSQL, Kafka, WireMock, the
Spring Boot example backend, the Kafka dashboard backend, React dashboard, Elasticsearch
and OpenTelemetry Collector, use the repository Compose profile:

```bash
docker compose --profile DEV up --build
```

The example backend declares only `workflow-orchestrator-spring-boot-starter`. That single
starter brings the standard ORCAS integrations; Spring Boot auto-configuration registers
the workflow operational API at `/api/orchestrator/workflows` and the Kafka dashboard API
at `/api/orchestrator/kafka/*`. The React dashboard is then served on `http://localhost:5173`
and proxies `/api/*` to the `example-app` container.

The example application uses `workflow-orchestrator-spring-boot-starter`, so the standard ORCAS integrations are available without declaring each starter separately. Individual feature starters remain available for applications that want a smaller footprint.

## Runtime and management services

The repository now separates the workflow runtime from the dashboard management plane:

- `example-app` is a workflow runtime. It uses `workflow-orchestrator-spring-boot-starter` and owns workflow definitions, step beans and execution.
- `workflow-orchestrator-management-service` is a standalone Spring Boot application. It owns the persisted-state REST API and Kafka introspection API used by the dashboard.
- The dashboard proxies `/api/*` only to `workflow-management-service:8081`; it never calls `example-app`.
- Manual replay is sent from the management service to Kafka on `workflow.replay` and consumed by the runtime, so the management service does not need application workflow classes.

The canonical local stack can be started with:

```bash
docker compose up --build
```

The dashboard is available on `http://localhost:5173`, the management API on `http://localhost:8081`, and the example runtime on `http://localhost:8080`.

## Docker Compose profiles

The canonical Compose file uses explicit profiles:

- `dev`: PostgreSQL, Kafka, the standalone management service, the example Spring Boot application, and the dashboard.
- `prod`: the same application stack plus WireMock and the full observability stack (OpenTelemetry Collector, Elasticsearch, Kibana, and their initialization services).

Start development:

```bash
docker compose --profile dev up -d --build
```

Start the complete production-style stack:

```bash
docker compose --profile prod up -d --build
```

The dashboard is served on `http://localhost:5173`. Browser requests stay same-origin (`/api/orchestrator/...`) and Nginx forwards them to the management service at `http://workflow-management-service:8081` inside the Compose network. The management service is exposed on the host as `http://localhost:8081`.

The example application remains independent from the management API. It only consumes the workflow runtime starters and publishes/executes workflows.
