package com.github.orcas.orchestrator.core.event;

import com.github.orcas.orchestrator.core.model.StatusEvent;

/** Publishes workflow lifecycle events to the selected transport. */
@FunctionalInterface
public interface WorkflowEventPublisher {
    void publish(StatusEvent event);
}
