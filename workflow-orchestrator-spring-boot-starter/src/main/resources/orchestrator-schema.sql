CREATE TABLE IF NOT EXISTS workflow (
    pipeline_id VARCHAR(36) PRIMARY KEY,
    workflow VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    date_created TIMESTAMPTZ NOT NULL,
    date_updated TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_workflow_name ON workflow(workflow);
CREATE INDEX IF NOT EXISTS idx_workflow_status ON workflow(status);
CREATE INDEX IF NOT EXISTS idx_workflow_updated ON workflow(date_updated);

CREATE TABLE IF NOT EXISTS workflow_step (
    pipeline_id VARCHAR(36) NOT NULL,
    workflow VARCHAR(255) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type_class_name VARCHAR(1000),
    state VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    date_started TIMESTAMPTZ,
    date_ended TIMESTAMPTZ,
    date_updated TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pipeline_id, step_name),
    CONSTRAINT fk_workflow_step_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);
-- Idempotent for databases created before retry tracking was added (CREATE TABLE
-- IF NOT EXISTS above is a no-op once the table already exists).
ALTER TABLE workflow_step ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_workflow_step_state ON workflow_step(state);
CREATE INDEX IF NOT EXISTS idx_workflow_step_name_state ON workflow_step(step_name, state);
CREATE INDEX IF NOT EXISTS idx_workflow_step_pipeline ON workflow_step(pipeline_id);

CREATE TABLE IF NOT EXISTS workflow_context (
    pipeline_id VARCHAR(36) PRIMARY KEY,
    context_json TEXT NOT NULL,
    context_class_name VARCHAR(1000),
    date_created TIMESTAMPTZ NOT NULL,
    date_updated TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_context_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_metadata (
    pipeline_id VARCHAR(36) PRIMARY KEY,
    metadata_json TEXT NOT NULL,
    metadata_class_name VARCHAR(1000) NOT NULL,
    date_created TIMESTAMPTZ NOT NULL,
    date_updated TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_metadata_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_log (
    id BIGSERIAL PRIMARY KEY,
    pipeline_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    snapshot_json TEXT,
    date_created TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_log_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_log_pipeline_date ON workflow_log(pipeline_id, date_created);

CREATE TABLE IF NOT EXISTS workflow_step_log (
    id BIGSERIAL PRIMARY KEY,
    pipeline_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    snapshot_json TEXT,
    date_created TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_step_log_step FOREIGN KEY (pipeline_id, step_name)
        REFERENCES workflow_step(pipeline_id, step_name) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_step_log_pipeline_date ON workflow_step_log(pipeline_id, date_created);
CREATE INDEX IF NOT EXISTS idx_workflow_step_log_step ON workflow_step_log(pipeline_id, step_name);

CREATE TABLE IF NOT EXISTS workflow_context_log (
    id BIGSERIAL PRIMARY KEY,
    pipeline_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    snapshot_json TEXT,
    date_created TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_context_log_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_context_log_pipeline_date ON workflow_context_log(pipeline_id, date_created);

CREATE TABLE IF NOT EXISTS workflow_metadata_log (
    id BIGSERIAL PRIMARY KEY,
    pipeline_id VARCHAR(36) NOT NULL,
    action VARCHAR(64) NOT NULL,
    snapshot_json TEXT,
    date_created TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_metadata_log_workflow FOREIGN KEY (pipeline_id) REFERENCES workflow(pipeline_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_metadata_log_pipeline_date ON workflow_metadata_log(pipeline_id, date_created);

CREATE TABLE IF NOT EXISTS workflow_step_context (
    pipeline_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    parent_step_name VARCHAR(255),
    input_json TEXT,
    output_json TEXT,
    attributes_json TEXT NOT NULL,
    date_updated TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pipeline_id, step_name),
    CONSTRAINT fk_workflow_step_context_step FOREIGN KEY (pipeline_id, step_name)
        REFERENCES workflow_step(pipeline_id, step_name) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_step_context_parent
    ON workflow_step_context(pipeline_id, parent_step_name);

CREATE TABLE IF NOT EXISTS workflow_step_context_log (
    id BIGSERIAL PRIMARY KEY,
    pipeline_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    snapshot_json TEXT,
    date_created TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_step_context_log_step FOREIGN KEY (pipeline_id, step_name)
        REFERENCES workflow_step(pipeline_id, step_name) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_workflow_step_context_log_step
    ON workflow_step_context_log(pipeline_id, step_name, date_created);

