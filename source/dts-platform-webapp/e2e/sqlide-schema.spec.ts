/**
 * Schema panel smoke test.
 * Catches Sprint-11 bugs:
 *   #4 — catalog API returned static Trino/Hive placeholders instead of
 *         reading from infra_data_source
 *   #3 — (partial) QueryGateway catalog contract wired incorrectly
 *
 * The environment has exactly ONE real datasource:
 *   数据仓库 (biadmin) — ID a0000000-0000-0000-0000-000000000001
 */

import { expect, test } from "@playwright/test";

test("schema panel shows real datasources from infra_data_source", async ({ page }) => {
  await page.goto("/#/explore/workbench");

  // Wait for the app to fully load
  await expect(page.locator(".monaco-editor").first()).toBeVisible({ timeout: 10_000 });

  // The schema panel defaults to active (activeActivity = "schema").
  // Clicking the already-active button TOGGLES it off — so we ensure the
  // panel is open by clicking a different activity first, then clicking schema.
  const historyBtn = page.getByTestId("sqlide-activity-history");
  await historyBtn.click();
  // Now close history by clicking schema — this always opens schema
  await page.getByTestId("sqlide-activity-schema").click();

  // Side panel becomes visible
  await expect(page.getByTestId("sqlide-side-panel")).toBeVisible();

  // The ONE real datasource must appear — match either the display name or the DB name
  await expect(page.getByText(/数据仓库|biadmin/)).toBeVisible({ timeout: 10_000 });

  // Old static placeholders must NOT appear
  await expect(page.getByText("Trino · default")).not.toBeVisible();
  await expect(page.getByText("Apache Hive")).not.toBeVisible();
});
