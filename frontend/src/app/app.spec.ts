import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { MappingDefinition, MappingRule, Project } from './models/schema-bridge.models';
import { HOST_SYSTEM_A_SWAGGER, DESTINATION_SYSTEM_B_SWAGGER } from './samples/sample-swaggers';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('uses same-origin API endpoints', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/projects').flush([]);
    http.expectOne('/api/aviator/health').flush({ status: 'UP' });
    http.verify();
  });

  it('should load Host Swagger sample into sourceSchemaInput', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.loadHostSwaggerSample();
    expect(app.sourceSchemaType).toBe('OPENAPI');
    expect(app.sourceSchemaInput).toBe(HOST_SYSTEM_A_SWAGGER);
    expect(app.sourceSchemaInput).toContain('LegacyOrderPayload');
    expect(app.sourceSchemaInput).toContain('order_num');
  });

  it('should load Destination Swagger sample into targetSchemaInput', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.loadDestinationSwaggerSample();
    expect(app.targetSchemaType).toBe('OPENAPI');
    expect(app.targetSchemaInput).toBe(DESTINATION_SYSTEM_B_SWAGGER);
    expect(app.targetSchemaInput).toContain('CloudErpOrderRecord');
    expect(app.targetSchemaInput).toContain('orderId');
  });

  describe('mapping rule persistence', () => {
    const firstRule: MappingRule = {
      id: 'rule-name', sourcePaths: ['name'], targetPath: 'userName',
      operation: 'RENAME', parameters: {}, matchMethod: 'EXACT',
      confidence: 1, confidenceLevel: 'HIGH', status: 'APPROVED',
      explanation: 'Keep the name', requiresReview: false
    };
    const reviewRule: MappingRule = {
      ...firstRule, id: 'rule-email', sourcePaths: ['email'], targetPath: 'emailAddress',
      status: 'REVIEW_REQUIRED', explanation: 'Review the email', requiresReview: true
    };

    function createWorkspace() {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      const http = TestBed.inject(HttpTestingController);
      app.selectedProject = {
        id: 'project-1', name: 'Directory', description: '', status: 'ACTIVE',
        createdBy: 'tester', createdAt: '', updatedAt: '', systemCount: 2,
        mappingVersionCount: 1
      } satisfies Project;
      app.activeMapping = {
        id: 'mapping-1', projectId: 'project-1', direction: 'SOURCE_TO_TARGET',
        version: 1, sourceSchemaId: 'source-1', targetSchemaId: 'target-1',
        status: 'DRAFT', createdBy: 'tester', createdAt: '',
        rules: [structuredClone(firstRule), structuredClone(reviewRule)],
        unmappedSourceFields: [], unmappedTargetFields: [], warnings: []
      } satisfies MappingDefinition;
      app.versions = [app.activeMapping];
      app.activeTab = 'mapping';
      app.ruleFilter = 'REVIEW';
      fixture.detectChanges();
      http.expectOne(req => req.url.endsWith('/projects')).flush([]);
      http.expectOne(req => req.url.endsWith('/aviator/health')).flush({ status: 'UP' });
      return { fixture, app, http };
    }

    it('persists the displayed review rule without changing the first unfiltered rule', () => {
      const { fixture, app, http } = createWorkspace();
      (fixture.nativeElement.querySelector('[data-aid="btn-edit-rule-0"]') as HTMLButtonElement).click();
      app.editingRule!.explanation = 'Verified email';
      app.saveEditedRule();

      const request = http.expectOne(req => req.method === 'PUT' && req.url.endsWith('/mapping-1/rules'));
      expect(request.request.body[0].targetPath).toBe('userName');
      expect(request.request.body[0].explanation).toBe('Keep the name');
      expect(request.request.body[1].explanation).toBe('Verified email');
      expect(app.activeMapping!.rules[1].explanation).toBe('Review the email');

      const saved = { ...app.activeMapping!, rules: [firstRule, { ...reviewRule, explanation: 'Verified email' }] };
      request.flush(saved);
      expect(app.activeMapping!.rules[1].explanation).toBe('Verified email');
      expect(app.versions[0].rules[1].explanation).toBe('Verified email');
      expect(app.showEditRuleModal).toBe(false);
      http.verify();
    });

    it('persists removal of the displayed review rule only after server confirmation', () => {
      const { fixture, app, http } = createWorkspace();
      (fixture.nativeElement.querySelector('[data-aid="btn-delete-rule-0"]') as HTMLButtonElement).click();

      const request = http.expectOne(req => req.method === 'PUT' && req.url.endsWith('/mapping-1/rules'));
      expect(request.request.body.map((rule: MappingRule) => rule.targetPath)).toEqual(['userName']);
      expect(app.activeMapping!.rules).toHaveLength(2);
      request.flush({ ...app.activeMapping!, rules: [firstRule] });
      expect(app.activeMapping!.rules.map(rule => rule.targetPath)).toEqual(['userName']);
      http.verify();
    });

    it('keeps failed edits open without changing saved rules', () => {
      const { fixture, app, http } = createWorkspace();
      (fixture.nativeElement.querySelector('[data-aid="btn-edit-rule-0"]') as HTMLButtonElement).click();
      app.editingRule!.explanation = 'Unsaved explanation';
      app.saveEditedRule();

      const request = http.expectOne(req => req.method === 'PUT' && req.url.endsWith('/mapping-1/rules'));
      request.flush({ message: 'Could not save this rule' }, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();
      expect(app.showEditRuleModal).toBe(true);
      expect(app.editingRule!.explanation).toBe('Unsaved explanation');
      expect(app.activeMapping!.rules[1].explanation).toBe('Review the email');
      expect(fixture.nativeElement.querySelector('[data-aid="rule-edit-error"]').textContent).toContain('Could not save this rule');
      http.verify();
    });

    it('retains the instruction and saved rules when a generated rule cannot be persisted', () => {
      const { app, http } = createWorkspace();
      app.nlInstruction = 'Copy the email';
      app.convertNaturalLanguage();
      http.expectOne(req => req.url.endsWith('/mappings/natural-language')).flush(reviewRule);
      http.expectOne(req => req.url.endsWith('/mappings/custom-rule'))
        .flush({ message: 'Rule save failed' }, { status: 503, statusText: 'Unavailable' });

      expect(app.activeMapping!.rules).toHaveLength(2);
      expect(app.nlInstruction).toBe('Copy the email');
      expect(app.isConvertingNl).toBe(false);
      http.verify();
    });

    it('keeps saved rules when deletion fails', () => {
      const { app, http } = createWorkspace();
      app.deleteRule(reviewRule);
      http.expectOne(req => req.method === 'PUT' && req.url.endsWith('/mapping-1/rules'))
        .flush({ message: 'Deletion failed' }, { status: 503, statusText: 'Unavailable' });

      expect(app.activeMapping!.rules).toHaveLength(2);
      expect(app.ruleSaveError).toBe('Deletion failed');
      expect(app.isSavingRules).toBe(false);
      http.verify();
    });

    it('does not submit duplicate saves or allow competing mapping mutations', () => {
      const { app, http } = createWorkspace();
      app.activeMapping!.status = 'APPROVED';
      app.openEditRule(reviewRule);
      app.saveEditedRule();
      app.saveEditedRule();
      app.approveMapping();
      app.approveAllMappings();
      app.approveAllHighConfidence();
      app.publishMapping();
      app.cloneMapping();
      app.generateMappings();
      app.invertCurrentMapping();
      app.openCustomRuleModal();

      http.expectNone(req => req.method === 'POST');
      const requests = http.match(req => req.method === 'PUT');
      expect(requests).toHaveLength(1);
      expect(app.showCustomRuleModal).toBe(false);
      requests[0].flush(app.activeMapping);
      http.verify();
    });

    it('does not replace another projects mapping with an earlier save response', () => {
      const { app, http } = createWorkspace();
      app.openEditRule(reviewRule);
      app.saveEditedRule();
      const request = http.expectOne(req => req.method === 'PUT');
      const previousMapping = app.activeMapping!;
      app.selectedProject = { ...app.selectedProject!, id: 'project-2' };
      app.activeMapping = { ...previousMapping, id: 'mapping-2', projectId: 'project-2' };

      request.flush(previousMapping);

      expect(app.activeMapping.id).toBe('mapping-2');
      expect(app.isSavingRules).toBe(false);
      http.verify();
    });

    it('keeps published versions read-only and disables publication until approved', () => {
      const { fixture, app, http } = createWorkspace();
      expect((fixture.nativeElement.querySelector('[data-aid="btn-publish-mapping"]') as HTMLButtonElement).disabled).toBe(true);
      app.activeMapping!.status = 'PUBLISHED';
      app.openEditRule(reviewRule);
      app.deleteRule(reviewRule);
      app.approveAllMappings();
      app.approveAllHighConfidence();
      expect(app.showEditRuleModal).toBe(false);
      http.expectNone(req => req.method === 'POST' || req.method === 'PUT');
      http.verify();
    });
  });
});
