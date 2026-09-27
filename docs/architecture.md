# Architecture

Workflow Orchestrator is built in layers. The inner layer knows nothing about Spring or transport libraries.

```text
application
   │
   ├── workflow-orchestrator-spring-boot-autoconfigure
   │       │
   │       ├── core engine + IoC discovery
   │       │
   │       ├── jdbc       → PostgreSQL / JDBC state
   │       ├── kafka      → workflow status events
   │       ├── rest       → declarative WebClient steps
   │       ├── resilience → circuit breakers / replay
   │       ├── observability → MDC / lifecycle hooks
   │       ├── service API → service-autoconfigure → operational REST API
   │       └── dashboard-service API → dashboard-service-autoconfigure → Kafka dashboard API
   │
   └── React dashboard (separate frontend)

core
├── API and extension contracts
├── execution engine
├── workflow/step annotations
├── fluent routing DSL
├── context + metadata model
└── in-memory state store for standalone use and tests
```

## Dependency rule

Integration modules may depend on core. Core must not depend on Spring, Reactor, Resilience4j, SLF4J, JPA or a transport.

The boundary is enforced by `scripts/check-module-boundaries.py` and `core/src/test/.../ArchitectureTest.java`.

## Spring IoC

The Spring module owns discovery and construction. Application classes can use constructor injection normally. Feature modules resolve user extension beans through Spring rather than calling constructors reflectively.

Default components are created only when an application has not supplied its own bean. Feature starters therefore behave like standard Spring Boot integrations: dependency presence selects the feature, and `@ConditionalOnMissingBean` preserves application control.

## Choosing dependencies

Use the BOM and then choose only the starters needed by an application. Use the umbrella Spring starter for the convenience path when all integrations are wanted.
