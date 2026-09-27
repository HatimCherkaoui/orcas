package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.WorkflowStep;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Catalog of executable steps keyed by the annotation-derived step name. */
public final class StepCatalog {
    private final Map<String, WorkflowStep> steps = new LinkedHashMap<>();

    public StepCatalog(Collection<? extends WorkflowStep> steps) {
        registerAll(steps);
    }

    public StepCatalog(Collection<? extends WorkflowStep> steps,
                       Collection<? extends WorkflowStep> discovered) {
        registerAll(steps);
        registerAll(discovered);
    }

    public void registerAll(Collection<? extends WorkflowStep> values) {
        for (WorkflowStep step : values) {
            String name = step.name();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Step name is required: " + step.getClass().getName());
            }
            if (steps.putIfAbsent(name, step) != null) {
                throw new IllegalStateException("Duplicate step: " + name);
            }
        }
    }

    public WorkflowStep get(String name) {
        var step = steps.get(name);
        if (step == null) throw new IllegalArgumentException("Unknown step: " + name);
        return step;
    }

    public WorkflowStep get(Class<? extends WorkflowStep> type) {
        return get(com.github.orcas.orchestrator.core.api.StepNames.of(type));
    }

    public Set<String> names() {
        return Set.copyOf(steps.keySet());
    }
}
