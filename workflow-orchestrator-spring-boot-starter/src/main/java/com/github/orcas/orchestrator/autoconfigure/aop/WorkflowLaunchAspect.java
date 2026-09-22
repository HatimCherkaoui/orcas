package com.github.orcas.orchestrator.autoconfigure.aop;

import com.github.orcas.orchestrator.core.annotation.LaunchWorkflow;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

/**
 * AOP aspect implementing {@link LaunchWorkflow}: intercepts the annotated controller
 * method, builds a {@link WorkflowContext} from its arguments and the current HTTP
 * request headers, and starts the named workflow on the {@link WorkflowEngine} before
 * the controller method body executes.
 */
@Aspect
@Component
public final class WorkflowLaunchAspect {
    private static final Logger log = LoggerFactory.getLogger(WorkflowLaunchAspect.class);

    private final WorkflowEngine engine;

    public WorkflowLaunchAspect(WorkflowEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(launch)")
    public Object launch(ProceedingJoinPoint joinPoint, LaunchWorkflow launch) throws Throwable {
        Object body = null;
        HttpHeaders headers = new HttpHeaders();
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpHeaders h) headers.putAll(h);
            else if (arg instanceof WorkflowContext) body = ((WorkflowContext) arg).businessInput();
            else if (body == null && !(arg instanceof org.springframework.validation.BindingResult)) body = arg;
        }
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            Collections.list(request.getHeaderNames()).forEach(headerName ->
                    headers.set(headerName, request.getHeader(headerName))
            );
        }
        log.debug("Launching workflow '{}' from @LaunchWorkflow-annotated method {}", launch.value(), joinPoint.getSignature().toShortString());
        engine.start(launch.value(), body == null ? WorkflowContext.of(null, headers.toSingleValueMap()) : WorkflowContext.of(body, headers.toSingleValueMap()));
        return joinPoint.proceed();
    }
}
