package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.StatusEvent;
import com.github.orcas.orchestrator.core.model.StepContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Non-persistent {@link WorkflowStateStore} backed by {@link ConcurrentHashMap}s.
 *
 * <p>Intended for tests, samples and single-node demos where durability across
 * restarts is not required. Production deployments should use a durable
 * implementation such as {@code JdbcWorkflowStateStore} from the Spring Boot starter,
 * which also maintains an append-only audit log queryable from the dashboard.
 */
public final class InMemoryWorkflowStateStore implements WorkflowStateStore {
    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkflowStateStore.class);

    private final ConcurrentMap<String, WorkflowContext> m = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StepContext> steps = new ConcurrentHashMap<>();

    public void start(String i, String w, WorkflowContext c) {
        log.debug("Storing initial in-memory state for workflow instance {} ('{}')", i, w);
        m.put(i, c);
    }

    public void record(StatusEvent e) {
    }

    public StepContext stepContext(String workflowId, String stepName) {
        return steps.get(workflowId + ":" + stepName);
    }

    public void saveStepContext(StepContext context) {
        log.trace("Saving in-memory step context for {}:{}", context.workflowId(), context.stepName());
        steps.put(context.workflowId() + ":" + context.stepName(), context);
    }

    public WorkflowContext context(String i) {
        var c = m.get(i);
        if (c == null) {
            log.warn("Requested context for unknown in-memory pipeline {}", i);
            throw new IllegalArgumentException("Unknown pipeline: " + i);
        }
        return c;
    }

    public void updateContext(String i, WorkflowContext c) {
        m.put(i, c);
    }

    public void finish(StatusEvent e) {
    }
}
