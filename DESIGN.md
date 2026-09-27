# Design

## Boundary

`workflow-orchestrator-core` contains only workflow semantics. It does not know about Spring, JDBC, Kafka, HTTP clients, Reactor, Resilience4j or application logging.

Integration artifacts depend inward:

```text
core
├── rest API ────────────> rest-autoconfigure ──> Spring/WebClient
├── resilience API ─────> resilience-autoconfigure ──> Resilience4j
├── service API ────────> service-autoconfigure ──> Spring MVC/JDBC
└── dashboard contracts ─> dashboard-service-autoconfigure ──> Kafka

jdbc-autoconfigure ──> core
kafka-autoconfigure ─> core
observability-autoconfigure ─> core
spring-boot-autoconfigure ─> core
```

The starter artifacts only package the corresponding auto-configuration with its public API.

## Application model

The normal application path is declarative:

1. Annotate a workflow and its steps.
2. Inject business services normally with Spring.
3. Select only the integration starters the application uses.
4. Let auto-configuration create the engine, registry, state store, publishers and adapters.

`Steps.step(...)` provides a functional path for programmatic workflow composition.

## Naming

Annotation values are optional. When a workflow or step name is omitted, the framework derives it from the annotated class or method. Explicit names remain available when a stable external identifier is required.

## Extension points

Core exposes small interfaces instead of concrete infrastructure types:

- `WorkflowEventPublisher` for event delivery
- `WorkflowStateStore` for persistence
- `WorkflowObserver` for execution observations
- `WorkflowStepInvocationInterceptor` for cross-cutting step behavior
- `WorkflowErrorCategorizer` for retry/suspend classification
- `ContextMapper` and `ResponseConsumer` for functional request/response composition

This keeps integrations replaceable and makes the core unit-testable without a container.
