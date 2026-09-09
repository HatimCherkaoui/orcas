package com.github.orcas.orchestrator.core.engine;

import com.github.orcas.orchestrator.core.builder.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory catalog of the {@link WorkflowDefinition}s known to the application,
 * keyed by their unique {@link WorkflowDefinition#name() name}. Definitions are
 * typically registered once at startup (see {@code WorkflowDefinitionProvider} in the
 * {@code builder} package and the Spring Boot auto-configuration that wires it) and
 * looked up by the {@link WorkflowEngine} for every incoming event.
 */
public final class WorkflowRegistry {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRegistry.class);

    private final Map<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * Registers a workflow definition.
     *
     * @param d the definition to register
     * @throws IllegalStateException if a definition with the same name is already registered
     */
    public void register(WorkflowDefinition d) {
        if (definitions.putIfAbsent(d.name(), d) != null) {
            log.error("Duplicate workflow registration attempted for '{}'", d.name());
            throw new IllegalStateException("Duplicate workflow: " + d.name());
        }
        log.info("Registered workflow '{}' with {} route(s)", d.name(), d.routes().size());
    }

    /**
     * Looks up a registered definition by name.
     *
     * @param n workflow name
     * @return the matching definition, or {@code null} if none is registered
     */
    public WorkflowDefinition get(String n) {
        return definitions.get(n);
    }

    /** Returns an immutable snapshot of all registered definitions. */
    public Collection<WorkflowDefinition> all() {
        return List.copyOf(definitions.values());
    }
}
