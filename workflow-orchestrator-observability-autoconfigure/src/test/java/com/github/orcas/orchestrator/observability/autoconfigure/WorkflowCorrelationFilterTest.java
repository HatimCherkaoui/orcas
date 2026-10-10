package com.github.orcas.orchestrator.observability.autoconfigure;

import com.github.orcas.orchestrator.core.model.*;
import com.github.orcas.orchestrator.core.engine.WorkflowTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowCorrelationFilterTest {
    @Test void returnsIdsAndRestoresContextEvenOnFailure() {
        var filter = new WorkflowCorrelationFilter(WorkflowTelemetry.noop());
        var request = new MockHttpServletRequest("POST","/orders");
        request.addHeader("X-Request-ID","req-42"); request.addHeader("Authorization","secret");
        var response = new MockHttpServletResponse();
        var previous = WorkflowContext.of("outer"); WorkflowContextHolder.set(previous);
        try {
            assertThatThrownBy(() -> filter.doFilter(request,response,(req,res) -> {
                assertThat(WorkflowContextHolder.current().metadata().identifier("requestId")).isEqualTo("req-42");
                assertThat(WorkflowContextHolder.current().metadata().asMap()).doesNotContainKey("Authorization");
                throw new IllegalStateException("failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(response.getHeader("x-request-id")).isEqualTo("req-42");
            assertThat(response.getHeader("x-transaction-id")).isNotBlank();
            assertThat(WorkflowContextHolder.current()).isSameAs(previous);
        } finally { WorkflowContextHolder.clear(); }
    }
}
