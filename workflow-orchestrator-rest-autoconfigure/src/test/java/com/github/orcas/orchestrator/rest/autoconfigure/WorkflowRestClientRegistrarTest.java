package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.annotation.WorkflowStep;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.rest.annotation.WorkflowRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRestClientRegistrarTest {
    @Test
    void constructorDependenciesAreAutowiredForGeneratedClientAndStepBeans() {
        var factory = new DefaultListableBeanFactory();
        AutoConfigurationPackages.register(factory, getClass().getPackageName());

        var registrar = new WorkflowRestClientRegistrar(factory);
        registrar.setEnvironment(new StandardEnvironment());
        registrar.postProcessBeanDefinitionRegistry(factory);

        var client = factory.getBeanDefinition(
                "workflowRestClient_" + ExternalClient.class.getName().replace('.', '_'));
        var step = factory.getBeanDefinition(
                "workflowRestStep_" + ExternalClient.class.getName().replace('.', '_') + "_reserve_0");

        assertThat(client.getConstructorArgumentValues().getArgumentValue(0, Class.class).getValue())
                .isEqualTo(ExternalClient.class);
        assertThat(client.getConstructorArgumentValues().getArgumentValue(1, String.class).getValue())
                .isEqualTo("${payment.url}");

        assertThat(step.getBeanClassName())
                .isEqualTo(RestClientWorkflowStep.class.getName());
        assertThat(step.getConstructorArgumentValues().getIndexedArgumentValues().get(0))
                .isNotNull();
        assertThat(step.getConstructorArgumentValues().getArgumentValue(1, java.lang.reflect.Method.class).getValue())
                .isInstanceOf(java.lang.reflect.Method.class);
        assertThat(step.getConstructorArgumentValues().getArgumentValue(2, Class.class).getValue())
                .isEqualTo(ContextMapper.Identity.class);
        assertThat(step.getConstructorArgumentValues().getArgumentValue(3, Class.class).getValue())
                .isEqualTo(ResponseConsumer.Void.class);

    }

    @WorkflowRestClient(baseUrl = "${payment.url}")
    interface ExternalClient {
        @WorkflowStep
        void reserve();
    }
}
