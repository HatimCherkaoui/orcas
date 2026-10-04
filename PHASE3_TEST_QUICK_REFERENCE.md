# Phase 3 Testing - Quick Reference Guide

## Files to Test (with exact line counts)

### 1. JDBC State Store
- **Path**: `workflow-orchestrator-jdbc-autoconfigure/src/main/java/com/github/orcas/orchestrator/jdbc/autoconfigure/JdbcWorkflowStateStore.java`
- **Lines**: 388
- **Status**: 0% coverage
- **Key Methods** (15):
  - `start()` (61-117)
  - `record(StatusEvent)` (120-195)
  - `finish()` (198-212)
  - `recordRetry()` (219-240)
  - `workflowName()`, `context()`, `stepContext()`, `suspendedWorkflowIds()`, `saveStepContext()`, `updateContext()`

### 2. Kafka Consumer
- **Path**: `workflow-orchestrator-kafka-autoconfigure/src/main/java/com/github/orcas/orchestrator/kafka/autoconfigure/WorkflowEventConsumer.java`
- **Lines**: 38
- **Status**: 0% coverage
- **Key Method**:
  - `onMessage(String)` (32-36) - @KafkaListener handler

### 3. Retry Scheduler
- **Path**: `workflow-orchestrator-resilience-autoconfigure/src/main/java/com/github/orcas/orchestrator/resilience/autoconfigure/WorkflowRetryScheduler.java`
- **Lines**: 157
- **Status**: 0% coverage
- **Key Methods** (7):
  - `schedule()` (49-60)
  - `scheduleHalfOpenReplay()` (65-83)
  - `runHalfOpenReplay()` (88-111)
  - `replayWorkflowIds()` (129-141)

### 4. Service Controller
- **Path**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/WorkflowServiceController.java`
- **Lines**: 142
- **Status**: Strong partial coverage - real HTTP-level MockMvc tests now cover search binding/400 handling, workflow detail 200/404, step-list populated/empty responses, step 200/404, context 200/404, metadata 200/404, replay with/without body, batch replay with malformed-instant 400 handling, and replay-history; remaining endpoints still lack integration coverage
- **Key Endpoints** (18):
  - `search()` (41-55)
  - `find()`, `steps()`, `step()` (58-65)
  - `stepContext()`, `context()`, `metadata()` (67-74)
  - `logs()`, `stepLogs()`, `contextLogs()`, `metadataLogs()` (76-82)
  - `updateWorkflow()`, `updateStep()` (95-104)
  - `context()` PUT, `metadata()` PUT (106-114)
  - `replay()`, `replaySuspended()` (116-123)
  - **Currently integration-tested**: `GET /workflows` (binding + invalid timestamp 400), `GET /workflows/{id}` (200/404), `GET /workflows/{id}/steps` (populated + empty arrays), `GET /workflows/{id}/steps/{step}` (200/404), `GET /workflows/{id}/context` (200/404), `GET /workflows/{id}/metadata` (200/404), `POST /workflows/{id}/steps/{step}/replay` (with and without body), `POST /workflows/replay` (binding + invalid timestamp 400), `GET /workflows/{id}/replays`

### 5. JDBC Query Service
- **Path**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowQueryService.java`
- **Lines**: 222
- **Status**: Partial coverage - SQL builder coverage plus real replay-history JDBC integration tests; broader search/query surface still incomplete
- **Key Methods** (8):
  - `search()` (53-79)
  - `find()` (82-91)
  - `steps()` (94-100)
  - `step()`, `stepContext()`, `context()`, `metadata()` (103-116)
  - Log query methods (120-137)

### 6. JDBC Admin Service
- **Path**: `workflow-orchestrator-service-autoconfigure/src/main/java/com/github/orcas/orchestrator/service/jdbc/JdbcWorkflowAdminService.java`
- **Lines**: 157
- **Status**: Partial coverage - replay publishing and replay-audit integration paths are tested; wider admin mutation surface still incomplete
- **Key Methods** (6):
  - `updateWorkflowStatus()` (44-54)
  - `updateStepState()` (57-70)
  - `replaceContext()` (73-85)
  - `replaceMetadata()` (88-99)
  - `replayStep()`, `replaySuspendedSteps()` (102-128)

**Total Implementation Lines**: 1,091 lines across 6 files

---

## Existing Test Infrastructure

### Testing Framework Stack
| Component | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 Jupiter | (in spring-boot-starter-test) | Test framework |
| AssertJ | (in spring-boot-starter-test) | Fluent assertions |
| Mockito | 5.23.0 | Mocking (with JVM agent) |
| Testcontainers | 2.0.5 | Docker container management |
| - PostgreSQL | (testcontainers-postgresql) | Database testing |
| - Kafka | (testcontainers-kafka) | Messaging testing |
| REST Assured | 6.0.0 | HTTP testing |
| ArchUnit | 1.4.1 | Architecture testing |

### Existing Test Files by Module
- **core**: 11 files (engine, builder, model, error, api)
- **spring-boot-autoconfigure**: 3 files
- **jdbc-autoconfigure**: 3 files (minimal)
- **kafka-autoconfigure**: 1 file (minimal)
- **resilience-autoconfigure**: 3 files (config only)
- **observability**: 2 files
- **service-autoconfigure**: 7 files (controller unit/integration, SQL builder, JDBC query/admin integration, properties, context test)
- **rest-autoconfigure**: 4 files
- **dashboard-service**: 3 files
- **management-service**: 1 file
- **example-app**: 1 file (486 lines - comprehensive integration test)

**Total**: 49 test files out of 156 implementation files (31% coverage by file count)

---

## Test Patterns Already in Use

### Pattern 1: Unit Test (No Spring)
**Example**: `WorkflowEngineTest.java`
```java
class WorkflowEngineTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    @AfterEach
    void shutdown() { executor.shutdownNow(); }
    
    @Test
    void testScenario() {
        // Arrange: manual instantiation
        var registry = new WorkflowRegistry();
        var store = new InMemoryWorkflowStateStore();
        
        // Act & Assert
        var engine = new WorkflowEngine(registry, events::add, store, executor);
        engine.start("orders", WorkflowContext.of("order-1"));
        
        assertThat(events).extracting(StatusEvent::step).contains(...);
    }
}
```

### Pattern 2: Properties Test (No Spring)
**Example**: `WorkflowJdbcPropertiesTest.java`
```java
class WorkflowJdbcPropertiesTest {
    @Test
    void exposesDefaults() {
        var props = new WorkflowJdbcProperties();
        assertThat(props.isEnabled()).isTrue();
    }
}
```

### Pattern 3: SQL Unit Test (No Spring/DB)
**Example**: `WorkflowQuerySqlTest.java`
```java
class WorkflowQuerySqlTest {
    @Test
    void appendsFiltersWhenPresent() {
        var query = new WorkflowQuery(...);
        var sql = new StringBuilder(" where 1=1");
        var params = new MapSqlParameterSource();
        
        WorkflowQuerySql.appendWorkflowFilters(sql, params, query, "w");
        
        assertThat(sql).contains("w.pipeline_id = :workflowId");
    }
}
```

### Pattern 4: Mock Controller Test
**Example**: `WorkflowServiceControllerTest.java`
```java
class WorkflowServiceControllerTest {
    @Test
    void testEndpoint() {
        // Inline mock implementation
        var controller = new WorkflowServiceController(
            new NoopQuery(), new NoopAdmin());
        
        var response = controller.definition("orders");
        
        assertThat(response.workflow()).isEqualTo("orders");
    }
    
    private static final class NoopQuery implements WorkflowQueryService {
        // Implement all interface methods as no-ops
    }
}
```

### Pattern 5: Integration Test (With Testcontainers)
**Example**: `OrderWorkflowIntegrationTest.java` (486 lines)
```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderWorkflowIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = 
        new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("workflow");
    
    @Container
    static final KafkaContainer KAFKA = 
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
    
    @Test
    void endToEnd() throws Exception {
        given().port(port).body(fixture()).post("/orders").then().statusCode(202);
        awaitAssertWithBackoff(() -> 
            assertThat(queryCount("select count(*) from workflow where status='SUCCESS'"))
                .isEqualTo(1));
    }
}
```

---

## Configuration Files

### Database Schema
- **Path**: `workflow-orchestrator-jdbc-autoconfigure/src/main/resources/orchestrator-schema.sql`
- **Lines**: 120
- **Tables**: 10 (workflow, workflow_step, workflow_context, workflow_metadata, workflow_log, workflow_step_log, workflow_context_log, workflow_metadata_log, workflow_step_context, workflow_step_context_log)
- **RDBMS**: PostgreSQL (TIMESTAMP WITH TIME ZONE, GENERATED BY DEFAULT AS IDENTITY)

### Test Logging
- **Path**: `workflow-orchestrator-observability-autoconfigure/src/test/resources/logback-test.xml`
- **Lines**: 12
- **Config**: Root level = WARN, suppresses verbose logging during tests

### Test Resources
**Observation**: No `application-test.yml` or `application-test.properties` files exist.
- Each test uses programmatic Spring configuration via `@DynamicPropertySource` or manual bean instantiation
- Example app integration test sets all properties dynamically at runtime

---

## Dependencies to Add to POMs

### workflow-orchestrator-jdbc-autoconfigure/pom.xml
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

### workflow-orchestrator-kafka-autoconfigure/pom.xml
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

### workflow-orchestrator-service-autoconfigure/pom.xml
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

---

## Test Classes to Create (Phase 3)

| Test Class | Module | Purpose | Est. Lines |
|-----------|--------|---------|-----------|
| `JdbcWorkflowStateStoreIntegrationTest` | jdbc-autoconfigure | JDBC persistence integration | 400-500 |
| `WorkflowEventConsumerIntegrationTest` | kafka-autoconfigure | Kafka event consumption | 250-350 |
| `WorkflowRetrySchedulerIntegrationTest` | resilience-autoconfigure | Retry scheduling & circuit breaker | 350-450 |
| `WorkflowServiceControllerIntegrationTest` | service-autoconfigure | REST API endpoints | 500-700 |
| `JdbcWorkflowQueryServiceIntegrationTest` | service-autoconfigure | Query service integration | 350-450 |
| `JdbcWorkflowAdminServiceIntegrationTest` | service-autoconfigure | Admin service mutations | 300-400 |

**Estimated Total Test Code**: ~2,100-2,900 lines

---

## Build & Verify Commands

```bash
# Build all modules
mvn clean install

# Run unit tests only
mvn test

# Run integration tests (Failsafe)
mvn verify

# Run specific test
mvn -Dtest=JdbcWorkflowStateStoreIntegrationTest test

# Run with full Docker stack
docker compose up --build

# Run specific module's tests
mvn -pl workflow-orchestrator-jdbc-autoconfigure test
mvn -pl workflow-orchestrator-service-autoconfigure verify

# View test coverage report
mvn jacoco:report
```

---

## Key Observations

1. **No Mock-Heavy Approach**: Project avoids @Mock/@InjectMocks; prefers inline anonymous interface implementations
2. **Virtual Thread Usage**: Tests use `Executors.newVirtualThreadPerTaskExecutor()` to match Java 25 production
3. **Testcontainers Integration**: Already proven pattern in example-app; ready for expansion
4. **Schema Management**: SQL schema file is comprehensive but not auto-executed in library modules (relies on app-level Flyway/Liquibase or manual execution)
5. **Async Event Testing**: Polling/backoff pattern used to wait for async Kafka events in integration tests
6. **JDBC Direct Approach**: No ORM/JPA; direct NamedParameterJdbcTemplate usage makes testing more straightforward
7. **Audit Trail Verification**: Critical for state store tests - verify entries in all 4 audit log tables
8. **Transaction Retry Logic**: JdbcTransactionRunner handles deadlock/serialization failures - test this with concurrent updates

---

## Next Steps

1. **Review** this analysis with team
2. **Approve** suggested test class locations and dependencies
3. **Add Testcontainers dependencies** to affected POMs
4. **Create test fixture helpers** (e.g., WorkflowFixture, StatusEventFixture) if needed
5. **Begin Phase 3 test implementation** using patterns identified above
6. **Run** `mvn verify` to execute all integration tests
7. **Report** test coverage gaps and ensure Phase 3 achieves target %






