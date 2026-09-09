package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.List;

/**
 * Immutable routing table for a single workflow: an ordered list of {@link Route}s,
 * each describing which step(s) should run when a {@link StatusEvent} matches a
 * {@link StatusCriteria}. Built via {@link PipelineBuilder} and looked up from the
 * {@code WorkflowRegistry} by name at runtime.
 */
public final class WorkflowDefinition {
    /**
     * A single routing rule: when an incoming {@link StatusEvent} matches
     * {@code criteria}, execute every step in {@code steps}. If {@code joinStep} is
     * non-null and there is more than one step, the steps run as parallel branches and
     * {@code joinStep} runs once all of them report {@code SUCCESS} (see
     * {@code WorkflowEngine}'s join tracking, keyed by {@code joinKey}).
     *
     * @param criteria predicate matched against incoming events to select this route
     * @param steps    step(s) to execute when the route matches (branches, if parallel)
     * @param joinStep optional step executed once all parallel branches complete
     * @param joinKey  unique key used to track in-flight join state for this route
     */
    public record Route(StatusCriteria criteria, List<WorkflowStep> steps, WorkflowStep joinStep, String joinKey) {
        public Route {
            steps = List.copyOf(steps);
        }
    }

    private final String name;
    private final List<Route> routes;

    /**
     * @param n workflow name; must be non-blank and unique across the application
     * @param r ordered routing table for this workflow
     * @throws IllegalArgumentException if {@code n} is null or blank
     */
    public WorkflowDefinition(String n, List<Route> r) {
        if (n == null || n.isBlank()) throw new IllegalArgumentException("workflow name is required");
        name = n;
        routes = List.copyOf(r);
    }

    /** @return the unique workflow name */
    public String name() {
        return name;
    }

    /** @return an immutable view of this workflow's routing table */
    public List<Route> routes() {
        return routes;
    }

    /**
     * Finds a step by name across all routes (including join steps).
     *
     * @param stepName step name to look up; the synthetic {@link StepNames#INIT} name
     *                 always resolves to {@code null} since it is not a real step
     * @return the matching step, or {@code null} if not found
     */
    public WorkflowStep findStep(String stepName) {
        if (StepNames.INIT.name().equals(stepName)) {
            return null;
        }
        return routes.stream()
                .flatMap(route -> java.util.stream.Stream.concat(
                        route.steps().stream(),
                        route.joinStep() == null ? java.util.stream.Stream.empty()
                                : java.util.stream.Stream.of(route.joinStep())))
                .filter(step -> step.name().equals(stepName))
                .findFirst()
                .orElse(null);
    }

    /**
     * @param e incoming status event
     * @return every route whose criteria matches the event, in declaration order
     */
    public List<Route> matching(StatusEvent e) {
        return routes.stream().filter(r -> r.criteria().matches(e)).toList();
    }

    /**
     * @param e incoming status event
     * @return {@code true} if some route's criteria is waiting for further events
     *         before it can match (e.g. an incomplete parallel join)
     */
    public boolean waitingFor(StatusEvent e) {
        return routes.stream().anyMatch(r -> r.criteria().waitsFor(e));
    }
}
