package com.github.orcas.orchestrator.autoconfigure.step;

import com.github.orcas.orchestrator.core.annotation.Workflow;
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

/**
 * Bean-definition-registry post-processor that scans the application's base packages
 * for classes annotated with {@code @Workflow} extending {@code WorkflowStep}, and
 * registers them as Spring beans so they participate in dependency injection and are
 * picked up by {@link WorkflowMethodStepScanner}/{@code StepCatalog}.
 */
public final class WorkflowClassRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private final BeanFactory beanFactory;
    private Environment environment;

    public WorkflowClassRegistrar(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (!AutoConfigurationPackages.has(beanFactory)) return;
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bd) {
                return bd.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(Workflow.class));
        for (String base : AutoConfigurationPackages.get(beanFactory))
            for (var candidate : scanner.findCandidateComponents(base)) {
                String className = candidate.getBeanClassName();
                try {
                    Class<?> type = Class.forName(className);
                    if (type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) continue;
                    String name = "workflow_" + type.getAnnotation(Workflow.class).value().replaceAll("[^A-Za-z0-9_]", "_");
                    if (!registry.containsBeanDefinition(name)) {
                        var bd = BeanDefinitionBuilder.genericBeanDefinition(type).getBeanDefinition();
                        bd.setAutowireMode(org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
                        registry.registerBeanDefinition(name, bd);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Unable to scan workflow " + className, e);
                }
            }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }
}
