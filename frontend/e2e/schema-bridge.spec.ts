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

    // Modal should close and navigate to setup tab
    await expect(page.locator('[data-aid="view-setup"]')).toBeVisible();

    // 3. Load Sample Data (Prompt User Profile Sample)
    await page.locator('[data-aid="btn-load-sample"]').click();
    await expect(page.locator('[data-aid="input-source-schema"]')).toContainText('Baljinder Singh');
    await expect(page.locator('[data-aid="input-target-schema"]')).toContainText('userName');

    // 4. Proceed to Mapping Workspace
    await page.locator('[data-aid="btn-proceed-to-mapping"]').click();
    await expect(page.locator('[data-aid="view-mapping"]')).toBeVisible();

    // 5. Generate Suggestions (if not already triggered)
    await page.locator('[data-aid="btn-generate-mappings"]').click();

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
    await page.locator('[data-aid="btn-save-edit-rule"]').click();
    await expect(page.locator('[data-aid="modal-edit-rule"]')).not.toBeVisible();

    // 8. Approve Mappings
    await page.locator('[data-aid="btn-approve-mapping"]').click();

    // 9. Navigate to Transformation Preview Lab
    await page.locator('[data-aid="nav-tab-preview"]').click();
    await expect(page.locator('[data-aid="view-transformation-lab"]')).toBeVisible();

    // Click 'Run Deterministic Transformation'
    await page.locator('[data-aid="btn-run-transformation"]').click();

    // Verify transformed output and validation status
    await expect(page.locator('[data-aid="output-transformed-payload"]')).toContainText('Baljinder Singh');
    await expect(page.locator('[data-aid="output-transformed-payload"]')).toContainText('1992-05-10');
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
});
