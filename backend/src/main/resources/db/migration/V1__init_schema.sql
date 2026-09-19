-- SchemaBridge AI - Initial Database Schema Migration

CREATE TABLE IF NOT EXISTS integration_projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_definitions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES integration_projects(id) ON DELETE CASCADE,
    system_name VARCHAR(255) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    direction VARCHAR(50) NOT NULL,
    base_url VARCHAR(500),
    authentication_type VARCHAR(50) DEFAULT 'NONE',
    schema_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS schema_definitions (
    id VARCHAR(64) PRIMARY KEY,
    system_definition_id VARCHAR(64) NOT NULL REFERENCES system_definitions(id) ON DELETE CASCADE,
    schema_name VARCHAR(255) NOT NULL,
    schema_version VARCHAR(50) NOT NULL DEFAULT '1.0',
    schema_type VARCHAR(50) NOT NULL,
    original_schema TEXT NOT NULL,
    schema_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS schema_fields (
    id VARCHAR(64) PRIMARY KEY,
    schema_definition_id VARCHAR(64) NOT NULL REFERENCES schema_definitions(id) ON DELETE CASCADE,
    field_path VARCHAR(255) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    description TEXT,
    data_type VARCHAR(50) NOT NULL,
    format VARCHAR(50),
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_array BOOLEAN NOT NULL DEFAULT FALSE,
    parent_path VARCHAR(255),
    constraints_json TEXT
);

CREATE TABLE IF NOT EXISTS mapping_definitions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES integration_projects(id) ON DELETE CASCADE,
    direction VARCHAR(50) NOT NULL DEFAULT 'SOURCE_TO_TARGET',
    version INT NOT NULL DEFAULT 1,
    source_schema_id VARCHAR(64) REFERENCES schema_definitions(id) ON DELETE SET NULL,
    target_schema_id VARCHAR(64) REFERENCES schema_definitions(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100) DEFAULT 'system',
    approved_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mapping_rules (
    id VARCHAR(64) PRIMARY KEY,
    mapping_definition_id VARCHAR(64) NOT NULL REFERENCES mapping_definitions(id) ON DELETE CASCADE,
    source_paths TEXT NOT NULL, -- JSON array of source paths
    target_path VARCHAR(255) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    parameters_json TEXT,
    match_method VARCHAR(50) NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SUGGESTED',
    explanation TEXT
);

CREATE TABLE IF NOT EXISTS synonym_entries (
    id VARCHAR(64) PRIMARY KEY,
    domain VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    canonical_term VARCHAR(255) NOT NULL,
    synonym VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS transformation_executions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES integration_projects(id) ON DELETE CASCADE,
    mapping_definition_id VARCHAR(64) REFERENCES mapping_definitions(id) ON DELETE SET NULL,
    mapping_version INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    source_payload_hash VARCHAR(64),
    validation_result TEXT,
    error_summary TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(100) NOT NULL,
    changed_by VARCHAR(100) DEFAULT 'system',
    previous_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed Standard Synonyms
INSERT INTO synonym_entries (id, domain, canonical_term, synonym, enabled) VALUES
('syn-1', 'USER_PROFILE', 'name', 'userName', TRUE),
('syn-2', 'USER_PROFILE', 'name', 'displayName', TRUE),
('syn-3', 'USER_PROFILE', 'name', 'fullName', TRUE),
('syn-4', 'USER_PROFILE', 'surname', 'lastName', TRUE),
('syn-5', 'USER_PROFILE', 'surname', 'familyName', TRUE),
('syn-6', 'USER_PROFILE', 'dob', 'dateOfBirth', TRUE),
('syn-7', 'USER_PROFILE', 'dob', 'birthDate', TRUE),
('syn-8', 'COMMUNICATION', 'email', 'emailAddress', TRUE),
('syn-9', 'COMMUNICATION', 'mobile', 'phoneNumber', TRUE),
('syn-10', 'COMMUNICATION', 'phone', 'telephone', TRUE),
('syn-11', 'ADDRESS', 'postalCode', 'zipCode', TRUE),
('syn-12', 'STATUS', 'active', 'accountEnabled', TRUE),
('syn-13', 'STATUS', 'active', 'enabled', TRUE),
('syn-14', 'METADATA', 'createdOn', 'creationDate', TRUE),
('syn-15', 'METADATA', 'createdOn', 'createdAt', TRUE),
('syn-16', 'PROJECT', 'projectId', 'project_id', TRUE),
('syn-17', 'PROJECT', 'project_code', 'externalProjectId', TRUE);
