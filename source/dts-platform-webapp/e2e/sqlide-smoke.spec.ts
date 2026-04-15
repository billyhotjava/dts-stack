/**
 * SQL IDE layout smoke test.
 * Catches Sprint-11 bugs #5 (SqlIdePage absolute positioning) and #6
 * (menu.component hardcoding that hid the activity bar / mode switcher).
 */

import { expect, test } from "@playwright/test";

test("SQL IDE layout renders correctly", async ({ page }) => {
  await page.goto("/#/explore/workbench");

  // Wait for Monaco editor to initialise — this is the slowest asset to load
  await expect(page.locator(".monaco-editor").first()).toBeVisible({ timeout: 10_000 });

  // Breadcrumb / page title
  await expect(page.getByText("即席查询")).toBeVisible();

  // Activity Bar (44px left column with icon buttons)
  await expect(page.getByTestId("sqlide-activity-bar")).toBeVisible();
  await expect(page.getByTestId("sqlide-activity-schema")).toBeVisible();
  await expect(page.getByTestId("sqlide-activity-history")).toBeVisible();
  await expect(page.getByTestId("sqlide-activity-saved")).toBeVisible();

  // Mode switcher (AntD Segmented rendered as radiogroup)
  await expect(page.getByRole("radiogroup")).toBeVisible();

  // Open a fresh tab to guarantee the empty state placeholder is shown.
  // (The restored session may have a tab with prior execution results.)
  await page.getByTestId("sqlide-tab-bar").getByText("+").click();
  await expect(page.getByText("运行 SQL 后结果出现在这里")).toBeVisible();
});
