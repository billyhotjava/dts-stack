import { readFileSync } from "node:fs";
import { expect, type Page, test } from "@playwright/test";
import { installSprint79ProductionReadOnlyBarrier } from "./support/sprint79ProductionReadOnly";

type SeedNode = {
	key: string;
	path: string;
	titleKey: string;
	title: string;
	icon?: string;
	externalLink?: string;
	children?: SeedNode[];
};

type StoredAuthState = {
	cookies?: Array<{
		name: string;
		value: string;
		httpOnly?: boolean;
		expires?: number;
	}>;
	origins?: Array<{
		localStorage?: Array<{ name: string; value: string }>;
	}>;
};

type StoredUserStore = {
	state?: Record<string, unknown> & { userToken?: Record<string, unknown> };
};

const seed = JSON.parse(
	readFileSync(
		new URL("../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: SeedNode[] };

const storedAuth = JSON.parse(readFileSync(new URL("./.auth/user.json", import.meta.url), "utf8")) as StoredAuthState;

function menuTree() {
	let menuId = 1;
	const mapNode = (node: SeedNode, sectionKey: string, parentPath = ""): Record<string, unknown> => {
		const path = node.externalLink || `${parentPath}/${node.path}`.replace(/\/{2,}/g, "/");
		return {
			id: menuId++,
			name: node.titleKey,
			displayName: node.title,
			path,
			icon: node.icon,
			deleted: false,
			metadata: JSON.stringify({
				key: node.key,
				sectionKey,
				entryKey: node.key,
				titleKey: node.titleKey,
				title: node.title,
				icon: node.icon,
			}),
			children: (node.children || []).map((child) => mapNode(child, sectionKey, path)),
		};
	};
	return seed.portalNavSections.map((node) => mapNode(node, node.key));
}

async function installLocalAuthenticatedState(page: Page) {
	const baseURL = new URL(process.env.E2E_BASE_URL || "http://127.0.0.1:4182");
	const entries = storedAuth.origins?.flatMap((origin) => origin.localStorage || []) || [];
	const userStoreEntry = entries.find((entry) => entry.name === "dts.platform.userStore");
	if (!userStoreEntry || !storedAuth.cookies?.length) {
		throw new Error("e2e/.auth/user.json must contain an authenticated user store and portal session cookies");
	}
	const now = Date.now();
	const userStore = JSON.parse(userStoreEntry.value) as StoredUserStore;
	userStore.state = {
		...(userStore.state || {}),
		userToken: {
			...(userStore.state?.userToken || {}),
			authenticated: true,
			tokenExpiresAt: now + 60 * 60 * 1000,
		},
	};
	const localEntries = entries.map((entry) =>
		entry.name === "dts.platform.userStore"
			? { ...entry, value: JSON.stringify(userStore) }
			: entry.name.startsWith("dts.platform.session.")
				? { ...entry, value: String(now) }
				: entry,
	);
	await page.context().addCookies(
		storedAuth.cookies.map((cookie) => ({
			name: cookie.name,
			value: cookie.value,
			url: baseURL.origin,
			httpOnly: cookie.httpOnly === true,
			secure: false,
			sameSite: "Lax" as const,
			expires: cookie.expires && cookie.expires * 1000 > now ? cookie.expires : -1,
		})),
	);
	await page.addInitScript((storageEntries) => {
		for (const entry of storageEntries) window.localStorage.setItem(entry.name, entry.value);
	}, localEntries);
}

async function installPlatformReadProxy(page: Page) {
	const platformHost = process.env.E2E_PLATFORM_HOST?.trim();
	if (!platformHost) throw new Error("E2E_PLATFORM_HOST is required for the local read-only regression");
	await page.route(
		(url) => url.pathname.startsWith("/api/"),
		async (route) => {
			const request = route.request();
			const pathname = new URL(request.url()).pathname;
			if (!new Set(["GET", "HEAD", "OPTIONS"]).has(request.method()) && pathname !== "/api/workbench/audit") {
				await route.fallback();
				return;
			}
			const upstream = new URL(request.url());
			upstream.protocol = "https:";
			upstream.hostname = "127.0.0.1";
			upstream.port = "";
			const response = await route.fetch({
				url: upstream.toString(),
				headers: { ...request.headers(), host: platformHost },
			});
			await route.fulfill({ response });
		},
	);
}

test("opens a draft from list management without reloading the workbench catalog", async ({ page }) => {
	test.setTimeout(90_000);
	await page.setViewportSize({ width: 1366, height: 768 });
	await installLocalAuthenticatedState(page);
	const failures = await installSprint79ProductionReadOnlyBarrier(page);
	await installPlatformReadProxy(page);
	await page.route(/\/api\/menu\/tree(?:\?.*)?$/, (route) =>
		route.fulfill({
			status: 200,
			contentType: "application/json",
			body: JSON.stringify({ status: 200, data: menuTree(), message: "OK" }),
		}),
	);
	const consoleErrors: string[] = [];
	const requests: Array<{ method: string; pathname: string; resourceType: string }> = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("request", (request) => {
		requests.push({
			method: request.method(),
			pathname: new URL(request.url()).pathname,
			resourceType: request.resourceType(),
		});
	});

	await page.goto("/#/data-modeling/dimensions/workbench");
	await expect(page.locator("main.dmx-workbench-page h1")).toHaveText("维度建模", { timeout: 20_000 });
	await expect(page.getByRole("button", { name: "列表管理" })).toBeVisible();
	await page.waitForLoadState("networkidle", { timeout: 20_000 });
	const modelListReadsBefore = requests.filter(
		(request) => request.method === "GET" && request.pathname === "/api/modeling/model-specs",
	).length;
	const lifecycleReadsBefore = requests.filter(
		(request) => request.method === "GET" && /\/api\/modeling\/model-specs\/[^/]+\/lifecycle$/.test(request.pathname),
	).length;
	const documentReadsBefore = requests.filter((request) => request.resourceType === "document").length;

	await page.getByRole("button", { name: "列表管理" }).click();
	await expect(page.getByRole("heading", { name: "模型列表" })).toBeVisible();
	await expect(page.getByRole("button", { name: "查看" }).first()).toBeVisible();
	await page.getByLabel("搜索模型列表").fill("日期维度表");
	const targetRow = page
		.locator(".dmx-model-list-table tbody tr")
		.filter({ hasText: "日期维度表" })
		.filter({ has: page.getByRole("button", { name: "编辑" }) });
	await expect(targetRow).toHaveCount(1);
	await targetRow.getByRole("button", { name: "编辑" }).click();

	const editor = page.locator(".dmx-model-editor");
	await expect(editor.getByRole("heading", { name: "基本信息" })).toBeVisible({ timeout: 20_000 });
	await expect(page).toHaveURL(/#\/data-modeling\/dimensions\/workbench\?modelSpecId=/);
	const tableName = editor.getByLabel("表名", { exact: true });
	await expect(tableName).toBeEnabled();
	await expect(editor.getByLabel("实现来源")).toBeVisible();
	if ((await tableName.inputValue()).trim()) {
		await expect(tableName).toHaveValue(/^[a-z][a-z0-9_]*$/);
	} else {
		await expect(editor).toContainText("历史草稿尚未保存物理表名，请补录后保存");
	}
	await expect
		.poll(
			() =>
				requests.filter(
					(request) =>
						request.method === "GET" && /\/api\/modeling\/model-specs\/[^/]+\/lifecycle$/.test(request.pathname),
				).length,
			{ message: "selecting one model must load exactly one model lifecycle" },
		)
		.toBe(lifecycleReadsBefore + 1);
	expect(
		requests.filter((request) => request.method === "GET" && request.pathname === "/api/modeling/model-specs"),
		"model selection must not reload the full model catalog",
	).toHaveLength(modelListReadsBefore);
	expect(
		requests.filter((request) => request.resourceType === "document"),
		"model selection must stay in the current document",
	).toHaveLength(documentReadsBefore);
	await expect(page.getByText("正在加载模型工作台")).toHaveCount(0);

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(tableName).toBeVisible();
	const widths = await page.evaluate(() => ({
		viewport: document.documentElement.clientWidth,
		document: document.documentElement.scrollWidth,
	}));
	expect(widths.document).toBeLessThanOrEqual(widths.viewport + 1);
	expect(failures.modelingWrites, "unexpected API writes").toEqual([]);
	expect(failures.pageErrors, "page errors").toEqual([]);
	expect(failures.requestFailures, "request failures").toEqual([]);
	expect(failures.httpFailures, "HTTP API failures").toEqual([]);
	expect(consoleErrors, "console errors").toEqual([]);
});
