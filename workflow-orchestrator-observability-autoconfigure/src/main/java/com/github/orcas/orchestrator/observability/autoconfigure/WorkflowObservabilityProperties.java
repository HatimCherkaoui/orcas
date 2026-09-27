package com.github.orcas.orchestrator.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Controls workflow lifecycle logging and MDC propagation. */
@ConfigurationProperties("workflow.orchestrator.observability")
public class WorkflowObservabilityProperties {
    private boolean enabled = true;
    private boolean mdc = true;
    private boolean events = true;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isMdc() { return mdc; }
    public void setMdc(boolean value) { mdc = value; }
    public boolean isEvents() { return events; }
    public void setEvents(boolean value) { events = value; }
}
