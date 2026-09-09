package com.github.orcas.orchestrator.autoconfigure.rest;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import java.lang.reflect.Method;

public final class WorkflowRestClientRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private final org.springframework.beans.factory.BeanFactory beanFactory;
    private Environment environment;
    public WorkflowRestClientRegistrar(org.springframework.beans.factory.BeanFactory beanFactory) { this.beanFactory = beanFactory; }
    @Override public void setEnvironment(Environment environment) { this.environment = environment; }

    @Override public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (!AutoConfigurationPackages.has(beanFactory)) return;
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bd) {
                return bd.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(WorkflowRestClient.class));
        for (String base : AutoConfigurationPackages.get(beanFactory)) {
            for (var candidate : scanner.findCandidateComponents(base)) {
                try {
                    Class<?> type = Class.forName(candidate.getBeanClassName());
                    if (!type.isInterface()) continue;
                    String beanName = "workflowRestClient_" + type.getSimpleName();
                    if (!registry.containsBeanDefinition(beanName)) {
                        var ann = type.getAnnotation(WorkflowRestClient.class);
                        var bd = BeanDefinitionBuilder.genericBeanDefinition(WorkflowRestClientFactoryBean.class)
                                .addConstructorArgValue(type).addConstructorArgValue(ann.baseUrl()).getBeanDefinition();
                        bd.setAutowireMode(org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
                        registry.registerBeanDefinition(beanName, bd);
                    }
                    int index = 0;
                    for (Method method : type.getMethods()) {
                        WorkflowStep step = method.getAnnotation(WorkflowStep.class);
                        if (step == null) continue;
                        String stepBean = "workflowRestStep_" + type.getSimpleName() + "_" + method.getName() + "_" + index++;
                        if (registry.containsBeanDefinition(stepBean)) continue;
                        var stepType = step.async() ? AsyncRestClientWorkflowStep.class : RestClientWorkflowStep.class;
                        var stepBd = BeanDefinitionBuilder.genericBeanDefinition(stepType)
                                .addConstructorArgValue(new RuntimeBeanReference(beanName))
                                .addConstructorArgValue(type)
                                .addConstructorArgValue(method.getName())
                                .addConstructorArgValue(step.value())
                                .addConstructorArgValue(step.mapper()).getBeanDefinition();
                        stepBd.setAutowireMode(org.springframework.beans.factory.config.AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
                        registry.registerBeanDefinition(stepBean, stepBd);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Unable to scan REST client " + candidate.getBeanClassName(), e);
                }
            }
        }
    }
    @Override public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {}
}
