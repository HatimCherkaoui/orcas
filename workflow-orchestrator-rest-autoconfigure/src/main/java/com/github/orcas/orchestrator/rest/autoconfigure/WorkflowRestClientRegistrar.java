package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestCall;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/** Registers REST interfaces and turns their @WorkflowStep methods into executable steps. */
public final class WorkflowRestClientRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private final BeanFactory beanFactory;
    private Environment environment;

    public WorkflowRestClientRegistrar(BeanFactory beanFactory) {
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

        var scanner = restClientScanner();
        AutoConfigurationPackages.get(beanFactory).stream()
                .flatMap(basePackage -> scanner.findCandidateComponents(basePackage).stream())
                .forEach(candidate -> register(registry, candidate.getBeanClassName()));
    }

    private ClassPathScanningCandidateComponentProvider restClientScanner() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                // REST clients are interfaces, which the default component scanner may reject.
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(WorkflowRestClient.class));
        return scanner;
    }

    private void register(BeanDefinitionRegistry registry, String className) {
        try {
            var type = Class.forName(className);
            if (!type.isInterface()) {
                return;
            }

            var clientName = clientBeanName(type);
            registerClient(registry, type, clientName);
            registerSteps(registry, type, clientName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to register REST client " + className, exception);
        }
    }

    private void registerClient(
            BeanDefinitionRegistry registry,
            Class<?> type,
            String clientName) {
        if (registry.containsBeanDefinition(clientName)) {
            return;
        }

        var annotation = type.getAnnotation(WorkflowRestClient.class);
        var definition = BeanDefinitionBuilder.genericBeanDefinition(WorkflowRestClientFactoryBean.class)
                .addConstructorArgValue(type)
                .addConstructorArgValue(annotation.baseUrl())
                .getBeanDefinition();
        definition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
        registry.registerBeanDefinition(clientName, definition);
    }

    private void registerSteps(
            BeanDefinitionRegistry registry,
            Class<?> type,
            String clientName) {
        var index = 0;
        for (var method : type.getMethods()) {
            var step = method.getAnnotation(WorkflowStep.class);
            if (step == null) {
                continue;
            }

            var rest = method.getAnnotation(WorkflowRestCall.class);
            var stepBeanName = stepBeanName(type, method, index++);
            if (registry.containsBeanDefinition(stepBeanName)) {
                continue;
            }

            var stepType = step.async()
                    ? AsyncRestClientWorkflowStep.class
                    : RestClientWorkflowStep.class;
            var definition = BeanDefinitionBuilder.genericBeanDefinition(stepType)
                    .addConstructorArgReference(clientName)
                    .addConstructorArgValue(method)
                    .addConstructorArgValue(
                            rest == null ? ContextMapper.Identity.class : rest.mapper())
                    .addConstructorArgValue(
                            rest == null ? ResponseConsumer.Void.class : rest.responseSubscriber())
                    .getBeanDefinition();
            definition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
            registry.registerBeanDefinition(stepBeanName, definition);
        }
    }

    private static String clientBeanName(Class<?> type) {
        return "workflowRestClient_" + type.getName().replace('.', '_');
    }

    private static String stepBeanName(Class<?> type, java.lang.reflect.Method method, int index) {
        return "workflowRestStep_"
                + type.getName().replace('.', '_')
                + "_"
                + method.getName()
                + "_"
                + index;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // All registrations are made before normal bean instantiation.
    }
}
