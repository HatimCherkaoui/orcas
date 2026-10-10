package com.github.orcas.orchestrator.autoconfigure.core;

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

/** Registers {@link Workflow} classes as constructor-autowired Spring beans. */
public final class WorkflowClassRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

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
        if (!AutoConfigurationPackages.has(beanFactory)) {
            return;
        }

        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Workflow.class));

        for (String basePackage : AutoConfigurationPackages.get(beanFactory)) {
            scanner.findCandidateComponents(basePackage)
                    .forEach(candidate -> register(registry, candidate.getBeanClassName()));
        }
    }

    private void register(BeanDefinitionRegistry registry, String className) {
        try {
            Class<?> type = Class.forName(className);
            if (type.isInterface() || java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
                return;
            }

            String beanName = "workflow_" + className.replace('.', '_');
            if (registry.containsBeanDefinition(beanName)) {
                return;
            }

            var definition = BeanDefinitionBuilder.genericBeanDefinition(type).getBeanDefinition();
            definition.setAutowireMode(
                    org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
            registry.registerBeanDefinition(beanName, definition);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to register workflow " + className, exception);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // Registration is complete during postProcessBeanDefinitionRegistry.
    }
}
