import { mkdirSync, readFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type Page, type Route, test } from "@playwright/test";

type Scenario = "success" | "empty" | "error";

type SeedNode = {
	key: string;
	path: string;
	titleKey: string;
	title: string;
	icon?: string;
	externalLink?: string;
	children?: SeedNode[];
};

type PortalDirectory = {
	id: number;
	name: string;
	parent_id: number | null;
	sort_order: number;
};

type PortalBinding = {
	id: number;
	directory_id: number;
	content_type: "SCREEN" | "DASHBOARD";
	content_id: number;
	sort_order: number;
};

const menuSeed = JSON.parse(
	readFileSync(
		new URL("../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: SeedNode[] };

const menuEvidencePath = fileURLToPath(
	new URL(
		"../../../worklog/v2.2.3/sprint-96-202608-data-portal/it/evidence/chrome95/menu-separation-1366x768.png",
		import.meta.url,
	),
);

const screens = [
	{
		id: 101,
		name: "项目进度总览",
		description: "项目节点与进度",
		classification: "INTERNAL",
		publishedVersionNo: 3,
		publishedAt: "2026-08-20T08:00:00Z",
		canRead: true,
	},
	{
		id: 102,
		name: "项目风险跟踪",
		description: "项目风险变化",
		classification: "INTERNAL",
		publishedVersionNo: 2,
		publishedAt: "2026-08-20T08:10:00Z",
		canRead: true,
	},
];

const dashboards = [
	{
		id: 201,
		name: "财务执行看板",
		description: "预算执行",
		lifecycle_status: "PUBLISHED",
		published_revision_id: 9,
		registration_status: "AVAILABLE",
		version_no: 4,
	},
];

function initialPortal() {
	return {
		directories: [
			{ id: 11, name: "一月", parent_id: null, sort_order: 10 },
			{ id: 12, name: "项目管理", parent_id: 11, sort_order: 10 },
			{ id: 13, name: "财务管理", parent_id: 11, sort_order: 20 },
			{ id: 21, name: "二月", parent_id: null, sort_order: 20 },
		] satisfies PortalDirectory[],
		items: [
			{ id: 31, directory_id: 12, content_type: "SCREEN", content_id: 101, sort_order: 10 },
			{ id: 32, directory_id: 13, content_type: "DASHBOARD", content_id: 201, sort_order: 10 },
		] satisfies PortalBinding[],
	};
}

function platformEnvelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

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
	return menuSeed.portalNavSections.map((node) => mapNode(node, node.key));
}

async function json(route: Route, data: unknown, envelope = false, status = 200) {
	await route.fulfill({
		status,
		contentType: "application/json",
		body: envelope ? platformEnvelope(data) : JSON.stringify(data),
	});
}

async function installIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "data-portal-viewer",
						fullName: "数据门户用户",
						roles: ["ROLE_INST_DATA_OWNER"],
						permissions: ["read", "write"],
						enabled: true,
					},
					userToken: { accessToken: "data-portal-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page, scenario: Scenario, canWrite = false) {
	const portal = scenario === "empty" ? { directories: [] as PortalDirectory[], items: [] as PortalBinding[] } : initialPortal();
	await page.route(/^https?:\/\/[^/]+\/(?:bi\/api\/|api\/)/, async (route) => {
		const request = route.request();
		const url = new URL(request.url());
		if (url.pathname === "/api/session/status") {
			return json(route, { authenticated: true, remainingSeconds: 3600 }, true);
		}
		if (url.pathname === "/api/menu/tree") return json(route, [], true);
		if (url.pathname === "/api/reports/visit") return json(route, { recorded: true }, true);
		if (url.pathname === "/bi/api/data-portal") {
			if (scenario === "error") return json(route, { message: "portal unavailable" }, false, 503);
			return json(route, { can_write: canWrite, directories: portal.directories, items: portal.items });
		}
		if (url.pathname === "/bi/api/data-portal/directories" && request.method() === "POST") {
			const body = request.postDataJSON() as { name: string; parent_id: number | null };
			const id = Math.max(0, ...portal.directories.map((item) => item.id)) + 1;
			const siblings = portal.directories.filter((item) => item.parent_id === body.parent_id);
			const created = { id, name: body.name, parent_id: body.parent_id, sort_order: (siblings.at(-1)?.sort_order ?? 0) + 10 };
			portal.directories.push(created);
			return json(route, created, false, 201);
		}
		const directoryMatch = url.pathname.match(/^\/bi\/api\/data-portal\/directories\/(\d+)$/);
		if (directoryMatch && request.method() === "PUT") {
			const id = Number(directoryMatch[1]);
			const body = request.postDataJSON() as { name: string; parent_id: number | null };
			const current = portal.directories.find((item) => item.id === id);
			if (!current) return json(route, { message: "not found" }, false, 404);
			Object.assign(current, body);
			return json(route, current);
		}
		if (directoryMatch && request.method() === "DELETE") {
			const id = Number(directoryMatch[1]);
			portal.directories = portal.directories.filter((item) => item.id !== id);
			return route.fulfill({ status: 204 });
		}
		if (url.pathname === "/bi/api/data-portal/items" && request.method() === "POST") {
			const body = request.postDataJSON() as Omit<PortalBinding, "id" | "sort_order">;
			const id = Math.max(0, ...portal.items.map((item) => item.id)) + 1;
			const siblings = portal.items.filter((item) => item.directory_id === body.directory_id);
			const created: PortalBinding = { ...body, id, sort_order: (siblings.at(-1)?.sort_order ?? 0) + 10 };
			portal.items.push(created);
			return json(route, created, false, 201);
		}
		const bindingMatch = url.pathname.match(/^\/bi\/api\/data-portal\/items\/(\d+)$/);
		if (bindingMatch && request.method() === "DELETE") {
			const id = Number(bindingMatch[1]);
			portal.items = portal.items.filter((item) => item.id !== id);
			return route.fulfill({ status: 204 });
		}
		if (url.pathname === "/bi/api/screens") {
			expect(url.searchParams.get("publishedOnly")).toBe("true");
			return json(route, screens);
		}
		if (url.pathname === "/bi/api/dashboard") return json(route, dashboards);
		const dashboardMatch = url.pathname.match(/^\/bi\/api\/dashboard\/(\d+)$/);
		if (dashboardMatch) {
			const dashboard = dashboards.find((item) => String(item.id) === dashboardMatch[1]) ?? dashboards[0];
			return json(route, { ...dashboard, parameters: [], ordered_cards: [] });
		}
		const detailMatch = url.pathname.match(/^\/bi\/api\/screens\/(\d+)$/);
		if (detailMatch) {
			expect(url.searchParams.get("mode")).toBe("published");
			expect(url.searchParams.get("fallbackDraft")).toBe("false");
			const screen = screens.find((item) => String(item.id) === detailMatch[1]) ?? screens[0];
			return json(route, {
				...screen,
				sourceMode: "published",
				width: 1920,
				height: 1080,
				backgroundColor: "#08121f",
				theme: "dark-command",
				components: [],
				globalVariables: [],
			});
		}
		return json(route, url.pathname.startsWith("/bi/api/") ? {} : {}, !url.pathname.startsWith("/bi/api/"));
	});
	return portal;
}

async function installMenuApis(page: Page) {
	await page.route(/^https?:\/\/[^/]+\/(?:bi\/api\/|api\/)/, async (route) => {
		const url = new URL(route.request().url());
		if (url.pathname === "/api/session/status") {
			return json(route, { authenticated: true, remainingSeconds: 3600 }, true);
		}
		if (url.pathname === "/api/keycloak/auth/refresh") {
			return json(route, { authenticated: true, expiresIn: 3600, portalExpiresIn: 3600 }, true);
		}
		if (url.pathname === "/api/menu/tree") return json(route, menuTree(), true);
		if (url.pathname === "/api/infra/screen-fonts") return json(route, [], true);
		if (url.pathname === "/api/reports/visit") return json(route, { recorded: true }, true);
		if (url.pathname === "/bi/api/data-portal") return json(route, { can_write: false, directories: [], items: [] });
		if (url.pathname === "/bi/api/screens" || url.pathname === "/bi/api/dashboard") return json(route, []);
		return json(route, {}, !url.pathname.startsWith("/bi/api/"));
	});
}

function collectFailures(page: Page) {
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	const failedResponses: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("response", (response) => {
		if (response.status() >= 400) failedResponses.push(`${response.status()} ${new URL(response.url()).pathname}`);
	});
	return { consoleErrors, pageErrors, failedResponses };
}

test("navigates nested portal menus and renders both screen and dashboard content", async ({ page }, testInfo) => {
	await installIdentity(page);
	await installApis(page, "success");
	const failures = collectFailures(page);

	await page.goto("/#/bi/portal");
	await expect(page.getByTestId("data-portal-page")).toBeVisible();
	await expect(page.getByRole("tree").getByText("一月", { exact: true })).toBeVisible();
	await expect(page.getByRole("tree").getByText("项目管理", { exact: true })).toBeHidden();
	await page.getByRole("tree").getByText("一月", { exact: true }).click();
	await expect(page.getByRole("tree").getByText("项目管理", { exact: true })).toBeVisible();
	await page.getByRole("tree").getByText("项目管理", { exact: true }).click();
	await page.getByRole("tree").getByText("项目进度总览", { exact: true }).click();
	await expect(page).toHaveURL(/\/bi\/portal\/item\/31$/);

	const runtime = page.getByTestId("data-portal-runtime");
	await expect(runtime).toHaveAttribute("src", /mode=published/);
	await expect(runtime).toHaveAttribute("src", /embed=1/);
	await expect(runtime.contentFrame().getByTestId("analytics-screen-preview")).toBeVisible();

	await page.getByRole("tree").getByText("财务管理", { exact: true }).click();
	await page.getByRole("tree").getByText("财务执行看板", { exact: true }).click();
	await expect(page).toHaveURL(/\/bi\/portal\/item\/32$/);
	await expect(page.getByTestId("analytics-dashboard-detail")).toBeVisible();
	await expect(page.getByText("该看板暂未配置图表", { exact: true })).toBeVisible();

	await page.screenshot({ path: testInfo.outputPath("data-portal-desktop.png"), fullPage: true });
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});

test("creates nested custom menus and binds another published screen", async ({ page }, testInfo) => {
	await installIdentity(page);
	await installApis(page, "success", true);
	const failures = collectFailures(page);

	await page.goto("/#/bi/portal");
	await page.getByRole("button", { name: "门户编排" }).click();
	await expect(page.getByText("先建立月份、业务主题等多级目录")).toBeVisible();
	const drawer = page.getByRole("dialog", { name: "门户编排" });

	await page.getByRole("button", { name: "新建一级目录" }).click();
	let dialog = page.getByRole("dialog", { name: "新建门户目录" });
	await dialog.getByLabel("目录名称").fill("三月");
	await dialog.getByRole("button", { name: /保\s*存/ }).click();
	await expect(drawer.getByRole("tree").getByText("三月", { exact: true })).toBeVisible();

	await drawer.getByRole("tree").getByText("三月", { exact: true }).click();
	await page.getByRole("button", { name: "新建下级目录" }).click();
	dialog = page.getByRole("dialog", { name: "新建门户目录" });
	await dialog.getByLabel("目录名称").fill("项目复盘");
	await dialog.getByRole("button", { name: /保\s*存/ }).click();
	await expect(drawer.getByRole("tree").getByText("项目复盘", { exact: true })).toBeVisible();

	await drawer.getByRole("tree").getByText("项目复盘", { exact: true }).click();
	await page.getByRole("button", { name: "添加大屏或看板" }).click();
	dialog = page.getByRole("dialog", { name: "向“项目复盘”添加内容" });
	await dialog.getByRole("combobox", { name: "已发布内容" }).click();
	await page.getByText("项目风险跟踪", { exact: true }).last().click();
	await dialog.getByRole("button", { name: "加入门户" }).click();
	await expect(drawer.getByRole("treeitem", { name: /项目风险跟踪/ })).toBeVisible();

	await page.screenshot({ path: testInfo.outputPath("data-portal-editor.png"), fullPage: true });
	await page.keyboard.press("Escape");
	await page.getByRole("tree").first().getByText("三月", { exact: true }).click();
	await page.getByRole("tree").first().getByText("项目复盘", { exact: true }).click();
	await page.getByRole("tree").first().getByText("项目风险跟踪", { exact: true }).click();
	await expect(page).toHaveURL(/\/bi\/portal\/item\/33$/);
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});

test("shows an actionable unconfigured state at a narrow viewport", async ({ page }, testInfo) => {
	await page.setViewportSize({ width: 820, height: 720 });
	await installIdentity(page);
	await installApis(page, "empty");
	const failures = collectFailures(page);

	await page.goto("/#/bi/portal");
	await expect(page.getByTestId("data-portal-empty")).toBeVisible();
	await expect(page.getByText("请联系数据管理员完成门户编排。")).toBeVisible();
	await expect(page.getByTestId("data-portal-loading")).toHaveCount(0);
	await page.evaluate(() => new Promise<void>((resolve) => {
		requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
	}));
	await page.screenshot({ path: testInfo.outputPath("data-portal-empty-narrow.png"), fullPage: true });
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});

test("shows a retryable directory error without rendering a runtime", async ({ page }) => {
	await installIdentity(page);
	await installApis(page, "error");

	await page.goto("/#/bi/portal");
	await expect(page.getByTestId("data-portal-error")).toBeVisible();
	await expect(page.getByRole("button", { name: "重新加载" })).toBeVisible();
	await expect(page.getByTestId("data-portal-runtime")).toHaveCount(0);
});

test("keeps screen management direct and nests the data portal under BI analysis", async ({ page }) => {
	await installIdentity(page);
	await installMenuApis(page);
	const failures = collectFailures(page);

	await page.goto("/#/bi/screens");
	await expect(page.getByRole("heading", { name: "大屏管理", exact: true })).toBeVisible();
	const navigation = page.getByRole("navigation").first();
	await navigation.getByText("数据分析与服务", { exact: true }).last().click();
	const screenLink = navigation.locator('a[href*="/bi/screens"]').last();
	await expect(screenLink).toBeVisible();
	await expect(screenLink).toContainText("大屏管理");

	const businessIntelligence = navigation.getByText("商业智能应用", { exact: true }).last();
	if (!(await navigation.getByText("BI 分析", { exact: true }).last().isVisible())) {
		await businessIntelligence.click();
	}
	const biAnalysis = navigation.getByText("BI 分析", { exact: true }).last();
	if (!(await navigation.getByText("数据门户", { exact: true }).last().isVisible())) {
		await biAnalysis.click();
	}

	const portalLink = navigation.locator('a[href*="/bi/portal"]').last();
	await expect(portalLink).toBeVisible();
	await expect(portalLink).toContainText("数据门户");
	mkdirSync(dirname(menuEvidencePath), { recursive: true });
	await page.screenshot({ path: menuEvidencePath, fullPage: true });

	await portalLink.click();
	await expect(page).toHaveURL(/\/#\/bi\/portal$/);
	await expect(page.getByRole("heading", { name: "数据门户", exact: true })).toBeVisible();

	await screenLink.click();
	await expect(page).toHaveURL(/\/#\/bi\/screens$/);
	await expect(page.getByRole("heading", { name: "大屏管理", exact: true })).toBeVisible();
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});
