package com.github.orcas.orchestrator.autoconfigure.step;


import com.github.orcas.orchestrator.core.annotation.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkflowClassRegistrarTest {

    private DefaultListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private WorkflowClassRegistrar registrar;

    @BeforeEach
    void setUp() {
        beanFactory = new DefaultListableBeanFactory();
        registry = mock(BeanDefinitionRegistry.class);
        registrar = new WorkflowClassRegistrar(beanFactory);
        registrar.setEnvironment(new MockEnvironment());
    }

    @Test
    void shouldRegisterWorkflowBeanDefinition() {
        // Given
        AutoConfigurationPackages.register(
                beanFactory,
                WorkflowClassRegistrarTest.class.getPackageName()
        );

        when(registry.containsBeanDefinition("workflow_my_workflow_workflow"))
                .thenReturn(false);

        // When
        registrar.postProcessBeanDefinitionRegistry(registry);

        // Then
        var captor = org.mockito.ArgumentCaptor.forClass(BeanDefinition.class);

        verify(registry).registerBeanDefinition(
                eq("workflow_my_workflow_workflow"),
                captor.capture()
        );

        var definition = captor.getValue();

        assertThat(definition)
                .isNotNull();

        assertThat(definition.getBeanClassName())
                .isEqualTo(MyWorkflow.class.getName());

        assertThat(definition)
                .isInstanceOf(AbstractBeanDefinition.class);

        var abstractDefinition = (AbstractBeanDefinition) definition;

        assertThat(abstractDefinition.getAutowireMode())
                .isEqualTo(
                        AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR
                );
    }

    @Test
    void shouldNotRegisterWorkflowWhenBeanDefinitionAlreadyExists() {
        // Given
        AutoConfigurationPackages.register(
                beanFactory,
                WorkflowClassRegistrarTest.class.getPackageName()
        );

        when(registry.containsBeanDefinition("workflow_my_workflow_workflow"))
                .thenReturn(true);

        // When
        registrar.postProcessBeanDefinitionRegistry(registry);

        // Then
        verify(registry, never()).registerBeanDefinition(
                eq("workflow_my_workflow_workflow"),
                any(BeanDefinition.class)
        );
    }

    @Test
    void shouldIgnoreAbstractWorkflow() {
        // Given
        AutoConfigurationPackages.register(
                beanFactory,
                WorkflowClassRegistrarTest.class.getPackageName()
        );

        // When
        registrar.postProcessBeanDefinitionRegistry(registry);

        // Then
        verify(registry, never()).registerBeanDefinition(
                eq("workflow_abstract_workflow"),
                any(BeanDefinition.class)
        );
    }

    @Test
    void shouldIgnoreWorkflowInterface() {
        // Given
        AutoConfigurationPackages.register(
                beanFactory,
                WorkflowClassRegistrarTest.class.getPackageName()
        );

        // When
        registrar.postProcessBeanDefinitionRegistry(registry);

        // Then
        verify(registry, never()).registerBeanDefinition(
                eq("workflow_interface_workflow"),
                any(BeanDefinition.class)
        );
    }

    @Test
    void shouldDoNothingWhenAutoConfigurationPackagesAreNotAvailable() {
        // Given
        // No AutoConfigurationPackages registered.

        // When
        registrar.postProcessBeanDefinitionRegistry(registry);

        // Then
        verifyNoInteractions(registry);
    }

    @Test
    void shouldNotThrowWhenPostProcessBeanFactoryIsCalled() {
        assertThatNoException()
                .isThrownBy(() ->
                        registrar.postProcessBeanFactory(beanFactory)
                );
    }

    @Workflow("my-workflow-workflow")
    static class MyWorkflow {
    }

    @Workflow("abstract-workflow")
    abstract static class AbstractWorkflow {
    }

    @Workflow("interface-workflow")
    interface InterfaceWorkflow {
    }
}
