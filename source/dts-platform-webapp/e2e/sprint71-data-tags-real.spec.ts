import fs from "node:fs";
import path from "node:path";
import type { APIResponse, Page, Response as PlaywrightResponse } from "@playwright/test";
import { expect, test } from "@playwright/test";

const datasetId = process.env.E2E_S71_DATASET_ID ?? "0d981240-29b6-45aa-9263-14ba33c249d9";
const datasetName = process.env.E2E_S71_DATASET_NAME ?? "src_jdbc_orders";
const datasetAssetKey =
	process.env.E2E_S71_DATASET_KEY ??
	"source:a0000000-0000-0000-0000-000000000001/schema:s38_regression/table:src_jdbc_orders";
const domainId = process.env.E2E_S71_DOMAIN_ID ?? "cf71c12f-60a3-4012-8ece-43d8fe0bc3df";
const domainName = process.env.E2E_S71_DOMAIN_NAME ?? "项目质量";
const domainAssetKey =
	process.env.E2E_S71_DOMAIN_KEY ?? "tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa";
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-71-202607-data-tag-governance/it/evidence/chrome95",
);
const ASSET_MAP_API_PATHS = new Set([
	"/api/catalog/domains",
	"/api/catalog/domains/tree",
	"/api/catalog/assets-v2",
	"/api/catalog/assets-v2/overview",
]);

type CatalogTagCategory = {
	id: string;
	code: string;
	name: string;
	children?: CatalogTagCategory[];
};

type CatalogTag = {
	id: string;
	code: string;
	name: string;
};

type CatalogTagPage = {
	content: CatalogTag[];
};

type AssetRef = {
	assetType: string;
	assetKey: string;
};

type AssetRefPage = {
	content: AssetRef[];
	total: number;
	page: number;
	size: number;
};

type BrowserFailures = {
	consoleErrors: string[];
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
};

type CleanupState = {
	categoryId: string;
	categoryCode: string;
	categoryDeleted: boolean;
	tagCodes: string[];
	tagIdsByCode: Record<string, string>;
	deletedTagCodes: Set<string>;
};

function observeFailures(page: Page): BrowserFailures {
	const failures: BrowserFailures = {
		consoleErrors: [],
		pageErrors: [],
		requestFailures: [],
		httpFailures: [],
	};
	page.on("console", (message) => {
		if (message.type() === "error") failures.consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) => {
		failures.requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`);
	});
	page.on("response", (response) => {
		const url = new URL(response.url());
		const expectedLegacyDatasetFallback =
			response.status() === 404 &&
			response.request().method() === "GET" &&
			url.pathname === `/api/catalog/assets-v2/${datasetId}`;
		if (url.pathname.startsWith("/api/") && response.status() >= 400 && !expectedLegacyDatasetFallback) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${url.pathname}`);
		}
	});
	return failures;
}

function expectNoUnexpectedFailures(failures: BrowserFailures) {
	expect(failures.consoleErrors, "console errors").toEqual([]);
	expect(failures.pageErrors, "pageerror events").toEqual([]);
	expect(failures.requestFailures, "requestfailed events").toEqual([]);
	expect(failures.httpFailures, "HTTP API failures").toEqual([]);
}

async function responseData<T>(response: APIResponse | PlaywrightResponse): Promise<T> {
	expect(response.ok(), `${response.status()} ${response.url()}`).toBe(true);
	const envelope = (await response.json()) as { data: T };
	return envelope.data;
}

function findCategory(rows: CatalogTagCategory[], code: string): CatalogTagCategory | null {
	for (const row of rows) {
		if (row.code === code) return row;
		const nested = findCategory(row.children || [], code);
		if (nested) return nested;
	}
	return null;
}

async function addBusinessTag(page: Page, name: string) {
	const select = page.getByRole("combobox", { name: "添加业务数据标签" });
	await expect(select).toBeEnabled({ timeout: 15_000 });
	await select.click();
	const responsePromise = waitForApiMutation(page, "POST", "/api/catalog/asset-tags");
	await page.getByRole("option", { name, exact: true }).click();
	const response = await responsePromise;
	expect(response.ok(), `${response.status()} POST ${response.url()}`).toBe(true);
	await expect(page.getByRole("list", { name: "业务数据标签" })).toContainText(name);
}

async function removeBusinessTag(page: Page, name: string) {
	const remove = page.getByRole("button", { name: `移除业务标签 ${name}` });
	await expect(remove).toBeEnabled();
	const responsePromise = waitForApiMutation(page, "DELETE", "/api/catalog/asset-tags");
	await remove.click();
	const response = await responsePromise;
	expect(response.ok(), `${response.status()} DELETE ${response.url()}`).toBe(true);
	await expect(page.getByRole("button", { name: `移除业务标签 ${name}` })).toHaveCount(0);
}

function waitForApiMutation(page: Page, method: string, pathname: string): Promise<PlaywrightResponse> {
	return page.waitForResponse((response) => {
		return new URL(response.url()).pathname === pathname && response.request().method() === method;
	});
}

async function selectBusinessTagFilter(page: Page, name: string) {
	const filter = page.getByRole("combobox", { name: "按业务数据标签筛选" });
	await expect(filter).toBeEnabled({ timeout: 15_000 });
	await filter.click();
	await page.getByRole("option", { name, exact: true }).click();
	await page.keyboard.press("Escape");
}

async function readHashTagIds(page: Page): Promise<string[]> {
	return page.evaluate(() => {
		const query = window.location.hash.split("?")[1] || "";
		return new URLSearchParams(query).getAll("tagIds");
	});
}

async function searchAssetsByTags(page: Page, tagIds: string[]): Promise<AssetRefPage> {
	const params = new URLSearchParams({ page: "0", size: "100" });
	for (const tagId of tagIds) params.append("tagIds", tagId);
	return responseData<AssetRefPage>(await page.request.get(`/api/catalog/asset-tags/search?${params.toString()}`));
}

async function expectNoHorizontalOverflow(page: Page) {
	const widths = await page.evaluate(() => ({
		viewport: window.innerWidth,
		document: document.documentElement.scrollWidth,
		body: document.body.scrollWidth,
	}));
	expect(widths.document).toBeLessThanOrEqual(widths.viewport);
	expect(widths.body).toBeLessThanOrEqual(widths.viewport);
}

async function bestEffortCleanup(page: Page, state: CleanupState): Promise<string[]> {
	const failures: string[] = [];
	let categoryId = state.categoryId;
	if (!state.categoryDeleted && !categoryId) {
		try {
			const response = await page.request.get("/api/catalog/tag-categories");
			if (!response.ok()) {
				failures.push(`lookup category ${state.categoryCode}: HTTP ${response.status()} ${response.url()}`);
			} else {
				const envelope = (await response.json()) as { data?: CatalogTagCategory[] };
				categoryId = findCategory(envelope.data || [], state.categoryCode)?.id || "";
			}
		} catch (error) {
			failures.push(`lookup category ${state.categoryCode}: ${String(error)}`);
		}
	}

	const tagIdsByCode = { ...state.tagIdsByCode };
	for (const tagCode of state.tagCodes) {
		if (state.deletedTagCodes.has(tagCode) || tagIdsByCode[tagCode]) continue;
		try {
			const response = await page.request.get(
				`/api/catalog/tags?keyword=${encodeURIComponent(tagCode)}&page=0&size=100`,
			);
			if (!response.ok()) {
				failures.push(`lookup tag ${tagCode}: HTTP ${response.status()} ${response.url()}`);
				continue;
			}
			const envelope = (await response.json()) as { data?: CatalogTagPage };
			const tag = envelope.data?.content?.find((candidate) => candidate.code === tagCode);
			if (tag?.id) tagIdsByCode[tagCode] = tag.id;
		} catch (error) {
			failures.push(`lookup tag ${tagCode}: ${String(error)}`);
		}
	}

	const tagIds = Array.from(new Set(Object.values(tagIdsByCode).filter(Boolean)));
	if (tagIds.length) {
		for (const asset of [
			{ assetType: "DATASET", assetKey: datasetAssetKey },
			{ assetType: "CATALOG_DOMAIN", assetKey: domainAssetKey },
		]) {
			try {
				const response = await page.request.delete("/api/catalog/asset-tags", {
					data: { ...asset, tagIds },
				});
				if (!response.ok()) {
					failures.push(`untag ${asset.assetType}: HTTP ${response.status()} DELETE ${response.url()}`);
				}
			} catch (error) {
				failures.push(`untag ${asset.assetType}: ${String(error)}`);
			}
		}
	}
	for (const [tagCode, tagId] of Object.entries(tagIdsByCode).reverse()) {
		if (state.deletedTagCodes.has(tagCode)) continue;
		try {
			const response = await page.request.delete(`/api/catalog/tags/${encodeURIComponent(tagId)}?force=true`);
			if (!response.ok()) {
				failures.push(`delete tag ${tagCode}: HTTP ${response.status()} DELETE ${response.url()}`);
			}
		} catch (error) {
			failures.push(`delete tag ${tagCode}: ${String(error)}`);
		}
	}
	if (!state.categoryDeleted && categoryId) {
		try {
			const response = await page.request.delete(`/api/catalog/tag-categories/${encodeURIComponent(categoryId)}`);
			if (!response.ok()) {
				failures.push(`delete category ${state.categoryCode}: HTTP ${response.status()} DELETE ${response.url()}`);
			}
		} catch (error) {
			failures.push(`delete category ${state.categoryCode}: ${String(error)}`);
		}
	}
	return failures;
}

test("Sprint-71 real data-tag governance closes CRUD, dataset, domain and shareable search loops", async ({
	browser,
	page,
}) => {
	test.setTimeout(180_000);
	expect(browser.version()).toContain("95.0.4638.0");
	fs.mkdirSync(evidenceDir, { recursive: true });

	const suffix = String(Date.now()).slice(-8);
	const categoryCode = `S71_CAT_${suffix}`;
	const categoryName = `S71 验收分类 ${suffix}`;
	const updatedCategoryName = `S71 验收分类已更新 ${suffix}`;
	const firstTagCode = `S71_CORE_${suffix}`;
	const firstTagName = `S71 核心标签 ${suffix}`;
	const updatedFirstTagName = `S71 核心标签已更新 ${suffix}`;
	const secondTagCode = `S71_SHARED_${suffix}`;
	const secondTagName = `S71 共享标签 ${suffix}`;
	const cleanup: CleanupState = {
		categoryId: "",
		categoryCode,
		categoryDeleted: false,
		tagCodes: [firstTagCode, secondTagCode],
		tagIdsByCode: {},
		deletedTagCodes: new Set(),
	};
	const failures = observeFailures(page);
	const assetMapRequests: string[] = [];
	page.on("request", (request) => {
		const pathname = new URL(request.url()).pathname;
		if (ASSET_MAP_API_PATHS.has(pathname)) assetMapRequests.push(pathname);
	});

	try {
		await page.setViewportSize({ width: 1366, height: 768 });
		const tagDirectoryReady = page.waitForResponse(
			(response) => new URL(response.url()).pathname === "/api/catalog/tag-categories" && response.status() < 400,
		);
		await page.goto("/#/catalog/assets?tab=catalog-tags");
		await tagDirectoryReady;
		await expect(page).toHaveURL(/\/#\/catalog\/assets\?tab=catalog-tags$/);
		await expect(page.getByRole("tab", { name: "数据标签" })).toHaveAttribute("aria-selected", "true");
		expect(assetMapRequests).toEqual([]);

		await page.getByRole("button", { name: "新建标签分类", exact: true }).click();
		let dialog = page.getByRole("dialog").filter({ hasText: "新建标签分类" });
		await dialog.getByLabel("分类名称").fill(categoryName);
		await dialog.getByLabel("分类编码").fill(categoryCode);
		await dialog.getByLabel("分类说明").fill("Sprint-71 Chromium 95 真实验收分类");
		const categoryCreateResponse = waitForApiMutation(page, "POST", "/api/catalog/tag-categories");
		await dialog.getByRole("button", { name: /保\s*存/ }).click();
		const createdCategory = await responseData<CatalogTagCategory>(await categoryCreateResponse);
		expect(createdCategory.id).toBeTruthy();
		cleanup.categoryId = createdCategory.id;
		await expect(page.getByText(categoryName, { exact: false }).first()).toBeVisible();

		await page.getByRole("button", { name: "编辑分类", exact: true }).click();
		dialog = page.getByRole("dialog").filter({ hasText: "编辑标签分类" });
		await dialog.getByLabel("分类名称").fill(updatedCategoryName);
		await dialog.getByRole("button", { name: /保\s*存/ }).click();
		await expect(page.getByText(updatedCategoryName, { exact: false }).first()).toBeVisible();

		await page.getByRole("button", { name: "新建标签", exact: true }).click();
		dialog = page.getByRole("dialog").filter({ hasText: "新建业务数据标签" });
		await dialog.getByLabel("标签名称").fill(firstTagName);
		await dialog.getByLabel("标签编码").fill(firstTagCode);
		await dialog.getByLabel("业务说明").fill("验证标签 CRUD、资产打标和精确检索");
		const firstTagCreateResponse = waitForApiMutation(page, "POST", "/api/catalog/tags");
		await dialog.getByRole("button", { name: /保\s*存/ }).click();
		const createdFirstTag = await responseData<CatalogTag>(await firstTagCreateResponse);
		expect(createdFirstTag.id).toBeTruthy();
		cleanup.tagIdsByCode[firstTagCode] = createdFirstTag.id;
		await expect(page.getByText(firstTagName, { exact: true })).toBeVisible();

		const firstRow = page.locator("tr").filter({ hasText: firstTagName });
		await firstRow.getByRole("button", { name: `编辑标签 ${firstTagName}` }).click();
		dialog = page.getByRole("dialog").filter({ hasText: "编辑业务数据标签" });
		await dialog.getByLabel("标签名称").fill(updatedFirstTagName);
		await dialog.getByRole("button", { name: /保\s*存/ }).click();
		await expect(page.getByText(updatedFirstTagName, { exact: true })).toBeVisible();

		await page.getByRole("button", { name: "新建标签", exact: true }).click();
		dialog = page.getByRole("dialog").filter({ hasText: "新建业务数据标签" });
		await dialog.getByLabel("标签名称").fill(secondTagName);
		await dialog.getByLabel("标签编码").fill(secondTagCode);
		await dialog.getByLabel("业务说明").fill("验证重复 tagIds 分享 URL 的 AND 语义");
		const secondTagCreateResponse = waitForApiMutation(page, "POST", "/api/catalog/tags");
		await dialog.getByRole("button", { name: /保\s*存/ }).click();
		const createdSecondTag = await responseData<CatalogTag>(await secondTagCreateResponse);
		expect(createdSecondTag.id).toBeTruthy();
		cleanup.tagIdsByCode[secondTagCode] = createdSecondTag.id;
		await expect(page.getByText(secondTagName, { exact: true })).toBeVisible();

		const categories = await responseData<CatalogTagCategory[]>(await page.request.get("/api/catalog/tag-categories"));
		const category = findCategory(categories, categoryCode);
		expect(category).not.toBeNull();
		expect(category?.id).toBe(cleanup.categoryId);
		const tags = await responseData<CatalogTagPage>(
			await page.request.get(`/api/catalog/tags?categoryId=${encodeURIComponent(cleanup.categoryId)}&page=0&size=100`),
		);
		const firstTag = tags.content.find((tag) => tag.code === firstTagCode);
		const secondTag = tags.content.find((tag) => tag.code === secondTagCode);
		expect(firstTag).toBeDefined();
		expect(secondTag).toBeDefined();
		expect(firstTag?.id).toBe(cleanup.tagIdsByCode[firstTagCode]);
		expect(secondTag?.id).toBe(cleanup.tagIdsByCode[secondTagCode]);

		await page.screenshot({
			path: path.join(evidenceDir, "tag-directory-crud-desktop-1366x768.png"),
			fullPage: true,
		});

		await page.goto(`/#/catalog/datasets/${datasetId}?tab=overview`);
		await expect(page.getByText("业务数据标签", { exact: true }).first()).toBeVisible();
		await addBusinessTag(page, updatedFirstTagName);
		await addBusinessTag(page, secondTagName);
		await page.reload();
		await expect(page.getByRole("list", { name: "业务数据标签" })).toContainText(updatedFirstTagName);
		await expect(page.getByRole("list", { name: "业务数据标签" })).toContainText(secondTagName);
		await page.screenshot({
			path: path.join(evidenceDir, "dataset-tagging-desktop-1366x768.png"),
			fullPage: true,
		});

		await page.goto(`/#/governance/subjects?active=${domainId}&domainId=${domainId}&tab=governance`);
		await expect(page.getByText(domainName, { exact: true }).first()).toBeVisible();
		await addBusinessTag(page, updatedFirstTagName);
		await page.reload();
		await expect(page.getByRole("list", { name: "业务数据标签" })).toContainText(updatedFirstTagName);
		await page.screenshot({
			path: path.join(evidenceDir, "catalog-domain-tagging-desktop-1366x768.png"),
			fullPage: true,
		});

		const firstTagId = cleanup.tagIdsByCode[firstTagCode];
		const secondTagId = cleanup.tagIdsByCode[secondTagCode];
		const firstTagMatches = await searchAssetsByTags(page, [firstTagId]);
		expect(firstTagMatches.total).toBe(2);
		expect(firstTagMatches.content).toEqual(
			expect.arrayContaining([
				{ assetType: "DATASET", assetKey: datasetAssetKey },
				{ assetType: "CATALOG_DOMAIN", assetKey: domainAssetKey },
			]),
		);

		await page.goto("/#/catalog/search");
		await selectBusinessTagFilter(page, updatedFirstTagName);
		await selectBusinessTagFilter(page, secondTagName);
		await expect(page).toHaveURL(new RegExp(`tagIds=${firstTagId}.*tagIds=${secondTagId}`));
		expect(await readHashTagIds(page)).toEqual([firstTagId, secondTagId]);

		const twoTagMatches = await searchAssetsByTags(page, [firstTagId, secondTagId]);
		expect(twoTagMatches.total).toBe(1);
		expect(twoTagMatches.content).toEqual([{ assetType: "DATASET", assetKey: datasetAssetKey }]);
		await expect(page.getByText(datasetName, { exact: false }).first()).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText(datasetAssetKey, { exact: true }).first()).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText(domainAssetKey, { exact: true })).toHaveCount(0);
		await page.reload();
		await expect(page).toHaveURL(new RegExp(`tagIds=${firstTagId}.*tagIds=${secondTagId}`));
		expect(await readHashTagIds(page)).toEqual([firstTagId, secondTagId]);
		await expect(page.getByText(datasetName, { exact: false }).first()).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText(datasetAssetKey, { exact: true }).first()).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText(domainAssetKey, { exact: true })).toHaveCount(0);
		expect((await searchAssetsByTags(page, [firstTagId, secondTagId])).content).toEqual([
			{ assetType: "DATASET", assetKey: datasetAssetKey },
		]);
		await page.screenshot({
			path: path.join(evidenceDir, "tag-filter-share-url-desktop-1366x768.png"),
			fullPage: true,
		});

		await page.setViewportSize({ width: 390, height: 844 });
		await page.goto(`/#/governance/subjects?active=${domainId}&domainId=${domainId}&tab=governance`);
		await expect(page.getByText(domainName, { exact: true }).first()).toBeVisible();
		await expect(page.getByRole("list", { name: "业务数据标签" })).toContainText(updatedFirstTagName);
		await expectNoHorizontalOverflow(page);
		await page.screenshot({
			path: path.join(evidenceDir, "catalog-domain-tagging-narrow-390x844.png"),
			fullPage: true,
		});

		await page.setViewportSize({ width: 1366, height: 768 });
		await page.goto(`/#/catalog/datasets/${datasetId}?tab=overview`);
		await removeBusinessTag(page, updatedFirstTagName);
		await removeBusinessTag(page, secondTagName);
		await page.goto(`/#/governance/subjects?active=${domainId}&domainId=${domainId}&tab=governance`);
		await removeBusinessTag(page, updatedFirstTagName);

		await page.goto("/#/catalog/assets?tab=catalog-tags");
		await expect(page.getByRole("tab", { name: "数据标签" })).toHaveAttribute("aria-selected", "true");
		await page.getByText(updatedCategoryName, { exact: false }).first().click();
		for (const tag of [
			{ code: firstTagCode, name: updatedFirstTagName },
			{ code: secondTagCode, name: secondTagName },
		]) {
			const row = page.locator("tr").filter({ hasText: tag.name });
			await row.getByRole("button", { name: `删除标签 ${tag.name}` }).click();
			const confirm = page.getByRole("dialog").filter({ hasText: /确认删除(?:已使用的)?标签/ });
			await confirm.getByRole("button", { name: /^(?:删\s*除|删除标签及关系)$/ }).click();
			await expect(page.getByText(tag.name, { exact: true })).toHaveCount(0);
			cleanup.deletedTagCodes.add(tag.code);
			delete cleanup.tagIdsByCode[tag.code];
		}
		await page.getByRole("button", { name: "删除分类", exact: true }).click();
		dialog = page.getByRole("dialog").filter({ hasText: "确认删除标签分类" });
		await dialog.getByRole("button", { name: /删\s*除/ }).click();
		await expect(page.getByText(updatedCategoryName, { exact: false })).toHaveCount(0);
		cleanup.categoryId = "";
		cleanup.categoryDeleted = true;

		expectNoUnexpectedFailures(failures);
	} finally {
		const cleanupFailures = await bestEffortCleanup(page, cleanup);
		expect.soft(cleanupFailures, "Sprint-71 cleanup HTTP failures").toEqual([]);
	}
});
