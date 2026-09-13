package com.github.orcas.orchestrator.autoconfigure.step;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodWorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodAsyncWorkflowStep;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans all Spring-managed beans for methods annotated with {@code @WorkflowStep} and
 * adapts each into a {@code MethodWorkflowStep} (or {@code MethodAsyncWorkflowStep}
 * when {@code async() == true}), so plain {@code @Component} methods can act as
 * workflow steps without implementing {@code Step}/{@code AsyncStep} directly.
 */
public final class workflowMethodStepScanner {
    private final ListableBeanFactory beanFactory;

    public workflowMethodStepScanner(ListableBeanFactory beanFactory) { this.beanFactory = beanFactory; }

    public List<com.github.orcas.orchestrator.core.api.WorkflowStep> discover() {
        var result = new ArrayList<com.github.orcas.orchestrator.core.api.WorkflowStep>();
        for (var entry : beanFactory.getBeansWithAnnotation(Workflow.class).entrySet()) {
            Object bean = entry.getValue();
            Class<?> type = AopUtils.getTargetClass(bean);
            for (Method method : type.getMethods()) {
                WorkflowStep annotation = method.getAnnotation(WorkflowStep.class);
                if (annotation == null) continue;
                result.add(annotation.async()
                        ? new MethodAsyncWorkflowStep(bean, method, annotation.value())
                        : new MethodWorkflowStep(bean, method, annotation.value()));
            }
        }
        return List.copyOf(result);
    }
}
