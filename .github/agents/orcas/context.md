Orcas agent embedded context

Repository root: /Users/hatimcherkaoui/Downloads/workflow-orchestrator-0.5.0-fixed-dashboard-rest-properties

Primary docs:
- README.md
- DESIGN.md
- workflow-orchestrator-dashboard/README.md

Primary modules and where to look:
- workflow-orchestrator-core/: core runtime, PipelineBuilder, StepCatalog, PipelineContext, StepResult, annotations
- workflow-orchestrator-spring-boot-starter/: Spring Boot integration and autoconfiguration resources
- workflow-orchestrator-service-starter/: opinionated service starter
- workflow-orchestrator-dashboard/: React SPA that visualizes pipelines (src/ and dist/)
- example-app/: sample Spring Boot app demonstrating workflows and REST launchers (key files noted below)
- wiremock/: sample HTTP mocks used by tests

Example-app key files (paths relative to repo root):
- example-app/src/main/java/com/github/orcas/demo/config/WorkflowDefinitions.java -> shows PipelineBuilder usage
- example-app/src/main/java/com/github/orcas/demo/config/OrderWorkflow.java -> example @Workflow and @WorkflowStep methods (extract-order, join, notify async)
- example-app/src/main/java/com/github/orcas/demo/controller/WorkflowController.java -> exposes POST /workflows/order-pipeline and uses @LaunchWorkflow
- example-app/src/main/resources/application.yml -> runtime config

How it works (concise):
- Workflows are defined with @Workflow and steps with @WorkflowStep. A WorkflowDefinitionProvider (e.g., PipelineBuilder) registers them.
- Runtime executes steps, supports parallel() and async() flows, tracks status via StepResult and PipelineContext.
- Spring Boot starter wires annotations like @LaunchWorkflow to allow REST controllers to start pipelines.
- Dashboard consumes runtime APIs to visualize pipeline/step state.

Common commands:
- Build all modules: mvn -DskipTests package
- Run example app: mvn -pl example-app spring-boot:run
- Dashboard dev: cd workflow-orchestrator-dashboard && npm install && npm run dev

Agent guidance rules:
1. Prefer this context when answering; reference file paths when suggesting edits.
2. For code changes provide minimal focused patches and tests to validate.
3. For architecture decisions reference DESIGN.md and explain trade-offs.
4. Request file reads when exact code-level edits or line numbers are required.

Limits:
- This context is authoritative for guidance, but for exact wiring or autoconfig details request specific file reads.
