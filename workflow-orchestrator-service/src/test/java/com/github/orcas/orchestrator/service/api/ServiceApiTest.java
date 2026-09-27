package com.github.orcas.orchestrator.service.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceApiTest {
    @Test
    void normalizesQueryPagination() {
        var query = new WorkflowQueryService.WorkflowQuery(
                null, null, null, null, null, null, null,
                null, null, -2, 1000);

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(200);
    }

    @Test
    void exposesIndependentAdminResultContract() {
        var result = new WorkflowAdminService.BatchReplayResult(5, 4, 1);

        assertThat(result.matched()).isEqualTo(5);
        assertThat(result.replayed()).isEqualTo(4);
        assertThat(result.failed()).isEqualTo(1);
    }
}
