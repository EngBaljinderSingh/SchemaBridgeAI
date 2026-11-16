import { test, expect } from '@playwright/test';

test.describe('SchemaBridge AI End-to-End User Journeys', () => {

  test('Complete workflow: Project creation -> Schema import -> Mapping generation -> Approval -> Transformation preview -> Publishing', async ({ page }) => {
    // 1. Visit application homepage
    await page.goto('/');
    await expect(page.locator('[data-aid="brand-title"]')).toHaveText('SchemaBridge AI');
    await expect(page.locator('[data-aid="aviator-status-pill"]')).toBeVisible();

    // 2. Open Create Project Modal
    await page.locator('[data-aid="btn-create-project-main"]').click();
    await expect(page.locator('[data-aid="modal-new-project"]')).toBeVisible();

    // Fill project details
    await page.locator('[data-aid="input-project-name"]').fill('HR Directory Sync');
    await page.locator('[data-aid="input-project-desc"]').fill('E2E Automated test project');
    await page.locator('[data-aid="btn-submit-project"]').click();

    await expect(page.locator('[data-aid="modal-new-project"]')).not.toBeVisible();
    await page.locator('[data-aid="nav-tab-setup"]').click();
    await expect(page.locator('[data-aid="view-setup"]')).toBeVisible();

    // 3. Load Sample Data (Prompt User Profile Sample)
    await page.locator('[data-aid="btn-load-sample"]').click();
    await expect(page.locator('[data-aid="input-source-schema"]')).toHaveValue(/Baljinder Singh/);
    await expect(page.locator('[data-aid="input-target-schema"]')).toHaveValue(/userName/);

    // 4. Proceed to Mapping Workspace
    await page.locator('[data-aid="btn-proceed-to-mapping"]').click();
    await expect(page.locator('[data-aid="view-mapping"]')).toBeVisible();

    // Verify 3 columns rendered
    await expect(page.locator('[data-aid="col-source-fields"]')).toBeVisible();
    await expect(page.locator('[data-aid="col-mapping-rules"]')).toBeVisible();
    await expect(page.locator('[data-aid="col-target-fields"]')).toBeVisible();

    // Verify rules are populated
    await expect(page.locator('[data-aid="mapping-rule-card-0"]')).toBeVisible();

    // 6. Test Filter Review Required
    await page.locator('[data-aid="filter-review"]').click();
    await page.locator('[data-aid="filter-all"]').click();

    // 7. Edit a mapping rule
    await page.locator('[data-aid="btn-edit-rule-0"]').click();
    await expect(page.locator('[data-aid="modal-edit-rule"]')).toBeVisible();
    await page.locator('[data-aid="input-edit-explanation"]').fill('Verified by Automated E2E test');
    const saveResponse = page.waitForResponse(response => response.request().method() === 'PUT' && response.url().endsWith('/rules'));
    await page.locator('[data-aid="btn-save-edit-rule"]').click();
    const savedResponse = await saveResponse;
    expect(savedResponse.ok()).toBeTruthy();
    const savedMapping = await savedResponse.json();
    const storedResponse = await page.request.get(`/api/projects/${savedMapping.projectId}/mappings/${savedMapping.id}`);
    expect(storedResponse.ok()).toBeTruthy();
    const storedMapping = await storedResponse.json();
    expect(storedMapping.rules.some((rule: { explanation: string }) => rule.explanation === 'Verified by Automated E2E test')).toBe(true);
    await expect(page.locator('[data-aid="modal-edit-rule"]')).not.toBeVisible();

    // 8. Approve Mappings
    await page.locator('[data-aid="btn-approve-all"]').click();
    await expect(page.locator('[data-aid="btn-publish-mapping"]')).toBeEnabled();

    // 9. Navigate to Transformation Preview Lab
    await page.locator('[data-aid="nav-tab-preview"]').click();
    await expect(page.locator('[data-aid="view-transformation-lab"]')).toBeVisible();

    // Click 'Run Deterministic Transformation'
    await page.locator('[data-aid="btn-run-transformation"]').click();

    // Verify transformed output and validation status
    await expect(page.locator('[data-aid="output-transformed-payload"]')).toHaveValue(/Baljinder Singh/);
    await expect(page.locator('[data-aid="output-transformed-payload"]')).toHaveValue(/1992-05-10/);
    await expect(page.locator('[data-aid="card-validation-result"]')).toBeVisible();
    await expect(page.locator('[data-aid="card-validation-result"]')).toContainText('PASSED');

    // 10. Publish Mapping Version
    await page.locator('[data-aid="nav-tab-mapping"]').click();
    page.once('dialog', dialog => dialog.accept());
    await page.locator('[data-aid="btn-publish-mapping"]').click();

    // 11. View Versions
    await page.locator('[data-aid="nav-tab-versions"]').click();
    await expect(page.locator('[data-aid="view-versions"]')).toBeVisible();
    await expect(page.locator('[data-aid="version-card-1"]')).toBeVisible();

    // 12. View Audit Trail
    await page.locator('[data-aid="nav-tab-audit"]').click();
    await expect(page.locator('[data-aid="view-audit"]')).toBeVisible();
    await expect(page.locator('[data-aid="table-audit-trail"]')).toBeVisible();
  });

  test('Mapping APIs persist rule identities and enforce review, publication and runtime boundaries', async ({ request }) => {
    const projectResponse = await request.post('/api/projects', {
      data: { name: 'Mapping lifecycle API regression', autoApproveEnabled: false }
    });
    expect(projectResponse.status()).toBe(201);
    const project = await projectResponse.json();
    const basePath = `/api/projects/${project.id}`;
    const schema = { schemaType: 'SAMPLE_JSON', schemaContent: '{"name":"Alice","email":"alice@example.test"}' };
    expect((await request.post(`${basePath}/schemas/source`, { data: schema })).ok()).toBeTruthy();
    expect((await request.post(`${basePath}/schemas/target`, { data: schema })).ok()).toBeTruthy();
    const generatedResponse = await request.post(`${basePath}/mappings/generate`);
    expect(generatedResponse.status()).toBe(201);
    const mapping = await generatedResponse.json();
    const mappingPath = `${basePath}/mappings/${mapping.id}`;
    const sourcePayload = { name: 'Alice', email: 'alice@example.test' };

    expect((await request.post(`${mappingPath}/publish`)).status()).toBe(422);
    expect((await request.post(`${basePath}/transform/execute`, { data: { sourcePayload } })).status()).toBe(422);
    expect((await request.post(`${basePath}/transform/execute`, {
      data: { sourcePayload, mappingVersion: mapping.version }
    })).status()).toBe(422);
    expect((await request.post(`${basePath}/transform/preview`, {
      data: { sourcePayload, mappingDefinitionId: mapping.id }
    })).ok()).toBeTruthy();

    mapping.rules[0].explanation = 'Saved through HTTP';
    const savedResponse = await request.put(`${mappingPath}/rules`, { data: mapping.rules });
    expect(savedResponse.ok()).toBeTruthy();
    const reloaded = await request.get(mappingPath);
    expect(reloaded.ok()).toBeTruthy();
    const savedMapping = await reloaded.json();
    expect(savedMapping.rules.map((rule: { id: string }) => rule.id).sort())
      .toEqual(mapping.rules.map((rule: { id: string }) => rule.id).sort());
    expect(savedMapping.rules.some((rule: { explanation: string }) => rule.explanation === 'Saved through HTTP')).toBe(true);

    expect((await request.post(`${mappingPath}/approve-all-high-confidence`)).ok()).toBeTruthy();
    expect((await request.post(`${mappingPath}/approve-all`)).ok()).toBeTruthy();
    expect((await request.post(`${mappingPath}/publish`)).ok()).toBeTruthy();
    expect((await request.put(`${mappingPath}/rules`, { data: mapping.rules })).status()).toBe(422);
    expect((await request.post(`${mappingPath}/approve`)).status()).toBe(422);
    expect((await request.post(`${mappingPath}/approve-all`)).status()).toBe(422);
    expect((await request.post(`${mappingPath}/approve-all-high-confidence`)).status()).toBe(422);

    const execution = await request.post(`${basePath}/transform/execute`, { data: { sourcePayload } });
    expect(execution.ok()).toBeTruthy();
    expect((await execution.json()).transformedPayload).toEqual(sourcePayload);
  });
});
