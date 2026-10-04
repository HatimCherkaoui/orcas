# Phase 3 Integration Testing - Implementation Checklist

## Files Analyzed & Line Counts Verified

### Core Phase 3 Test Targets

#### 1. JDBC Workflow State Store (388 lines)
✅ **File Located**: `workflow-orchestrator-jdbc-autoconfigure/src/main/java/com/github/orcas/orchestrator/jdbc/autoconfigure/JdbcWorkflowStateStore.java`

**Methods Requiring Tests**:
- [ ] `start(String id, String workflow, WorkflowContext context)` - Lines 61-117
  - [ ] Inserts workflow row with STARTED status
  - [ ] Inserts workflow_context row with JSON serialization
  - [ ] Inserts workflow_metadata row
  - [ ] Records audit log entries
  - [ ] Handles duplicate workflow ID (ON CONFLICT DO NOTHING)

- [ ] `record(StatusEvent event, String stepTypeClassName)` - Lines 125-195
  - [ ] Inserts workflow_step row
  - [ ] Updates workflow_step on conflict (merges step type class name)
  - [ ] Sets date_ended for terminal states (SUCCESS, FAILED, SKIPPED)
  - [ ] Updates workflow status
  - [ ] Updates workflow_metadata if event.metadata() present
  - [ ] Records audit log for step and workflow
  - [ ] Skips step row insertion if event.step() == INIT

- [ ] `finish(StatusEvent event)` - Lines 198-212
  - [ ] Updates workflow status to SUCCESS or FAILED
  - [ ] Records audit log with FINISH action

- [ ] `recordRetry(String workflowId, String stepName, int attempt, String reason)` - Lines 219-240
  - [ ] Increments workflow_step.retry_count
  - [ ] Updates date_updated
  - [ ] Records audit log with RETRY action and reason

- [ ] `workflowName(String id)` - Lines 244-247
  - [ ] Queries workflow table by pipeline_id
  - [ ] Returns workflow name

- [ ] `context(String id)` - Lines 250-265
  - [ ] Joins workflow_context and workflow_metadata
  - [ ] Deserializes context_json and metadata_json
  - [ ] Returns WorkflowContext object
  - [ ] Handles JSON deserialization errors

- [ ] `stepContext(String workflowId, String stepName)` - Lines 268-285
  - [ ] Queries workflow_step_context
  - [ ] Deserializes input_json, output_json, attributes_json
  - [ ] Returns Optional<StepContext>
  - [ ] Maps timestamp to Instant

- [ ] `suspendedWorkflowIds(String stepName)` - Lines 288-296
  - [ ] Queries workflow_step with state='SUSPENDED'
  - [ ] Orders by date_updated and pipeline_id
  - [ ] Returns List<String> of workflow IDs

- [ ] `saveStepContext(StepContext context)` - Lines 298-346
  - [ ] Inserts workflow_step row with state='RUNNING' if not exists
  - [ ] Upserts workflow_step_context
  - [ ] Records step context snapshot to audit log
  - [ ] Handles composite FK (pipeline_id, step_name)

- [ ] `updateContext(String id, WorkflowContext context)` - Lines 349-385
  - [ ] Updates workflow_context with new context_json
  - [ ] Updates workflow_metadata with new metadata_json
  - [ ] Updates workflow.date_updated
  - [ ] Records audit logs for context, metadata, and workflow

**Transaction Management**:
- [ ] Verify JdbcTransactionRunner retries on deadlock/serialization failure
- [ ] Test concurrent updates to same workflow (simulate parallel steps)
- [ ] Verify audit trail completeness for all operations

**Test Implementation Approach**:
- Use `@Testcontainers` with PostgreSQL 18-alpine
- Auto-execute `orchestrator-schema.sql` before tests
- Mock ObjectMapper for controlled serialization
- Mock JdbcJsonCodec, JdbcAuditLog for isolation
- Test with real PlatformTransactionManager
- Estimated lines: 400-500

---

#### 2. Kafka Event Consumer (38 lines)
✅ **File Located**: `workflow-orchestrator-kafka-autoconfigure/src/main/java/com/github/orcas/orchestrator/kafka/autoconfigure/WorkflowEventConsumer.java`

**Methods Requiring Tests**:
- [ ] `onMessage(String message)` - Lines 32-36
  - [ ] Receives string message from Kafka topic
  - [ ] Deserializes to StatusEvent via ObjectMapper
  - [ ] Invokes WorkflowEngine.handle()
  - [ ] Handles JSON parsing exceptions
  - [ ] Handles engine exceptions without throwing (consumer backpressure)

**Configuration Requirements**:
- [ ] Verify @KafkaListener annotation properties
  - [ ] topics = "${workflow.orchestrator.kafka.topic}"
  - [ ] groupId = "${workflow.orchestrator.kafka.consumer-group}"
  - [ ] containerFactory = "workflowKafkaListenerContainerFactory"

**Test Implementation Approach**:
- Use `@Testcontainers` with KafkaContainer
- Embed Spring application context with auto-configuration
- Use KafkaTemplate to send test messages
- Verify WorkflowEngine.handle() invoked via Mockito or spy
- Test error scenarios (malformed JSON, engine exceptions)
- Estimated lines: 250-350

---

#### 3. Retry Scheduler (157 lines)
✅ **File Located**: `workflow-orchestrator-resilience-autoconfigure/src/main/java/com/github/orcas/orchestrator/resilience/autoconfigure/WorkflowRetryScheduler.java`

**Methods Requiring Tests**:
- [ ] `schedule(String workflowId, String stepName, Duration delay)` - Lines 49-60
  - [ ] Schedules replay task on executor
  - [ ] Records retry attempt via stateStore
  - [ ] Invokes engine.replay() after delay
  - [ ] Catches and logs RuntimeException

- [ ] `scheduleHalfOpenReplay(String breakerName, String stepName, Duration delay, int permittedCalls)` - Lines 65-83
  - [ ] Deduplicates by breakerName:stepName key
  - [ ] Creates ScheduledHalfOpenReplay record
  - [ ] Schedules batch on executor
  - [ ] Only creates one scheduled future per key

- [ ] `scheduledHalfOpenReplay(String breakerName, String stepName)` - Lines 85-87
  - [ ] Returns Optional of pending replay
  - [ ] Returns empty if not scheduled

- [ ] `runHalfOpenReplay(String breakerName, String stepName, Duration delay, int batchSize)` - Lines 88-111
  - [ ] Queries suspended workflow IDs from stateStore
  - [ ] Selects first batchSize workflows
  - [ ] Invokes replayWorkflowIds()
  - [ ] Checks breaker state after probes
  - [ ] Reschedules if still OPEN

- [ ] `replayRemaining(String breakerName, String stepName, Set<String> alreadyReplayed, CircuitBreaker breaker, Duration delay, int batchSize)` - Lines 112-128
  - [ ] Filters out already replayed workflows
  - [ ] Logs remaining count
  - [ ] Replays all remaining if breaker CLOSED
  - [ ] Reschedules if breaker reopens during replay

- [ ] `replayWorkflowIds(String stepName, Set<String> workflowIds, CircuitBreaker breaker, String reason)` - Lines 129-141
  - [ ] Iterates workflow IDs
  - [ ] Records retry attempt for each
  - [ ] Invokes engine.replay()
  - [ ] Short-circuits if breaker OPEN

- [ ] `recordRetryAttempt(String workflowId, String stepName, String reason)` - Lines 142-151
  - [ ] Handles null stateStore gracefully
  - [ ] Calls stateStore.recordRetry()
  - [ ] Logs warnings on failure

- [ ] `destroy()` - Lines 152-155
  - [ ] Calls executor.shutdownNow()
  - [ ] Cleans up pending scheduled tasks

**Circuit Breaker Integration**:
- [ ] Test state transitions: CLOSED → OPEN → HALF_OPEN → CLOSED
- [ ] Verify half-open probe batching
- [ ] Verify cascading replay when breaker closes
- [ ] Test with null stateStore/registry (graceful degradation)

**Test Implementation Approach**:
- Use real ScheduledExecutorService (not mocked)
- Mock WorkflowEngine and WorkflowRetryStateStore
- Mock CircuitBreakerRegistry with Mockito
- Use CountDownLatch to synchronize async scheduling
- Verify executor cleanup via @AfterEach
- Estimated lines: 350-450

---

#### 4. Workflow Service Controller (129 lines)
✅ **File Located**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/WorkflowServiceController.java`

**Endpoints Requiring Tests** (18 total):

**READ Endpoints**:
- [ ] `search(...)` - Lines 41-55 (GET `/workflows`)
  - [ ] Filters by workflowId, workflow, status, stepName, stepStatus, metadata (key/value), date range
  - [ ] Pagination: page, size, returns PageResult
  - [ ] Test all filter combinations and their combinations
  - [ ] Test empty results
  - [ ] Test SQL injection prevention (parameter binding)

- [ ] `find(String id)` - Line 58 (GET `/workflows/{id}`)
  - [ ] Returns WorkflowDetails (pipeline_id, workflow, status, date_created, date_updated)
  - [ ] Returns 200 OK
  - [ ] Returns ResponseEntity.empty() if not found

- [ ] `steps(String id)` - Line 60 (GET `/workflows/{id}/steps`)
  - [ ] Returns List<WorkflowStepView>
  - [ ] Includes all step fields (name, type_class_name, state, retry_count, dates)

- [ ] `step(String id, String stepName)` - Lines 62-65 (GET `/workflows/{id}/steps/{stepName}`)
  - [ ] Returns 200 OK with WorkflowStepView
  - [ ] Returns 404 NOT FOUND if step not found (handles EmptyResultDataAccessException)

- [ ] `stepContext(...)` - Lines 67-70 (GET `/workflows/{id}/steps/{stepName}/context`)
  - [ ] Returns StepContext with input/output/attributes
  - [ ] Returns 200 OK
  - [ ] Returns 404 if not found

- [ ] `context(String id)` - Line 72 (GET `/workflows/{id}/context`)
  - [ ] Returns ContextView (deserialized businessInput)
  - [ ] Returns 200 OK
  - [ ] Returns ResponseEntity.empty() if not found

- [ ] `metadata(String id)` - Line 74 (GET `/workflows/{id}/metadata`)
  - [ ] Returns MetadataView
  - [ ] Returns 200 OK
  - [ ] Returns ResponseEntity.empty() if not found

**LOG Endpoints** (4 endpoints):
- [ ] `logs(String id)` - Line 76 (GET `/workflows/{id}/logs`)
  - [ ] Returns List<EntityLogView> ordered by date_created

- [ ] `stepLogs(...)` - Line 78 (GET `/workflows/{id}/steps/{stepName}/logs`)
  - [ ] Returns step audit log entries

- [ ] `contextLogs(String id)` - Line 80 (GET `/workflows/{id}/context/logs`)
  - [ ] Returns context mutation audit log

- [ ] `metadataLogs(String id)` - Line 82 (GET `/workflows/{id}/metadata/logs`)
  - [ ] Returns metadata mutation audit log

**WRITE Endpoints**:
- [ ] `updateWorkflow(String id, Map<String, String> body)` - Lines 95-98 (PATCH `/workflows/{id}`)
  - [ ] Extracts "status" from body
  - [ ] Calls admin.updateWorkflowStatus()
  - [ ] Returns 204 NO CONTENT

- [ ] `updateStep(...)` - Lines 100-104 (PATCH `/workflows/{id}/steps/{stepName}`)
  - [ ] Extracts "state" from body
  - [ ] Calls admin.updateStepState()
  - [ ] Returns 204 NO CONTENT

- [ ] `context(String id, Object body)` - Lines 106-109 (PUT `/workflows/{id}/context`)
  - [ ] Receives arbitrary JSON object
  - [ ] Calls admin.replaceContext()
  - [ ] Returns 204 NO CONTENT

- [ ] `metadata(String id, Map<String, String> body)` - Lines 111-114 (PUT `/workflows/{id}/metadata`)
  - [ ] Receives metadata map
  - [ ] Calls admin.replaceMetadata()
  - [ ] Returns 204 NO CONTENT

**REPLAY Endpoints**:
- [ ] `replay(String id, String stepName)` - Lines 116-119 (POST `/workflows/{id}/steps/{stepName}/replay`)
  - [ ] Calls admin.replayStep()
  - [ ] Returns 202 ACCEPTED

- [ ] `replaySuspended(WorkflowQuery query)` - Lines 121-123 (POST `/workflows/replay`)
  - [ ] Accepts WorkflowQuery body
  - [ ] Calls admin.replaySuspendedSteps()
  - [ ] Returns BatchReplayResult (replayed, failed, total)

**Definition Endpoint** (minimal):
- [ ] `definition(String workflow)` - Lines 91-93 (GET `/workflows/definitions/{workflow}`)
  - [ ] Returns empty WorkflowGraph (no workflow definitions in management service)
  - [ ] Returns 200 OK

**Test Implementation Approach**:
- Use `@SpringBootTest` with `@DynamicPropertySource`
- Use Testcontainers for PostgreSQL + Kafka
- Use MockMvc or REST Assured for HTTP assertions
- Pre-populate database with fixture workflows/steps
- Test error responses (404, 400, etc.)
- Estimated lines: 500-700

---

#### 5. JDBC Query Service (222 lines)
✅ **File Located**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowQueryService.java`

**Current Test Coverage**: Only SQL WHERE clause building tested (WorkflowQuerySqlTest.java - 62 lines)
**Gap**: No JDBC execution, result mapping, or integration tests

**Methods Requiring Tests**:
- [ ] `search(WorkflowQuery query)` - Lines 53-79
  - [ ] Constructs COUNT(*) query for pagination
  - [ ] Constructs main SELECT with LEFT JOIN for current_step
  - [ ] Applies WHERE filters via WorkflowQuerySql
  - [ ] Orders by date_created DESC
  - [ ] Applies LIMIT/OFFSET for pagination
  - [ ] Returns PageResult with total, page, size, pages
  - [ ] Test all filter combinations (workflow, status, step, metadata, date range)

- [ ] `find(String id)` - Lines 82-91
  - [ ] Queries workflow table by pipeline_id
  - [ ] Maps to WorkflowDetails (using JdbcWorkflowViewMapper.details())
  - [ ] Returns Optional<WorkflowDetails>
  - [ ] Returns empty if not found

- [ ] `steps(String id)` - Lines 94-100
  - [ ] Queries workflow_step table filtered by pipeline_id
  - [ ] Orders by date_started (NULLS LAST), then step_name
  - [ ] Maps to List<WorkflowStepView>
  - [ ] Includes all fields: name, type_class_name, state, retry_count, dates

- [ ] `step(String id, String stepName)` - Lines 103-104
  - [ ] Queries single step by pipeline_id and step_name
  - [ ] Returns WorkflowStepView

- [ ] `stepContext(String workflowId, String stepName)` - Lines 107-108
  - [ ] Queries workflow_step_context
  - [ ] Deserializes JSON payloads
  - [ ] Returns Optional<StepContext>

- [ ] `context(String workflowId)` - Lines 111-112
  - [ ] Queries workflow_context and workflow_metadata
  - [ ] Deserializes context_json and metadata_json
  - [ ] Returns Optional<ContextView>

- [ ] `metadata(String workflowId)` - Lines 115-116
  - [ ] Queries workflow_metadata
  - [ ] Returns Optional<MetadataView>

- [ ] `workflowLogs(String workflowId)` - Lines 120-123
  - [ ] Queries workflow_log ordered by date_created
  - [ ] Returns List<EntityLogView>

- [ ] `stepLogs(String workflowId, String stepName)` - Lines 126-127
  - [ ] Queries workflow_step_log
  - [ ] Filters by pipeline_id and step_name
  - [ ] Returns List<EntityLogView>

- [ ] `contextLogs(String workflowId)` - Lines 130-131
  - [ ] Queries workflow_context_log by pipeline_id
  - [ ] Returns List<EntityLogView>

- [ ] `metadataLogs(String workflowId)` - Lines 134-135
  - [ ] Queries workflow_metadata_log by pipeline_id
  - [ ] Returns List<EntityLogView>

**Result Mapping**:
- [ ] Verify JdbcWorkflowViewMapper.summary() correctly maps workflow rows
- [ ] Verify JdbcWorkflowViewMapper.details() correctly maps workflow details
- [ ] Verify JdbcWorkflowViewMapper.step() correctly maps step rows
- [ ] Verify JSON deserialization for context/metadata payloads
- [ ] Verify timestamp/Instant mapping

**Test Implementation Approach**:
- Use `@Testcontainers` with PostgreSQL 18
- Create fixture workflows/steps/contexts via direct JDBC or helper methods
- Verify query results match fixtures
- Test empty/null results
- Test pagination edge cases (first page, last page, beyond max)
- Estimated lines: 350-450

---

#### 6. JDBC Admin Service (157 lines)
✅ **File Located**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowAdminService.java`

**Current Test Coverage**: 0% - No integration tests

**Methods Requiring Tests**:
- [ ] `updateWorkflowStatus(String workflowId, String status)` - Lines 44-54
  - [ ] Updates workflow.status by pipeline_id
  - [ ] Updates workflow.date_updated
  - [ ] Records audit log entry with action='ADMIN_UPDATE'
  - [ ] Throws EmptyResultDataAccessException if not found

- [ ] `updateStepState(String workflowId, String stepName, String state)` - Lines 57-70
  - [ ] Updates workflow_step.state
  - [ ] Sets date_ended if terminal state (SUCCESS, FAILED, SKIPPED)
  - [ ] Leaves date_ended NULL for non-terminal states
  - [ ] Updates date_updated
  - [ ] Records audit log for step with action='ADMIN_UPDATE'
  - [ ] Throws EmptyResultDataAccessException if not found

- [ ] `replaceContext(String workflowId, Object context)` - Lines 73-85
  - [ ] Serializes context object to JSON
  - [ ] Updates workflow_context.context_json and context_class_name
  - [ ] Handles null context (sets class name to null)
  - [ ] Records audit log with action='ADMIN_UPDATE'
  - [ ] Throws EmptyResultDataAccessException if not found

- [ ] `replaceMetadata(String workflowId, Map<String, String> metadata)` - Lines 88-99
  - [ ] Serializes metadata map to JSON
  - [ ] Updates workflow_metadata.metadata_json
  - [ ] Records audit log
  - [ ] Throws EmptyResultDataAccessException if not found

- [ ] `replayStep(String workflowId, String stepName)` - Lines 102-104
  - [ ] Delegates to replayPublisher.publish()

- [ ] `replaySuspendedSteps(WorkflowQuery query)` - Lines 107-128
  - [ ] Queries suspended steps matching WorkflowQuery filters
  - [ ] Publishes replay events via replayPublisher
  - [ ] Returns BatchReplayResult (replayed, failed, suspended)

**Audit Trail Verification**:
- [ ] Verify each mutation records exactly one audit log entry
- [ ] Verify action field is set to 'ADMIN_UPDATE'
- [ ] Verify snapshot_json contains mutation data
- [ ] Verify date_created is set correctly

**Error Handling**:
- [ ] Test EmptyResultDataAccessException thrown for non-existent workflows
- [ ] Test JSON serialization errors (invalid context objects)

**Test Implementation Approach**:
- Use `@Testcontainers` with PostgreSQL 18
- Create fixture workflows and steps
- Verify mutations in database via direct JDBC queries
- Verify audit log entries
- Test edge cases (null values, terminal state transitions)
- Estimated lines: 300-400

---

## Pre-Implementation Checklist

### Repository Setup
- [ ] Clone/update workspace: `/Users/hatimcherkaoui/Downloads/workflow-orchestrator-0.5.0-fixed-dashboard-rest-properties`
- [ ] Verify Java version: `java -version` → Must be 25+
- [ ] Verify Maven version: `mvn -v` → Must be 3.9.6+
- [ ] Verify Docker installed for Testcontainers

### Dependency Updates
- [ ] Add Testcontainers dependencies to `workflow-orchestrator-jdbc-autoconfigure/pom.xml`
- [ ] Add Testcontainers dependencies to `workflow-orchestrator-kafka-autoconfigure/pom.xml`
- [ ] Add Testcontainers dependencies to `workflow-orchestrator-service-autoconfigure/pom.xml`
- [ ] Run `mvn clean install` to verify no conflicts

### Base Test Infrastructure
- [ ] Create base test fixture classes if needed
  - [ ] `WorkflowFixture` - factory for test workflows
  - [ ] `StatusEventFixture` - factory for test events
  - [ ] `ContainerFixture` - shared Testcontainers setup
- [ ] Create test utility methods (wait strategies, assertions)

### Documentation Review
- [ ] Read DESIGN.md for architectural constraints
- [ ] Review existing OrderWorkflowIntegrationTest.java (486 lines) for patterns
- [ ] Review core unit tests for InMemoryWorkflowStateStore usage

---

## Testing Strategy by Component

### JDBC State Store Testing Strategy
```
Test Levels:
1. Unit (No DB): Test JdbcTransactionRunner retry logic
2. Integration (PostgreSQL):
   - Test all CRUD methods with real JDBC
   - Test transaction retries with concurrent updates
   - Test audit trail completeness
   - Test JSON serialization/deserialization round-trips
   - Test NULL handling and edge cases
   - Test composite key handling (pipeline_id, step_name)
```

### Kafka Consumer Testing Strategy
```
Test Levels:
1. Unit (No Kafka): Mock ObjectMapper deserialization
2. Integration (Kafka):
   - Test message consumption from topic
   - Test status event deserialization
   - Test engine.handle() invocation
   - Test error scenarios (malformed JSON, engine exceptions)
   - Verify consumer group configuration
```

### Retry Scheduler Testing Strategy
```
Test Levels:
1. Unit (No DB/KB/CB): Test scheduling logic with mocks
2. Integration (Partial):
   - Test ScheduledExecutorService delays
   - Test circuit breaker state machine with Mockito
   - Test deduplication of half-open replays
   - Test cascading replay logic
   - Test executor cleanup on destroy()
```

### Service Controller Testing Strategy
```
Test Levels:
1. Unit: Mock query/admin services (already done for definition())
2. Integration (PostgreSQL + Kafka + Web):
   - Test all REST endpoints with HTTP assertions
   - Pre-populate database with fixture data
   - Test request/response serialization
   - Test error handling (404, 400, etc.)
   - Test all query filters and pagination
   - Test mutations and audit logging
```

### Query & Admin Service Testing Strategy
```
Test Levels:
1. Unit: Test SQL building logic (already done)
2. Integration (PostgreSQL):
   - Test JDBC execution with real queries
   - Test result mapping with all field types
   - Test JSON deserialization
   - Test mutation side effects (audit logs)
   - Test error scenarios (not found, serialization errors)
```

---

## Verification Checklist (Post-Implementation)

- [ ] All 6 test classes compile without errors
- [ ] All tests pass: `mvn verify`
- [ ] Test code follows existing patterns (assertions, naming, structure)
- [ ] Test coverage for target files > 80%
- [ ] Testcontainers properly cleaned up after tests
- [ ] No hardcoded ports or IPs in tests (use exposed ports)
- [ ] All 4 audit log tables verified in state store tests
- [ ] Concurrent/async scenarios tested where applicable
- [ ] Error paths tested (not found, invalid input, serialization errors)
- [ ] Documentation updated with test patterns used
- [ ] CI/CD pipeline passes all tests

---

## Files Created by This Analysis

1. **PHASE3_TEST_INFRASTRUCTURE_ANALYSIS.md** - Comprehensive 10-section analysis (this repo root)
   - Test files inventory (49 files)
   - Target files analysis (388/38/157/129/222/157 lines)
   - Test patterns with code examples
   - Configuration references
   - Dependency requirements
   - Implementation checklist

2. **PHASE3_TEST_QUICK_REFERENCE.md** - Executive summary (this repo root)
   - Files to test (with line counts)
   - Existing test infrastructure
   - Test patterns quick reference
   - Dependencies to add
   - Build commands
   - Key observations

3. **PHASE3_TEST_IMPLEMENTATION_CHECKLIST.md** - Detailed task list (this file)
   - Method-by-method test requirements
   - Implementation approaches per component
   - Pre-implementation checklist
   - Testing strategies
   - Verification checklist

---

## Key Metrics

| Metric | Value |
|--------|-------|
| Total Implementation Files | 156 |
| Total Test Files (Current) | 49 |
| Current Coverage by File | 31% |
| Target Phase 3 Implementation Lines | ~1,091 (6 files) |
| Estimated New Test Lines | 2,100-2,900 |
| Database Tables to Test | 10 |
| REST Endpoints to Test | 18 |
| Testcontainers Configurations | 3 (PostgreSQL, Kafka, optional WireMock) |
| New Test Classes to Create | 6 |


