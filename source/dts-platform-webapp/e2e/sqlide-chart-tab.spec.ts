/**
 * Chart tab smoke test.
 * Catches Sprint-11 bug #9 — opening the Chart tab after execution crashed
 * with "Rendered more hooks than previous render" due to a conditional
 * hook invocation in ResultChart.tsx.
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

test("opening Chart tab after execution does not crash", async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on("pageerror", (err) => consoleErrors.push(err.message));

  await page.goto("/#/explore/workbench");
  await expect(page.locator(".monaco-editor").first()).toBeVisible({ timeout: 10_000 });

  // Run a query that produces a numeric result set suitable for charting
  await setMonacoContent(page, "SELECT 1 AS x, 2 AS y UNION ALL SELECT 3, 4");
  await page.keyboard.press("Control+Enter");
  await expect(page.getByText(/成功/)).toBeVisible({ timeout: 30_000 });

  // Click the "图表" (Chart) tab in the bottom result panel
  await page.getByRole("tab", { name: /图表|Chart/ }).click();

  // Give React a moment to render without crashing
  await page.waitForTimeout(2_000);

  // Hook violation errors must not appear
  const hookErrors = consoleErrors.filter((e) => e.includes("Rendered more hooks"));
  expect(hookErrors).toEqual([]);
});
