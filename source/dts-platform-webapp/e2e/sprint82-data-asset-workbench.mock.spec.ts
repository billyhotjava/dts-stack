import { expect, type Page, type Route, test } from "@playwright/test";

const tag = {
	id: "tag-1",
	categoryId: "category-1",
	code: "OPERATIONS",
	name: "经营主题",
	color: "blue",
	builtin: false,
	enabled: true,
	description: "经营分析与运营管理数据",
	usageCount: 1,
};

const assets = [
	{
		id: "asset-1",
		displayName: "xycyl_stg_recovery_info_item",
		type: "POSTGRESQL",
		assetType: "DATASET",
		assetKey: "tenant:default/env:prod/dialect:postgresql/table:public.xycyl_stg_recovery_info_item",
		database: "public",
		table: "xycyl_stg_recovery_info_item",
		warehouseLayer: "STG",
		classification: "C2",
		lifecycleStatus: "ACTIVE",
		governanceStatus: "PENDING_DOMAIN",
		matchStatus: "MATCHED",
		ownerDept: "运营部",
		enabled: true,
		assetTags: [tag],
	},
	{
		id: "asset-2",
		displayName: "dwd_recovery_summary",
		type: "POSTGRESQL",
		assetType: "DATASET",
		assetKey: "tenant:default/env:prod/dialect:postgresql/table:public.dwd_recovery_summary",
		database: "public",
		table: "dwd_recovery_summary",
		warehouseLayer: "DWD",
		classification: "C2",
		lifecycleStatus: "ACTIVE",
		governanceStatus: "GOVERNED",
		matchStatus: "MATCHED",
		domainId: "domain-1",
		domainName: "运营主题域",
		owner: "张三",
		ownerDept: "运营部",
		enabled: true,
		assetTags: [],
	},
];

const domains = [
	{
		id: "domain-1",
		code: "OPERATIONS",
		name: "运营主题域",
		parentId: null,
		children: [],
	},
];

function envelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

async function fulfill(route: Route, data: unknown) {
	await route.fulfill({ status: 200, contentType: "application/json", body: envelope(data) });
}

async function installMockIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "sprint82-reviewer",
						fullName: "Sprint 82 Reviewer",
						roles: ["ROLE_OP_ADMIN"],
						permissions: ["catalog.manage"],
						enabled: true,
					},
					userToken: { accessToken: "sprint82-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installMockApis(page: Page) {
	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const requestUrl = new URL(request.url());
		if (!requestUrl.pathname.startsWith("/api/") && !requestUrl.pathname.startsWith("/admin/api/")) {
			return route.continue();
		}
		const apiPath = requestUrl.pathname.replace(/^\/api/, "");

		if (apiPath === "/session/status") return fulfill(route, { authenticated: true, remainingSeconds: 3600 });
		if (apiPath === "/menu/tree") return fulfill(route, []);
		if (apiPath === "/catalog/domains/tree") return fulfill(route, domains);
		if (apiPath === "/catalog/domains") {
			return fulfill(route, { content: domains, page: 0, size: 200, total: domains.length });
		}
		if (apiPath === "/catalog/assets-v2/overview") {
			return fulfill(route, {
				total: assets.length,
				unclassified: 0,
				missingDomain: 1,
				stale: 0,
				attention: 1,
				tagged: 1,
				untagged: 1,
				tagCoveragePercent: 50,
				byLayer: { STG: 1, DWD: 1 },
				governanceStatusCounts: { PENDING_DOMAIN: 1, GOVERNED: 1 },
				matrix: [
					{ layer: "STG", domainId: null, total: 1, attention: 1 },
					{ layer: "DWD", domainId: "domain-1", total: 1, attention: 0 },
				],
				scanned: assets.length,
				truncated: false,
			});
		}
		if (apiPath === "/catalog/assets-v2") {
			return fulfill(route, { content: assets, page: 0, size: 10, total: assets.length });
		}
		if (apiPath === "/catalog/assets-v2/governance-gaps") {
			return fulfill(route, { content: [], page: 0, size: 20, total: 0 });
		}
		if (apiPath === "/catalog/assets-v2/lineage-failures") {
			return fulfill(route, { content: [], page: 0, size: 20, total: 0 });
		}
		if (apiPath === "/catalog/governance-workbench/classification-facts") {
			return fulfill(
				route,
				assets.map((asset) => ({
					subjectType: asset.assetType,
					subjectKey: asset.assetKey,
					effectiveLevel: asset.classification,
					snapshotVersion: 1,
					propagationStatus: "PROPAGATED",
					sealed: true,
				})),
			);
		}
		if (apiPath === "/catalog/tag-categories") {
			return fulfill(route, [
				{
					id: "category-1",
					code: "BUSINESS",
					name: "业务主题",
					parentId: null,
					sortOrder: 1,
					builtin: false,
					enabled: true,
					tagCount: 1,
					children: [],
				},
			]);
		}
		if (apiPath === "/catalog/tags") {
			return fulfill(route, { content: [tag], total: 1, page: 0, size: 10 });
		}
		if (apiPath === "/catalog/asset-tags/capability") return fulfill(route, { canTag: true });
		if (apiPath === "/catalog/asset-tags/batch") {
			return fulfill(route, { assetCount: 1, created: 1, skipped: 0, results: [] });
		}
		if (apiPath === "/catalog/asset-tags") {
			if (request.method() === "GET") return fulfill(route, [tag]);
			return fulfill(route, { created: 1, skipped: 0, removed: 0 });
		}
		if (apiPath.includes("/catalog/reconciliation")) {
			return fulfill(route, { assertions: [], generatedAt: "2026-08-01T00:00:00Z" });
		}

		return fulfill(route, {});
	});
}

function collectErrors(page: Page) {
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));
	return { consoleErrors, pageErrors };
}

test.describe("Sprint-82 data asset workbench mock-API acceptance", () => {
	test.beforeEach(async ({ page }) => {
		await installMockIdentity(page);
		await installMockApis(page);
	});

	test("keeps the asset map summary-only and governs one asset through a single entry", async ({ page }, testInfo) => {
		await page.setViewportSize({ width: 1366, height: 768 });
		const errors = collectErrors(page);

		await page.goto("/#/catalog/assets");
		await expect(page.getByRole("heading", { name: "资产地图" })).toBeVisible();
		await expect(page.getByText("标签覆盖 50%", { exact: false })).toBeVisible();
		await expect(page.getByRole("tab", { name: "数据标签" })).toHaveCount(0);

		await page.getByRole("button", { name: /当前范围/ }).click();
		await expect(page).toHaveURL(/#\/catalog\/assets\/ledger/);
		await expect(page.getByRole("tab", { name: "资产列表" })).toBeVisible();
		await expect(page.getByRole("button", { name: "治理资产" })).toHaveCount(2);

		await page.getByRole("button", { name: "治理资产" }).first().click();
		const drawer = page.locator(".ant-drawer").filter({ hasText: "资产治理工作台" });
		await expect(drawer).toBeVisible();
		await expect(drawer.getByText("当前治理任务：补齐治理责任")).toBeVisible();
		await expect(drawer.getByRole("button", { name: "完整资产档案" })).toBeVisible();

		await drawer.getByRole("tab", { name: "数据标签" }).click();
		await expect(drawer.locator(".ant-tabs-tabpane-active").getByText("经营主题", { exact: true })).toBeVisible();
		await expect(drawer).not.toContainText("404 NOT_FOUND");
		await page.screenshot({ path: testInfo.outputPath("asset-governance-workbench-1366x768.png"), fullPage: true });

		expect(errors.pageErrors).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
	});

	test("closes the tag catalog to asset association and verification journey", async ({ page }) => {
		const errors = collectErrors(page);
		await page.goto("/#/catalog/assets/ledger?tab=catalog-tags");

		await expect(page.getByText("业务数据标签用于资产发现与检索")).toBeVisible();
		await expect(page.getByRole("button", { name: "查看资产" })).toBeVisible();
		await expect(page.getByRole("button", { name: "关联资产" })).toBeVisible();

		await page.getByRole("button", { name: "关联资产" }).click();
		await expect(page).toHaveURL(/manageTag=tag-1/);
		await expect(page.getByText("正在关联标签：经营主题")).toBeVisible();

		await page.getByRole("checkbox").nth(1).check();
		await page.getByRole("button", { name: "关联选中资产（1）" }).click();
		await expect(page).toHaveURL(/tagIds=tag-1/);
		await expect(page.getByText("经营主题", { exact: true }).first()).toBeVisible();

		expect(errors.pageErrors).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
	});

	test("keeps the ledger usable without page-level overflow at the narrow acceptance viewport", async ({
		page,
	}, testInfo) => {
		await page.setViewportSize({ width: 768, height: 900 });
		const errors = collectErrors(page);

		await page.goto("/#/catalog/assets/ledger");
		await expect(page.getByRole("tab", { name: "资产列表" })).toBeVisible();
		await expect(page.getByRole("tab", { name: "数据标签" })).toBeVisible();
		await expect(page.getByRole("button", { name: "治理资产" }).first()).toBeVisible();
		const metricBox = await page.getByText("台账总量", { exact: true }).locator("../..").boundingBox();
		expect(metricBox?.width ?? 0).toBeGreaterThan(140);
		const healthAlert = page.locator(".ant-alert").filter({ hasText: "当前筛选未发现治理阻断和血缘证据缺口" });
		const healthAlertBox = await healthAlert.boundingBox();
		expect(healthAlertBox?.height ?? Number.POSITIVE_INFINITY).toBeLessThan(260);
		const hasBodyOverflow = await page.evaluate(
			() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
		);
		expect(hasBodyOverflow).toBe(false);
		await page.screenshot({ path: testInfo.outputPath("asset-ledger-768x900.png"), fullPage: true });

		expect(errors.pageErrors).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
	});
});
