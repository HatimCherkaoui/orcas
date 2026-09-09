package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StepCatalog {
    private final Map<String, com.github.orcas.orchestrator.core.api.WorkflowStep> steps = new LinkedHashMap<>();

    public StepCatalog(Collection<? extends com.github.orcas.orchestrator.core.api.WorkflowStep> beans) {
        registerAll(beans);
    }

    public StepCatalog(Collection<? extends com.github.orcas.orchestrator.core.api.WorkflowStep> beans,
                       Collection<? extends com.github.orcas.orchestrator.core.api.WorkflowStep> discovered) {
        registerAll(beans);
        registerAll(discovered);
    }

    public void registerAll(Collection<? extends com.github.orcas.orchestrator.core.api.WorkflowStep> beans) {
        for (var s : beans) {
            var a = s.getClass().getAnnotation(WorkflowStep.class);
            var n = a != null ? a.value() : s.name();
            if (n == null || n.isBlank()) throw new IllegalArgumentException("Step name/alias required: " + s.getClass().getName());
            if (steps.putIfAbsent(n, s) != null) throw new IllegalStateException("Duplicate step: " + n);
        }
    }

    public com.github.orcas.orchestrator.core.api.WorkflowStep get(String n) {
        var s = steps.get(n);
        if (s == null) throw new IllegalArgumentException("Unknown step: " + n);
        return s;
    }

    public Set<String> aliases() { return Set.copyOf(steps.keySet()); }
}
