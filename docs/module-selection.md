# Module selection

Use the smallest dependency set that matches the application. Each feature has a framework-neutral API where custom adapters are useful, followed by Spring Boot auto-configuration and a convenience starter.

## Minimal Spring application

Use:

```xml
<dependency>
    <groupId>io.github.hatimcherkaoui</groupId>
    <artifactId>workflow-orchestrator-spring-boot-autoconfigure</artifactId>
</dependency>
```

This gives the core engine, discovery and default IoC wiring. The default state store is in memory.

## Database-backed workflow

Add:

```xml
<dependency>
    <groupId>io.github.hatimcherkaoui</groupId>
    <artifactId>workflow-orchestrator-jdbc-starter</artifactId>
</dependency>
```

The application keeps its own business JPA/JDBC repositories; the orchestrator persists its execution state through its own JDBC adapter.

## External REST steps

Add `workflow-orchestrator-rest-starter`. REST client interfaces remain application code. Mappers and response consumers are Spring beans, so normal constructor injection applies.

## Kafka events

Add `workflow-orchestrator-kafka-starter` when the application needs workflow event publication or asynchronous status callbacks. Kafka does not enter the core dependency graph.

## Resilience

Add `workflow-orchestrator-resilience-starter` when step calls need circuit breakers, fallbacks or the related retry scheduling.

## Operational API

Add `workflow-orchestrator-service-starter` when an application needs HTTP search, workflow details, context/metadata inspection, audit logs or replay/update operations.

## Dashboard

The React application is separate. Run the Java operational service and dashboard-service integrations, then point the frontend at their HTTP endpoints.

## All integrations

For applications that want the complete standard ORCAS runtime, use `workflow-orchestrator-spring-boot-starter`.

It is the all-in-one bundle for the Spring Boot stack: core wiring, JDBC, Kafka, REST, Resilience4j, observability, the operational workflow API, and the dashboard Kafka backend. The individual starters remain independent so a service can deliberately omit, for example, the dashboard or observability integration.
