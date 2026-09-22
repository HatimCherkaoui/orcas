package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.annotation.Workflow;
import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent DSL for building a {@link WorkflowDefinition}'s routing table.
 *
 * <p>Typical usage (see {@code WorkflowDefinitionProvider} implementations):
 * <pre>{@code
 * new PipelineBuilder(OrderWorkflow.class, stepCatalog)
 *     .initialize().on(StatusCriteria.init()).then("validateOrder")
 *     .sequential().when(StatusCriteria.success("validateOrder")).then("reserveStock")
 *     .parallel().when(StatusCriteria.success("reserveStock"))
 *         .and("chargePayment").and("notifyWarehouse")
 *         .then("finalizeOrder").end()
 *     .build();
 * }</pre>
 *
 * <p>The target class must carry the {@link Workflow @Workflow} annotation, whose
 * value becomes {@link WorkflowDefinition#name()}.
 */
public final class PipelineBuilder {
    private final String name;
    private final List<WorkflowDefinition.Route> routes = new ArrayList<>();
    private final StepCatalog stepCatalog;

    public PipelineBuilder(Class<?> clazz, StepCatalog stepCatalog) {
        var annotation = clazz.getAnnotation(Workflow.class);
        if (annotation == null) throw new IllegalArgumentException("@Workflow is required on " + clazz.getName());
        name = annotation.value();
        this.stepCatalog = stepCatalog;
    }

    public Initialize initialize() {
        return new Initialize();
    }

    public Async async() {
        return new Async();
    }

    public Sequential sequential() {
        return new Sequential();
    }

    public Parallel parallel() {
        return new Parallel();
    }

    public WorkflowDefinition build() {
        return new WorkflowDefinition(name, routes);
    }

    public final class Sequential {
        private StatusCriteria c;

        public Sequential when(StatusCriteria x) {
            c = x;
            return this;
        }

        public PipelineBuilder then(String name) {
            return add(c, stepCatalog.get(name));
        }

        public PipelineBuilder then(Class<? extends WorkflowStep> clazz) {
            return add(c, stepFromClass(clazz));
        }
    }

    public final class Parallel {
        private StatusCriteria c;
        private final List<WorkflowStep> branches = new ArrayList<>();
        private WorkflowStep join;

        public Parallel when(StatusCriteria x) {
            c = x;
            return this;
        }

        public Parallel and(String name) {
            branches.add(stepCatalog.get(name));
            return this;
        }

        public Parallel and(Class<? extends WorkflowStep> clazz) {
            branches.add(stepFromClass(clazz));
            return this;
        }

        public Parallel andAsync(Class<? extends WorkflowStep> clazz) {
            branches.add(stepFromClass(clazz));
            return this;
        }

        public Parallel then(String name) {
            join = stepCatalog.get(name);
            return this;
        }

        public Parallel then(Class<? extends WorkflowStep> clazz) {
            join = stepFromClass(clazz);
            return this;
        }

        public PipelineBuilder end() {
            if (branches.isEmpty() || join == null)
                throw new IllegalStateException("parallel requires branches and join");
            routes.add(new WorkflowDefinition.Route(c, branches, join, UUID.randomUUID().toString()));
            return PipelineBuilder.this;
        }
    }

    public final class Initialize {
        private StatusCriteria c;

        public Initialize on(StatusCriteria x) {
            c = x;
            return this;
        }

        public PipelineBuilder then(WorkflowStep s) {
            return add(c, s);
        }

        public PipelineBuilder then(String name) {
            return add(c, stepCatalog.get(name));
        }

        public PipelineBuilder then(Class<? extends WorkflowStep> clazz) {
            return add(c, stepFromClass(clazz));
        }
    }

    public final class Async {
        private StatusCriteria criteria;
        private final List<WorkflowStep> asyncEvents = new ArrayList<>();

        public Async when(StatusCriteria c) {
            criteria = c;
            return this;
        }

        public Async then(String name) {
            asyncEvents.add(stepCatalog.get(name));
            return this;
        }

        public Async then(Class<? extends AsyncStep> clazz) {
            asyncEvents.add(stepFromClass(clazz));
            return this;
        }

        public PipelineBuilder end() {
            routes.add(new WorkflowDefinition.Route(criteria, asyncEvents, null, UUID.randomUUID().toString()));
            return PipelineBuilder.this;
        }
    }

    private PipelineBuilder add(StatusCriteria criteria, WorkflowStep step) {
        routes.add(new WorkflowDefinition.Route(criteria, List.of(step), null, null));
        return this;
    }

    private WorkflowStep stepFromClass(Class<? extends WorkflowStep> clazz) {
        var annotation = clazz.getAnnotation(com.github.orcas.orchestrator.core.annotation.WorkflowStep.class);
        if (annotation == null) throw new IllegalArgumentException("@WorkflowStep is required on " + clazz.getName());
        return stepCatalog.get(annotation.value());
    }
}
