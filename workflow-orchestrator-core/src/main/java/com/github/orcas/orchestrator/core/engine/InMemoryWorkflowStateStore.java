package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.Status;
import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Non-persistent state store for tests and local runs. */
public final class InMemoryWorkflowStateStore implements WorkflowStateStore, WorkflowRetryStateStore {
    private final ConcurrentMap<String, WorkflowContext> contexts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> workflows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StepContext> steps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<StatusEvent>> events = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> retries = new ConcurrentHashMap<>();

    @Override
    public void start(String workflowId, String workflow, WorkflowContext context) {
        workflows.put(workflowId, workflow);
        contexts.put(workflowId, context);
    }

    @Override
    public void record(StatusEvent event, String stepTypeClassName) {
        events.computeIfAbsent(event.workflowId(), ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(event);
    }

    @Override
    public String workflowName(String workflowId) {
        var name = workflows.get(workflowId);
        if (name == null) throw new IllegalArgumentException("Unknown pipeline: " + workflowId);
        return name;
    }

    @Override
    public WorkflowContext context(String workflowId) {
        var context = contexts.get(workflowId);
        if (context == null) throw new IllegalArgumentException("Unknown pipeline: " + workflowId);
        return context;
    }

    @Override
    public Optional<StepContext> stepContext(String workflowId, String stepName) {
        return Optional.ofNullable(steps.get(key(workflowId, stepName)));
    }

    @Override
    public void saveStepContext(StepContext context) {
        steps.put(key(context.workflowId(), context.stepName()), context);
    }

    @Override
    public void updateContext(String workflowId, WorkflowContext context) {
        contexts.put(workflowId, context);
    }

    @Override
    public void finish(StatusEvent event) {
        record(event, null);
    }

    @Override
    public List<String> suspendedWorkflowIds(String stepName) {
        return events.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .filter(event -> event.step().equals(stepName))
                        .reduce((first, second) -> second)
                        .map(event -> event.status() == Status.SUSPENDED)
                        .orElse(false))
                .map(java.util.Map.Entry::getKey)
                .toList();
    }

    @Override
    public int retryCount(String workflowId, String stepName) {
        return retries.getOrDefault(key(workflowId, stepName), 0);
    }

    @Override
    public void recordRetry(String workflowId, String stepName, int attempt, String reason) {
        retries.merge(key(workflowId, stepName), 1, Integer::sum);
    }

    private static String key(String workflowId, String stepName) {
        return workflowId + ':' + stepName;
    }
}
