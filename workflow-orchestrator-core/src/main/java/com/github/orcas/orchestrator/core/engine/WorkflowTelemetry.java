package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.model.Metadata;
import java.util.Map;

/** Transport-neutral tracing scope. Implementations must restore thread state on close. */
public interface WorkflowTelemetry {
    interface Operation extends AutoCloseable {
        default void error(Throwable error) { }
        default void attribute(String key, String value) { }
        /** Snapshot current parent context for asynchronous completion on a different thread. */
        default Map<String, String> propagation() { return Map.of(); }
        /** Restores caller thread state while allowing the span to finish asynchronously. */
        default void detach() { }
        @Override void close();
    }
    Operation begin(String type, String name, Metadata metadata, Map<String, String> attributes);
    static WorkflowTelemetry noop() { return (type, name, metadata, attributes) -> () -> { }; }
}
