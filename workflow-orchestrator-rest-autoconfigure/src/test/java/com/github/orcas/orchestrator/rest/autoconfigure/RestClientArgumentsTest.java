package com.github.orcas.orchestrator.rest.autoconfigure;

import com.github.orcas.orchestrator.core.model.StepExecutionContext;
import com.github.orcas.orchestrator.core.model.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientArgumentsTest {

    @Test
    void resolvesImplicitPathVariableNameFromJavaParameter() throws Exception {
        Method method = Client.class.getDeclaredMethod("refund", String.class);
        var execution = execution(Map.of("paymentId", "pay-context"));

        Object[] arguments = RestClientArguments.resolve(method, "pay-mapped", execution);

        assertThat(arguments).containsExactly("pay-mapped");
    }

    @Test
    void fallsBackToContextWhenMappedValueIsAbsent() throws Exception {
        Method method = Client.class.getDeclaredMethod("refund", String.class);
        var execution = execution(Map.of("paymentId", "pay-context"));

        Object[] arguments = RestClientArguments.resolve(method, null, execution);

        assertThat(arguments).containsExactly("pay-context");
    }

    @Test
    void resolvesNamedPathVariableFromMappedContext() throws Exception {
        Method method = NamedClient.class.getDeclaredMethod("refund", String.class);
        var execution = execution(Map.of());

        Object[] arguments = RestClientArguments.resolve(
                method, Map.of("paymentId", "pay-mapped"), execution);

        assertThat(arguments).containsExactly("pay-mapped");
    }

    @Test
    void mappedPathVariableWinsOverContextWhenBothContainTheSameKey() throws Exception {
        Method method = NamedClient.class.getDeclaredMethod("refund", String.class);
        var execution = execution(Map.of("paymentId", "pay-context"));

        Object[] arguments = RestClientArguments.resolve(
                method, Map.of("paymentId", "pay-mapped"), execution);

        assertThat(arguments).containsExactly("pay-mapped");
    }

    private static StepExecutionContext execution(Map<String, Object> input) {
        return new StepExecutionContext(
                "wf-1", "payment-refund", "refund-payment",
                WorkflowContext.of(input), null, input);
    }

    interface Client {
        @PostExchange("/payments/{paymentId}/refund")
        ResponseEntity<Void> refund(@PathVariable String paymentId);
    }

    interface NamedClient {
        @PostExchange("/payments/{paymentId}/refund")
        ResponseEntity<Void> refund(@PathVariable("paymentId") String value);
    }
}
