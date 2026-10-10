package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/**
 * Base implementation for synchronous workflow steps.
 *
 * This mirrors the existing {@link Step} contract but provides a reusable
 * template method for implementations that want a cached step name and a single
 * execution hook.
 *
 * Subclasses should implement:
 * - {@link #doExecute(WorkflowContext)} - The actual step logic
 */
public abstract class BaseWorkflowStep extends Step {

  protected final String name;

  /** Initialize step with cached name for performance. */
  protected BaseWorkflowStep(String name) {
    this.name = name != null ? name : StepNames.of(this);
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final StepResult execute(WorkflowContext context) throws Exception {
    return doExecute(context);
  }

  /**
   * Template method: Subclasses implement actual step logic here.
   *
   * @param context The workflow context containing input/output
   * @return The result of step execution
   * @throws Exception if step execution fails
   */
  protected abstract StepResult doExecute(WorkflowContext context) throws Exception;

}


