package com.github.orcas.orchestrator.autoconfigure.core;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Registers concrete @WorkflowStep classes with constructor injection. */
public final class WorkflowStepClassRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private final BeanFactory beanFactory;
    private Environment environment;

    public WorkflowStepClassRegistrar(BeanFactory beanFactory) { this.beanFactory = beanFactory; }
    @Override public void setEnvironment(Environment environment) { this.environment = environment; }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (!AutoConfigurationPackages.has(beanFactory)) return;
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(WorkflowStep.class));
        for (String base : AutoConfigurationPackages.get(beanFactory)) {
            for (var candidate : scanner.findCandidateComponents(base)) {
                try {
                    Class<?> type = Class.forName(candidate.getBeanClassName());
                    if (type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) continue;
                    String name = "workflowStep_" + type.getName().replace('.', '_');
                    if (!registry.containsBeanDefinition(name)) {
                        var definition = BeanDefinitionBuilder.genericBeanDefinition(type).getBeanDefinition();
                        definition.setAutowireMode(
                                org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
                        registry.registerBeanDefinition(name, definition);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Unable to register workflow step " + candidate.getBeanClassName(), e);
                }
            }
        }
    }

    @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) { }
}
