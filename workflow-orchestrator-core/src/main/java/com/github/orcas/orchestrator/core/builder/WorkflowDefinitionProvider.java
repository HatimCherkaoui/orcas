package com.github.orcas.orchestrator.core.builder;

/** Supplies one resolved workflow definition. */
@FunctionalInterface
public interface WorkflowDefinitionProvider {
    WorkflowDefinition workflow();
}
