package com.github.orcas.orchestrator.core.api;

import com.github.orcas.orchestrator.core.model.WorkflowContext;

/**
 * Pipeline pattern for workflow context processing.
 *
 * This utility is intentionally lightweight and works with the existing
 * {@link WorkflowContext} record used throughout the engine.
 */
public class WorkflowContextPipeline {

  public interface ContextExtractor {
    Object extract(WorkflowContext context) throws Exception;
  }

  public interface ContextTransformer {
    Object transform(Object input) throws Exception;
  }

  public interface JsonMapper {
    String toJson(Object obj) throws Exception;
    Object fromJson(String json, Class<?> type) throws Exception;
  }

  public interface ResultMapper {
    void mapResult(WorkflowContext context, Object result) throws Exception;
  }

  private final ContextExtractor extractor;
  private final ContextTransformer transformer;
  private final JsonMapper jsonMapper;
  private final ResultMapper resultMapper;

  public WorkflowContextPipeline(
      ContextExtractor extractor,
      ContextTransformer transformer,
      JsonMapper jsonMapper,
      ResultMapper resultMapper) {
    this.extractor = extractor;
    this.transformer = transformer;
    this.jsonMapper = jsonMapper;
    this.resultMapper = resultMapper;
  }

  /** Execute pipeline: Extract → Transform → Map Result. */
  public void process(WorkflowContext context, Object result) throws Exception {
    // Extract
    Object extracted = extractor != null ? extractor.extract(context) : null;

    // Transform
    Object transformed = transformer != null && extracted != null
        ? transformer.transform(extracted)
        : extracted;

    // Map Result
    if (resultMapper != null) {
      resultMapper.mapResult(context, result);
    }
  }

  /** Fluent builder for pipeline composition. */
  public static class Builder {
    private ContextExtractor extractor;
    private ContextTransformer transformer;
    private JsonMapper jsonMapper;
    private ResultMapper resultMapper;

    public Builder withExtractor(ContextExtractor extractor) {
      this.extractor = extractor;
      return this;
    }

    public Builder withTransformer(ContextTransformer transformer) {
      this.transformer = transformer;
      return this;
    }

    public Builder withJsonMapper(JsonMapper jsonMapper) {
      this.jsonMapper = jsonMapper;
      return this;
    }

    public Builder withResultMapper(ResultMapper resultMapper) {
      this.resultMapper = resultMapper;
      return this;
    }

    public WorkflowContextPipeline build() {
      return new WorkflowContextPipeline(extractor, transformer, jsonMapper, resultMapper);
    }
  }

  /** Factory: Create builder for fluent API. */
  public static Builder builder() {
    return new Builder();
  }

}


