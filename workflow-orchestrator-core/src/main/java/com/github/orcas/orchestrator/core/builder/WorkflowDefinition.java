package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Immutable routing table for one workflow. */
public final class WorkflowDefinition {
    public record Route(
            StatusCriteria criteria,
            List<WorkflowStep> steps,
            WorkflowStep joinStep,
            String joinKey) {

        /** Creates an immutable route. A join requires both a step and a key. */
        public Route {
            Objects.requireNonNull(criteria, "criteria");
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("Route requires at least one step");
            }
            steps.forEach(step -> Objects.requireNonNull(step, "step"));
            if ((joinStep == null) != (joinKey == null)) {
                throw new IllegalArgumentException("joinStep and joinKey must be provided together");
            }
        }

        /** Returns whether this route waits for multiple steps before its join step. */
        public boolean hasJoin() {
            return joinStep != null;
        }
    }

    private final String name;
    private final List<Route> routes;

    public WorkflowDefinition(String name, List<Route> routes) {
        this.name = requireText(name, "workflow name");
        this.routes = List.copyOf(routes);
        this.routes.forEach(Objects::requireNonNull);
    }

    public String name() {
        return name;
    }

    public List<Route> routes() {
        return routes;
    }

    public Optional<WorkflowStep> findStep(String stepName) {
        if (StepNames.INIT.equals(stepName)) {
            return Optional.empty();
        }
        return routes.stream()
                .flatMap(route -> Stream.concat(
                        route.steps().stream(),
                        route.joinStep() == null ? Stream.empty() : Stream.of(route.joinStep())))
                .filter(step -> step.name().equals(stepName))
                .findFirst();
    }

    public List<Route> matching(StatusEvent event) {
        return routes.stream()
                .filter(route -> route.criteria().matches(event))
                .toList();
    }

    public boolean waitingFor(StatusEvent event) {
        return routes.stream().anyMatch(route -> route.criteria().waitsFor(event));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
