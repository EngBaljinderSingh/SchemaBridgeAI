-- SchemaBridge AI - Multi-API Registry for Integration Apps
CREATE TABLE IF NOT EXISTS registered_apis (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES integration_projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    http_method VARCHAR(20) NOT NULL DEFAULT 'POST',
    endpoint_path VARCHAR(500) NOT NULL,
    direction VARCHAR(50) NOT NULL DEFAULT 'SOURCE_TO_TARGET',
    target_url VARCHAR(500),
    source_schema TEXT,
    target_schema TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
