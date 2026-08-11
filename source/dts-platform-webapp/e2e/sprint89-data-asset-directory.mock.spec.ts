import { expect, type Page, type Route, test } from "@playwright/test";

const asset = {
	id: "asset-1",
	displayName: "项目任务快照",
	description: "项目任务每日状态快照",
	fqn: "source:project/schema:public/table:project_task_snapshot",
	type: "POSTGRESQL",
	service: "项目管理库",
	assetType: "DATASET",
	assetKey: "tenant:default/env:prod/dialect:postgresql/table:public.project_task_snapshot",
	domainId: "domain-1",
	classification: "CONFIDENTIAL",
	warehouseLayer: "DWD",
	owner: "张三",
	ownerDept: "D01",
	governanceStatus: "GOVERNED",
	lifecycleStatus: "ACTIVE",
	matchStatus: "MATCHED",
	columnCount: 12,
	lastSyncedAt: "2026-08-12T01:02:03Z",
	metadataSource: "dts-catalog",
	assetTags: [],
};

const domains = [{ id: "domain-1", code: "PROJECT", name: "项目管理域", parentId: null, children: [] }];

function envelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

async function fulfill(route: Route, data: unknown) {
	await route.fulfill({ status: 200, contentType: "application/json", body: envelope(data) });
}

async function installIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "sprint89-reviewer",
						fullName: "Sprint 89 Reviewer",
						roles: ["ROLE_OP_ADMIN"],
						permissions: ["catalog.manage"],
						enabled: true,
					},
					userToken: { accessToken: "sprint89-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page, unknownApiPaths: string[]) {
	await page.route("**/api/**", async (route) => {
		const requestUrl = new URL(route.request().url());
		const apiPath = requestUrl.pathname.replace(/^\/api/, "");
		if (apiPath === "/session/status") return fulfill(route, { authenticated: true, remainingSeconds: 3600 });
		if (apiPath === "/menu/tree") return fulfill(route, []);
		if (apiPath === "/catalog/domains/tree") return fulfill(route, domains);
		if (apiPath === "/catalog/domains") {
			return fulfill(route, { content: domains, page: 0, size: 200, total: domains.length });
		}
		if (apiPath === "/catalog/assets-v2") {
			return fulfill(route, { content: [asset], page: 0, size: 10, total: 1 });
		}
		if (apiPath === "/catalog/tag-categories") return fulfill(route, []);
		if (apiPath === "/catalog/tags") return fulfill(route, { content: [], total: 0, page: 0, size: 10 });
		if (apiPath === "/catalog/asset-tags/capability") return fulfill(route, { canTag: true });
		unknownApiPaths.push(apiPath);
		return fulfill(route, {});
	});
}

function collectBrowserErrors(page: Page) {
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	const requestFailures: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) => requestFailures.push(`${request.method()} ${request.url()}`));
	return { consoleErrors, pageErrors, requestFailures };
}

test.describe("Sprint-89 data asset directory mock-API acceptance", () => {
	test.beforeEach(async ({ page }) => {
		await installIdentity(page);
	});

	test("shows business, governance and technical characteristics at desktop and narrow widths", async ({ page }) => {
		const unknownApiPaths: string[] = [];
		await installApis(page, unknownApiPaths);
		const errors = collectBrowserErrors(page);

		await page.setViewportSize({ width: 1366, height: 768 });
		await page.goto("/#/catalog/search");
		await expect(page.getByRole("heading", { name: "数据资产目录" })).toBeVisible();
		await expect(page.getByText("项目任务每日状态快照", { exact: true })).toBeVisible();
		await expect(page.getByText("项目管理域", { exact: true }).last()).toBeVisible();
		await expect(page.getByText("张三（D01）", { exact: true })).toBeVisible();
		await expect(page.getByText("明细层（DWD）", { exact: true })).toBeVisible();
		await expect(page.getByText("治理状态：已治理", { exact: true })).toBeVisible();
		await expect(page.getByText("平台登记", { exact: false })).toBeVisible();
		await expect(page.locator("body")).not.toContainText("dts-catalog");
		await expect(page.locator("body")).not.toContainText("CONFIDENTIAL");
		await page.screenshot({ path: "/tmp/sprint89-data-asset-directory-1366x768.png", fullPage: true });

		await page.setViewportSize({ width: 768, height: 900 });
		await expect(page.getByText("责任归属", { exact: true })).toBeVisible();
		const hasBodyOverflow = await page.evaluate(
			() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
		);
		expect(hasBodyOverflow).toBe(false);
		await page.screenshot({ path: "/tmp/sprint89-data-asset-directory-768x900.png", fullPage: true });

		expect(unknownApiPaths).toEqual([]);
		expect(errors.consoleErrors).toEqual([]);
		expect(errors.pageErrors).toEqual([]);
		expect(errors.requestFailures).toEqual([]);
	});
});
