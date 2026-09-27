package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodAsyncWorkflowStep;
import com.github.orcas.orchestrator.core.api.MethodWorkflowStep;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/** Adapts @Workflow methods into core step objects. */
public final class WorkflowMethodStepScanner {
    private final ListableBeanFactory beanFactory;

    public WorkflowMethodStepScanner(ListableBeanFactory beanFactory) { this.beanFactory = beanFactory; }

    public List<com.github.orcas.orchestrator.core.api.WorkflowStep> discover() {
        var steps = new ArrayList<com.github.orcas.orchestrator.core.api.WorkflowStep>();
        for (Object bean : beanFactory.getBeansWithAnnotation(Workflow.class).values()) {
            Class<?> targetType = AopUtils.getTargetClass(bean);
            for (Method method : targetType.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) continue;
                var annotation = method.getAnnotation(WorkflowStep.class);
                if (annotation == null) continue;
                steps.add(annotation.async()
                        ? new MethodAsyncWorkflowStep(bean, method)
                        : new MethodWorkflowStep(bean, method));
            }
        }
        return List.copyOf(steps);
    }
}
