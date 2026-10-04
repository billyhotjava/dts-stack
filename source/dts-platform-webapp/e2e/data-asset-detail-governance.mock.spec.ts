import { expect, type Page, type Route, test } from "@playwright/test";

const asset = {
	id: "01b52077-d962-4a30-b3a8-fe0000000001",
	displayName: "预算汇总明细",
	description: "预算执行与汇总分析使用的数仓明细资产",
	fqn: "source:finance/schema:public/table:biz_dws_budget_v2",
	type: "POSTGRESQL",
	service: "财务数据仓库",
	database: "biadmin",
	schema: "public",
	table: "biz_dws_budget_v2",
	assetType: "DATASET",
	assetKey: "tenant:default/env:prod/dialect:postgresql/table:public.biz_dws_budget_v2",
	classification: "SECRET",
	warehouseLayer: "DWS",
	owner: "postgres",
	governanceStatus: "PENDING_DOMAIN",
	lifecycleStatus: "PENDING_GOVERNANCE",
	matchStatus: "MATCHED",
	columnCount: 12,
	metadataSource: "dts-catalog",
	tags: [],
};

const domains = [{ id: "domain-finance", code: "FINANCE", name: "财务管理数据域", parentId: null, children: [] }];

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
						username: "asset-detail-reviewer",
						fullName: "资产治理验收员",
						roles: ["ROLE_OP_ADMIN"],
						permissions: ["catalog.manage"],
						enabled: true,
					},
					userToken: { accessToken: "asset-detail-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page, unknownApiPaths: string[], governanceWrites: Record<string, unknown>[]) {
	let governanceSaved = false;
	await page.route("**/api/**", async (route) => {
		const requestUrl = new URL(route.request().url());
		const apiPath = requestUrl.pathname.replace(/^\/api/, "");
		if (apiPath === "/session/status") return fulfill(route, { authenticated: true, remainingSeconds: 3600 });
		if (apiPath === "/menu/tree") return fulfill(route, []);
		if (apiPath === "/catalog/domains/tree") return fulfill(route, domains);
		if (apiPath === `/catalog/assets-v2/${asset.id}`) {
			return fulfill(route, {
				asset,
				columns: Array.from({ length: 12 }, (_, index) => ({ name: `field_${index + 1}`, dataType: "text" })),
			});
		}
		if (apiPath === `/catalog/assets-v2/${asset.id}/contract`) {
			return fulfill(route, {
				assetKey: asset.assetKey,
				grantAssetType: "DATASET",
				grantAssetId: asset.id,
				consumable: governanceSaved,
				canTag: true,
				classification: asset.classification,
				owner: asset.owner,
				lifecycleStatus: asset.lifecycleStatus,
				governanceStatus: governanceSaved ? "GOVERNED" : asset.governanceStatus,
				matchStatus: asset.matchStatus,
				metadataSource: asset.metadataSource,
				missingGovernanceFields: governanceSaved ? [] : ["domainId"],
			});
		}
		if (apiPath === `/catalog/assets-v2/${asset.id}/schema-contract`) {
			return fulfill(route, {
				columnCount: 12,
				schemaSource: "openmetadata",
				columns: Array.from({ length: 12 }, (_, index) => ({ name: `field_${index + 1}`, dataType: "text" })),
			});
		}
		if (apiPath === `/catalog/assets-v2/${asset.id}/governance` && route.request().method() === "PATCH") {
			const payload = route.request().postDataJSON() as Record<string, unknown>;
			governanceWrites.push(payload);
			governanceSaved = true;
			return fulfill(route, {
				asset: {
					...asset,
					domainId: payload.domainId,
					ownerDept: payload.ownerDept,
					governanceStatus: "GOVERNED",
				},
			});
		}
		if (apiPath === "/catalog/governance-workbench/classification-facts") {
			return fulfill(route, [
				{
					subjectType: "ASSET",
					subjectKey: asset.assetKey,
					declaredLevel: asset.classification,
					effectiveLevel: asset.classification,
					snapshotVersion: 1,
					sealed: true,
				},
			]);
		}
		if (apiPath === "/catalog/tag-categories") return fulfill(route, []);
		if (apiPath === "/catalog/tags") return fulfill(route, { content: [], total: 0, page: 0, size: 100 });
		if (apiPath === "/catalog/asset-tags") return fulfill(route, []);
		unknownApiPaths.push(`${route.request().method()} ${apiPath}`);
		return fulfill(route, {});
	});
}

test("asset detail highlights the next governance action and exposes the data-domain editor", async ({ page }) => {
	const unknownApiPaths: string[] = [];
	const governanceWrites: Record<string, unknown>[] = [];
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	await installIdentity(page);
	await installApis(page, unknownApiPaths, governanceWrites);
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));

	await page.setViewportSize({ width: 1366, height: 768 });
	await page.goto(`/#/catalog/datasets/${asset.id}`);
	await expect(page.getByText("数据资产详情", { exact: true })).toBeVisible();
	await expect(page.getByText("当前治理待办", { exact: true })).toBeVisible();
	await expect(page.getByText("缺少业务归属数据域", { exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "完善治理信息" })).toBeVisible();
	await expect(page.getByRole("tab", { name: "治理信息" })).toBeVisible();
	await expect(page.getByText("业务数据标签", { exact: true })).toHaveCount(0);
	await page.screenshot({ path: "/tmp/dts-asset-detail-1366x768.png", fullPage: true });

	await page.getByRole("button", { name: "完善治理信息" }).click();
	await expect(page).toHaveURL(/tab=governance/);
	await expect(page.getByText("治理归属", { exact: true })).toBeVisible();
	await expect(page.getByText("不等同于数据建模中的主题域", { exact: false })).toBeVisible();
	await expect(page.getByRole("button", { name: "保存治理信息" })).toBeVisible();
	await page.getByRole("combobox", { name: "业务归属数据域" }).click();
	await page.getByText("财务管理数据域", { exact: true }).click();
	await page.getByRole("button", { name: "保存治理信息" }).click();
	await expect(page.getByText("治理信息已保存", { exact: true })).toBeVisible();
	expect(governanceWrites).toHaveLength(1);
	expect(governanceWrites[0]?.domainId).toBe("domain-finance");
	await expect(page.getByRole("button", { name: "完善治理信息" })).toHaveCount(0);
	await expect(page.getByText("高级治理：提升人工密级下限", { exact: true })).toBeVisible();
	await page.screenshot({ path: "/tmp/dts-asset-detail-governance-1366x768.png", fullPage: true });

	await page.setViewportSize({ width: 768, height: 900 });
	await page.evaluate(() => window.scrollTo(0, 0));
	await expect(page.getByText("治理归属", { exact: true })).toBeVisible();
	const hasBodyOverflow = await page.evaluate(
		() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
	);
	expect(hasBodyOverflow).toBe(false);
	await page.screenshot({ path: "/tmp/dts-asset-detail-governance-768x900.png", fullPage: true });

	expect(unknownApiPaths).toEqual([]);
	expect(consoleErrors).toEqual([]);
	expect(pageErrors).toEqual([]);
});
