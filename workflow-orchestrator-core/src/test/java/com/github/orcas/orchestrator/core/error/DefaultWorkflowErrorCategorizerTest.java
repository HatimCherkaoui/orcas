package com.github.orcas.orchestrator.core.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWorkflowErrorCategorizerTest {
    private final DefaultWorkflowErrorCategorizer categorizer = new DefaultWorkflowErrorCategorizer();

    @Test
    void classifiesTransientIoFailuresAsReplayable() {
        assertThat(categorizer.classify(new SocketTimeoutException("timeout")).replayable()).isTrue();
        assertThat(categorizer.classify(new ConnectException("refused")).replayable()).isTrue();
        assertThat(categorizer.classify(new IOException("read failed")).replayable()).isTrue();
    }

    @Test
    void classifiesUnknownApplicationFailuresAsTerminal() {
        var error = categorizer.classify(new IllegalStateException("invalid state"));

        assertThat(error.disposition()).isEqualTo(ErrorDisposition.FAILED);
        assertThat(error.replayable()).isFalse();
    }

    @Test
    void classifiesTransientHttpResponsesAsReplayable() {
        assertThat(categorizer.classifyResponse(408).replayable()).isTrue();
        assertThat(categorizer.classifyResponse(429).replayable()).isTrue();
        assertThat(categorizer.classifyResponse(502).replayable()).isTrue();
        assertThat(categorizer.classifyResponse(503).replayable()).isTrue();
        assertThat(categorizer.classifyResponse(400).replayable()).isFalse();
        assertThat(categorizer.classifyResponse(500).disposition()).isEqualTo(ErrorDisposition.FAILED);
    }
}
