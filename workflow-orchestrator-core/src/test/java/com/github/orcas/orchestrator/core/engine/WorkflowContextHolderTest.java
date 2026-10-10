package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowContextHolderTest {
    @Test void withRestoresPreviousExecution() {
        var outer = WorkflowContext.of("outer", java.util.Map.of());
        var inner = WorkflowContext.of("inner", java.util.Map.of());
        WorkflowContextHolder.set(outer);
        var original = WorkflowContextHolder.execution();
        var result = WorkflowContextHolder.with(
                new WorkflowContextHolder.Execution("id", "flow", inner, null),
                WorkflowContextHolder::current);
        assertThat(result.businessInput()).isEqualTo("inner");
        assertThat(WorkflowContextHolder.execution()).isSameAs(original);
        WorkflowContextHolder.clear();
    }
}
