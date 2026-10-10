# Changelog

## 0.6.0-SNAPSHOT — first POC

This development POC reorganizes the project into small integration modules while keeping the workflow engine in a framework-neutral core.

### Architecture

- `workflow-orchestrator-core` contains the engine, workflow model, routing DSL, state contracts and extension interfaces.
- `workflow-orchestrator-spring-boot-autoconfigure` adds Spring IoC, discovery and default engine wiring.
- JDBC, Kafka, REST, resilience, observability, service and dashboard-service integrations are independent starters.
- REST, resilience and service contracts are published separately from their Spring Boot auto-configuration.
- `workflow-orchestrator-spring-boot-starter` remains the all-integrations convenience bundle.
- `workflow-orchestrator-bom` keeps published module versions aligned.

### API cleanup

- Workflow and step names are derived from `@Workflow` and `@WorkflowStep` when their values are omitted.
- Circuit-breaker names are derived from the step name when `@WorkflowCircuitBreaker.name` is omitted.
- Spring-managed response consumers and context mappers use constructor injection through the application context.
- Functional steps are available through `Steps.step(...)`.
- Transport-specific classes and annotations no longer live in core.

### Migration from 0.5.x

Move feature-specific APIs to their feature modules:

| Old location | New location |
| --- | --- |
| `core.annotation.LaunchWorkflow` | `rest.annotation.LaunchWorkflow` |
| `core.annotation.WorkflowCircuitBreaker` | `resilience.annotation.WorkflowCircuitBreaker` |
| `core.annotation.FallbackStrategy` | `resilience.annotation.FallbackStrategy` |
| `core.api.RestCallStep` | `rest.autoconfigure.RestClientWorkflowStep` |
| starter-owned JDBC classes | `jdbc.autoconfigure.*` |
| starter-owned Kafka classes | `kafka.autoconfigure.*` |

Applications using Spring Boot should depend on the feature starter that matches the feature they use instead of the historical monolithic starter.

## 0.6.0 - management auto-configuration routing fix

- Simplified `ManagementServiceApplication` to rely on Spring Boot auto-configuration metadata instead of manually importing orchestrator auto-configurations.
- Made the workflow service auto-configuration activate only when its JDBC infrastructure is present, with service/controller beans created from required dependencies rather than bean-order-sensitive `@ConditionalOnBean` method conditions.
- Applied the same bean-ordering fix to the Kafka dashboard service auto-configuration.
- Added a management-service HTTP integration test using RestAssured for `GET /api/orchestrator/workflows`.
- Corrected the management-service test property to `workflow.orchestrator.jdbc.schema-initialization`.

### POC packaging and recovery

- Maven coordinates now use the maintainer namespace `io.github.hatimcherkaoui`.
- Library artifacts include license/notice metadata, sources and Javadocs in the release profile.
- Runnable applications are separated into the default POC profile and excluded from publication.
- Semantic HTTP/network/database failures support terminal failure, automatic retry and breaker suspension.
- Retry sequencing and half-open permits are verified by WireMock/Testcontainers scenarios.
- Obsolete phase reports and local agent instructions are removed from the public repository.
