-- SchemaBridge AI - 3-URL Gateway Architecture & Execution/Error Telemetry Logs

ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS approver_email VARCHAR(150) DEFAULT 'baljinders@opentext.com';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS auto_approve_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS source_system_name VARCHAR(255) DEFAULT 'Source System A';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS source_system_url VARCHAR(500) DEFAULT 'http://localhost:8080/mock/system-a';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS target_system_name VARCHAR(255) DEFAULT 'Target System B';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS target_system_url VARCHAR(500) DEFAULT 'http://localhost:8080/mock/system-b';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS unmapped_field_policy VARCHAR(50) DEFAULT 'DROP_WITH_WARNING';
ALTER TABLE integration_projects ADD COLUMN IF NOT EXISTS missing_mapping_policy VARCHAR(50) DEFAULT 'STRICT_REJECT_422';

ALTER TABLE registered_apis ADD COLUMN IF NOT EXISTS source_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS gateway_execution_logs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES integration_projects(id) ON DELETE CASCADE,
    project_name VARCHAR(255),
    api_id VARCHAR(64),
    api_name VARCHAR(255),
    correlation_id VARCHAR(64) NOT NULL,
    scenario_name VARCHAR(255),
    direction VARCHAR(50) NOT NULL,
    http_method VARCHAR(20) NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    source_url VARCHAR(500),
    gateway_url VARCHAR(500),
    target_url VARCHAR(500),
    mapping_version INT,
    status VARCHAR(64) NOT NULL,
    http_status_code INT NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    request_payload TEXT,
    transformed_payload TEXT,
    downstream_response TEXT,
    applied_rules_json TEXT,
    unmapped_fields_json TEXT,
    error_message TEXT,
    resolution_hint TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
