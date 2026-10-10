package com.github.orcas.orchestrator.rest.annotation;

import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** REST-specific adapter options kept outside the core @WorkflowStep annotation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WorkflowRestCall {
    Class<? extends ContextMapper<?>> mapper() default ContextMapper.Identity.class;
    Class<? extends ResponseConsumer<?>> responseSubscriber() default ResponseConsumer.Void.class;
}
