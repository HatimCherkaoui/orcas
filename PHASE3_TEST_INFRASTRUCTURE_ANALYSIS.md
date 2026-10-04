# Phase 3 Integration Testing - Infrastructure Analysis

**Date**: 2025
**Project**: Orcas Workflow Orchestrator (v0.6.0-SNAPSHOT)
**Analysis Scope**: Test infrastructure for Phase 3 integration testing

---

## Executive Summary

- **Total Test Files**: 49 (across 156 implementation files → 31% test coverage by file count)
- **Key Modules Needing Testing**: JDBC State Store (388 lines), Kafka Consumer (38 lines), Retry Scheduler (157 lines), Operational API Controller (129 lines), Service Query Classes (157-222 lines)
- **Testing Framework**: JUnit 5, AssertJ, Testcontainers, Mockito (5.23.0)
- **Build Infrastructure**: Maven 3.9.6+, Java 25, Docker Compose for integration tests
- **Database**: PostgreSQL 18 (schema: 120 lines, 10 tables with audit logs)
- **Messaging**: Apache Kafka 4.3.1 (via Testcontainers)

---

## 1. Existing Test Files Inventory

### Test File Locations by Module

#### Core Module Tests (11 files)
- `workflow-orchestrator-core/src/test/java/com/github/orcas/orchestrator/core/`
  - `api/`: `MethodWorkflowStepTest.java`, `ResponseConsumerTest.java`, `StepNamesTest.java`, `StepResultsTest.java`, `StepsTest.java`
  - `builder/`: `PipelineBuilderTest.java`, `StatusCriteriaTest.java`, `StepCatalogTest.java`, `WorkflowDefinitionTest.java`
  - `engine/`: `InMemoryWorkflowStateStoreTest.java`, `WorkflowContextHolderTest.java`, `WorkflowEngineTest.java`, `WorkflowJoinCoordinatorTest.java`, `WorkflowRegistryTest.java`
  - `error/`: `DefaultWorkflowErrorCategorizerTest.java`
  - `model/`: `MetadataTest.java`, `StepExecutionContextTest.java`, `StatusEventTest.java`, `WorkflowContextTest.java`
  - `ArchitectureTest.java`

#### Spring Boot Auto-Configuration Tests (12 files)
- `workflow-orchestrator-spring-boot-autoconfigure/src/test/java/com/github/orcas/orchestrator/autoconfigure/core/`
  - `WorkflowAsyncPropertiesTest.java`
  - `WorkflowBeanResolverTest.java`
  - `WorkflowCoreAutoConfigurationTest.java`

- `workflow-orchestrator-jdbc-autoconfigure/src/test/java/com/github/orcas/orchestrator/jdbc/autoconfigure/`
  - `JdbcTimestampBindingTest.java`
  - `WorkflowJdbcAutoConfigurationTest.java` (19 lines)
  - `WorkflowJdbcPropertiesTest.java`

- `workflow-orchestrator-kafka-autoconfigure/src/test/java/com/github/orcas/orchestrator/kafka/autoconfigure/`
  - `WorkflowKafkaPropertiesTest.java`

- `workflow-orchestrator-resilience-autoconfigure/src/test/java/com/github/orcas/orchestrator/resilience/autoconfigure/`
  - `WorkflowCircuitBreakerPropertiesTest.java`
  - `WorkflowResilienceInvocationInterceptorTest.java`
  - `WorkflowRetryPropertiesTest.java`

- `workflow-orchestrator-observability-autoconfigure/src/test/java/com/github/orcas/orchestrator/observability/autoconfigure/`
  - `WorkflowMdcObserverTest.java`
  - `WorkflowObservabilityPropertiesTest.java`

- `workflow-orchestrator-observability-starter/src/test/java/com/github/orcas/orchestrator/observability/starter/`
  - `WorkflowObservabilityTelemetryCompatibilityTest.java`

#### REST Integration Tests (4 files)
- `workflow-orchestrator-rest/src/test/java/com/github/orcas/orchestrator/rest/annotation/`
  - `RestAnnotationTest.java`

- `workflow-orchestrator-rest-autoconfigure/src/test/java/com/github/orcas/orchestrator/rest/autoconfigure/`
  - `RestClientArgumentsTest.java`
  - `RestClientWorkflowStepTest.java`
  - `WorkflowRestClientPropertiesTest.java`
  - `WorkflowRestClientRegistrarTest.java`

#### Service & Dashboard Tests (7 files)
- `workflow-orchestrator-service/src/test/java/com/github/orcas/orchestrator/service/api/`
  - `ServiceApiTest.java`

- `workflow-orchestrator-service-autoconfigure/src/test/java/com/github/orcas/orchestrator/service/`
  - `WorkflowServiceAutoConfigurationContextTest.java`
  - `WorkflowServiceControllerTest.java` (54 lines - minimal mock-only tests)
  - `WorkflowServicePropertiesTest.java`

- `workflow-orchestrator-service-autoconfigure/src/test/java/com/github/orcas/orchestrator/service/jdbc/`
  - `WorkflowQuerySqlTest.java` (62 lines - unit tests for SQL building)

- `workflow-orchestrator-dashboard-service/src/test/java/com/github/orcas/orchestrator/dashboard/api/`
  - `KafkaServiceTest.java`

- `workflow-orchestrator-dashboard-service-autoconfigure/src/test/java/com/github/orcas/orchestrator/dashboard/autoconfigure/`
  - `WorkflowDashboardServiceAutoConfigurationContextTest.java`
  - `WorkflowDashboardServicePropertiesTest.java`

#### Resilience Tests (2 files)
- `workflow-orchestrator-resilience/src/test/java/com/github/orcas/orchestrator/resilience/annotation/`
  - `ResilienceAnnotationTest.java`

#### Management Service Tests (1 file)
- `workflow-orchestrator-management-service/src/test/java/com/github/orcas/management/`
  - `ManagementServiceApplicationContextTest.java`

#### Example Application Tests (1 file)
- `example-app/src/test/java/com/github/orcas/demo/`
  - `OrderWorkflowIntegrationTest.java` (486 lines - comprehensive integration test)

---

## 2. Target Files for Phase 3 Integration Testing

### 2.1 JDBC State Store
**File**: `workflow-orchestrator-jdbc-autoconfigure/src/main/java/com/github/orcas/orchestrator/jdbc/autoconfigure/JdbcWorkflowStateStore.java`
- **Lines**: 388
- **Current Test Coverage**: None (only `WorkflowJdbcAutoConfigurationTest.java` tests properties)
- **Key Methods** (15 methods):
  - `start(String id, String workflow, WorkflowContext context)` - Lines 61-117
  - `record(StatusEvent event)` - Lines 120-122
  - `record(StatusEvent event, String stepTypeClassName)` - Lines 125-195
  - `finish(StatusEvent event)` - Lines 198-212
  - `recordRetry(String workflowId, String stepName, int attempt, String reason)` - Lines 219-240
  - `workflowName(String id)` - Lines 244-247
  - `context(String id)` - Lines 250-265
  - `stepContext(String workflowId, String stepName)` - Lines 268-285
  - `suspendedWorkflowIds(String stepName)` - Lines 288-296
  - `saveStepContext(StepContext context)` - Lines 298-346
  - `updateContext(String id, WorkflowContext context)` - Lines 349-385
- **Dependencies**:
  - `NamedParameterJdbcTemplate` (Spring JDBC)
  - `JdbcJsonCodec`, `JdbcAuditLog`, `JdbcTransactionRunner` (internal)
  - `ObjectMapper` (Jackson)
  - `PlatformTransactionManager` (Spring TX)
- **Critical Functionality**:
  - ACID transaction management with auto-retry on deadlock/serialization failures
  - JSON payload serialization/deserialization
  - Audit trail logging across 4 audit tables
  - Support for parallel/concurrent step execution

**Test Coverage Gap**: ~0% - Needs comprehensive integration tests with Testcontainers PostgreSQL

### 2.2 Kafka Consumer
**File**: `workflow-orchestrator-kafka-autoconfigure/src/main/java/com/github/orcas/orchestrator/kafka/autoconfigure/WorkflowEventConsumer.java`
- **Lines**: 38
- **Current Test Coverage**: None
- **Key Method**:
  - `onMessage(String message)` - Lines 32-36 (KafkaListener)
- **Dependencies**:
  - `WorkflowEngine` (core)
  - `ObjectMapper` (Jackson)
  - `KafkaListener` (Spring Kafka)
- **Critical Functionality**:
  - Receives `StatusEvent` messages from Kafka topic
  - Deserializes JSON to `StatusEvent` object
  - Dispatches to `WorkflowEngine.handle()` to trigger step progression

**Test Coverage Gap**: ~0% - Needs Testcontainers Kafka integration tests

### 2.3 Retry Scheduler
**File**: `workflow-orchestrator-resilience-autoconfigure/src/main/java/com/github/orcas/orchestrator/resilience/autoconfigure/WorkflowRetryScheduler.java`
- **Lines**: 157
- **Current Test Coverage**: None
- **Key Methods** (7 methods):
  - `schedule(String workflowId, String stepName, Duration delay)` - Lines 49-60
  - `scheduleHalfOpenReplay(String breakerName, String stepName, Duration delay, int permittedCalls)` - Lines 65-83
  - `scheduledHalfOpenReplay(String breakerName, String stepName)` - Lines 85-87
  - `runHalfOpenReplay(String breakerName, String stepName, Duration delay, int batchSize)` - Lines 88-111
  - `replayRemaining(...)` - Lines 112-128
  - `replayWorkflowIds(String stepName, Set<String> workflowIds, CircuitBreaker breaker, String reason)` - Lines 129-141
  - `recordRetryAttempt(String workflowId, String stepName, String reason)` - Lines 142-151
- **Dependencies**:
  - `WorkflowEngine` (core)
  - `WorkflowRetryStateStore` (JDBC)
  - `CircuitBreakerRegistry` (Resilience4j)
  - `ScheduledExecutorService` (Java concurrency)
- **Critical Functionality**:
  - Schedules delayed replays for suspended steps using virtual threads
  - Manages circuit breaker half-open probe batches
  - Tracks scheduled replays in memory
  - Cascades retries when circuit breaker closes after being open

**Test Coverage Gap**: ~0% - Needs tests for scheduling, batching, and circuit breaker integration

### 2.4 Operational API Controller
**File**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/WorkflowServiceController.java`
- **Lines**: 129
- **Current Test Coverage**: Minimal (only `definition()` method tested via mock)
- **Key Endpoints** (18 methods):
  - `search(...)` - Lines 41-55 (GET `/workflows`)
  - `find(String id)` - Lines 58 (GET `/workflows/{id}`)
  - `steps(String id)` - Lines 60 (GET `/workflows/{id}/steps`)
  - `step(String id, String stepName)` - Lines 62-65 (GET `/workflows/{id}/steps/{stepName}`)
  - `stepContext(...)` - Lines 67-70 (GET `/workflows/{id}/steps/{stepName}/context`)
  - `context(String id)` - Lines 72 (GET `/workflows/{id}/context`)
  - `metadata(String id)` - Lines 74 (GET `/workflows/{id}/metadata`)
  - `logs(String id)` - Lines 76 (GET `/workflows/{id}/logs`)
  - `stepLogs(...)` - Lines 78 (GET `/workflows/{id}/steps/{stepName}/logs`)
  - `contextLogs(String id)` - Lines 80 (GET `/workflows/{id}/context/logs`)
  - `metadataLogs(String id)` - Lines 82 (GET `/workflows/{id}/metadata/logs`)
  - `definition(String workflow)` - Lines 91-93 (GET `/workflows/definitions/{workflow}`)
  - `updateWorkflow(String id, Map<String, String> body)` - Lines 95-98 (PATCH `/workflows/{id}`)
  - `updateStep(...)` - Lines 100-104 (PATCH `/workflows/{id}/steps/{stepName}`)
  - `context(String id, Object body)` - Lines 106-109 (PUT `/workflows/{id}/context`)
  - `metadata(String id, Map<String, String> body)` - Lines 111-114 (PUT `/workflows/{id}/metadata`)
  - `replay(String id, String stepName)` - Lines 116-119 (POST `/workflows/{id}/steps/{stepName}/replay`)
  - `replaySuspended(WorkflowQuery query)` - Lines 121-123 (POST `/workflows/replay`)
- **Dependencies**:
  - `WorkflowQueryService` (interface)
  - `WorkflowAdminService` (interface)
  - Spring Web MVC annotations
- **Critical Functionality**:
  - Query workflows with complex filtering (workflow name, status, step name, metadata, date range)
  - Retrieve execution history and audit logs
  - Administrative state updates (workflow/step status, context, metadata)
  - Manual step replay triggering
  - Batch replay of suspended steps

**Test Coverage Gap**: ~85% - Only `definition()` tested; needs HTTP integration tests for all endpoints

### 2.5 Service Query Classes (JdbcWorkflowQueryService & JdbcWorkflowAdminService)
**Files**:
- `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowQueryService.java` (222 lines)
- `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowAdminService.java` (157 lines)

**Current Test Coverage**:
- `WorkflowQuerySqlTest.java` (62 lines) - Only tests SQL WHERE clause building logic
- No tests for actual JDBC execution or result mapping

**JdbcWorkflowQueryService Methods** (8 methods):
- `search(WorkflowQuery query)` - Lines 53-79
- `find(String id)` - Lines 82-91
- `steps(String id)` - Lines 94-100
- `step(String id, String stepName)` - Lines 103-104
- `stepContext(String workflowId, String stepName)` - Lines 107-108
- `context(String workflowId)` - Lines 111-112
- `metadata(String workflowId)` - Lines 115-116
- `workflowLogs()`, `stepLogs()`, `contextLogs()`, `metadataLogs()` - Lines 120-137

**JdbcWorkflowAdminService Methods** (6 methods):
- `updateWorkflowStatus(String workflowId, String status)` - Lines 44-54
- `updateStepState(String workflowId, String stepName, String state)` - Lines 57-70
- `replaceContext(String workflowId, Object context)` - Lines 73-85
- `replaceMetadata(String workflowId, Map<String, String> metadata)` - Lines 88-99
- `replayStep(String workflowId, String stepName)` - Lines 102-104
- `replaySuspendedSteps(WorkflowQuery query)` - Lines 107-128

**Test Coverage Gap**: ~17% - SQL building tested, but no JDBC result mapping or admin mutation tests

---

## 3. Current Test Patterns Analysis

### 3.1 Testing Framework & Dependencies

**From Root pom.xml** (lines 36-57):
- **JUnit 5** (Jupiter): `org.junit.jupiter:junit-jupiter`
- **AssertJ**: `org.assertj:assertj-core` (assertion library)
- **Mockito**: `org.mockito:mockito-core:5.23.0`
- **Testcontainers BOM**: `2.0.5` (via importScope)
  - `org.testcontainers:testcontainers-junit-jupiter`
  - `org.testcontainers:testcontainers-postgresql`
  - `org.testcontainers:testcontainers-kafka`
- **REST Assured**: `6.0.0` (HTTP testing)
- **ArchUnit**: `1.4.1` (architecture testing)

**Maven Plugins**:
- Maven Surefire: `3.5.3` (unit test runner)
- Maven Failsafe: `3.5.3` (integration test runner)
- Mockito JVM agent configured (line 264, 273)

### 3.2 Unit Test Pattern Examples

#### Pattern 1: Core Engine Tests (WorkflowEngineTest.java, lines 1-106)
```java
class WorkflowEngineTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    @AfterEach
    void shutdown() { executor.shutdownNow(); }
    
    @Test
    void routesInitToFirstStepAndFirstStepToNextStep() {
        // Arrange: create workflow definition and registry
        var definition = new WorkflowDefinition("orders", List.of(...));
        var registry = new WorkflowRegistry();
        registry.register(definition);
        
        // Act & Assert: use InMemoryWorkflowStateStore + event collector
        var events = new ArrayList<StatusEvent>();
        var engine = new WorkflowEngine(registry, events::add, store, executor);
        engine.start("orders", WorkflowContext.of("order-1"));
        
        assertThat(events).extracting(StatusEvent::step)
                .contains(StepNames.INIT, validate.name(), reserve.name());
    }
}
```
**Characteristics**:
- No external dependencies (InMemory state store)
- Manual dependency injection (no @Autowired)
- Event-driven assertions using ArrayList collectors
- Virtual thread executor cleanup via @AfterEach

#### Pattern 2: Property Tests (WorkflowJdbcPropertiesTest.java)
```java
class WorkflowJdbcAutoConfigurationTest {
    @Test
    void exposesJdbcOnlyDefaults() {
        var properties = new WorkflowJdbcProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isSchemaLocation())
            .isEqualTo("classpath:orchestrator-schema.sql");
    }
}
```
**Characteristics**:
- Direct class instantiation
- No Spring context
- Simple assertion of default values

#### Pattern 3: SQL Query Building Tests (WorkflowQuerySqlTest.java, lines 1-62)
```java
class WorkflowQuerySqlTest {
    @Test
    void appendsWorkflowFiltersOnlyWhenValuesArePresent() {
        var query = new WorkflowQuery(
            "wf-1", "orders", "RUNNING", null, null,
            "customer", "42", ...);
        var sql = new StringBuilder(" where 1=1");
        var parameters = new MapSqlParameterSource();
        
        WorkflowQuerySql.appendWorkflowFilters(sql, parameters, query, "w");
        
        assertThat(sql).contains("w.pipeline_id = :workflowId")
                       .contains("w.status = :status");
        assertThat(parameters.getValue("workflowId")).isEqualTo("wf-1");
    }
}
```
**Characteristics**:
- Tests SQL string building logic
- Verifies parameter binding
- Pure unit test (no database)

#### Pattern 4: Mock-Only Controller Tests (WorkflowServiceControllerTest.java, lines 1-54)
```java
class WorkflowServiceControllerTest {
    @Test
    void returnsRegisteredWorkflowDefinition() {
        var controller = new WorkflowServiceController(
            new NoopQuery(), new NoopAdmin());
        
        var response = controller.definition("orders");
        
        assertThat(response.workflow()).isEqualTo("orders");
    }
    
    private static final class NoopQuery implements WorkflowQueryService {
        public PageResult<WorkflowSummary> search(WorkflowQuery query) {
            return new PageResult<>(List.of(), 0, 0, 25, 0);
        }
        // ... other noop implementations
    }
}
```
**Characteristics**:
- Inline anonymous mock implementations of interfaces
- No Mockito framework usage
- Single method tested only (definition)

### 3.3 Integration Test Pattern (OrderWorkflowIntegrationTest.java)

**Lines 55-486**: Comprehensive multi-container integration test

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderWorkflowIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = 
        new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("workflow")
            .withUsername("workflow")
            .withPassword("workflow");
    
    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
            .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1");
    
    @Container
    static final GenericContainer<?> WIREMOCK =
        new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.2"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/").forStatusCode(200));
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // ... 15+ more dynamic properties
    }
    
    @Autowired
    private WorkflowEngine engine;
    
    @Autowired
    private JdbcTemplate jdbc;
    
    @Autowired
    private KafkaListenerEndpointRegistry kafkaRegistry;
    
    @Test
    void orderWorkflowSucceedsWithValidPayload() throws Exception {
        given()
            .port(this.port)
            .contentType(ContentType.JSON)
            .body(JsonFixture.order())
        .when()
            .post("/orders")
        .then()
            .statusCode(202);
        
        awaitAssertWithBackoff(() ->
            assertThat(queryCount("select count(*) from workflow where status='SUCCESS'"))
                .isEqualTo(1));
    }
    
    private void awaitAssertWithBackoff(Runnable assertion) { /* ... */ }
}
```

**Characteristics**:
- Uses `@Testcontainers` (JUnit 5 extension)
- Multiple containers: PostgreSQL, Kafka, WireMock
- `@DynamicPropertySource` to inject container URLs into Spring test context
- REST Assured for HTTP testing
- Polling/backoff waits for async events
- JDBC/JdbcTemplate to verify database state
- Kafka listener registry manipulation

---

## 4. Test Configuration Files

### 4.1 Logback Configuration
**File**: `workflow-orchestrator-observability-autoconfigure/src/test/resources/logback-test.xml` (12 lines)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%msg%n</pattern>
        </encoder>
    </appender>
    <root level="WARN">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```
**Purpose**: Suppresses verbose logging during test runs (WARN level)

### 4.2 Application Configuration Files
**Locations**: Test modules only have logback-test.xml; no `application-test.yml` files found
- Each test either uses `@SpringBootTest` with `@DynamicPropertySource` or creates beans manually
- Example app integration test sets all properties dynamically via registry

### 4.3 Schema Initialization
**File**: `workflow-orchestrator-jdbc-autoconfigure/src/main/resources/orchestrator-schema.sql` (120 lines)
- 10 tables (workflow, workflow_step, workflow_context, workflow_metadata, workflow_log, workflow_step_log, workflow_context_log, workflow_metadata_log, workflow_step_context, workflow_step_context_log)
- PostgreSQL-specific: TIMESTAMP WITH TIME ZONE, GENERATED BY DEFAULT AS IDENTITY, ON DELETE CASCADE
- Not auto-executed by test modules; relies on `spring.jpa.hibernate.ddl-auto=create-drop` in example-app or manual execution

---

## 5. Phase 3 Integration Test Requirements

### 5.1 Test Environment Setup

**Testcontainers Stack** (based on example-app):
1. **PostgreSQL 18-alpine** (database for JDBC state store)
   - Database: `workflow`
   - User: `workflow` / Password: `workflow`
   - Schema auto-initialized via `orchestrator-schema.sql`

2. **Apache Kafka 4.3.1** (for async event transport)
   - Broker configs: Share coordinator disabled for single-broker setup
   - Topics auto-created by consumer group
   - Consumer group: `workflow-orchestrator` (from `application.yml`)

3. **Optional: WireMock 3.13.2** (mock HTTP services for REST steps)
   - Port 8080 exposed
   - Health check: `GET /__admin/` → HTTP 200

### 5.2 Test Class Locations (to be created)

| Component | Test Class Name | Target Module | Suggested Approach |
|-----------|-----------------|----------------|--------------------|
| JdbcWorkflowStateStore | `JdbcWorkflowStateStoreIntegrationTest.java` | `workflow-orchestrator-jdbc-autoconfigure` | Testcontainers PostgreSQL, mock ObjectMapper |
| WorkflowEventConsumer | `WorkflowEventConsumerIntegrationTest.java` | `workflow-orchestrator-kafka-autoconfigure` | Testcontainers Kafka, embedded Spring context |
| WorkflowRetryScheduler | `WorkflowRetrySchedulerIntegrationTest.java` | `workflow-orchestrator-resilience-autoconfigure` | Mock state store, mock circuit breaker registry, real scheduler |
| WorkflowServiceController | `WorkflowServiceControllerIntegrationTest.java` | `workflow-orchestrator-service-autoconfigure` | Testcontainers PostgreSQL + Kafka, MockMvc or REST Assured |
| JdbcWorkflowQueryService | `JdbcWorkflowQueryServiceIntegrationTest.java` | `workflow-orchestrator-service-autoconfigure` | Testcontainers PostgreSQL, insert fixture data, query assertions |
| JdbcWorkflowAdminService | `JdbcWorkflowAdminServiceIntegrationTest.java` | `workflow-orchestrator-service-autoconfigure` | Testcontainers PostgreSQL, verify mutations & audit logs |

### 5.3 Dependencies to Add

**To `workflow-orchestrator-jdbc-autoconfigure/pom.xml`**:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**To `workflow-orchestrator-kafka-autoconfigure/pom.xml`**:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**To `workflow-orchestrator-service-autoconfigure/pom.xml`**:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-kafka</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 6. Existing Test Pattern Summary

### Test Naming Conventions
- **Unit tests**: `<ClassName>Test.java` (e.g., `WorkflowEngineTest.java`)
- **Integration tests**: `<ClassName>IntegrationTest.java` (e.g., `OrderWorkflowIntegrationTest.java`)
- **Context tests**: `<ClassName>AutoConfigurationContextTest.java` (e.g., `WorkflowServiceAutoConfigurationContextTest.java`)
- **Property tests**: `<ClassName>PropertiesTest.java` (e.g., `WorkflowJdbcPropertiesTest.java`)

### Assertion Pattern
- **AssertJ fluent assertions**: `assertThat(actual).isEqualTo(expected)`, `.contains()`, `.extracting()`
- **No Hamcrest matchers** used in existing tests

### Mock Pattern
- **No @Mock / @InjectMocks**: Project uses inline anonymous implementations of interfaces
- **Mockito when needed**: Available via maven-surefire javaagent, but not currently used in core tests
- **ObjectProvider pattern**: Used for lazy dependency injection in Kafka consumer

### Virtual Threads
- Tests use `Executors.newVirtualThreadPerTaskExecutor()` to match production
- Executor cleanup via `@AfterEach`

### Database Testing
- **InMemoryWorkflowStateStore** used for unit tests
- **Testcontainers PostgreSQL** used for integration tests (example-app only)
- No H2 or embedded database used

---

## 7. File Paths Reference

### Main Implementation Files for Phase 3

| Component | Module Path | File Path | Lines |
|-----------|------------|-----------|-------|
| JDBC State Store | `workflow-orchestrator-jdbc-autoconfigure` | `src/main/java/.../JdbcWorkflowStateStore.java` | 388 |
| Kafka Consumer | `workflow-orchestrator-kafka-autoconfigure` | `src/main/java/.../WorkflowEventConsumer.java` | 38 |
| Retry Scheduler | `workflow-orchestrator-resilience-autoconfigure` | `src/main/java/.../WorkflowRetryScheduler.java` | 157 |
| Service Controller | `workflow-orchestrator-service-autoconfigure` | `src/main/java/.../WorkflowServiceController.java` | 129 |
| JDBC Query Service | `workflow-orchestrator-service-autoconfigure` | `src/main/java/.../jdbc/JdbcWorkflowQueryService.java` | 222 |
| JDBC Admin Service | `workflow-orchestrator-service-autoconfigure` | `src/main/java/.../jdbc/JdbcWorkflowAdminService.java` | 157 |
| Database Schema | `workflow-orchestrator-jdbc-autoconfigure` | `src/main/resources/orchestrator-schema.sql` | 120 |

### Supporting Classes (to be mocked/extended)

| Class | Module Path | File Path |
|-------|------------|-----------|
| JdbcJsonCodec | `workflow-orchestrator-jdbc-autoconfigure` | `src/main/java/.../JdbcJsonCodec.java` |
| JdbcAuditLog | `workflow-orchestrator-jdbc-autoconfigure` | `src/main/java/.../JdbcAuditLog.java` |
| JdbcTransactionRunner | `workflow-orchestrator-jdbc-autoconfigure` | `src/main/java/.../JdbcTransactionRunner.java` |
| WorkflowQuerySql | `workflow-orchestrator-service-autoconfigure` | `src/main/java/.../jdbc/WorkflowQuerySql.java` |
| JdbcWorkflowViewMapper | `workflow-orchestrator-service-autoconfigure` | `src/main/java/.../jdbc/JdbcWorkflowViewMapper.java` |
| WorkflowEngine | `workflow-orchestrator-core` | `src/main/java/.../engine/WorkflowEngine.java` |

---

## 8. Checklist for Phase 3 Test Implementation

- [ ] **Dependency Updates**
  - [ ] Add Testcontainers dependencies to JDBC, Kafka, and Service autoconfigure modules
  - [ ] Add `spring-boot-starter-test` to test scope (includes Mockito, AssertJ)

- [ ] **JDBC State Store Tests** (`JdbcWorkflowStateStoreIntegrationTest.java`)
  - [ ] Container setup (PostgreSQL with orchestrator-schema.sql)
  - [ ] Test `start()` - workflow row insertion, context persistence, audit trail
  - [ ] Test `record()` - step status updates, workflow status transitions, metadata updates
  - [ ] Test `finish()` - terminal state marking
  - [ ] Test `recordRetry()` - retry count incrementation, audit logging
  - [ ] Test `context()` and `updateContext()` - context serialization/deserialization
  - [ ] Test `stepContext()` and `saveStepContext()` - step execution context storage
  - [ ] Test `suspendedWorkflowIds()` - query suspended steps
  - [ ] Test transaction retry on deadlock (simulate concurrent updates)
  - [ ] Test audit trail completeness (workflow_log, workflow_step_log, workflow_context_log, workflow_metadata_log)

- [ ] **Kafka Consumer Tests** (`WorkflowEventConsumerIntegrationTest.java`)
  - [ ] Container setup (Kafka)
  - [ ] Test message consumption from Kafka topic
  - [ ] Test StatusEvent deserialization
  - [ ] Test engine.handle() invocation
  - [ ] Test error handling on malformed JSON
  - [ ] Test listener configuration (@KafkaListener properties)

- [ ] **Retry Scheduler Tests** (`WorkflowRetrySchedulerIntegrationTest.java`)
  - [ ] Test `schedule()` - delayed replay invocation via ScheduledExecutorService
  - [ ] Test `scheduleHalfOpenReplay()` - deduplication of concurrent OPEN events
  - [ ] Test `scheduledHalfOpenReplay()` - retrieval of pending replays
  - [ ] Test half-open batch logic with suspended workflow IDs
  - [ ] Test circuit breaker state transitions (OPEN → HALF_OPEN → CLOSED)
  - [ ] Test cascading remaining replays when breaker closes
  - [ ] Test retry count recording in state store
  - [ ] Test resource cleanup via `destroy()` (executor shutdown)

- [ ] **Service Controller Tests** (`WorkflowServiceControllerIntegrationTest.java`)
  - [ ] Setup Testcontainers for PostgreSQL + Kafka + Spring context
  - [ ] INSERT fixture workflows with varied statuses and steps
  - [ ] Test `search()` with all filter combinations (workflow, status, step, metadata, date range, pagination)
  - [ ] Test `find()` single workflow retrieval
  - [ ] Test `steps()` list all steps for a workflow
  - [ ] Test `step()` single step details
  - [ ] Test `stepContext()` step input/output/attributes
  - [ ] Test `context()`, `metadata()` retrieval
  - [ ] Test log endpoints (workflow, step, context, metadata logs)
  - [ ] Test `updateWorkflow()`, `updateStep()` mutations
  - [ ] Test `context()` PUT and `metadata()` PUT replacements
  - [ ] Test `replay()` step replay triggering
  - [ ] Test `replaySuspended()` batch replay
  - [ ] Test HTTP error responses (404 for missing workflows, 400 for invalid input)

- [ ] **JDBC Query Service Tests** (`JdbcWorkflowQueryServiceIntegrationTest.java`)
  - [ ] Setup PostgreSQL container with schema
  - [ ] INSERT fixture data (multiple workflows with various states)
  - [ ] Test `search()` filtering and pagination
  - [ ] Test `find()` single workflow details
  - [ ] Test `steps()` step list ordering (by start date or name)
  - [ ] Test `step()` single step retrieval with all fields
  - [ ] Test `stepContext()` JSON deserialization
  - [ ] Test `context()` and `metadata()` JSON deserialization
  - [ ] Test log queries (verify correct order and content)
  - [ ] Test result mapping for all field types (timestamps, JSON, enums)

- [ ] **JDBC Admin Service Tests** (`JdbcWorkflowAdminServiceIntegrationTest.java`)
  - [ ] Setup PostgreSQL container
  - [ ] Test `updateWorkflowStatus()` - status update + audit log entry
  - [ ] Test `updateStepState()` - state update, date_ended calculation for terminal states
  - [ ] Test `replaceContext()` - JSON serialization, audit logging
  - [ ] Test `replaceMetadata()` - map serialization, audit logging
  - [ ] Test `replayStep()` - publish event to publisher
  - [ ] Test `replaySuspendedSteps()` - batch queries and replay invocations
  - [ ] Verify audit trail entries for all mutations
  - [ ] Test optimistic error handling (EmptyResultDataAccessException)

---

## 9. Quick Reference: Build & Test Commands

```bash
# Build entire project
mvn clean install

# Run only unit tests (Surefire)
mvn test

# Run only integration tests (Failsafe)
mvn verify

# Run specific test class
mvn -Dtest=JdbcWorkflowStateStoreIntegrationTest test

# Run with detailed output
mvn test -X

# Build and start full Docker Compose stack
docker compose up --build

# Run example-app integration tests
mvn -pl example-app verify
```

---

## 10. Summary

| Aspect | Details |
|--------|---------|
| **Total Test Files** | 49 existing |
| **Test Coverage Gap** | JDBC (0%), Kafka Consumer (0%), Retry Scheduler (0%), Service Controller (85%), Query Services (17%) |
| **Testing Framework** | JUnit 5, AssertJ, Mockito, Testcontainers |
| **Database** | PostgreSQL 18 (schema: 120 lines, 10 tables) |
| **Messaging** | Kafka 4.3.1 |
| **Key Infrastructure** | Virtual threads (Java 25), Testcontainers for test isolation, Maven Surefire/Failsafe runners |
| **Pattern for New Tests** | Use inline anonymous interfaces for mocks, Testcontainers for external dependencies, @SpringBootTest + @DynamicPropertySource for Spring context |
| **Estimated New Test Lines** | ~3000+ lines across 6 integration test classes |


