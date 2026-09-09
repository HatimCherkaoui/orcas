package com.github.orcas.orchestrator.core.event;

import com.github.orcas.orchestrator.core.model.StatusEvent;

@FunctionalInterface
public interface WorkflowEventPublisher {
    void publish(StatusEvent event);
}
