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
  RegisteredApi,
  GatewayExecutionLog
} from './models/schema-bridge.models';
import { HOST_SYSTEM_A_SWAGGER, DESTINATION_SYSTEM_B_SWAGGER } from './samples/sample-swaggers';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  activeTab: 'projects' | 'apis' | 'gateway' | 'setup' | 'mapping' | 'preview' | 'versions' | 'audit' = 'projects';
  projects: Project[] = [];
  selectedProject: Project | null = null;
  schemas: SchemaResponse[] = [];
  sourceFields: FieldExtraction[] = [];
  targetFields: FieldExtraction[] = [];
  activeMapping: MappingDefinition | null = null;
  versions: MappingDefinition[] = [];
  auditEvents: AuditEvent[] = [];
  aviatorHealth: AviatorHealth | null = null;

  // 3-URL Live Gateway & Traffic/Error Logs State
  gatewayLogs: GatewayExecutionLog[] = [];
  selectedGatewayLog: GatewayExecutionLog | null = null;
  gatewayLogFilter: 'ALL' | 'SUCCESS' | 'WARNING' | 'ERROR' = 'ALL';
  isSimulatingGateway: boolean = false;
  isSeedingDemoProjects: boolean = false;
  copiedUrlBanner: string = '';
  gatewayUnmappedPolicy: string = 'DROP_WITH_WARNING';
  gatewayMissingPolicy: string = 'STRICT_REJECT_422';

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
  newApiSourceUrl: string = '';
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

  // Swagger/OpenAPI File Upload State
  sourceSchemaFile: File | null = null;
  targetSchemaFile: File | null = null;
  isUploadingSourceFile: boolean = false;
  isUploadingTargetFile: boolean = false;
  uploadErrorMessage: string = '';

  // Modals
  showNewProjectModal: boolean = false;
  isCreatingProject: boolean = false;
  newProjectName: string = '';
  newProjectDescription: string = '';
  newProjectSourceSystem: string = 'Source System A';
  newProjectSourceUrl: string = 'http://localhost:8080/mock/system-a';
  newProjectTargetSystem: string = 'Target System B';
  newProjectTargetUrl: string = 'http://localhost:8080/mock/system-b';
  newProjectUnmappedPolicy: string = 'DROP_WITH_WARNING';
  newProjectMissingPolicy: string = 'STRICT_REJECT_422';
  newProjectApproverEmail: string = 'baljinders@opentext.com';
  newProjectAutoApprove: boolean = true;

  // Asymmetric Schemas / Live Payload Tracing (NO Swagger support)
  sourceInputMode: 'SWAGGER' | 'TRACE' = 'SWAGGER';
  targetInputMode: 'SWAGGER' | 'TRACE' = 'SWAGGER';
  sourceTracePayload: string = '{\n  "bookName": "The Hobbit",\n  "bookYear": 1937,\n  "author": "J.R.R. Tolkien",\n  "stock": 42\n}';
  targetTracePayload: string = '{\n  "sheetName": "The Hobbit",\n  "sheetYear": "1937",\n  "writer": "J.R.R. Tolkien",\n  "available": true\n}';
  isTracingSource: boolean = false;
  isTracingTarget: boolean = false;

  // Selective 100 APIs Endpoint Picker State
  showEndpointPickerModal: boolean = false;
  parsedEndpoints: any[] = [];
  endpointSearchQuery: string = '';
  endpointPickerDirection: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE' = 'TARGET_TO_SOURCE';
  pendingRawSpec: string = '';
  isParsingEndpoints: boolean = false;
  isImportingSelectedEndpoints: boolean = false;

  // Batch Approval & Inversion State
  isApprovingAll: boolean = false;
  isApprovingHighConfidence: boolean = false;
  isInvertingMapping: boolean = false;
  approvalSuccessMessage: string = '';

  showEditRuleModal: boolean = false;
  editingRule: MappingRule | null = null;
  editingRuleId: string | null = null;
  isSavingRules: boolean = false;
  ruleSaveError: string = '';

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

  constructor(private api: ApiService, private cdr: ChangeDetectorRef) { }

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
    this.gatewayUnmappedPolicy = project.unmappedFieldPolicy || 'DROP_WITH_WARNING';
    this.gatewayMissingPolicy = project.missingMappingPolicy || 'STRICT_REJECT_422';
    this.loadProjectData(project.id);
    this.cdr.detectChanges();
  }

  loadProjectData(projectId: string): void {
    this.showEditRuleModal = false;
    this.editingRule = null;
    this.editingRuleId = null;
    this.ruleSaveError = '';
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
    this.loadGatewayLogs(projectId);
  }

  loadGatewayLogs(projectId: string): void {
    this.api.getGatewayLogs(projectId).subscribe({
      next: (logs) => {
        this.gatewayLogs = logs || [];
        if (this.gatewayLogs.length > 0 && (!this.selectedGatewayLog || this.selectedGatewayLog.projectId !== projectId)) {
          this.selectedGatewayLog = this.gatewayLogs[0];
        } else if (this.gatewayLogs.length === 0) {
          this.selectedGatewayLog = null;
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.gatewayLogs = [];
        this.selectedGatewayLog = null;
        this.cdr.detectChanges();
      }
    });
  }

  get filteredGatewayLogs(): GatewayExecutionLog[] {
    if (this.gatewayLogFilter === 'ALL') return this.gatewayLogs;
    if (this.gatewayLogFilter === 'SUCCESS') {
      return this.gatewayLogs.filter(l => l.status === 'SUCCESS');
    }
    if (this.gatewayLogFilter === 'WARNING') {
      return this.gatewayLogs.filter(l => l.status.includes('WARNING') || l.status.includes('FALLBACK'));
    }
    return this.gatewayLogs.filter(l => l.status.includes('ERROR'));
  }

  get gatewaySuccessCount(): number {
    return this.gatewayLogs.filter(l => l.status === 'SUCCESS').length;
  }

  get gatewayWarningCount(): number {
    return this.gatewayLogs.filter(l => l.status.includes('WARNING') || l.status.includes('FALLBACK')).length;
  }

  get gatewayErrorCount(): number {
    return this.gatewayLogs.filter(l => l.status.includes('ERROR')).length;
  }

  runAllGatewayScenarios(): void {
    if (!this.selectedProject || this.isSimulatingGateway) return;
    this.isSimulatingGateway = true;
    this.cdr.detectChanges();
    this.api.simulateAllGatewayCases(this.selectedProject.id).subscribe({
      next: (newLogs) => {
        this.isSimulatingGateway = false;
        this.loadGatewayLogs(this.selectedProject!.id);
        this.api.getProjectAudit(this.selectedProject!.id).subscribe({
          next: (events) => { this.auditEvents = events; this.cdr.detectChanges(); }
        });
        if (newLogs && newLogs.length > 0) {
          this.selectedGatewayLog = newLogs[0];
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.isSimulatingGateway = false;
        this.cdr.detectChanges();
      }
    });
  }

  runSingleGatewayCase(caseNum: 1 | 2 | 3 | 4 | 5 | 6): void {
    if (!this.selectedProject || this.isSimulatingGateway) return;
    this.isSimulatingGateway = true;
    this.cdr.detectChanges();

    const isSe = this.selectedProject.name.toLowerCase().includes('setocm3')
      || this.selectedProject.name.toLowerCase().includes('subscription');

    const baseWrite = isSe ? {
      sub_id: 'SUB-2026-9001',
      acct_num: 'ACCT-OTX-4410',
      customer_name: 'Global FinCorp Holdings',
      start_date: '15/11/2026',
      auto_renew: 'Yes',
      plan_code: 'ENTERPRISE_PLUS',
      seat_count: '250',
      admin: { email: 'cloud-admin@fincorp.io', phone: '+1-415-890-2211' }
    } : {
      order_num: 'ORD-2026-991',
      cust_id: 'CUST-4081',
      name: 'Dr. Sarah Connor',
      dob: '15/08/1988',
      active: 'Yes',
      tier: 'GOLD_TIER',
      contact: { email: 'sarah.c@cyberdyne.org', phone: '+1-555-0199' },
      total_amount: 499.99
    };

    const baseRead = isSe ? {
      subscriptionId: 'SUB-2026-9001',
      tenantAccountId: 'ACCT-OTX-4410',
      subscriberName: 'Global FinCorp Holdings',
      billingStartDate: '2026-11-15',
      autoRenewalEnabled: true,
      tierCode: 'ENT_PLUS',
      licensedSeats: 250,
      primaryContactEmail: 'cloud-admin@fincorp.io',
      primaryContactPhone: '+1-415-890-2211'
    } : {
      orderId: 'ORD-2026-991',
      customerId: 'CUST-4081',
      userName: 'Dr. Sarah Connor',
      dateOfBirth: '1988-08-15',
      accountEnabled: true,
      membershipLevel: 'GLD',
      emailAddress: 'sarah.c@cyberdyne.org',
      telephone: '+1-555-0199',
      totalAmount: 499.99
    };

    const writePath = isSe ? '/api/v1/subscriptions' : '/api/v1/orders';
    const readPath = isSe ? '/api/v1/subscriptions/SUB-2026-9001' : '/api/v1/orders/ORD-2026-991';
    const unmappedRoute = isSe ? '/api/v1/usage-events' : '/api/v1/refunds';

    let req: any;
    if (caseNum === 1) {
      req = {
        scenarioName: 'Case 1: Happy Path WRITE (System A -> Gateway -> System B)',
        httpMethod: 'POST',
        endpointPath: writePath,
        direction: 'SOURCE_TO_TARGET',
        payload: baseWrite,
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy
      };
    } else if (caseNum === 2) {
      req = {
        scenarioName: 'Case 2: Happy Path READ (System B -> Gateway Reverse -> System A)',
        httpMethod: 'GET',
        endpointPath: readPath,
        direction: 'TARGET_TO_SOURCE',
        payload: baseRead,
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy
      };
    } else if (caseNum === 3) {
      req = {
        scenarioName: 'Case 3: Payload with Unmapped Extra Fields (' + this.gatewayUnmappedPolicy + ')',
        httpMethod: 'POST',
        endpointPath: writePath,
        direction: 'SOURCE_TO_TARGET',
        payload: {
          ...baseWrite,
          legacy_region_code: 'EU-CENTRAL-FRA',
          internal_terminal_tag: 'TERM-882'
        },
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy
      };
    } else if (caseNum === 4) {
      req = {
        scenarioName: 'Case 4: Missing Mapping for Route (' + this.gatewayMissingPolicy + ')',
        httpMethod: 'POST',
        endpointPath: unmappedRoute,
        direction: 'SOURCE_TO_TARGET',
        payload: isSe
          ? { event_id: 'EVT-88210', sub_id: 'SUB-2026-9001', api_calls_consumed: 14500 }
          : { refund_id: 'RFD-5510', orig_order_num: 'ORD-2026-991', refund_amt: 120.00 },
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy,
        forceMissingMapping: true
      };
    } else if (caseNum === 5) {
      const badPayload: any = { ...baseWrite };
      if (isSe) {
        badPayload.start_date = '99/99/2026-INVALID';
        badPayload.seat_count = 'NOT_A_NUMBER';
      } else {
        badPayload.dob = '32/13/1988-CORRUPT';
      }
      req = {
        scenarioName: 'Case 5: Runtime Transformation Error (Invalid Date / Number Coercion)',
        httpMethod: 'POST',
        endpointPath: writePath,
        direction: 'SOURCE_TO_TARGET',
        payload: badPayload,
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy
      };
    } else {
      const badValPayload: any = { ...baseWrite };
      if (isSe) {
        badValPayload.sub_id = '';
        badValPayload.admin = { email: 'invalid-email-format', phone: '+1-415-890-2211' };
      } else {
        badValPayload.order_num = '';
        badValPayload.contact = { email: 'invalid-email-format', phone: '+1-555-0199' };
      }
      req = {
        scenarioName: 'Case 6: Target Schema Validation Failure (Missing Required ID & Invalid Email)',
        httpMethod: 'POST',
        endpointPath: writePath,
        direction: 'SOURCE_TO_TARGET',
        payload: badValPayload,
        unmappedFieldPolicy: this.gatewayUnmappedPolicy,
        missingMappingPolicy: this.gatewayMissingPolicy
      };
    }

    this.api.invokeGateway(this.selectedProject.id, req).subscribe({
      next: (logEntry) => {
        this.isSimulatingGateway = false;
        this.gatewayLogs.unshift(logEntry);
        this.selectedGatewayLog = logEntry;
        this.api.getProjectAudit(this.selectedProject!.id).subscribe({
          next: (events) => { this.auditEvents = events; this.cdr.detectChanges(); }
        });
        this.cdr.detectChanges();
      },
      error: () => {
        this.isSimulatingGateway = false;
        this.cdr.detectChanges();
      }
    });
  }

  clearGatewayHistory(): void {
    if (!this.selectedProject) return;
    this.api.clearGatewayLogs(this.selectedProject.id).subscribe({
      next: () => {
        this.gatewayLogs = [];
        this.selectedGatewayLog = null;
        this.cdr.detectChanges();
      }
    });
  }

  seedTwoSampleProjects(forceReset: boolean = true): void {
    if (this.isSeedingDemoProjects) return;
    this.isSeedingDemoProjects = true;
    this.cdr.detectChanges();
    this.api.seedAllDemoProjects(forceReset).subscribe({
      next: () => {
        this.isSeedingDemoProjects = false;
        this.selectedProject = null;
        this.loadProjects();
        this.cdr.detectChanges();
      },
      error: () => {
        this.isSeedingDemoProjects = false;
        this.cdr.detectChanges();
      }
    });
  }

  copyText(text: string, label: string = 'URL'): void {
    if (!text) return;
    navigator.clipboard?.writeText(text);
    this.copiedUrlBanner = `Copied ${label}: ${text}`;
    this.cdr.detectChanges();
    setTimeout(() => {
      this.copiedUrlBanner = '';
      this.cdr.detectChanges();
    }, 3500);
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
    this.newApiPath = '/api/v1/customers';
    this.newApiDirection = 'SOURCE_TO_TARGET';
    this.newApiSourceUrl = (this.selectedProject?.sourceSystemUrl || 'http://localhost:8080/mock/system-a') + '/api/v1/customers';
    this.newApiTargetUrl = (this.selectedProject?.targetSystemUrl || 'http://localhost:8080/mock/system-b') + '/api/v1/customers';
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
      sourceUrl: this.newApiSourceUrl,
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
        if (this.selectedProject) {
          this.loadGatewayLogs(this.selectedProject.id);
        }
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
      sourceSystemUrl: this.newProjectSourceUrl.trim(),
      targetSystemName: this.newProjectTargetSystem.trim(),
      targetSystemUrl: this.newProjectTargetUrl.trim(),
      unmappedFieldPolicy: this.newProjectUnmappedPolicy,
      missingMappingPolicy: this.newProjectMissingPolicy,
      approverEmail: this.newProjectApproverEmail.trim(),
      autoApproveEnabled: this.newProjectAutoApprove
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

  // Swagger/OpenAPI File Upload
  onSourceFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.sourceSchemaFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  onTargetFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.targetSchemaFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  uploadSourceSchemaFile(): void {
    if (!this.selectedProject || !this.sourceSchemaFile) return;
    this.isUploadingSourceFile = true;
    this.uploadErrorMessage = '';
    this.cdr.detectChanges();

    this.api.uploadSourceSchema(this.selectedProject.id, this.sourceSchemaFile).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.sourceSchemaType = srcResp.schemaType;
        this.isUploadingSourceFile = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isUploadingSourceFile = false;
        this.uploadErrorMessage = 'Failed to upload Source Swagger/Schema file: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  uploadTargetSchemaFile(): void {
    if (!this.selectedProject || !this.targetSchemaFile) return;
    this.isUploadingTargetFile = true;
    this.uploadErrorMessage = '';
    this.cdr.detectChanges();

    this.api.uploadTargetSchema(this.selectedProject.id, this.targetSchemaFile).subscribe({
      next: (tgtResp) => {
        this.targetFields = tgtResp.fields;
        this.targetSchemaType = tgtResp.schemaType;
        this.isUploadingTargetFile = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isUploadingTargetFile = false;
        this.uploadErrorMessage = 'Failed to upload Target Swagger/Schema file: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Uploads both Swagger/OpenAPI documents (if selected) and lets Aviator auto-generate mapping suggestions
  uploadBothAndAutoMap(): void {
    if (!this.selectedProject || !this.sourceSchemaFile || !this.targetSchemaFile) {
      this.uploadErrorMessage = 'Please select both a source and a target Swagger/OpenAPI file before auto-mapping.';
      this.cdr.detectChanges();
      return;
    }
    this.isUploadingSourceFile = true;
    this.isUploadingTargetFile = true;
    this.uploadErrorMessage = '';
    this.cdr.detectChanges();

    this.api.uploadSourceSchema(this.selectedProject.id, this.sourceSchemaFile).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.sourceSchemaType = srcResp.schemaType;
        this.isUploadingSourceFile = false;

        this.api.uploadTargetSchema(this.selectedProject!.id, this.targetSchemaFile!).subscribe({
          next: (tgtResp) => {
            this.targetFields = tgtResp.fields;
            this.targetSchemaType = tgtResp.schemaType;
            this.isUploadingTargetFile = false;

            // Both schemas are in place - let Aviator figure out the field correspondences automatically
            this.activeTab = 'mapping';
            this.cdr.detectChanges();
            this.generateMappings();
          },
          error: (err) => {
            this.isUploadingTargetFile = false;
            this.uploadErrorMessage = 'Target file upload failed: ' + (err.error?.message || err.message);
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isUploadingSourceFile = false;
        this.isUploadingTargetFile = false;
        this.uploadErrorMessage = 'Source file upload failed: ' + (err.error?.message || err.message);
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

  // Load Host Swagger (System A) sample into Source Editor
  loadHostSwaggerSample(): void {
    this.sourceSchemaType = 'OPENAPI';
    this.sourceSchemaInput = HOST_SYSTEM_A_SWAGGER;
    this.setupErrorMessage = '';
    this.uploadErrorMessage = '';
    if (this.selectedProject) {
      this.importSource();
    }
  }

  // Load Destination Swagger (System B) sample into Target Editor
  loadDestinationSwaggerSample(): void {
    this.targetSchemaType = 'OPENAPI';
    this.targetSchemaInput = DESTINATION_SYSTEM_B_SWAGGER;
    this.setupErrorMessage = '';
    this.uploadErrorMessage = '';
    if (this.selectedProject) {
      this.importTarget();
    }
  }

  // Load both Swagger samples and extract fields for demo
  loadBothSwaggerSamples(): void {
    if (!this.selectedProject) return;
    this.sourceSchemaType = 'OPENAPI';
    this.sourceSchemaInput = HOST_SYSTEM_A_SWAGGER;
    this.targetSchemaType = 'OPENAPI';
    this.targetSchemaInput = DESTINATION_SYSTEM_B_SWAGGER;
    this.isImportingSchemas = true;
    this.setupErrorMessage = '';
    this.uploadErrorMessage = '';
    this.cdr.detectChanges();

    this.api.importSourceSchema(this.selectedProject.id, {
      schemaType: this.sourceSchemaType,
      schemaContent: this.sourceSchemaInput,
      schemaName: 'System_A_Host_Swagger'
    }).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.api.importTargetSchema(this.selectedProject!.id, {
          schemaType: this.targetSchemaType,
          schemaContent: this.targetSchemaInput,
          schemaName: 'System_B_Destination_Swagger'
        }).subscribe({
          next: (tgtResp) => {
            this.targetFields = tgtResp.fields;
            this.isImportingSchemas = false;
            this.loadProjectData(this.selectedProject!.id);
            this.cdr.detectChanges();
          },
          error: (err) => {
            this.isImportingSchemas = false;
            this.setupErrorMessage = 'Failed to import Destination Swagger: ' + (err.error?.message || err.message);
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Failed to import Host Swagger: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Load both Swagger samples and immediately trigger Aviator auto-mapping
  loadBothSwaggersAndAutoMap(): void {
    if (!this.selectedProject) return;
    this.sourceSchemaType = 'OPENAPI';
    this.sourceSchemaInput = HOST_SYSTEM_A_SWAGGER;
    this.targetSchemaType = 'OPENAPI';
    this.targetSchemaInput = DESTINATION_SYSTEM_B_SWAGGER;
    this.isImportingSchemas = true;
    this.setupErrorMessage = '';
    this.uploadErrorMessage = '';
    this.cdr.detectChanges();

    this.api.importSourceSchema(this.selectedProject.id, {
      schemaType: this.sourceSchemaType,
      schemaContent: this.sourceSchemaInput,
      schemaName: 'System_A_Host_Swagger'
    }).subscribe({
      next: (srcResp) => {
        this.sourceFields = srcResp.fields;
        this.api.importTargetSchema(this.selectedProject!.id, {
          schemaType: this.targetSchemaType,
          schemaContent: this.targetSchemaInput,
          schemaName: 'System_B_Destination_Swagger'
        }).subscribe({
          next: (tgtResp) => {
            this.targetFields = tgtResp.fields;
            this.isImportingSchemas = false;
            this.activeTab = 'mapping';
            this.cdr.detectChanges();
            this.generateMappings();
          },
          error: (err) => {
            this.isImportingSchemas = false;
            this.setupErrorMessage = 'Failed to import Destination Swagger: ' + (err.error?.message || err.message);
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isImportingSchemas = false;
        this.setupErrorMessage = 'Failed to import Host Swagger: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // Helper to download sample swagger files directly from browser
  downloadSwaggerFile(type: 'host' | 'destination'): void {
    const filename = type === 'host' ? 'host-system-a-swagger.json' : 'destination-system-b-swagger.json';
    const content = type === 'host' ? HOST_SYSTEM_A_SWAGGER : DESTINATION_SYSTEM_B_SWAGGER;
    const blob = new Blob([content], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
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
    if (!this.selectedProject || this.isSavingRules || this.isConvertingNl) return;
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
    if (!this.selectedProject || !this.nlInstruction.trim() || this.isConvertingNl || this.isSavingRules) return;
    const projectId = this.selectedProject.id;
    this.isConvertingNl = true;
    this.ruleSaveError = '';
    this.cdr.detectChanges();
    this.api.convertNaturalLanguage(projectId, this.nlInstruction).subscribe({
      next: (rule) => {
        if (this.selectedProject?.id !== projectId) {
          this.isConvertingNl = false;
          this.cdr.detectChanges();
          return;
        }
        // Automatically persist rule into project's active mapping
        this.api.addCustomRule(projectId, rule).subscribe({
          next: (updatedMapping) => {
            this.isConvertingNl = false;
            if (this.selectedProject?.id !== projectId) {
              this.cdr.detectChanges();
              return;
            }
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
          error: (err) => {
            this.isConvertingNl = false;
            if (this.selectedProject?.id === projectId) {
              this.ruleSaveError = err.error?.message || 'The suggested rule could not be saved. Your instruction has been retained.';
            }
            this.cdr.detectChanges();
          }
        });
      },
      error: (err) => {
        this.isConvertingNl = false;
        if (this.selectedProject?.id === projectId) {
          this.ruleSaveError = err.error?.message || 'Natural language rule generation failed. Please try again.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  // Custom Mapping Rule Modal Operations
  openCustomRuleModal(): void {
    if (this.isSavingRules || this.isConvertingNl) return;
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
    if (!this.selectedProject || !this.customRuleTargetPath.trim() || this.isSavingRules || this.isConvertingNl) return;

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
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl || this.activeMapping.status === 'PUBLISHED') return;
    this.api.approveMapping(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.cdr.detectChanges();
      }
    });
  }

  publishMapping(): void {
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl || this.activeMapping.status !== 'APPROVED') return;
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
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl) return;
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
    if (!this.selectedProject || this.isSavingRules) return;
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
  openEditRule(rule: MappingRule): void {
    if (!rule.id || this.isSavingRules || this.isConvertingNl || this.activeMapping?.status === 'PUBLISHED') return;
    this.editingRule = structuredClone(rule);
    this.editingRuleId = rule.id;
    this.ruleSaveError = '';
    this.showEditRuleModal = true;
    this.cdr.detectChanges();
  }

  saveEditedRule(): void {
    if (!this.editingRule || !this.activeMapping || !this.editingRuleId) return;
    if (!this.activeMapping.rules.some(rule => rule.id === this.editingRuleId)) return;
    const editedRule = structuredClone(this.editingRule);
    const rules = this.activeMapping.rules.map(rule => rule.id === this.editingRuleId ? editedRule : rule);
    this.persistRules(rules, true);
  }

  deleteRule(rule: MappingRule): void {
    if (!rule.id || !this.activeMapping || !this.activeMapping.rules.some(savedRule => savedRule.id === rule.id)) return;
    this.persistRules(this.activeMapping.rules.filter(savedRule => savedRule.id !== rule.id));
  }

  private persistRules(rules: MappingRule[], closeEditor: boolean = false): void {
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl) return;
    if (this.activeMapping.status === 'PUBLISHED') {
      this.ruleSaveError = 'Published mapping versions are read-only. Clone this version before editing.';
      this.cdr.detectChanges();
      return;
    }
    const projectId = this.selectedProject.id;
    const mappingId = this.activeMapping.id;
    this.isSavingRules = true;
    this.ruleSaveError = '';
    this.cdr.detectChanges();
    this.api.updateRules(projectId, mappingId, rules).subscribe({
      next: (mapping) => {
        this.isSavingRules = false;
        if (this.selectedProject?.id === projectId && this.activeMapping?.id === mappingId) {
          this.activeMapping = mapping;
          this.versions = this.versions.map(version => version.id === mappingId ? mapping : version);
          this.previewResult = null;
          this.transformedPayloadText = '';
          if (closeEditor) {
            this.showEditRuleModal = false;
            this.editingRule = null;
            this.editingRuleId = null;
          }
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isSavingRules = false;
        if (this.selectedProject?.id === projectId && this.activeMapping?.id === mappingId) {
          this.ruleSaveError = err.error?.message || 'Mapping changes could not be saved. Please try again.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  // Asymmetric Schema: Trace live JSON/cURL payload for system with NO Swagger
  traceSourcePayload(): void {
    if (!this.selectedProject || !this.sourceTracePayload.trim()) return;
    this.isTracingSource = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    this.api.tracePayload(this.selectedProject.id, {
      payload: this.sourceTracePayload,
      direction: 'SOURCE_TO_TARGET',
      systemName: this.selectedProject.sourceSystemName || 'System A'
    }).subscribe({
      next: (res) => {
        this.sourceFields = res.fields;
        this.sourceSchemaType = res.schemaType;
        this.sourceSchemaInput = this.sourceTracePayload;
        this.isTracingSource = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isTracingSource = false;
        this.setupErrorMessage = 'Failed to trace Source payload: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  traceTargetPayload(): void {
    if (!this.selectedProject || !this.targetTracePayload.trim()) return;
    this.isTracingTarget = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    this.api.tracePayload(this.selectedProject.id, {
      payload: this.targetTracePayload,
      direction: 'TARGET_TO_SOURCE',
      systemName: this.selectedProject.targetSystemName || 'System B'
    }).subscribe({
      next: (res) => {
        this.targetFields = res.fields;
        this.targetSchemaType = res.schemaType;
        this.targetSchemaInput = this.targetTracePayload;
        this.isTracingTarget = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isTracingTarget = false;
        this.setupErrorMessage = 'Failed to trace Target payload: ' + (err.error?.message || err.message);
        this.cdr.detectChanges();
      }
    });
  }

  // 100 APIs Selective Endpoint Picker
  openEndpointPicker(direction: 'SOURCE_TO_TARGET' | 'TARGET_TO_SOURCE'): void {
    if (!this.selectedProject) return;
    this.endpointPickerDirection = direction;
    this.endpointSearchQuery = '';
    this.isParsingEndpoints = true;
    this.setupErrorMessage = '';
    this.cdr.detectChanges();

    const specContent = direction === 'SOURCE_TO_TARGET' ? this.sourceSchemaInput : this.targetSchemaInput;
    const file = direction === 'SOURCE_TO_TARGET' ? this.sourceSchemaFile : this.targetSchemaFile;

    if (file) {
      this.api.parseEndpointsFromFile(this.selectedProject.id, file).subscribe({
        next: (endpoints) => {
          this.parsedEndpoints = endpoints.map(ep => ({ ...ep, selected: true }));
          this.isParsingEndpoints = false;
          this.showEndpointPickerModal = true;
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.isParsingEndpoints = false;
          this.setupErrorMessage = 'Failed to parse endpoints from file: ' + (err.error?.message || err.message);
          this.cdr.detectChanges();
        }
      });
    } else if (specContent && specContent.trim()) {
      this.pendingRawSpec = specContent;
      this.api.parseEndpointsFromRaw(this.selectedProject.id, specContent).subscribe({
        next: (endpoints) => {
          this.parsedEndpoints = endpoints.map(ep => ({ ...ep, selected: true }));
          this.isParsingEndpoints = false;
          this.showEndpointPickerModal = true;
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.isParsingEndpoints = false;
          this.setupErrorMessage = 'Failed to parse endpoints: ' + (err.error?.message || err.message);
          this.cdr.detectChanges();
        }
      });
    } else {
      this.isParsingEndpoints = false;
      this.setupErrorMessage = 'Please paste a Swagger/OpenAPI specification or upload a file first.';
      this.cdr.detectChanges();
    }
  }

  toggleSelectAllEndpoints(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.parsedEndpoints.forEach(ep => ep.selected = checked);
    this.cdr.detectChanges();
  }

  getFilteredEndpoints(): any[] {
    if (!this.endpointSearchQuery.trim()) {
      return this.parsedEndpoints;
    }
    const q = this.endpointSearchQuery.toLowerCase();
    return this.parsedEndpoints.filter(ep =>
      (ep.path && ep.path.toLowerCase().includes(q)) ||
      (ep.httpMethod && ep.httpMethod.toLowerCase().includes(q)) ||
      (ep.summary && ep.summary.toLowerCase().includes(q)) ||
      (ep.tag && ep.tag.toLowerCase().includes(q))
    );
  }

  getSelectedEndpointsCount(): number {
    return this.parsedEndpoints.filter(ep => ep.selected).length;
  }

  areAllEndpointsSelected(): boolean {
    return this.parsedEndpoints.length > 0 && this.parsedEndpoints.every(ep => ep.selected);
  }

  confirmEndpointSelection(): void {
    if (!this.selectedProject) return;
    const selectedPaths = this.parsedEndpoints.filter(ep => ep.selected).map(ep => ep.path);
    if (selectedPaths.length === 0) {
      alert('Please select at least one endpoint to import.');
      return;
    }

    const direction = this.endpointPickerDirection;
    const systemName = (direction === 'SOURCE_TO_TARGET' ? this.selectedProject.sourceSystemName : this.selectedProject.targetSystemName) || (direction === 'SOURCE_TO_TARGET' ? 'System A' : 'System B');
    const rawSpec = this.pendingRawSpec || (direction === 'SOURCE_TO_TARGET' ? this.sourceSchemaInput : this.targetSchemaInput);

    this.isImportingSelectedEndpoints = true;
    this.cdr.detectChanges();

    this.api.importSelectedEndpoints(this.selectedProject.id, {
      direction,
      systemName,
      rawSpecContent: rawSpec,
      selectedEndpointPaths: selectedPaths
    }).subscribe({
      next: (res) => {
        if (direction === 'SOURCE_TO_TARGET') {
          this.sourceFields = res.fields;
        } else {
          this.targetFields = res.fields;
        }
        this.isImportingSelectedEndpoints = false;
        this.showEndpointPickerModal = false;
        this.loadProjectData(this.selectedProject!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isImportingSelectedEndpoints = false;
        alert('Failed to import selected endpoints: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // Batch Approval Operations
  approveAllMappings(): void {
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl || this.activeMapping.status === 'PUBLISHED') return;
    this.isApprovingAll = true;
    this.approvalSuccessMessage = '';
    this.cdr.detectChanges();

    this.api.approveAllMappings(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.isApprovingAll = false;
        this.approvalSuccessMessage = `All ${m.rules.length} rules approved successfully!`;
        this.cdr.detectChanges();
        setTimeout(() => { this.approvalSuccessMessage = ''; this.cdr.detectChanges(); }, 4000);
      },
      error: (err) => {
        this.isApprovingAll = false;
        alert('Failed to approve all rules: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  approveAllHighConfidence(): void {
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl || this.activeMapping.status === 'PUBLISHED') return;
    this.isApprovingHighConfidence = true;
    this.approvalSuccessMessage = '';
    this.cdr.detectChanges();

    this.api.approveAllHighConfidence(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (m) => {
        this.activeMapping = m;
        this.isApprovingHighConfidence = false;
        const approvedCount = m.rules.filter(r => r.status === 'APPROVED').length;
        const pendingCount = m.rules.filter(r => r.status !== 'APPROVED').length;
        this.approvalSuccessMessage = `Approved high-confidence rules (${approvedCount} total approved). ${pendingCount} ambiguous rules remain for manual review.`;
        this.cdr.detectChanges();
        setTimeout(() => { this.approvalSuccessMessage = ''; this.cdr.detectChanges(); }, 5000);
      },
      error: (err) => {
        this.isApprovingHighConfidence = false;
        alert('Failed to approve high-confidence rules: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // Invert Mapping (Bi-directional inversion)
  invertCurrentMapping(): void {
    if (!this.selectedProject || !this.activeMapping || this.isSavingRules || this.isConvertingNl) return;
    if (!confirm(`Generate inverse bidirectional mapping for ${this.activeMapping.rules.length} rules? This creates a new mapping reversing target -> source.`)) {
      return;
    }
    this.isInvertingMapping = true;
    this.cdr.detectChanges();

    this.api.invertMapping(this.selectedProject.id, this.activeMapping.id).subscribe({
      next: (inverted) => {
        this.isInvertingMapping = false;
        this.activeMapping = inverted;
        this.versions.unshift(inverted);
        this.approvalSuccessMessage = `Inverted mapping v${inverted.version} created successfully! Direction is now reversed.`;
        this.cdr.detectChanges();
        setTimeout(() => { this.approvalSuccessMessage = ''; this.cdr.detectChanges(); }, 5000);
      },
      error: (err) => {
        this.isInvertingMapping = false;
        alert('Failed to invert mapping: ' + (err.error?.message || err.message));
        this.cdr.detectChanges();
      }
    });
  }
}

