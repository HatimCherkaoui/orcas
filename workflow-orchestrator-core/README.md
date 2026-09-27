# Core

The framework-neutral workflow engine.

It contains workflow annotations, the execution model, context and metadata, routing, the builder DSL, state contracts and extension interfaces. It has no Spring, Kafka, JDBC, HTTP or Reactor dependency.

Use `Steps.step(...)` when a workflow step is easier to express as a function.
