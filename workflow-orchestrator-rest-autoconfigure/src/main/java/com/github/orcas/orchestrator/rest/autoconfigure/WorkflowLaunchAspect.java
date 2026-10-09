package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.rest.annotation.LaunchWorkflow;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds a workflow context from controller arguments and starts the selected workflow. */
@Aspect
public final class WorkflowLaunchAspect {
    private static final Logger log = Logger.getLogger(WorkflowLaunchAspect.class.getName());
    private final WorkflowEngine engine;
    public WorkflowLaunchAspect(WorkflowEngine engine) { this.engine = engine; }

    @Around("@annotation(launch)")
    public Object launch(ProceedingJoinPoint joinPoint, LaunchWorkflow launch) throws Throwable {
        Object body = null;
        var headers = new HttpHeaders();
        for (Object argument : joinPoint.getArgs()) {
            if (argument instanceof HttpHeaders httpHeaders) headers.putAll(httpHeaders);
            else if (argument instanceof WorkflowContext workflowContext) body = workflowContext.businessInput();
            else if (body == null && !(argument instanceof org.springframework.validation.BindingResult)) body = argument;
        }
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            Collections.list(request.getHeaderNames()).forEach(name -> headers.set(name, request.getHeader(name)));
        }
        var workflow = StepNames.workflow(launch.workflow());
        try {
            // Do not return an accepted response until the initial state is stored
            // and Kafka acknowledges the INIT event. Otherwise a failed async start
            // leaves a STARTED row with no event to drive it, and the caller cannot
            // distinguish that from a workflow that is merely queued.
            engine.startAsync(
                    workflow,
                    WorkflowContext.of(body, headers.toSingleValueMap()))
                    .join();
        } catch (java.util.concurrent.CompletionException error) {
            log.log(Level.SEVERE, "Failed to start workflow '" + workflow + "'", error.getCause());
            throw new IllegalStateException("Unable to start workflow '" + workflow + "'", error.getCause());
        }
        return joinPoint.proceed();
    }
}
