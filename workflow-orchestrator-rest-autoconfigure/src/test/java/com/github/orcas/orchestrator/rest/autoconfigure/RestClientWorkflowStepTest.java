package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.autoconfigure.core.WorkflowBeanResolver;
import com.github.orcas.orchestrator.core.api.ContextMapper;
import com.github.orcas.orchestrator.core.api.ResponseConsumer;
import com.github.orcas.orchestrator.core.error.DefaultWorkflowErrorCategorizer;
import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import com.github.orcas.orchestrator.core.model.WorkflowContextHolder;
import com.github.orcas.orchestrator.core.retry.WorkflowResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientWorkflowStepTest {
    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

    @AfterEach
    void clearContext() {
        WorkflowContextHolder.clear();
    }

    @Test
    void derivesStepNameAndRecordsResponseMetadata() throws Exception {
        var context = WorkflowContext.of(Map.of("id", "42"));
        var step = stepFor("reserve", new Client());
        var execution = execution(context);
        WorkflowContextHolder.set("wf-1", "orders", execution);

        var result = step.execute(context);

        assertThat(step.name()).isEqualTo("reserve");
        assertThat(result.status().name()).isEqualTo("SUCCESS");
        assertThat(execution.output()).isEqualTo("accepted");
        assertThat(context.metadata().get("http.response.status")).isEqualTo("201");
        assertThat(execution.attributes()).containsEntry("http.status", 201);
    }


    @Test
    void invokesDeclarativeClientThroughWorkflowStepInterceptor() throws Exception {
        var context = WorkflowContext.of(Map.of("id", "42"));
        var step = stepFor("reserve", new Client());
        step.setInvocationInterceptor((owner, method, invocation) -> {
            assertThat(owner).isEqualTo(Client.class);
            assertThat(method.getName()).isEqualTo("reserve");
            return invocation.proceed();
        });
        var execution = execution(context);
        WorkflowContextHolder.set("wf-1", "orders", execution);

        var result = step.execute(context);

        assertThat(result.status().name()).isEqualTo("SUCCESS");
    }

    @Test
    void classifiesErrorResponsesForTheWorkflowEngine() throws Exception {
        var context = WorkflowContext.of("42");
        var step = stepFor("fail", new Client());
        WorkflowContextHolder.set("wf-1", "orders", execution(context));

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(WorkflowResponseException.class)
                .hasMessageContaining("status 503");
    }

    private RestClientWorkflowStep stepFor(String methodName, Client client) throws Exception {
        Method method = Client.class.getMethod(methodName, Object.class);
        var properties = new WorkflowRestClientProperties();
        return new RestClientWorkflowStep(
                client, method, ContextMapper.Identity.class, ResponseConsumer.Void.class,
                new DefaultWorkflowErrorCategorizer(), new ObjectMapper(), properties,
                new WorkflowBeanResolver(beanFactory));
    }

    private static StepExecutionContext execution(WorkflowContext context) {
        return new StepExecutionContext(
                "wf-1", "orders", "step", context, null, context.businessInput());
    }

    static final class Client {
        public ResponseEntity<String> reserve(@RequestBody Object input) {
            return ResponseEntity.status(201).body("accepted");
        }

        public ResponseEntity<String> fail(@RequestBody Object input) {
            return ResponseEntity.status(503).body("unavailable");
        }
    }

}
