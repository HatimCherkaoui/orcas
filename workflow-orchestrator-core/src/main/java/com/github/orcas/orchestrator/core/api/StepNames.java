package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;

import java.lang.reflect.Method;
import java.util.Objects;

/** Central naming policy shared by annotations, scanners and the pipeline DSL. */
public final class StepNames {
    public static final String INIT = "INIT";

    private StepNames() {
    }

    public static String of(Object step) {
        return of(Objects.requireNonNull(step, "step").getClass());
    }

    public static String of(Class<?> type) {
        var annotation = type.getAnnotation(WorkflowStep.class);
        return annotation == null || annotation.value().isBlank()
                ? type.getSimpleName()
                : annotation.value();
    }

    public static String of(Method method) {
        var annotation = method.getAnnotation(WorkflowStep.class);
        return annotation == null || annotation.value().isBlank()
                ? method.getName()
                : annotation.value();
    }

    public static String workflow(Class<?> type) {
        var annotation = type.getAnnotation(Workflow.class);
        if (annotation == null) {
            throw new IllegalArgumentException("@Workflow is required on " + type.getName());
        }
        return annotation.value().isBlank() ? type.getSimpleName() : annotation.value();
    }
}
