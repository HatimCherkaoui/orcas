package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.api.*;
import com.github.orcas.orchestrator.core.model.*;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowAsyncScopeTest {
    @Test void restoresCallerContextWithADirectExecutorOnSuccessAndFailure() {
        var store = new InMemoryWorkflowStateStore();
        var context = WorkflowContext.of("input");
        var outer = WorkflowContext.of("outer");
        store.start("wf-async","async-workflow",context);
        var executor = new WorkflowStepExecutor(store,event -> { },Runnable::run,new DefaultWorkflowErrorCategorizer(),WorkflowObserver.noop(),null);
        WorkflowContextHolder.set(outer);
        try {
            for (boolean fail : new boolean[]{false,true}) {
                var step = new AsyncStep() {
                    @Override public String name() { return "async-step"; }
                    @Override public StepResult executeAsync(WorkflowContext c) {
                        assertThat(WorkflowContextHolder.current()).isSameAs(context);
                        if (fail) throw new IllegalStateException("expected failure");
                        return StepResult.success(c);
                    }
                };
                executor.execute(StatusEvent.of("wf-async","async-workflow",StepNames.INIT,Status.INIT,context.metadata().asMap(),"start"),step);
                assertThat(WorkflowContextHolder.current()).isSameAs(outer);
            }
        } finally { WorkflowContextHolder.clear(); }
    }
}
