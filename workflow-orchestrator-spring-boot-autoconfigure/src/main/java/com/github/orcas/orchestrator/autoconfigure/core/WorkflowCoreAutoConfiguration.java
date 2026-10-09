package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.StepCatalog;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinitionProvider;
import com.github.orcas.orchestrator.core.engine.InMemoryWorkflowStateStore;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowObserver;
import com.github.orcas.orchestrator.core.engine.WorkflowRegistry;
import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.error.WorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/** Base Spring wiring for the pure Java workflow engine. */
@AutoConfiguration(afterName = {
        "com.github.orcas.orchestrator.jdbc.autoconfigure.WorkflowJdbcAutoConfiguration",
        "com.github.orcas.orchestrator.kafka.autoconfigure.WorkflowKafkaAutoConfiguration",
        "com.github.orcas.orchestrator.observability.autoconfigure.WorkflowObservabilityAutoConfiguration"
})
public final class WorkflowCoreAutoConfiguration {
    @Bean
    static BeanDefinitionRegistryPostProcessor workflowClassRegistrar(BeanFactory beanFactory) {
        return new WorkflowClassRegistrar(beanFactory);
    }

    @Bean
    static BeanDefinitionRegistryPostProcessor workflowStepClassRegistrar(BeanFactory beanFactory) {
        return new WorkflowStepClassRegistrar(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowMethodStepScanner workflowMethodStepScanner(org.springframework.beans.factory.ListableBeanFactory beanFactory) {
        return new WorkflowMethodStepScanner(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowBeanResolver workflowBeanResolver(BeanFactory beanFactory) {
        return new WorkflowBeanResolver(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowErrorCategorizer workflowErrorCategorizer() {
        return new DefaultWorkflowErrorCategorizer();
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowObserver workflowObserver() {
        return WorkflowObserver.noop();
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowStateStore.class)
    WorkflowStateStore workflowStateStore() {
        return new InMemoryWorkflowStateStore();
    }

    @Bean(name = "workflowTaskExecutor", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "workflowTaskExecutor")
    Executor workflowTaskExecutor(WorkflowAsyncProperties properties) {
        return new BoundedWorkflowExecutor(properties.isVirtualThreads(), properties.getConcurrency());
    }

    @Bean
    @ConditionalOnMissingBean(StepCatalog.class)
    StepCatalog stepCatalog(
            ObjectProvider<WorkflowStep> steps,
            WorkflowMethodStepScanner methodScanner) {
        return new StepCatalog(steps.orderedStream().toList(), methodScanner.discover());
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowRegistry.class)
    WorkflowRegistry workflowRegistry(ObjectProvider<WorkflowDefinitionProvider> providers) {
        var registry = new WorkflowRegistry();
        providers.orderedStream().map(WorkflowDefinitionProvider::workflow).forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    WorkflowEventPublisher localWorkflowEventPublisher(ObjectProvider<WorkflowEngine> engine) {
        return event -> engine.getObject().handle(event);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowEngine.class)
    WorkflowEngine workflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore stateStore,
            WorkflowErrorCategorizer categorizer,
            WorkflowObserver observer,
            @org.springframework.beans.factory.annotation.Qualifier("workflowTaskExecutor") Executor executor) {
        return new WorkflowEngine(registry, publisher, stateStore, executor, categorizer, observer);
    }
}
