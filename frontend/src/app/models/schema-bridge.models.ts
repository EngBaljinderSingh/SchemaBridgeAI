export interface Project {
  id: string;
  name: string;
  description: string;
  status: 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'ARCHIVED';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  approverEmail?: string;
  autoApproveEnabled?: boolean;
  sourceSystemName?: string;
  sourceSystemUrl?: string;
  gatewayUrl?: string;
  targetSystemName?: string;
  targetSystemUrl?: string;
  unmappedFieldPolicy?: string;
  missingMappingPolicy?: string;
  systemCount: number;
  mappingVersionCount: number;
  latestPublishedVersion?: number;
}

export interface ApiEndpointSummary {
  httpMethod: string;
  endpointPath: string;
  summary: string;
  operationId: string;
  schemaModelName: string;
  description: string;
  selected?: boolean;
}

export interface TracePayloadRequest {
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  systemName: string;
  endpointPath?: string;
  payload: string;
}

export interface EndpointSelectionImportRequest {
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  systemName: string;
  rawSpecContent: string;
  selectedEndpointPaths: string[];
}

export interface FieldExtraction {
  id: string;
  fieldPath: string;
  fieldName: string;
  description?: string;
  dataType: string;
  format?: string;
  required: boolean;
  array: boolean;
  parentPath?: string;
  sampleValue?: any;
  enumValues?: string[];
}

export interface SchemaResponse {
  id: string;
  systemDefinitionId: string;
  systemName: string;
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  schemaName: string;
  schemaVersion: string;
  schemaType: 'JSON_SCHEMA' | 'OPENAPI' | 'SAMPLE_JSON';
  schemaHash: string;
  originalSchema: string;
  createdAt: string;
  fields: FieldExtraction[];
}

export interface MappingRule {
  id?: string;
  sourcePaths: string[];
  targetPath: string;
  operation: string;
  parameters: Record<string, any>;
  matchMethod: 'EXACT' | 'NORMALISED' | 'SYNONYM' | 'HISTORICAL' | 'AI_SEMANTIC' | 'MANUAL';
  confidence: number;
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'DRAFT' | 'SUGGESTED' | 'REVIEW_REQUIRED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'DEPRECATED';
  explanation: string;
  requiresReview: boolean;
}

export interface MappingDefinition {
  id: string;
  projectId: string;
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  version: number;
  sourceSchemaId: string;
  targetSchemaId: string;
  status: 'DRAFT' | 'SUGGESTED' | 'REVIEW_REQUIRED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'DEPRECATED';
  createdBy: string;
  approvedBy?: string;
  createdAt: string;
  approvedAt?: string;
  rules: MappingRule[];
  unmappedSourceFields: string[];
  unmappedTargetFields: string[];
  warnings: string[];
}

export interface ValidationResult {
  valid: boolean;
  errors: Array<{ field: string; message: string; rule?: string }>;
  warnings: string[];
}

export interface TransformationPreviewResponse {
  transformedPayload: any;
  validation: ValidationResult;
  appliedRules: string[];
  mappingVersion?: number;
  executionTimeMs: number;
}

export interface TransformationExecuteResponse {
  executionId: string;
  status: string;
  mappingVersion: number;
  transformedPayload: any;
  validationResult: ValidationResult;
  appliedRules: string[];
  executionDurationMs: number;
}

export interface AuditEvent {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  changedBy: string;
  previousValue?: string;
  newValue?: string;
  createdAt: string;
}

export interface AviatorHealth {
  status: string;
  provider: string;
  model: string;
  location: string;
  latencyMs: number;
  message: string;
  details?: Record<string, any>;
}

export interface RegisteredApi {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  httpMethod: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | string;
  endpointPath: string;
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  sourceUrl?: string;
  gatewayUrl?: string;
  targetUrl?: string;
  sourceSchema?: string;
  targetSchema?: string;
  status: 'ACTIVE' | 'DRAFT' | 'DEPRECATED' | 'UNMAPPED' | string;
  createdAt: string;
  updatedAt: string;
}

export interface GatewayExecutionLog {
  id: string;
  projectId: string;
  projectName?: string;
  apiId?: string;
  apiName?: string;
  correlationId: string;
  scenarioName: string;
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE' | string;
  httpMethod: string;
  endpointPath: string;
  sourceUrl: string;
  gatewayUrl: string;
  targetUrl: string;
  mappingVersion?: number;
  status: 'SUCCESS' | 'WARNING_UNMAPPED_FIELDS' | 'ERROR_MAPPING_MISSING' | 'ERROR_TRANSFORMATION_FAILED' | 'ERROR_VALIDATION_FAILED' | 'ERROR_UNMAPPED_FIELDS' | 'FALLBACK_NO_MAPPING_PASS_THROUGH' | string;
  httpStatusCode: number;
  durationMs: number;
  requestPayload: Record<string, any>;
  transformedPayload: Record<string, any>;
  downstreamResponse: Record<string, any>;
  appliedRules: string[];
  unmappedFields: string[];
  errorMessage?: string;
  resolutionHint?: string;
  createdAt: string;
}

export interface ApiCreateRequest {
  name: string;
  description?: string;
  httpMethod: string;
  endpointPath: string;
  direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  sourceUrl?: string;
  targetUrl?: string;
  sourceSchema?: string;
  targetSchema?: string;
}

export interface ApiUpdateRequest {
  name?: string;
  description?: string;
  httpMethod?: string;
  endpointPath?: string;
  direction?: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE';
  sourceUrl?: string;
  targetUrl?: string;
  sourceSchema?: string;
  targetSchema?: string;
  status?: string;
}
