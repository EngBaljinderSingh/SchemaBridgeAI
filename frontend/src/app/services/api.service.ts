import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Project,
  SchemaResponse,
  MappingDefinition,
  MappingRule,
  TransformationPreviewResponse,
  TransformationExecuteResponse,
  ValidationResult,
  AuditEvent,
  AviatorHealth
} from '../models/schema-bridge.models';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  // Uses relative /api path or local dev 8080 fallback
  private readonly baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // Project Endpoints
  getProjects(): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.baseUrl}/projects`);
  }

  getProject(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/projects/${id}`);
  }

  createProject(data: { name: string; description?: string; sourceSystemName?: string; targetSystemName?: string }): Observable<Project> {
    return this.http.post<Project>(`${this.baseUrl}/projects`, data);
  }

  updateProject(id: string, data: { name: string; description?: string }): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/projects/${id}`, data);
  }

  deleteProject(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${id}`);
  }

  addCustomRule(projectId: string, rule: any): Observable<MappingDefinition> {
    return this.http.post<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/custom-rule`, rule);
  }

  // Schema Endpoints
  importSourceSchema(projectId: string, data: { schemaType: string; schemaContent: string; schemaName?: string; systemName?: string }): Observable<SchemaResponse> {
    return this.http.post<SchemaResponse>(`${this.baseUrl}/projects/${projectId}/schemas/source`, { ...data, direction: 'SOURCE_TO_TARGET' });
  }

  importTargetSchema(projectId: string, data: { schemaType: string; schemaContent: string; schemaName?: string; systemName?: string }): Observable<SchemaResponse> {
    return this.http.post<SchemaResponse>(`${this.baseUrl}/projects/${projectId}/schemas/target`, { ...data, direction: 'TARGET_TO_SOURCE' });
  }

  getSchemas(projectId: string): Observable<SchemaResponse[]> {
    return this.http.get<SchemaResponse[]>(`${this.baseUrl}/projects/${projectId}/schemas`);
  }

  // Mapping Endpoints
  generateMappings(projectId: string, direction: string = 'SOURCE_TO_TARGET'): Observable<MappingDefinition> {
    return this.http.post<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/generate?direction=${direction}`, {});
  }

  getMappings(projectId: string): Observable<MappingDefinition[]> {
    return this.http.get<MappingDefinition[]>(`${this.baseUrl}/projects/${projectId}/mappings`);
  }

  getMapping(projectId: string, mappingId: string): Observable<MappingDefinition> {
    return this.http.get<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/${mappingId}`);
  }

  updateRules(projectId: string, mappingId: string, rules: any[]): Observable<MappingDefinition> {
    return this.http.put<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/${mappingId}/rules`, rules);
  }

  approveMapping(projectId: string, mappingId: string, approvedBy: string = 'admin'): Observable<MappingDefinition> {
    return this.http.post<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/${mappingId}/approve?approvedBy=${approvedBy}`, {});
  }

  publishMapping(projectId: string, mappingId: string, publishedBy: string = 'admin'): Observable<MappingDefinition> {
    return this.http.post<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/${mappingId}/publish?publishedBy=${publishedBy}`, {});
  }

  cloneMapping(projectId: string, mappingId: string): Observable<MappingDefinition> {
    return this.http.post<MappingDefinition>(`${this.baseUrl}/projects/${projectId}/mappings/${mappingId}/clone`, {});
  }

  convertNaturalLanguage(projectId: string, instruction: string): Observable<MappingRule> {
    return this.http.post<MappingRule>(`${this.baseUrl}/projects/${projectId}/mappings/natural-language`, { instruction });
  }

  // Transformation & Validation Endpoints
  previewTransformation(projectId: string, data: { mappingDefinitionId?: string; adhocRules?: MappingRule[]; sourcePayload: any }): Observable<TransformationPreviewResponse> {
    return this.http.post<TransformationPreviewResponse>(`${this.baseUrl}/projects/${projectId}/transform/preview`, data);
  }

  executeTransformation(projectId: string, data: { mappingVersion?: number; sourcePayload: any; validateTarget?: boolean }): Observable<TransformationExecuteResponse> {
    return this.http.post<TransformationExecuteResponse>(`${this.baseUrl}/projects/${projectId}/transform/execute`, data);
  }

  validatePayload(projectId: string, data: { schemaDefinitionId?: string; payload: any }): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.baseUrl}/projects/${projectId}/validate`, data);
  }

  // Schema Change Analysis
  analyzeSchemaChange(projectId: string, data: { systemDefinitionId: string; newSchemaContent: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/projects/${projectId}/schema-change/analyse`, data);
  }

  // Audit Endpoints
  getProjectAudit(projectId: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.baseUrl}/projects/${projectId}/audit`);
  }

  getAllAudit(): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.baseUrl}/audit`);
  }

  // Aviator ADT Health
  getAviatorHealth(): Observable<AviatorHealth> {
    return this.http.get<AviatorHealth>(`${this.baseUrl}/aviator/health`);
  }

  // Multi-API Registry Endpoints
  getApis(projectId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/projects/${projectId}/apis`);
  }

  getApiById(projectId: string, apiId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/projects/${projectId}/apis/${apiId}`);
  }

  createApi(projectId: string, data: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/projects/${projectId}/apis`, data);
  }

  updateApi(projectId: string, apiId: string, data: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/projects/${projectId}/apis/${apiId}`, data);
  }

  deleteApi(projectId: string, apiId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${projectId}/apis/${apiId}`);
  }

  generateApiMappings(projectId: string, apiId: string): Observable<any[]> {
    return this.http.post<any[]>(`${this.baseUrl}/projects/${projectId}/apis/${apiId}/mappings/generate`, {});
  }

  executeApiTransform(projectId: string, apiId: string, payload: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/projects/${projectId}/apis/${apiId}/transform`, payload);
  }
}
