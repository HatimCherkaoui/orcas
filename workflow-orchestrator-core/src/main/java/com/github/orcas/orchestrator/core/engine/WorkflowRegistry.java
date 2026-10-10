package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of workflow definitions. */
public final class WorkflowRegistry {
    private final Map<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();

    public void register(WorkflowDefinition definition) {
        if (definitions.putIfAbsent(definition.name(), definition) != null) {
            throw new IllegalStateException("Duplicate workflow: " + definition.name());
        }
    }

    public WorkflowDefinition get(String name) {
        return definitions.get(name);
    }

    public Collection<WorkflowDefinition> all() {
        return List.copyOf(definitions.values());
    }
}
