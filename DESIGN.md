# Design

## Bean ordering

The starter is split into four auto-configurations to avoid conditional bean ordering failures:

- `WorkflowJdbcAutoConfiguration`: DataSource-backed JDBC state store only.
- `WorkflowKafkaInfrastructureAutoConfiguration`: publisher, retry handler and listener factory.
- `WorkflowCoreAutoConfiguration`: registry, step catalog, executor and engine after JDBC/Kafka infrastructure.
- `WorkflowKafkaConsumerAutoConfiguration`: consumer after the engine and listener infrastructure.

No auto-configuration creates a DataSource, JPA EntityManagerFactory, JPA/Hibernate SessionFactory or Spring Data repository.

## Customization

Override `WorkflowStateStore`, `WorkflowEventPublisher`, `WorkflowEngine`, `StepCatalog`, `WorkflowRegistry`, `workflowTaskExecutor`, `workflowKafkaErrorHandler`, or `workflowKafkaListenerContainerFactory` with application beans.

## Persistence

The default state store is pure `NamedParameterJdbcTemplate`. Schema initialization can be disabled when Flyway/Liquibase owns schema lifecycle.
