package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/** Tracks parallel branch completion until a route can execute its join step. */
final class WorkflowJoinCoordinator {
    private final ConcurrentMap<String, JoinState> joins = new ConcurrentHashMap<>();

    void register(String workflowId, WorkflowDefinition.Route route) {
        if (!route.hasJoin()) {
            return;
        }

        var key = key(workflowId, route.joinKey());
        var expected = route.steps().stream()
                .map(WorkflowStep::name)
                .collect(Collectors.toUnmodifiableSet());
        joins.putIfAbsent(key, new JoinState(expected, route.joinStep()));
    }

    boolean isRelated(StatusEvent event) {
        String prefix = event.workflowId() + ":";
        return joins.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(prefix)
                        && entry.getValue().expected().contains(event.step()));
    }

    void accept(StatusEvent event, StepStarter starter) {
        if (event.status() != Status.SUCCESS) {
            return;
        }

        String prefix = event.workflowId() + ":";
        joins.forEach((key, join) -> {
            if (!key.startsWith(prefix) || !join.expected().contains(event.step())) {
                return;
            }
            join.completed().add(event.step());
            if (join.completed().containsAll(join.expected()) && joins.remove(key, join)) {
                starter.start(event, join.joinStep());
            }
        });
    }

    private static String key(String workflowId, String joinKey) {
        return workflowId + ":" + joinKey;
    }

    @FunctionalInterface
    interface StepStarter {
        void start(StatusEvent event, WorkflowStep step);
    }

    private record JoinState(Set<String> expected, Set<String> completed, WorkflowStep joinStep) {
        private JoinState(Set<String> expected, WorkflowStep joinStep) {
            this(expected, ConcurrentHashMap.newKeySet(), joinStep);
        }
    }
}
