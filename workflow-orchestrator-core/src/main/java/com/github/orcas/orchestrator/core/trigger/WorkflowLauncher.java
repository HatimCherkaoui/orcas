package com.github.orcas.orchestrator.core.trigger;

import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link WorkflowTrigger} that starts a new workflow instance directly on the
 * {@link WorkflowEngine}. Used as the terminal delegate by the concrete trigger
 * flavors ({@link RestTrigger}, {@link QueueTrigger}, {@link LambdaTrigger},
 * {@link SshFileTrigger}) once they have adapted their transport-specific payload
 * into a {@link PipelineContext}.
 */
public final class WorkflowLauncher implements WorkflowTrigger {
    private static final Logger log = LoggerFactory.getLogger(WorkflowLauncher.class);

    private final WorkflowEngine engine;

    public WorkflowLauncher(WorkflowEngine engine) { this.engine = engine; }

    @Override
    public void trigger(String workflow, PipelineContext context) {
        log.debug("Launching workflow '{}' via trigger", workflow);
        engine.start(workflow, context);
    }
}
