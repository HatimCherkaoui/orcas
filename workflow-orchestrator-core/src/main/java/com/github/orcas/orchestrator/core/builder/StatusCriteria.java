package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.Objects;
import java.util.function.Predicate;

/** Immutable route predicate with metadata used by the execution engine and dashboard. */
public final class StatusCriteria {
    private final Predicate<StatusEvent> predicate;
    private final String expectedStep;
    private final Status expectedStatus;

    private StatusCriteria(
            Predicate<StatusEvent> predicate,
            String expectedStep,
            Status expectedStatus) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.expectedStep = requireText(expectedStep, "expectedStep");
        this.expectedStatus = Objects.requireNonNull(expectedStatus, "expectedStatus");
    }

    public static StatusCriteria status(String step, Status status) {
        var expectedStep = requireText(step, "step");
        return new StatusCriteria(
                event -> event.step().equals(expectedStep) && event.status() == status,
                expectedStep,
                status);
    }

    public static StatusCriteria status(Class<? extends WorkflowStep> step, Status status) {
        return status(StepNames.of(step), status);
    }

    public static StatusCriteria success(String step) {
        return status(step, Status.SUCCESS);
    }

    public static StatusCriteria success(Class<? extends WorkflowStep> step) {
        return status(step, Status.SUCCESS);
    }

    public static StatusCriteria init() {
        return status(StepNames.INIT, Status.INIT);
    }

    public static StatusCriteria onStart() {
        return init();
    }

    public boolean matches(StatusEvent event) {
        return predicate.test(Objects.requireNonNull(event, "event"));
    }

    public boolean waitsFor(StatusEvent event) {
        Objects.requireNonNull(event, "event");
        return expectedStep.equals(event.step())
                && expectedStatus != event.status()
                && switch (event.status()) {
                    case INIT, STARTED, RUNNING, RUNNING_ASYNC -> true;
                    case SUCCESS, FAILED, SUSPENDED, SKIPPED -> false;
                };
    }

    public String expectedStep() {
        return expectedStep;
    }

    public Status expectedStatus() {
        return expectedStatus;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
