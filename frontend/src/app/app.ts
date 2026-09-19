import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from './services/api.service';
import {
  Project,
  SchemaResponse,
  FieldExtraction,
  MappingDefinition,
  MappingRule,
  TransformationPreviewResponse,
  AuditEvent,
  AviatorHealth,
  RegisteredApi
} from './models/schema-bridge.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  activeTab: 'projects' | 'apis' | 'setup' | 'mapping' | 'preview' | 'versions' | 'audit' = 'projects';
  projects: Project[] = [];
  selectedProject: Project | null = null;
  schemas: SchemaResponse[] = [];
  sourceFields: FieldExtraction[] = [];
  targetFields: FieldExtraction[] = [];
  activeMapping: MappingDefinition | null = null;
  versions: MappingDefinition[] = [];
  auditEvents: AuditEvent[] = [];
  aviatorHealth: AviatorHealth | null = null;

  // Multi-API Registry State
  registeredApis: RegisteredApi[] = [];
  selectedApi: RegisteredApi | null = null;
  showNewApiModal: boolean = false;
  showApiDetailsModal: boolean = false;
  showApiTestModal: boolean = false;

  newApiName: string = '';
  newApiDescription: string = '';
  newApiMethod: string = 'POST';
  newApiPath: string = '/api/v1/';
  newApiDirection: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE' = 'SOURCE_TO_TARGET';
  newApiTargetUrl: string = '';
  newApiSourceSchema: string = '';
  newApiTargetSchema: string = '';

  apiTestPayload: string = '';
  apiTestResult: any = null;
  isTestingApi: boolean = false;

  // Filter State
  ruleFilter: 'ALL' | 'MAPPED' | 'UNMAPPED' | 'REVIEW' = 'ALL';

  // Natural Language State
  nlInstruction: string = '';
  isConvertingNl: boolean = false;

  // Transformation Lab State
  sourcePayloadText: string = `{\n  "name": "Baljinder Singh",\n  "dob": "10/05/1992",\n  "active": "Yes",\n  "project_id": 1001,\n  "contact": {\n    "email": "user@example.com"\n  }\n}`;
  transformedPayloadText: string = '';
  previewResult: TransformationPreviewResponse | null = null;
  isTransforming: boolean = false;

  // Setup Screen State
  sourceSchemaInput: string = '';
  targetSchemaInput: string = '';
  sourceSchemaType: string = 'SAMPLE_JSON';
  targetSchemaType: string = 'SAMPLE_JSON';

  // Modals
  showNewProjectModal: boolean = false;
  isCreatingProject: boolean = false;
  newProjectName: string = '';
  newProjectDescription: string = '';
  newProjectSourceSystem: string = 'Source System A';
  newProjectTargetSystem: string = 'Target System B';

  showEditRuleModal: boolean = false;
  editingRule: MappingRule | null = null;
  editingRuleIndex: number = -1;

  // Custom Mapping Rule State
  showCustomRuleModal: boolean = false;
  customRuleSourcePaths: string = '';
  customRuleTargetPath: string = '';
  customRuleOperation: string = 'RENAME';
  customRuleParameterKey: string = '';
  customRuleParameterValue: string = '';
  customRuleExplanation: string = '';

  // Architecture & Gateway Guide Modal
  showArchitectureGuide: boolean = false;

  // Supported Operations list for dropdown
  supportedOperations: string[] = [
    'RENAME', 'STRING_TO_NUMBER', 'NUMBER_TO_STRING', 'STRING_TO_BOOLEAN',
    'BOOLEAN_TO_STRING', 'DATE_FORMAT', 'ENUM_MAP', 'DEFAULT_VALUE',
    'CONSTANT_VALUE', 'COPY', 'CONCAT', 'SPLIT', 'FLATTEN', 'NEST',
    'ARRAY_MAP', 'CONDITIONAL', 'REMOVE', 'TRIM', 'UPPERCASE', 'LOWERCASE'
  ];

  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadProjects();
    this.refreshAviatorHealth();
  }

  loadProjects(): void {
    this.api.getProjects().subscribe({
      next: (data) => {
        this.projects = data;
        if (data.length > 0 && !this.selectedProject) {
          this.selectProject(data[0]);
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed loading projects', err);
        this.cdr.detectChanges();
      }
    });
  }

  selectProject(project: Project): void {
    this.selectedProject = project;
    this.loadProjectData(project.id);
    this.cdr.detectChanges();
  }

  loadProjectData(projectId: string): void {
    // Reset schema inputs and fields so stale project data or OIDs never linger
    this.sourceSchemaInput = '';
    this.targetSchemaInput = '';
    this.sourceFields = [];
    this.targetFields = [];
    this.schemas = [];
    this.activeMapping = null;
    this.registeredApis = [];

    this.api.getSchemas(projectId).subscribe({
      next: (schemas) => {
        this.schemas = schemas;
        this.sourceFields = [];
        this.targetFields = [];
        for (const s of schemas) {
          // If originalSchema is a numeric OID string from previous Postgres Large Object storage, ignore it
          const cleanSchema = s.originalSchema && /^\d+$/.test(s.originalSchema.trim()) ? '' : (s.originalSchema || '');
          if (s.direction === 'SOURCE_TO_TARGET') {
            this.sourceFields = s.fields || [];
            this.sourceSchemaInput = cleanSchema;
          } else {
            this.targetFields = s.fields || [];
            this.targetSchemaInput = cleanSchema;
          }
        }
        this.cdr.detectChanges();

        // If no project-level schemas found, try to auto-import from API Registry
        if (schemas.length === 0) {
          this.autoImportSchemasFromApis(projectId);
        }
      },
      error: () => this.cdr.detectChanges()
    });

    this.api.getMappings(projectId).subscribe({
      next: (mappings) => {
        this.versions = mappings;
        if (mappings.length > 0) {
          // Always pick the mapping with the highest version number
          const sorted = [...mappings].sort((a, b) => (b.version || 0) - (a.version || 0));
          this.activeMapping = sorted[0];
        } else {
          this.activeMapping = null;
        }
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });

    this.api.getProjectAudit(projectId).subscribe({
      next: (events) => {
        this.auditEvents = events;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });

    this.loadApis(projectId);
  }

  // Auto-import schemas from the first API that has both source + target schemas configured
  autoImportSchemasFromApis(projectId: string): void {
    this.api.getApis(projectId).subscribe({
      next: (apis) => {
        const apiWithSchemas = apis.find((a: any) => a.sourceSchema?.trim() && a.targetSchema?.trim());
        if (!apiWithSchemas) return;

        const sourceJson = apiWithSchemas.sourceSchema;
        const targetJson = apiWithSchemas.targetSchema;
        const direction = apiWithSchemas.direction || 'SOURCE_TO_TARGET';

        this.sourceSchemaInput = sourceJson;
        this.targetSchemaInput = targetJson;
        this.sourceSchemaType = 'SAMPLE_JSON';
        this.targetSchemaType = 'SAMPLE_JSON';
        this.isImportingSchemas = true;
        this.cdr.detectChanges();

        this.api.importSourceSchema(projectId, {
          schemaType: 'SAMPLE_JSON',
          schemaContent: sourceJson,
          systemName: direction === 'SOURCE_TO_TARGET' ? 'A' : 'B'
        }).subscribe({
          next: (srcResp) => {
            this.sourceFields = srcResp.fields || [];
            this.api.importTargetSchema(projectId, {
              schemaType: 'SAMPLE_JSON',
              schemaContent: targetJson,
              systemName: direction === 'SOURCE_TO_TARGET' ? 'B' : 'A'
            }).subscribe({
              next: (tgtResp) => {
                this.targetFields = tgtResp.fields || [];
                this.isImportingSchemas = false;
                this.cdr.detectChanges();
                this.generateMappings();
              },
              error: () => { this.isImportingSchemas = false; this.cdr.detectChanges(); }
            });
          },
          error: () => { this.isImportingSchemas = false; this.cdr.detectChanges(); }
        });
      }
    });
  }

  // Multi-API Registry Operations
  loadApis(projectId: string): void {
    this.api.getApis(projectId).subscribe({
      next: (apis) => {
        this.registeredApis = apis;
        this.cdr.detectChanges();
      },
      error: () => {
        this.registeredApis = [];
        this.cdr.detectChanges();
      }
    });
  }

  openRegisterApiModal(): void {
    this.newApiName = '';
    this.newApiDescription = '';
    this.newApiMethod = 'POST';
    this.newApiPath = '/api/v1/';
    this.newApiDirection = 'SOURCE_TO_TARGET';
    this.newApiTargetUrl = '';
    // Pre-fill schemas from Setup tab if available (e.g. loaded via "Load Sample"), otherwise use defaults
    this.newApiSourceSchema = this.sourceSchemaInput?.trim()
      ? this.sourceSchemaInput
      : '{\n  "cust_id": "CUST-100",\n  "name": "Alex Mercer"\n}';
    this.newApiTargetSchema = this.targetSchemaInput?.trim()
      ? this.targetSchemaInput
      : '{\n  "customerId": "string",\n  "userName": "string"\n}';
    this.showNewApiModal = true;
    this.cdr.detectChanges();
  }

  createApi(): void {
    if (!this.selectedProject || !this.newApiName.trim() || !this.newApiPath.trim()) return;
    const sourceJson = this.newApiSourceSchema;
    const targetJson = this.newApiTargetSchema;
    const direction = this.newApiDirection;
    this.api.createApi(this.selectedProject.id, {
      name: this.newApiName.trim(),
      description: this.newApiDescription.trim(),
      httpMethod: this.newApiMethod,
      endpointPath: this.newApiPath.trim(),
      direction,
      targetUrl: this.newApiTargetUrl,
      sourceSchema: sourceJson,
      targetSchema: targetJson
    }).subscribe({
      next: (api) => {
        this.showNewApiModal = false;
        this.registeredApis.push(api);
        this.selectedApi = api;
        this.cdr.detectChanges();

        // Auto-import API schemas into project-level schema store so Mapping Workspace is ready
        if (sourceJson?.trim() && targetJson?.trim() && this.selectedProject) {
          this.sourceSchemaInput = sourceJson;
          this.targetSchemaInput = targetJson;
          this.sourceSchemaType = 'SAMPLE_JSON';
          this.targetSchemaType = 'SAMPLE_JSON';
          this.isImportingSchemas = true;
          this.setupErrorMessage = '';
          this.cdr.detectChanges();

          this.api.importSourceSchema(this.selectedProject.id, {
            schemaType: 'SAMPLE_JSON',
            schemaContent: sourceJson,
            systemName: direction === 'SOURCE_TO_TARGET' ? 'A' : 'B'
          }).subscribe({
            next: (srcResp) => {
              this.sourceFields = srcResp.fields || [];
              this.api.importTargetSchema(this.selectedProject!.id, {
                schemaType: 'SAMPLE_JSON',
                schemaContent: targetJson,
                systemName: direction === 'SOURCE_TO_TARGET' ? 'B' : 'A'
              }).subscribe({
                next: (tgtResp) => {
                  this.targetFields = tgtResp.fields || [];
                  this.isImportingSchemas = false;
                  this.cdr.detectChanges();
                  // Auto-generate mapping rules from the newly imported schemas
                  this.generateMappings();
                },
                error: () => { this.isImportingSchemas = false; this.cdr.detectChanges(); }
              });
            },
            error: () => { this.isImportingSchemas = false; this.cdr.detectChanges(); }
          });
        }
      },
      error: () => this.cdr.detectChanges()
    });
  }

  selectApi(api: RegisteredApi): void {
    this.selectedApi = { ...api };
    this.showApiDetailsModal = true;
    this.cdr.detectChanges();
  }

  saveApiDetails(): void {
    if (!this.selectedProject || !this.selectedApi) return;
    this.api.updateApi(this.selectedProject.id, this.selectedApi.id, {
      name: this.selectedApi.name,
      description: this.selectedApi.description,
      httpMethod: this.selectedApi.httpMethod,
      endpointPath: this.selectedApi.endpointPath,
      direction: this.selectedApi.direction,
      targetUrl: this.selectedApi.targetUrl,
      sourceSchema: this.selectedApi.sourceSchema,
      targetSchema: this.selectedApi.targetSchema,
      status: this.selectedApi.status
    }).subscribe({
      next: (updated) => {
        this.showApiDetailsModal = false;
        const idx = this.registeredApis.findIndex(a => a.id === updated.id);
        if (idx !== -1) this.registeredApis[idx] = updated;
        this.selectedApi = updated;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });
  }

  deleteApi(apiId: string): void {
    if (!this.selectedProject) return;
    this.api.deleteApi(this.selectedProject.id, apiId).subscribe({
      next: () => {
        this.registeredApis = this.registeredApis.filter(a => a.id !== apiId);
        if (this.selectedApi?.id === apiId) this.selectedApi = null;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });
  }

  openApiTest(api: RegisteredApi): void {
    this.selectedApi = api;
    this.apiTestPayload = api.sourceSchema || '{\n  "order_num": "ORD-991",\n  "name": "Dr. Connor"\n}';
    this.apiTestResult = null;
    this.showApiTestModal = true;
    this.cdr.detectChanges();
  }

  executeApiTest(): void {
    if (!this.selectedProject || !this.selectedApi) return;
    this.isTestingApi = true;
    this.cdr.detectChanges();
    let payload = {};
    try {
      payload = JSON.parse(this.apiTestPayload);
    } catch {
      payload = { raw: this.apiTestPayload };
    }
    this.api.executeApiTransform(this.selectedProject.id, this.selectedApi.id, payload).subscribe({
      next: (res) => {
        this.isTestingApi = false;
        this.apiTestResult = res;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isTestingApi = false;
        this.apiTestResult = { error: err.error?.message || err.message };
        this.cdr.detectChanges();
      }
    });
  }

  loadSampleApisForApp(): void {
    if (!this.selectedProject) return;
    const sample1 = {
      name: 'Create Customer Account',
      description: 'System A (SE) creates customer in System B (CCP). Translates legacy flat schema to cloud enterprise model.',
      httpMethod: 'POST',
      endpointPath: '/api/v1/customers',
      direction: 'SOURCE_TO_TARGET' as const,
      targetUrl: 'https://ccp.enterprise.internal/v1/customers',
      sourceSchema: JSON.stringify({
        cust_id: "CUST-8812",
        name: "Dr. Sarah Connor",
        dob: "15/08/1988",
        active: "Yes",
        tier: "GOLD_TIER",
        contact: { email: "sarah.c@cyberdyne.org", phone: "+1-555-0199" }
      }, null, 2),
      targetSchema: JSON.stringify({
        customerId: "string",
        userName: "string",
        dateOfBirth: "yyyy-MM-dd",
        accountEnabled: "boolean",
        membershipLevel: "string",
        emailAddress: "string",
        telephone: "string"
      }, null, 2)
    };

    const sample2 = {
      name: 'Get Customer Details by ID',
      description: 'System A (SE) queries customer from System B (CCP). Translates modern cloud record back to legacy format.',
      httpMethod: 'GET',
      endpointPath: '/api/v1/customers/{id}',
      direction: 'TARGET_TO_SOURCE' as const,
      targetUrl: 'https://ccp.enterprise.internal/v1/customers/{id}',
      sourceSchema: JSON.stringify({
        customerId: "CUST-8812",
        userName: "Dr. Sarah Connor",
        dateOfBirth: "1988-08-15",
        accountEnabled: true,
        membershipLevel: "GLD",
        emailAddress: "sarah.c@cyberdyne.org",
        telephone: "+1-555-0199"
      }, null, 2),
      targetSchema: JSON.stringify({
        cust_id: "string",
        name: "string",
        dob: "dd/MM/yyyy",
        active: "string",
        tier: "string",
        contact: "object"
      }, null, 2)
    };

    this.api.createApi(this.selectedProject.id, sample1).subscribe({
      next: (a1) => {
        this.registeredApis.push(a1);
        this.api.createApi(this.selectedProject!.id, sample2).subscribe({
          next: (a2) => {
            this.registeredApis.push(a2);
            this.cdr.detectChanges();
          }
        });
        this.cdr.detectChanges();
      }
    });
  }

  refreshAviatorHealth(): void {
    this.api.getAviatorHealth().subscribe({
      next: (h) => {
        this.aviatorHealth = h;
        this.cdr.detectChanges();
      },
      error: () => {
        this.aviatorHealth = {
          status: 'DEGRADED',
          provider: 'OpenText Aviator ADT',
          model: 'gemini-2.5-flash-lite',
          location: 'europe-west4',
          latencyMs: 14,
          message: 'Standalone heuristic fallback active.'
        };
        this.cdr.detectChanges();
      }
    });
  }

  // Project Creation
  createProject(): void {
    if (!this.newProjectName.trim() || this.isCreatingProject) return;
    this.isCreatingProject = true;
    this.cdr.detectChanges();

    this.api.createProject({
      name: this.newProjectName.trim(),
      description: this.newProjectDescription.trim(),
      sourceSystemName: this.newProjectSourceSystem.trim(),
      targetSystemName: this.newProjectTargetSystem.trim()
    }).subscribe({
      next: (project) => {
        this.isCreatingProject = false;
        this.showNewProjectModal = false;
        this.newProjectName = '';
        this.newProjectDescription = '';
        this.projects.unshift(project);
        this.selectProject(project);
        this.activeTab = 'apis';
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isCreatingProject = false;
        console.error('Failed to create project', err);
        this.cdr.detectChanges();
      }
    });
  }

  deleteProject(project: Project, event?: Event): void {
    if (event) event.stopPropagation();
    const confirmed = confirm(`Delete integration app "${project.name}"?\nAll associated schemas, mapping versions, and registered APIs will be permanently deleted.`);
    if (!confirmed) return;

    this.api.deleteProject(project.id).subscribe({
      next: () => {
        this.projects = this.projects.filter(p => p.id !== project.id);
        if (this.selectedProject?.id === project.id) {
          this.selectedProject = this.projects.length > 0 ? this.projects[0] : null;
          if (this.selectedProject) {
            this.loadProjectData(this.selectedProject.id);
          } else {
            this.schemas = [];
            this.sourceFields = [];
            this.targetFields = [];
            this.activeMapping = null;
            this.versions = [];
            this.registeredApis = [];
          }
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert('Failed to delete app: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // Setup & Schema Import State
  isImportingSchemas: boolean = false;
  setupErrorMessage: string = '';

  // Schema Import
  importSource(): void {
    if (!this.selectedProject || !this.sourceSchemaInput.trim()) return;
    this.isImportingSchemas = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    this.api.importSourceSchema(this.selectedProject.id, {
      schemaType: this.sourceSchemaType,
      schemaContent: this.sourceSchemaInput
    }).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.isImportingSchemas = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Failed to import Source Schema: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  importTarget(): void {
    if (!this.selectedProject || !this.targetSchemaInput.trim()) return;
    this.isImportingSchemas = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    this.api.importTargetSchema(this.selectedProject.id, {
      schemaType: this.targetSchemaType,
      schemaContent: this.targetSchemaInput
    }).subscribe({
      next: (tgtResp) => {
        this.targetFields = tgtResp.fields;
        this.isImportingSchemas = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Failed to import Target Schema: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Pre-fill Sample Schema and automatically extract fields into database
  loadPromptSampleData(): void {
    if (!this.selectedProject) return;
    this.sourceSchemaInput = `{\n  "name": "Baljinder Singh",\n  "dob": "10/05/1992",\n  "active": "Yes",\n  "project_id": 1001,\n  "contact": {\n    "email": "user@example.com"\n  }\n}`;
    this.targetSchemaInput = `{\n  "userName": "string",\n  "dateOfBirth": "yyyy-MM-dd",\n  "accountEnabled": "boolean",\n  "projectId": "string",\n  "emailAddress": "string"\n}`;
    this.isImportingSchemas = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    this.api.importSourceSchema(this.selectedProject.id, {
      schemaType: this.sourceSchemaType,
      schemaContent: this.sourceSchemaInput
    }).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.api.importTargetSchema(this.selectedProject!.id, {
          schemaType: this.targetSchemaType,
          schemaContent: this.targetSchemaInput
        }).subscribe({
          next: (tgtResp) => {
            this.targetFields = tgtResp.fields;
            this.isImportingSchemas = false;
            this.loadProjectData(this.selectedProject!.id);
            this.cdr.detectChanges();
          },
          error: (err) => {
            this.isImportingSchemas = false;
            this.setupErrorMessage = 'Failed to import Target Schema: ' + (err.error?.message || err.message);
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Failed to import Source Schema: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Proceed to Mapping: Automatically ensures both schemas are saved before generating mappings
  proceedToMapping(): void {
    if (!this.selectedProject) return;
    this.setupErrorMessage = '';

    if (!this.sourceSchemaInput.trim() || !this.targetSchemaInput.trim()) {
      this.setupErrorMessage = 'Please provide both Source Schema (System A) and Target Schema (System B) before proceeding.';
      this.cdr.detectChanges();
      return;
    }

    this.isImportingSchemas = true;
    this.cdr.detectChanges();

    // Step 1: Save/Import Source Schema
    this.api.importSourceSchema(this.selectedProject.id, {
      schemaType: this.sourceSchemaType,
      schemaContent: this.sourceSchemaInput
    }).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;

        // Step 2: Save/Import Target Schema
        this.api.importTargetSchema(this.selectedProject!.id, {
          schemaType: this.targetSchemaType,
          schemaContent: this.targetSchemaInput
        }).subscribe({
          next: (tgtResp) => {
            this.targetFields = tgtResp.fields;
            this.isImportingSchemas = false;

            // Step 3: Now both schemas exist in the database! Switch tab & generate mappings
            this.activeTab = 'mapping';
            this.cdr.detectChanges();
            this.generateMappings();
          },
          error: (err) => {
            this.isImportingSchemas = false;
            this.setupErrorMessage = 'Target schema import failed: ' + (err.error?.message || err.message);
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Source schema import failed: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Generate Mappings
  generateMappings(): void {
    if (!this.selectedProject) return;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();
    this.api.generateMappings(this.selectedProject.id).subscribe({
      next: (mapping) => {
        this.activeMapping = mapping;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.setupErrorMessage = err.error?.message || 'Failed to generate mapping suggestions.';
        this.cdr.detectChanges();
      }
    });
  }

  // Natural Language Rule Conversion
  convertNaturalLanguage(): void {
    if (!this.selectedProject || !this.nlInstruction.trim()) return;
    this.isConvertingNl = true;
    this.cdr.detectChanges();
    this.api.convertNaturalLanguage(this.selectedProject.id, this.nlInstruction).subscribe({
      next: (rule) => {
        // Automatically persist rule into project's active mapping
        this.api.addCustomRule(this.selectedProject!.id, rule).subscribe({
          next: (updatedMapping) => {
            this.isConvertingNl = false;
            // Directly set activeMapping without calling loadProjectData (which resets it to null first)
            this.activeMapping = updatedMapping;
            // Also update the versions list entry if it exists
            if (this.versions) {
              const idx = this.versions.findIndex(v => v.id === updatedMapping.id);
              if (idx >= 0) { this.versions[idx] = updatedMapping; }
              else { this.versions.unshift(updatedMapping); }
            }
            this.ruleFilter = 'ALL';
            this.nlInstruction = '';
            this.cdr.detectChanges();
          },
          error: () => {
            this.isConvertingNl = false;
            if (this.activeMapping) {
              this.activeMapping.rules.unshift(rule);
            }
            this.ruleFilter = 'ALL';
            this.nlInstruction = '';
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isConvertingNl = false;
        alert('Natural language rule generation failed: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // Custom Mapping Rule Modal Operations
  openCustomRuleModal(): void {
    this.customRuleSourcePaths = this.sourceFields.length > 0 ? this.sourceFields[0].fieldPath : '';
    this.customRuleTargetPath = this.targetFields.length > 0 ? this.targetFields[0].fieldPath : '';
    this.customRuleOperation = 'RENAME';
    this.customRuleParameterKey = '';
    this.customRuleParameterValue = '';
    this.customRuleExplanation = '';
    this.showCustomRuleModal = true;
    this.cdr.detectChanges();
  }

  saveCustomRule(): void {
    if (!this.selectedProject || !this.customRuleTargetPath.trim()) return;

    const sources = this.customRuleSourcePaths.split(',').map(s => s.trim()).filter(s => s.length > 0);
    const params: Record<string, any> = {};
    if (this.customRuleParameterKey && this.customRuleParameterValue) {
      params[this.customRuleParameterKey] = this.customRuleParameterValue;
    } else if (this.customRuleParameterValue) {
      if (this.customRuleOperation === 'CONCAT' || this.customRuleOperation === 'SPLIT') {
        params['separator'] = this.customRuleParameterValue;
      } else if (this.customRuleOperation === 'DATE_FORMAT') {
        params['targetFormat'] = this.customRuleParameterValue;
      } else if (this.customRuleOperation === 'DEFAULT_VALUE' || this.customRuleOperation === 'CONSTANT_VALUE') {
        params['value'] = this.customRuleParameterValue;
      }
    }

    const rulePayload = {
      sourcePaths: sources.length > 0 ? sources : ['*'],
      targetPath: this.customRuleTargetPath.trim(),
      operation: this.customRuleOperation,
      parameters: params,
      explanation: this.customRuleExplanation || `Manual ${this.customRuleOperation} rule for ${this.customRuleTargetPath}`,
      matchMethod: 'MANUAL',
      confidence: 1.0,
      confidenceLevel: 'HIGH',
      status: 'APPROVED',
      requiresReview: false
    };

    this.api.addCustomRule(this.selectedProject.id, rulePayload).subscribe({
      next: (mapping) => {
        // Directly set activeMapping without calling loadProjectData (which resets it to null first)
        this.activeMapping = mapping;
        if (this.versions) {
          const idx = this.versions.findIndex(v => v.id === mapping.id);
          if (idx >= 0) { this.versions[idx] = mapping; }
          else { this.versions.unshift(mapping); }
        }
        this.ruleFilter = 'ALL';
        this.showCustomRuleModal = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert('Failed to save custom rule: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // Approve & Publish
  approveMapping(): void {
    if (!this.selectedProject || !this.activeMapping) return;
    this.api.approveMapping(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.cdr.detectChanges();
      }
    });
  }

  publishMapping(): void {
    if (!this.selectedProject || !this.activeMapping) return;
    this.api.publishMapping(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
        alert('Mapping Version ' + m.version + ' published successfully!');
      },
      error: (err) => alert(err.error?.message || 'Publication failed')
    });
  }

  cloneMapping(): void {
    if (!this.selectedProject || !this.activeMapping) return;
    this.api.cloneMapping(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      }
    });
  }

  // Transformation Preview
  runTransformation(): void {
    if (!this.selectedProject) return;
    this.isTransforming = true;
    this.cdr.detectChanges();
    let payloadObj: any;
    try {
      payloadObj = JSON.parse(this.sourcePayloadText);
    } catch (e) {
      alert('Invalid JSON source payload');
      this.isTransforming = false;
      this.cdr.detectChanges();
      return;
    }

    this.api.previewTransformation(this.selectedProject.id, {
      mappingDefinitionId: this.activeMapping?.id,
      sourcePayload: payloadObj
    }).subscribe({
      next: (res) => {
        this.previewResult = res;
        this.transformedPayloadText = JSON.stringify(res.transformedPayload, null, 2);
        this.isTransforming = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert(err.error?.message || 'Transformation failed');
        this.isTransforming = false;
        this.cdr.detectChanges();
      }
    });
  }

  // Filtered Rules
  get filteredRules(): MappingRule[] {
    if (!this.activeMapping || !this.activeMapping.rules) return [];
    if (this.ruleFilter === 'REVIEW') {
      return this.activeMapping.rules.filter(r => r.requiresReview);
    }
    return this.activeMapping.rules;
  }

  // Edit Rule
  openEditRule(rule: MappingRule, index: number): void {
    this.editingRule = { ...rule };
    this.editingRuleIndex = index;
    this.showEditRuleModal = true;
    this.cdr.detectChanges();
  }

  saveEditedRule(): void {
    if (this.editingRule && this.activeMapping && this.editingRuleIndex >= 0) {
      this.activeMapping.rules[this.editingRuleIndex] = this.editingRule;
      this.showEditRuleModal = false;
      this.cdr.detectChanges();
    }
  }

  deleteRule(index: number): void {
    if (this.activeMapping && this.activeMapping.rules) {
      this.activeMapping.rules.splice(index, 1);
      this.cdr.detectChanges();
    }
  }
}
