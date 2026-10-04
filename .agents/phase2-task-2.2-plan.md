# PHASE 2 - TASK 2.2: Code Duplication Cleanup
## Implementation Plan Presentation

**Status**: PLAN PRESENTATION (AWAITING IMPLEMENTATION APPROVAL)
**Date**: October 4, 2026
**Owner**: Backend Developer Agent
**Effort**: 2-3 days
**Priority**: P2 (Medium)
**Model**: GPT (auto effort mode)

---

## 1. EXECUTIVE SUMMARY

Eliminate ~770 lines of duplicated code across 4 architectural areas by consolidating to reusable abstractions. Target: <300 LOC remaining duplication (~60% reduction). Impact: Improved maintainability, lower regression risk, consistent error handling.

---

## 2. DUPLICATION ANALYSIS & REFACTORING STRATEGY

### Area 1: Step Adapters (~200 LOC duplicated)

#### Current State
- **Files**: 6+ step adapter implementations
  - `workflow-orchestrator-core/src/main/java/.../MethodWorkflowStep.java`
  - `workflow-orchestrator-core/src/main/java/.../MethodAsyncWorkflowStep.java`
  - `workflow-orchestrator-core/src/main/java/.../RestClientWorkflowStep.java`
  - `workflow-orchestrator-kafka-autoconfigure/src/main/java/.../KafkaWorkflowStep.java`
  - (2+ more implementations)

- **Common Patterns** (duplicated across all):
  1. Execution lifecycle: validate → execute → handle errors → context mapping
  2. Retry handling: classify error → determine if replayable → schedule retry
  3. Context mapping: extract input → invoke → map output
  4. Error handling: catch exceptions → log → categorize → throw appropriate exception

- **Implementation Interface**: `WorkflowStep` interface
  - Methods: `execute()`, `supports()`, `name()`
  - Duplicated logic in all 6+ implementations

#### Proposed Solution
Create 2 base classes with template method pattern:

```
WorkflowStep (existing interface)
    ↑
    ├─ BaseWorkflowStep<T> (NEW)
    │   ├─ Template: validate → executeInternal() → mapResult → handleError
    │   ├─ Retry logic
    │   ├─ Error classification integration
    │   └─ Subclasses: MethodWorkflowStep, RestClientWorkflowStep, ...
    │
    └─ BaseAsyncWorkflowStep<T> (NEW)
        ├─ Template: async pattern with CompletableFuture
        ├─ Async retry logic
        ├─ Callback handling
        └─ Subclasses: MethodAsyncWorkflowStep, KafkaWorkflowStep, ...
```

#### Refactoring Steps
1. **Create BaseWorkflowStep<T>** (abstract class)
   - Extract common execution logic
   - Define protected abstract methods: `doExecute()`, `extractInput()`, `mapOutput()`
   - Implement: retry scheduling, error classification, logging
   - ~80 LOC

2. **Create BaseAsyncWorkflowStep<T>** (abstract class extending BaseWorkflowStep)
   - Async execution pattern (CompletableFuture)
   - Callback/listener support
   - Async error handling
   - ~60 LOC

3. **Refactor each implementation** to extend base class
   - Remove duplicated lifecycle code (~30 LOC per class × 6 = 180 LOC saved)
   - Keep only unique behavior in overridden methods
   - Update inheritance: MethodWorkflowStep extends BaseWorkflowStep<Result>

4. **Update Spring bean configurations** (if using autowiring)
   - Ensure base classes are abstract (@Component not applied)
   - Concrete implementations remain @Component

#### Acceptance Criteria (Area 1)
- [ ] BaseWorkflowStep<T> created with full template method pattern
- [ ] BaseAsyncWorkflowStep<T> created with async support
- [ ] All 6+ step adapters refactored to use base classes
- [ ] Existing unit tests for adapters pass (100%)
- [ ] No behavioral changes to public WorkflowStep interface
- [ ] Code coverage maintained for step adapters (≥80%)
- [ ] Javadoc added to base classes explaining template methods

---

### Area 2: Error Classification (~60 LOC duplicated)

#### Current State
- **Files**:
  - `workflow-orchestrator-resilience-autoconfigure/src/main/java/.../DefaultWorkflowErrorCategorizer.java` (~28 LOC)
  - `workflow-orchestrator-rest-autoconfigure/src/main/java/.../RestOperationExceptionHandler.java` (~32 LOC)

- **Duplicated Logic**:
  1. Error type detection (IOException, HttpStatus5xx, timeout, etc.)
  2. Categorization decision: replayable vs non-replayable
  3. Mapping error to appropriate exception class

- **Current Logic** (simplified):
  ```java
  // DefaultWorkflowErrorCategorizer
  if (ex instanceof IOException) return REPLAYABLE;
  if (ex instanceof HttpClientErrorException) {
    int code = ex.getStatusCode();
    if (code == 408 || code == 425 || code == 429 || code >= 500) return REPLAYABLE;
  }
  return NON_REPLAYABLE;
  
  // RestOperationExceptionHandler (similar logic)
  if (ex instanceof IOException) return shouldRetry();
  if (ex instanceof WebClientResponseException) {
    return ex.getStatusCode().is5xxServerError();
  }
  return false;
  ```

#### Proposed Solution
Create unified `WorkflowErrorStrategy` class with strategy pattern:

```
WorkflowErrorStrategy (NEW strategy interface)
├─ IoErrorStrategy: IOException → REPLAYABLE
├─ HttpErrorStrategy: HTTP status codes → REPLAYABLE/NON_REPLAYABLE
├─ TimeoutErrorStrategy: Timeout exceptions → REPLAYABLE
└─ DefaultErrorStrategy: Everything else → NON_REPLAYABLE
```

#### Refactoring Steps
1. **Create WorkflowErrorStrategy interface**
   - Method: `canRetry(Throwable ex) → boolean`
   - Method: `categorize(Throwable ex) → ErrorCategory` (enum: REPLAYABLE, NON_REPLAYABLE, CIRCUIT_BREAKER_OPEN)

2. **Create concrete strategy classes**
   - IoErrorStrategy (~10 LOC)
   - HttpErrorStrategy (~15 LOC with HTTP status mapping)
   - TimeoutErrorStrategy (~8 LOC)
   - DefaultErrorStrategy (~5 LOC)

3. **Create CompositeErrorStrategy**
   - Wraps multiple strategies in order
   - First matching strategy wins
   - Order: IOException → HTTP → Timeout → Default
   - ~20 LOC

4. **Update DefaultWorkflowErrorCategorizer**
   - Use CompositeErrorStrategy internally
   - Remove duplicated logic

5. **Update RestOperationExceptionHandler**
   - Use CompositeErrorStrategy internally
   - Remove duplicated logic
   - Both now call: `errorStrategy.canRetry(ex)`

#### Acceptance Criteria (Area 2)
- [ ] WorkflowErrorStrategy interface created
- [ ] All concrete strategies implemented
- [ ] CompositeErrorStrategy created with ordering
- [ ] DefaultWorkflowErrorCategorizer refactored to use strategy
- [ ] RestOperationExceptionHandler refactored to use strategy
- [ ] All existing error classification tests pass (100%)
- [ ] No behavioral changes to error handling
- [ ] Error categorization decisions identical before/after

---

### Area 3: Context Mapping (~150 LOC duplicated)

#### Current State
- **Files**:
  - `workflow-orchestrator-core/src/main/java/.../WorkflowContextExtractor.java` (~35 LOC)
  - `workflow-orchestrator-core/src/main/java/.../WorkflowContextTransformer.java` (~40 LOC)
  - `workflow-orchestrator-core/src/main/java/.../WorkflowResultMapper.java` (~38 LOC)
  - `workflow-orchestrator-rest-autoconfigure/src/main/java/.../ContextPayloadMapper.java` (~37 LOC)

- **Duplicated Logic**:
  1. Extract business object from workflow context
  2. Serialize/deserialize to/from JSON
  3. Apply transformations (filtering, mapping)
  4. Map execution result back to context

- **Pattern**: Each implements similar pipeline:
  ```
  Input → Extract → Transform → Serialize/Deserialize → Output
  ```

#### Proposed Solution
Create unified `WorkflowContextPipeline` with pluggable stages:

```
WorkflowContextPipeline (NEW)
  ├─ Stage 1: ContextExtractor (interface)
  │   └─ Extract business object from WorkflowContext
  ├─ Stage 2: ContextTransformer (interface)
  │   └─ Apply transformations (filter, map, validate)
  ├─ Stage 3: JsonMapper (interface)
  │   └─ Serialize/deserialize to JSON
  └─ Stage 4: ResultMapper (interface)
      └─ Map step result back to context
```

#### Refactoring Steps
1. **Create pipeline interfaces**
   - ContextExtractor: `Object extract(WorkflowContext) throws Exception`
   - ContextTransformer: `Object transform(Object input) throws Exception`
   - JsonMapper: `String toJson(Object obj)` / `Object fromJson(String json, Class<?>)`
   - ResultMapper: `void mapResult(WorkflowContext ctx, Object result) throws Exception`

2. **Create WorkflowContextPipeline class**
   - Builder pattern for composing stages
   - Method: `process(WorkflowContext input) → WorkflowContext output`
   - Handles errors and retries at pipeline level
   - ~60 LOC

3. **Migrate implementations** to use pipeline
   - WorkflowContextExtractor → register as ContextExtractor
   - WorkflowContextTransformer → register as ContextTransformer
   - ContextPayloadMapper → register as JsonMapper
   - WorkflowResultMapper → register as ResultMapper

4. **Create convenience builders**
   - `PipelineBuilder.withExtractor(...).withTransformer(...).build()`
   - Reduces boilerplate in step adapters

#### Acceptance Criteria (Area 3)
- [ ] WorkflowContextPipeline interface created with all stage interfaces
- [ ] Pipeline builder implemented
- [ ] All 4 mapping classes refactored to use pipeline
- [ ] Context extraction tests pass (100%)
- [ ] Context transformation tests pass (100%)
- [ ] Result mapping tests pass (100%)
- [ ] No behavioral changes to context handling
- [ ] Pipeline supports error handling and retries

---

### Area 4: Query Services (~360 LOC duplicated)

#### Current State
- **Files**:
  - `workflow-orchestrator-service/src/main/java/.../JdbcWorkflowQueryService.java` (~120 LOC)
  - `workflow-orchestrator-kafka-autoconfigure/src/main/java/.../KafkaWorkflowQueryService.java` (~130 LOC)
  - `workflow-orchestrator-dashboard-service/src/main/java/.../DashboardQueryService.java` (~110 LOC)

- **Duplicated Logic**:
  1. Query building: filter by status, name, date range
  2. Sorting: by creation date, update date, status
  3. Pagination: limit, offset, total count
  4. Result mapping: row/record → WorkflowExecution DTO

- **Pattern**: Each implements similar query structure
  ```
  Status Filter + Name Filter + Date Range + Sort + Paginate → List<WorkflowExecution>
  ```

- **Example Duplication**:
  ```java
  // JdbcWorkflowQueryService.query()
  String sql = "SELECT * FROM workflow WHERE 1=1";
  if (filters.getStatus() != null) sql += " AND status = ?";
  if (filters.getName() != null) sql += " AND workflow LIKE ?";
  if (filters.getCreatedFrom() != null) sql += " AND date_created >= ?";
  sql += " ORDER BY " + filters.getSortBy();
  sql += " LIMIT ? OFFSET ?";
  
  // KafkaWorkflowQueryService.query() - SAME LOGIC
  // DashboardQueryService.query() - SAME LOGIC
  ```

#### Proposed Solution
Create repository pattern with backend abstraction:

```
WorkflowRepository (NEW interface)
├─ query(WorkflowQuery) → List<WorkflowExecution>
├─ findById(id) → WorkflowExecution
├─ save(WorkflowExecution)
└─ delete(id)

Abstract QueryBuilder (NEW)
├─ addFilter(String field, Object value)
├─ addSort(String field, Direction)
├─ paginate(limit, offset)
└─ execute() → List<WorkflowExecution>

JdbcQueryBuilder extends QueryBuilder
├─ Builds SQL queries
├─ Executes via JdbcTemplate
└─ Maps ResultSet → WorkflowExecution

KafkaQueryBuilder extends QueryBuilder
├─ Queries Kafka state store
├─ Filters in-memory
└─ Maps ConsumerRecord → WorkflowExecution

WorkflowRepositoryImpl implements WorkflowRepository
├─ Delegates to QueryBuilder
├─ Common filtering/sorting logic (100+ LOC saved)
└─ Consistent pagination
```

#### Refactoring Steps
1. **Create WorkflowRepository interface** (~15 LOC)
   - Standard CRUD + query methods
   - QueryCriteria DTO for filter/sort/paginate params

2. **Create QueryBuilder abstract class** (~50 LOC)
   - Common filter/sort/paginate logic
   - Template methods: `buildQuery()`, `execute()`, `mapRow()`
   - Error handling, validation

3. **Create JdbcQueryBuilder** (~80 LOC → 40 LOC saved)
   - SQL query building
   - ResultSet mapping
   - Transaction handling

4. **Create KafkaQueryBuilder** (~90 LOC → 40 LOC saved)
   - Kafka state store queries
   - In-memory filtering/sorting
   - Record mapping

5. **Create WorkflowRepositoryImpl** (~60 LOC)
   - Delegates to QueryBuilder
   - Provides WorkflowRepository interface
   - Backend selection (JDBC vs Kafka)

6. **Update service classes**
   - JdbcWorkflowQueryService → inject WorkflowRepository
   - KafkaWorkflowQueryService → inject WorkflowRepository
   - DashboardQueryService → inject WorkflowRepository
   - Remove duplicated logic

#### Acceptance Criteria (Area 4)
- [ ] WorkflowRepository interface created
- [ ] QueryBuilder abstract class implemented
- [ ] JdbcQueryBuilder creates SQL correctly
- [ ] KafkaQueryBuilder queries state store correctly
- [ ] WorkflowRepositoryImpl provides consistent interface
- [ ] All query tests pass (100%)
- [ ] Filtering works: status, name, date range
- [ ] Sorting works: creation, update, status
- [ ] Pagination works: limit, offset, total count
- [ ] Result mapping identical before/after
- [ ] No breaking changes to query API

---

## 3. IMPLEMENTATION SCHEDULE

### Week 1: Step Adapters (1 day)
- Day 1: Create BaseWorkflowStep<T> + BaseAsyncWorkflowStep<T> + refactor 3 implementations
- Parallel: Run unit tests after each refactoring
- End of day: 3/6 adapters refactored, tests passing

### Week 1: Error Classification (0.5 day)
- Afternoon: Create strategy classes + update categorizers
- Run error classification tests
- End: All error tests passing

### Week 2: Context Mapping (0.5 day)
- Day 1 AM: Create WorkflowContextPipeline + builders
- Day 1 PM: Refactor 4 mapping classes
- Run context tests

### Week 2: Query Services (1 day)
- Day 1-2: Create QueryBuilder + implementations
- Day 2: Refactor query services
- Run query tests + integration tests

### Week 2: Final Validation (0.5 day)
- Full Maven build: `mvn clean install`
- Docker build: `docker build -f .../Dockerfile .`
- Integration test suite: All passing
- Code coverage: ≥80%

---

## 4. DETAILED IMPLEMENTATION CHECKLIST

### Step Adapters Refactoring
- [ ] Create `BaseWorkflowStep<T>` abstract class
  - [ ] Protected abstract methods: doExecute(), extractInput(), mapOutput()
  - [ ] Public execute() template method
  - [ ] Retry scheduling logic
  - [ ] Error classification integration
  - [ ] Logging/MDC support
- [ ] Create `BaseAsyncWorkflowStep<T>` extending BaseWorkflowStep
  - [ ] CompletableFuture support
  - [ ] Async callbacks
  - [ ] Async error handling
- [ ] Refactor MethodWorkflowStep
  - [ ] Extend BaseWorkflowStep
  - [ ] Implement doExecute(), extractInput(), mapOutput()
  - [ ] Remove duplicated lifecycle code
  - [ ] Run tests
- [ ] Refactor MethodAsyncWorkflowStep (same pattern)
- [ ] Refactor RestClientWorkflowStep (same pattern)
- [ ] Refactor KafkaWorkflowStep (same pattern)
- [ ] Refactor remaining adapters (if any)
- [ ] Update Spring bean configs (if needed)
- [ ] All unit tests passing

### Error Categorization Refactoring
- [ ] Create `WorkflowErrorStrategy` interface
  - [ ] canRetry(Throwable) → boolean
  - [ ] categorize(Throwable) → ErrorCategory
- [ ] Create strategy implementations
  - [ ] IoErrorStrategy
  - [ ] HttpErrorStrategy (with status code mapping)
  - [ ] TimeoutErrorStrategy
  - [ ] DefaultErrorStrategy
- [ ] Create `CompositeErrorStrategy`
  - [ ] Strategy list with order
  - [ ] First match wins
- [ ] Update DefaultWorkflowErrorCategorizer
  - [ ] Inject CompositeErrorStrategy
  - [ ] Remove duplicated logic
- [ ] Update RestOperationExceptionHandler
  - [ ] Use CompositeErrorStrategy
  - [ ] Remove duplicated logic
- [ ] All error classification tests passing
- [ ] Verify error decisions unchanged

### Context Mapping Refactoring
- [ ] Create pipeline interfaces
  - [ ] ContextExtractor
  - [ ] ContextTransformer
  - [ ] JsonMapper
  - [ ] ResultMapper
- [ ] Create `WorkflowContextPipeline` class
  - [ ] Stage registration
  - [ ] Pipeline execution with error handling
  - [ ] Builder support
- [ ] Create pipeline builder
  - [ ] Fluent API: withExtractor().withTransformer()...
  - [ ] Stage composition
- [ ] Refactor WorkflowContextExtractor
  - [ ] Implement ContextExtractor interface
  - [ ] Remove duplicate code
- [ ] Refactor WorkflowContextTransformer (same)
- [ ] Refactor ContextPayloadMapper (same)
- [ ] Refactor WorkflowResultMapper (same)
- [ ] All context tests passing

### Query Services Refactoring
- [ ] Create `WorkflowRepository` interface
  - [ ] query(WorkflowQuery)
  - [ ] findById()
  - [ ] save()
  - [ ] delete()
- [ ] Create `QueryCriteria` DTO
  - [ ] Filters: status, name, dateFrom, dateTo
  - [ ] Sort: field, direction
  - [ ] Pagination: limit, offset
- [ ] Create `QueryBuilder` abstract class
  - [ ] addFilter(), addSort(), paginate()
  - [ ] Template methods: buildQuery(), execute(), mapRow()
  - [ ] Error handling
- [ ] Create `JdbcQueryBuilder` extends QueryBuilder
  - [ ] SQL query building
  - [ ] ResultSet → WorkflowExecution mapping
  - [ ] Transaction handling
- [ ] Create `KafkaQueryBuilder` extends QueryBuilder
  - [ ] Kafka store queries
  - [ ] Record → WorkflowExecution mapping
  - [ ] In-memory filtering/sorting
- [ ] Create `WorkflowRepositoryImpl` implements WorkflowRepository
  - [ ] Backend selection (JDBC vs Kafka)
  - [ ] Delegation to QueryBuilder
- [ ] Update JdbcWorkflowQueryService
  - [ ] Inject WorkflowRepository
  - [ ] Remove duplicated logic
  - [ ] Delegate to repository
- [ ] Update KafkaWorkflowQueryService (same)
- [ ] Update DashboardQueryService (same)
- [ ] All query tests passing
- [ ] Integration tests passing

### Final Validation
- [ ] Maven clean build: `mvn clean install` ✓
- [ ] All unit tests passing (100%)
- [ ] All integration tests passing
- [ ] Docker build successful: `docker build ...` ✓
- [ ] Code coverage ≥80%
- [ ] No new warnings
- [ ] API contracts unchanged (verify with mock server)
- [ ] Behavioral verification (orchestration still works end-to-end)

---

## 5. ACCEPTANCE CRITERIA (OVERALL)

✅ **Code Reduction**
- [ ] Total LOC duplication: ~770 → <300 (~60% reduction)
- [ ] Step adapters: ~180 LOC saved (30 LOC × 6 adapters)
- [ ] Error classification: ~60 LOC saved
- [ ] Context mapping: ~150 LOC saved
- [ ] Query services: ~380 LOC saved

✅ **Code Quality**
- [ ] No duplicated logic across areas
- [ ] Consistent error handling everywhere
- [ ] Consistent context mapping everywhere
- [ ] Consistent querying everywhere
- [ ] All code follows project conventions

✅ **Testing**
- [ ] All unit tests pass (100%)
- [ ] All integration tests pass
- [ ] Code coverage maintained (≥80%)
- [ ] No behavioral changes to external APIs
- [ ] Error handling decisions identical before/after
- [ ] Query results identical before/after

✅ **Build & Deployment**
- [ ] Maven clean build succeeds
- [ ] Zero Maven warnings
- [ ] Docker build succeeds
- [ ] No new CVE vulnerabilities
- [ ] Backward compatible (no breaking changes)

✅ **Documentation**
- [ ] Javadoc added to base classes
- [ ] Template methods explained
- [ ] Strategy pattern documented
- [ ] Pipeline pattern documented
- [ ] Repository pattern documented
- [ ] README updated if needed

---

## 6. RISK MITIGATION

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Behavioral regression in adapters | Medium | High | Comprehensive unit + integration tests after each refactoring |
| Error classification changes | Low | High | Side-by-side testing (old vs new) before removing old code |
| Query result differences | Medium | High | Compare JDBC vs Kafka results before/after |
| Breaking API changes | Low | Critical | Verify external contracts via mock server |
| Performance regression | Low | Medium | Performance tests before/after key operations |
| Merge conflicts | Medium | Low | Frequent small commits + syncing with team |

---

## 7. EFFORT BREAKDOWN

| Task | Estimated | Actual | Notes |
|------|-----------|--------|-------|
| Step Adapters base classes | 2 hours | | BaseWorkflowStep + BaseAsyncWorkflowStep |
| Step Adapters refactoring | 4 hours | | 6 adapters × 40 min each |
| Step Adapters testing | 2 hours | | Unit + integration tests |
| Error Strategy creation | 1.5 hours | | Interfaces + concrete strategies |
| Error Categorization refactoring | 1.5 hours | | Update 2 categorizers |
| Error testing | 1 hour | | Verify decisions unchanged |
| Context Pipeline creation | 2 hours | | Interfaces + builder + pipeline class |
| Context Mapping refactoring | 2 hours | | Refactor 4 mapping classes |
| Context testing | 1 hour | | Verify mapping unchanged |
| Query Repository creation | 2 hours | | Interfaces + builders |
| Query Services refactoring | 3 hours | | Refactor 3 services |
| Query testing | 2 hours | | Comprehensive query testing |
| Final validation & build | 2 hours | | Maven build, Docker build, full test suite |
| **TOTAL** | **26 hours** | | ~3-4 days distributed work |

---

## 8. READY FOR APPROVAL

**Awaiting User Confirmation:**
- [ ] Plan is acceptable
- [ ] Refactoring approach aligns with project vision
- [ ] Effort estimate is realistic
- [ ] Acceptance criteria are clear and measurable
- [ ] Risk mitigation strategies sufficient

**Next Step**: User approves → Implementation begins (Week 1) → Reviewer validates → Phase 3 begins


