package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;
import java.util.concurrent.CompletableFuture;

/**
 * Base implementation for asynchronous workflow steps.
 *
 * Mirrors the existing {@link AsyncStep} contract while providing a reusable
 * async template method for implementations that return a {@link CompletableFuture}.
 */
public abstract class BaseAsyncWorkflowStep extends AsyncStep {

  protected final String name;

  /** Initialize async step with cached name. */
  protected BaseAsyncWorkflowStep(String name) {
    this.name = name != null ? name : StepNames.of(this);
  }

  @Override
  public String name() {
    return name;
  }

  /**
   * Execute step asynchronously using CompletableFuture.
   * Wraps doExecuteAsync() and handles async errors.
   */
  @Override
  public final StepResult executeAsync(WorkflowContext context) throws Exception {
    return doExecuteAsync(context).get();
  }

  /**
   * Template method: Subclasses implement async step logic here.
   *
   * @param context The workflow context containing input/output
   * @return A CompletableFuture that will contain the step result
   */
  protected abstract CompletableFuture<StepResult> doExecuteAsync(WorkflowContext context);

}



