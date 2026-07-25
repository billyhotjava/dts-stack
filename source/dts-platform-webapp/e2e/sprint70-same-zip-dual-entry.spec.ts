import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const planId = process.env.E2E_S70_PLAN_ID ?? "";
const zipPath = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/s10/v4/pjm/pjm-dbt-model.zip",
);
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-70-202607-dbt-model-package-import/it/evidence/chrome95",
);

const importResponse = (page: Page, method: string, pathname: string) =>
	page.waitForResponse((response) => {
		const url = new URL(response.url());
		return response.request().method() === method && url.pathname === pathname;
	});

test.beforeAll(() => {
	fs.mkdirSync(evidenceDir, { recursive: true });
	expect(fs.statSync(zipPath).isFile()).toBe(true);
});

test("ordinary modeling statically converts the unmodified PJM ZIP and reaches preview", async ({ page }) => {
	test.skip(!planId, "E2E_S70_PLAN_ID is required");
	test.setTimeout(120_000);
	await page.setViewportSize({ width: 1366, height: 900 });
	await page.goto(`/#/modeling/workbench?planId=${encodeURIComponent(planId)}`);
	await expect(page.getByTestId("warehouse-plan-workbench")).toBeVisible();
	await page.getByRole("button", { name: "导入已有模型" }).click();

	const drawer = page.locator(".ant-drawer-content").filter({ hasText: "导入已有模型" });
	await expect(drawer).toBeVisible();
	const inspect = importResponse(page, "POST", "/api/modeling/model-spec-imports/dbt/archive/inspect");
	await drawer.locator('input[type="file"]').setInputFiles(zipPath);
	const inspectResult = await inspect;
	expect(inspectResult.status()).toBe(200);
	await expect(drawer.getByText("pm_analytics_v3", { exact: true })).toBeVisible();
	await expect(drawer.getByText("源项目安全解析（未执行 SQL）", { exact: true })).toBeVisible();
	await expect(drawer.getByText("38 个", { exact: true })).toBeVisible();

	await drawer.getByRole("button", { name: "下一步" }).click();
	await expect(drawer.getByText("已从 dbt 源项目生成普通模型候选", { exact: true })).toBeVisible();
	await expect(drawer.getByText("入口来自当前计划，导入期间不可静默切换。", { exact: true })).toBeVisible();

	const preview = importResponse(page, "POST", "/api/modeling/model-spec-imports/dbt/preview");
	await drawer.getByRole("button", { name: "开始预检" }).click();
	const previewResult = await preview;
	expect(previewResult.status()).toBe(200);
	await expect(drawer.getByText("本批次来自 dbt 源项目安全解析", { exact: true })).toBeVisible();
	await expect(drawer.getByText(/预检已完成/)).toBeVisible();
	await expect(drawer.getByText("38", { exact: true }).first()).toBeVisible();
	await page.screenshot({
		path: path.join(evidenceDir, "same-zip-ordinary-preview-chromium95.png"),
		fullPage: true,
	});

	await page.setViewportSize({ width: 390, height: 844 });
	await expect
		.poll(() => page.evaluate(() => Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)))
		.toBeLessThanOrEqual(390);
	await page.screenshot({
		path: path.join(evidenceDir, "same-zip-ordinary-preview-chromium95-narrow.png"),
		fullPage: true,
	});
});

test("advanced modeling submits the same unmodified ZIP as the raw archive", async ({ page }) => {
	test.setTimeout(120_000);
	await page.setViewportSize({ width: 1366, height: 900 });
	await page.goto("/#/studio/sql-modeling");
	await expect(page.getByTestId("platform-sql-modeling-page")).toBeVisible();
	await page.getByRole("button", { name: /模\s*型/ }).click();
	await page.getByText("批量导入", { exact: true }).click();

	const modal = page.getByRole("dialog", { name: "批量导入模型" });
	await expect(modal).toBeVisible();
	await expect(modal.getByText(/上传标准 dbt 项目 ZIP/)).toBeVisible();
	await modal.getByRole("checkbox", { name: "跳过已存在的模型" }).check();
	await modal.locator('input[type="file"]').setInputFiles(zipPath);

	const requestPromise = page.waitForRequest((request) => {
		const url = new URL(request.url());
		return request.method() === "POST" && url.pathname === "/api/modeling/sql-models/batch-import";
	});
	const responsePromise = importResponse(page, "POST", "/api/modeling/sql-models/batch-import");
	await modal.getByRole("button", { name: "导入", exact: true }).click();
	const [request, response] = await Promise.all([requestPromise, responsePromise]);
	const body = request.postDataBuffer()?.toString("latin1") ?? "";
	expect(body).toContain("pjm-dbt-model.zip");
	expect(body).toContain('name="skipExisting"');
	expect(body).toContain("true");
	expect(response.status()).toBe(200);
	await expect(page.getByRole("dialog", { name: "批量导入结果" })).toBeVisible();
	await page.screenshot({
		path: path.join(evidenceDir, "same-zip-advanced-import-chromium95.png"),
		fullPage: true,
	});
});
