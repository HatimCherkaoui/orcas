package com.github.orcas.orchestrator.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.step.WorkflowStepClassRegistrar;
import com.github.orcas.orchestrator.core.api.WorkflowStep;
import com.github.orcas.orchestrator.core.builder.StepCatalog;
import com.github.orcas.orchestrator.core.builder.WorkflowDefinitionProvider;
import com.github.orcas.orchestrator.core.engine.WorkflowEngine;
import com.github.orcas.orchestrator.core.engine.WorkflowRegistry;
import com.github.orcas.orchestrator.core.engine.WorkflowStateStore;
import com.github.orcas.orchestrator.core.event.WorkflowEventPublisher;
import com.github.orcas.orchestrator.autoconfigure.step.workflowMethodStepScanner;
import com.github.orcas.orchestrator.autoconfigure.step.WorkflowClassRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Core auto-configuration wiring the workflow engine: the {@link StepCatalog} of
 * discovered steps, the {@link WorkflowRegistry} of workflow definitions, the async
 * executor used for {@code AsyncStep}s and parallel branches, and the
 * {@link WorkflowEngine} itself.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}, so applications can substitute
 * their own implementation (e.g. a custom {@link WorkflowStateStore}) simply by
 * declaring a bean of that type.
 */
@AutoConfiguration
@AutoConfigureAfter({WorkflowJdbcAutoConfiguration.class, WorkflowKafkaInfrastructureAutoConfiguration.class})
public class WorkflowCoreAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCoreAutoConfiguration.class);

    @Bean
    static WorkflowClassRegistrar workflowClassRegistrar(org.springframework.beans.factory.BeanFactory beanFactory) { return new WorkflowClassRegistrar(beanFactory); }

    @Bean
    static WorkflowStepClassRegistrar workflowStepClassRegistrar(org.springframework.beans.factory.BeanFactory beanFactory) { return new WorkflowStepClassRegistrar(beanFactory); }

    @Bean
    @ConditionalOnMissingBean(workflowMethodStepScanner.class)
    workflowMethodStepScanner workflowMethodStepScanner(org.springframework.beans.factory.ListableBeanFactory beanFactory) {
        return new workflowMethodStepScanner(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean(StepCatalog.class)
    StepCatalog stepCatalog(ObjectProvider<WorkflowStep> steps, workflowMethodStepScanner methodScanner) {
        var catalog = new StepCatalog(steps.orderedStream().toList(), methodScanner.discover());
        log.debug("Built workflow step catalog");
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowRegistry.class)
    WorkflowRegistry workflowRegistry(ObjectProvider<WorkflowDefinitionProvider> providers) {
        var registry = new WorkflowRegistry();
        providers.orderedStream().map(WorkflowDefinitionProvider::workflow).forEach(registry::register);
        return registry;
    }

    /**
     * Async executor shared by {@code AsyncStep}s and parallel route branches. Uses a
     * virtual-thread-per-task executor by default ({@code workflow.orchestrator.async.virtual-threads=true}),
     * otherwise a fixed thread pool sized by {@code workflow.orchestrator.async.concurrency}.
     */
    @Bean(name = "workflowTaskExecutor", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "workflowTaskExecutor")
    Executor workflowTaskExecutor(WorkflowProperties properties) {
        if (properties.getAsync().isVirtualThreads()) {
            log.info("Configuring workflow async executor with virtual threads");
            return Executors.newVirtualThreadPerTaskExecutor();
        }
        log.info("Configuring workflow async executor with a fixed thread pool of size {}", properties.getAsync().getConcurrency());
        return Executors.newFixedThreadPool(properties.getAsync().getConcurrency());
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowEngine.class)
    @ConditionalOnBean({WorkflowRegistry.class, WorkflowEventPublisher.class, WorkflowStateStore.class, StepCatalog.class})
    WorkflowEngine workflowEngine(
            WorkflowRegistry registry,
            WorkflowEventPublisher publisher,
            WorkflowStateStore stateStore,
            @Qualifier("workflowTaskExecutor") Executor asyncExecutor) {
        log.info("Initializing workflow engine with {} registered workflow(s)", registry.all().size());
        return new WorkflowEngine(registry, publisher, stateStore, asyncExecutor);
    }
}
