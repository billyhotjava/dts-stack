/**
 * Tab persistence smoke test.
 * Catches Sprint-11 bugs:
 *   #7  — stale updatedAt after debounce causes 409 on second save
 *   #10 — activeTabId remap left UI stuck on "正在恢复 Tab" spinner
 */

import { expect, test } from "@playwright/test";

/** Replace all text in the Monaco editor using keyboard shortcuts. */
async function setMonacoContent(page: import("@playwright/test").Page, sql: string) {
  // Click the editor container with force to bypass overlay view-line divs
  await page.getByTestId("sqlide-editor").click({ force: true });
  await page.keyboard.press("Control+a");
  await page.keyboard.press("Delete");
  await page.keyboard.type(sql);
}

test("editing a tab does not trigger stale-updatedAt after debounce", async ({ page }) => {
  await page.goto("/#/explore/workbench");
  await expect(page.locator(".monaco-editor").first()).toBeVisible({ timeout: 10_000 });

  // Type SQL — triggers updateTab + schedules syncDirty (2 s debounce)
  await setMonacoContent(page, "SELECT 1");

  // Wait for first debounced sync + server round-trip to complete
  await page.waitForTimeout(4_000);

  // Must NOT be stuck in "正在恢复 Tab" spinner
  await expect(page.getByText("正在恢复 Tab")).not.toBeVisible();

  // Capture 409s from here on
  const errors: string[] = [];
  page.on("response", (r) => {
    if (r.url().includes("/api/sql/v2/tabs") && r.status() === 409) {
      errors.push(`409 on ${r.url()}`);
    }
  });

  // Second edit should not cause a conflict
  await setMonacoContent(page, "SELECT 1, 2");
  await page.waitForTimeout(4_000);

  expect(errors).toEqual([]);
});
