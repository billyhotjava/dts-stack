import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

const installFailureProbe = (page: Page) => {
	const pageErrors: string[] = [];
	const requestFailures: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) =>
		requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`),
	);
	return { pageErrors, requestFailures };
};

test("F4 live menu respects the authenticated role and excludes retired entries", async ({ page }) => {
	const failures = installFailureProbe(page);
	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto("/#/modeling/workbench");
	await expect(page.getByTestId("warehouse-plan-workbench")).toBeVisible();

	const menuText = await page.getByRole("navigation").first().innerText();
	expect(menuText).toContain("工作台");
	for (const retired of ["标准包导入", "标准模板", "低代码开发向导", "项目文件浏览", "指标管理", "发布审核"]) {
		expect(menuText).not.toContain(retired);
	}
	await page.screenshot({ path: path.join(evidenceDir, "f4-live-modeling-menu-chromium95.png"), fullPage: true });

	await page.goto("/#/modeling/models");
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
});

test("F4 legacy links normalize context and recover unmapped objects", async ({ page }) => {
	const failures = installFailureProbe(page);
	await page.goto(
		"/#/modeling/semantic/models?planningId=legacy-plan&modelId=legacy-model&revision=3&returnTo=%2Fworkbench&junk=x",
	);
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	const redirected = await page.evaluate(() => new URL(window.location.hash.slice(1), "https://bi.yuzhicloud.com"));
	expect(redirected.pathname).toBe("/modeling/models");
	expect(redirected.searchParams.get("planId")).toBe("legacy-plan");
	expect(redirected.searchParams.get("modelSpecId")).toBe("legacy-model");
	expect(redirected.searchParams.get("revision")).toBe("3");
	expect(redirected.searchParams.get("returnTo")).toBe("/workbench");
	for (const retired of ["planningId", "modelId", "junk"]) expect(redirected.searchParams.has(retired)).toBe(false);

	await page.goto("/#/modeling/semantic/models?returnTo=https%3A%2F%2Fevil.example");
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	const safeRedirect = await page.evaluate(() => new URL(window.location.hash.slice(1), "https://bi.yuzhicloud.com"));
	expect(safeRedirect.searchParams.has("returnTo")).toBe(false);

	await page.goto("/#/modeling/semantic/objects?objectId=sprint67-f4-unmapped-object");
	await expect(page.getByTestId("modeling-compatibility-recovery")).toBeVisible();
	await expect(page.getByText("NEEDS_CLASSIFICATION", { exact: true })).toBeVisible();
	await page.setViewportSize({ width: 390, height: 844 });
	const overflow = await page.evaluate(() => ({
		viewport: window.innerWidth,
		document: document.documentElement.scrollWidth,
		body: document.body.scrollWidth,
	}));
	expect(overflow.document).toBeLessThanOrEqual(overflow.viewport);
	expect(overflow.body).toBeLessThanOrEqual(overflow.viewport);
	await page.screenshot({ path: path.join(evidenceDir, "f4-legacy-recovery-chromium95-narrow.png"), fullPage: true });
	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
});
