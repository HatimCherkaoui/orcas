package com.github.orcas.orchestrator.autoconfigure.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Executor settings for async and parallel workflow steps. */
@ConfigurationProperties("workflow.orchestrator.async")
public class WorkflowAsyncProperties {
    private boolean virtualThreads = true;
    private int concurrency = 8;

    public boolean isVirtualThreads() { return virtualThreads; }
    public void setVirtualThreads(boolean value) { virtualThreads = value; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int value) { concurrency = value; }
}
