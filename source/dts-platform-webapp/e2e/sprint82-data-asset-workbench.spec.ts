import { expect, test } from "@playwright/test";

const assetApiPattern = /(catalog\/assets-v2|catalog\/tag|catalog\/asset-tags|catalog\/classification-facts)/;

function collectErrors(page: import("@playwright/test").Page) {
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	const assetApiErrors: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("response", (response) => {
		if (response.status() >= 400 && assetApiPattern.test(response.url())) {
			assetApiErrors.push(String(response.status()) + " " + response.request().method() + " " + response.url());
		}
	});
	return { consoleErrors, pageErrors, assetApiErrors };
}

test.describe("Sprint-82 data asset workbench", () => {
	test("desktop journey keeps the map summary-only and governs one asset from a single entry", async ({
		page,
	}, testInfo) => {
		await page.setViewportSize({ width: 1366, height: 768 });
		const errors = collectErrors(page);

		await page.goto("/#/catalog/assets");
		await expect(page.getByRole("heading", { name: "资产地图" })).toBeVisible();
		await expect(page.getByText(/标签覆盖 \d+%/)).toBeVisible();
		await expect(page.getByRole("tab", { name: "数据标签" })).toHaveCount(0);

		await page.getByRole("button", { name: /当前范围/ }).click();
		await expect(page).toHaveURL(/\/catalog\/assets\/ledger/);
		await expect(page.getByRole("tab", { name: "资产列表" })).toBeVisible();
		await expect(page.getByRole("tab", { name: "数据标签" })).toBeVisible();

		const governButton = page.getByRole("button", { name: "治理资产" }).first();
		await expect(governButton).toBeVisible({ timeout: 15_000 });
		await governButton.click();
		const drawer = page.locator(".ant-drawer").filter({ hasText: "资产治理工作台" });
		await expect(drawer).toBeVisible();
		await expect(drawer.getByText(/当前治理任务：/)).toBeVisible();
		await expect(drawer.getByRole("button", { name: "完整资产档案" })).toBeVisible();

		await drawer.getByRole("tab", { name: "数据标签" }).click();
		await expect(drawer.getByText("业务数据标签", { exact: true }).first()).toBeVisible();
		await expect(drawer).not.toContainText("404 NOT_FOUND");
		await page.screenshot({ path: testInfo.outputPath("asset-governance-workbench-1366x768.png"), fullPage: true });

		expect(errors.pageErrors).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
		expect(errors.assetApiErrors).toEqual([]);
	});

	test("narrow ledger keeps navigation and the governance action usable", async ({ page }, testInfo) => {
		await page.setViewportSize({ width: 768, height: 900 });
		const errors = collectErrors(page);

		await page.goto("/#/catalog/assets/ledger");
		await expect(page.getByRole("tab", { name: "资产列表" })).toBeVisible();
		await expect(page.getByRole("tab", { name: "数据标签" })).toBeVisible();
		await expect(page.getByRole("button", { name: "治理资产" }).first()).toBeVisible({ timeout: 15_000 });
		const hasBodyOverflow = await page.evaluate(
			() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
		);
		expect(hasBodyOverflow).toBe(false);
		await page.screenshot({ path: testInfo.outputPath("asset-ledger-768x900.png"), fullPage: true });

		expect(errors.pageErrors).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
		expect(errors.assetApiErrors).toEqual([]);
	});
});
