package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate describing when a route should trigger in response to an incoming
 * {@link StatusEvent}. Besides exact matching, it also exposes the expected step/status
 * pair so the dashboard can render the workflow graph without re-parsing DSL code.
 */
public final class StatusCriteria {
    private final Predicate<StatusEvent> predicate;
    private final String expectedStep;
    private final Status expectedStatus;

    private StatusCriteria(Predicate<StatusEvent> predicate, String expectedStep, Status expectedStatus) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.expectedStep = Objects.requireNonNull(expectedStep, "expectedStep");
        this.expectedStatus = Objects.requireNonNull(expectedStatus, "expectedStatus");
    }

    public static StatusCriteria status(String step, Status status) {
        return new StatusCriteria(e -> e.step().equals(step) && e.status() == status, step, status);
    }

    public static StatusCriteria success(String step) {
        return status(step, Status.SUCCESS);
    }

    public static StatusCriteria init() {
        return status(StepNames.INIT.name(), Status.INIT);
    }

    public static StatusCriteria onStart() {
        return init();
    }

    public boolean matches(StatusEvent event) {
        return predicate.test(event);
    }

    /**
     * Returns whether an incoming event belongs to the same join key/step we are tracking,
     * but has not yet reached the expected terminal status. Only in-flight statuses are
     * treated as "still waiting"; terminal statuses such as {@code SUSPENDED} must not
     * keep a route retrying forever.
     */
    public boolean waitsFor(StatusEvent event) {
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
}
