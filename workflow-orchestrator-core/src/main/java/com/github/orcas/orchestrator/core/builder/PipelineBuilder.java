package com.github.orcas.orchestrator.core.builder;

import com.github.orcas.orchestrator.core.api.AsyncStep;
import com.github.orcas.orchestrator.core.api.StepNames;
import com.github.orcas.orchestrator.core.api.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Small fluent DSL for sequential, asynchronous and parallel workflow routing.
 */
public final class PipelineBuilder {
    private final String workflowName;
    private final StepCatalog stepCatalog;
    private final List<WorkflowDefinition.Route> routes = new ArrayList<>();
    private StatusCriteria currentCriteria;

    public PipelineBuilder(Class<?> workflowType, StepCatalog stepCatalog) {
        this(StepNames.workflow(workflowType), stepCatalog);
    }

    public PipelineBuilder(String workflowName, StepCatalog stepCatalog) {
        this.workflowName = Objects.requireNonNull(workflowName, "workflowName");
        this.stepCatalog = Objects.requireNonNull(stepCatalog, "stepCatalog");
    }

    public static PipelineBuilder forWorkflow(Class<?> workflowType, StepCatalog stepCatalog) {
        return new PipelineBuilder(workflowType, stepCatalog);
    }

    public Initialize initialize() {
        return new Initialize();
    }

    public Sequential sequential() {
        return new Sequential();
    }

    public Parallel parallel() {
        return new Parallel();
    }

    public Async async() {
        return new Async();
    }

    public WorkflowDefinition build() {
        return new WorkflowDefinition(workflowName, routes);
    }

    /**
     * Configures the route triggered by the workflow INIT event.
     */
    public final class Initialize {
        public Initialize on(StatusCriteria criteria) {
            currentCriteria = Objects.requireNonNull(criteria, "criteria");
            return this;
        }

        public PipelineBuilder then(Class<? extends WorkflowStep> stepType) {
            return add(currentCriteria, stepCatalog.get(stepType));
        }

        public PipelineBuilder then(String stepName) {
            return add(currentCriteria, stepCatalog.get(stepName));
        }

        public PipelineBuilder then(WorkflowStep step) {
            return add(currentCriteria, step);
        }
    }

    /**
     * Configures a route that runs its step after a status criterion matches.
     */
    public final class Sequential {
        public Sequential when(StatusCriteria criteria) {
            currentCriteria = Objects.requireNonNull(criteria, "criteria");
            return this;
        }

        public PipelineBuilder then(Class<? extends WorkflowStep> stepType) {
            return add(currentCriteria, stepCatalog.get(stepType));
        }

        public PipelineBuilder then(String stepName) {
            return add(currentCriteria, stepCatalog.get(stepName));
        }

        public PipelineBuilder then(WorkflowStep step) {
            return add(currentCriteria, step);
        }
    }

    /**
     * Configures parallel branch steps followed by one join step.
     */
    public final class Parallel {
        private StatusCriteria criteria;
        private final List<WorkflowStep> branches = new ArrayList<>();

        public Parallel when(StatusCriteria criteria) {
            this.criteria = Objects.requireNonNull(criteria, "criteria");
            return this;
        }

        public Parallel and(Class<? extends WorkflowStep> stepType) {
            branches.add(stepCatalog.get(stepType));
            return this;
        }

        public Parallel and(String stepName) {
            branches.add(stepCatalog.get(stepName));
            return this;
        }

        public Parallel and(WorkflowStep step) {
            branches.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        public Parallel andAsync(Class<? extends AsyncStep> stepType) {
            branches.add(stepCatalog.get(stepType));
            return this;
        }

        public PipelineBuilder then(Class<? extends WorkflowStep> stepType) {
            return addJoin(stepCatalog.get(stepType));
        }

        public PipelineBuilder then(String stepName) {
            return addJoin(stepCatalog.get(stepName));
        }

        public PipelineBuilder then(WorkflowStep joinStep) {
            return addJoin(Objects.requireNonNull(joinStep, "joinStep"));
        }

        private PipelineBuilder addJoin(WorkflowStep joinStep) {
            if (branches.isEmpty()) throw new IllegalStateException("Parallel route requires at least one branch");
            routes.add(new WorkflowDefinition.Route(criteria, branches, joinStep,
                    UUID.randomUUID().toString()));
            return PipelineBuilder.this;
        }
    }

    /**
     * Configures steps that are submitted to the workflow asynchronous executor.
     */
    public final class Async {
        private StatusCriteria criteria;
        private final List<WorkflowStep> events = new ArrayList<>();

        public Async when(StatusCriteria criteria) {
            this.criteria = Objects.requireNonNull(criteria, "criteria");
            return this;
        }

        public Async then(Class<? extends AsyncStep> stepType) {
            events.add(stepCatalog.get(stepType));
            return this;
        }

        public Async then(String stepName) {
            events.add(stepCatalog.get(stepName));
            return this;
        }

        public Async then(WorkflowStep step) {
            events.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        public PipelineBuilder end() {
            if (events.isEmpty()) throw new IllegalStateException("Async route requires at least one step");
            routes.add(new WorkflowDefinition.Route(criteria, events, null, null));
            return PipelineBuilder.this;
        }
    }

    private PipelineBuilder add(StatusCriteria criteria, WorkflowStep step) {
        routes.add(new WorkflowDefinition.Route(Objects.requireNonNull(criteria, "criteria"),
                List.of(step), null, null));
        return this;
    }
}
