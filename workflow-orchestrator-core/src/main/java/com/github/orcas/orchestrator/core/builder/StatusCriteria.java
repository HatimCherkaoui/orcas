package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.function.Predicate;

public record StatusCriteria(Predicate<StatusEvent> predicate, String expectedStep, Status expectedStatus) {

    public boolean matches(StatusEvent e) {
        return predicate.test(e);
    }

    public boolean waitsFor(StatusEvent e) {
        return expectedStep.equals(e.step()) && expectedStatus != e.status();
    }

    public static StatusCriteria status(String step, Status status) {
        return new StatusCriteria(e -> e.step().equals(step) && e.status() == status, step, status);
    }

    public static StatusCriteria status(Class<?> step, Status status) {
        var a = step.getAnnotation(com.github.orcas.orchestrator.core.annotation.WorkflowStep.class);
        if (a == null) throw new IllegalArgumentException("@WorkflowStep is required on " + step.getName());
        return status(a.value(), status);
    }

    public static StatusCriteria onStart() {
        return status(StepNames.INIT.name(), Status.INIT);
    }
}
