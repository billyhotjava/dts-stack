/**
 * End-to-end SQL execution smoke test.
 * Catches Sprint-11 bugs:
 *   #1 — JdbcSqlExecutor not wired into execution path (Trino stub returned)
 *   #2 — chunk-persist path wrote null result set
 *   #3 — QueryGateway contract broken (wrong datasource ID in request)
 *   #8 — RUNNING state never transitioned to SUCCESS/FAILED on the FE
 *
 * Uses information_schema.tables which always exists in PostgreSQL.
 */

import { expect, test } from "@playwright/test";

/** Replace all text in the Monaco editor using keyboard shortcuts. */
async function setMonacoContent(page: import("@playwright/test").Page, sql: string) {
  // Click the editor container with force to bypass overlay view-line divs
  await page.getByTestId("sqlide-editor").click({ force: true });
  // Select all existing content and delete it
  await page.keyboard.press("Control+a");
  await page.keyboard.press("Delete");
  // Type the new SQL
  await page.keyboard.type(sql);
}

test("executing a valid SELECT returns rows", async ({ page }) => {
  await page.goto("/#/explore/workbench");
  await expect(page.locator(".monaco-editor").first()).toBeVisible({ timeout: 10_000 });

  // Write a query guaranteed to return rows in any PG database
  await setMonacoContent(
    page,
    "SELECT table_schema, table_name FROM information_schema.tables LIMIT 5",
  );

  // Trigger execution via keyboard shortcut (editor already focused from setMonacoContent)
  await page.keyboard.press("Control+Enter");

  // Wait for success status label (timeout covers query round-trip + FE polling)
  await expect(page.getByText(/成功/)).toBeVisible({ timeout: 30_000 });

  // Execution failure indicators must NOT appear
  await expect(page.getByText("❌ 执行失败")).not.toBeVisible();
  await expect(page.getByText("result set not available")).not.toBeVisible();
});
