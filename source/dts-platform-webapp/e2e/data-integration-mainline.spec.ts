import { readFileSync } from "node:fs";
import { expect, type Page, test } from "@playwright/test";

const executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH;

test.use({
	baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:3001",
	...(executablePath ? { launchOptions: { executablePath } } : {}),
});

type SeedNode = {
	key: string;
	path: string;
	icon?: string;
	titleKey: string;
	title: string;
	externalLink?: string;
	children?: SeedNode[];
};

const seed = JSON.parse(
	readFileSync(
		new URL("../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: SeedNode[] };

let menuId = 1;
const toMenuTree = (node: SeedNode, sectionKey: string, parentPath = ""): Record<string, unknown> => {
	const path = node.externalLink || `${parentPath}/${node.path}`.replace(/\/{2,}/g, "/");
	return {
		id: menuId++,
		name: node.titleKey,
		displayName: node.title,
		path,
		icon: node.icon,
		deleted: false,
		metadata: JSON.stringify({ key: node.key, sectionKey, titleKey: node.titleKey, title: node.title }),
		children: (node.children ?? []).map((child) => toMenuTree(child, sectionKey, path)),
	};
};

const menuTree = seed.portalNavSections.map((node) => toMenuTree(node, node.key));

async function installReadOnlyFixture(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		window.localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "data-integration-e2e",
						fullName: "Data Integration E2E",
						roles: ["ROLE_OP_ADMIN"],
						permissions: [],
						enabled: true,
					},
					userToken: { authenticated: true },
				},
				version: 0,
			}),
		);
		window.localStorage.setItem("dts.platform.session.loginTs", now);
		window.localStorage.setItem("dts.platform.session.lastActivity", now);
	});

	await page.route("**/runtime-config.js", (route) =>
		route.fulfill({ status: 200, contentType: "application/javascript", body: "window.__RUNTIME_CONFIG__ = {};" }),
	);
	await page.route(/^https?:\/\/[^/]+\/(?:(?:platform|admin|analytics|bi)\/)?api\//, (route) => {
		const { pathname } = new URL(route.request().url());
		let data: unknown = { authenticated: true };
		if (pathname.endsWith("/infra/data-source-selections")) data = { items: [] };
		else if (pathname.endsWith("/ingestion/tasks/list")) {
			data = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };
		} else if (pathname.endsWith("/infra/data-sources")) data = [];
		else if (pathname.endsWith("/menu/tree")) data = menuTree;
		return route.fulfill({
			status: 200,
			contentType: "application/json",
			body: JSON.stringify({ status: 200, data, message: "OK" }),
		});
	});
}

test("数据集成原型主线保留表格概览、连接管理与旧详情重定向", async ({ page }) => {
	await page.setViewportSize({ width: 1366, height: 768 });
	const pageErrors: string[] = [];
	const consoleErrors: string[] = [];
	const failedRequests: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("requestfailed", (request) => failedRequests.push(`${request.method()} ${request.url()}`));
	await installReadOnlyFixture(page);

	await page.goto("/#/foundation/data-sources");
	await expect(
		page.getByRole("heading", { name: "接入概览", exact: true }),
		`页面未加载。page errors: ${pageErrors.join(" | ")}; console errors: ${consoleErrors.join(" | ")}; failed requests: ${failedRequests.join(" | ")}`,
	).toBeVisible();
	const overviewTable = page.getByRole("table").first();
	await expect(overviewTable).toBeVisible();
	for (const heading of [
		"接入名称",
		"接入方式",
		"来源 / 资源",
		"同步模式",
		"生命周期",
		"健康状态",
		"最近运行",
		"负责人 / 密级",
		"操作",
	]) {
		await expect(overviewTable.getByRole("columnheader", { name: heading, exact: true })).toBeVisible();
	}

	await page.goto("/#/foundation/connections");
	await expect(page.getByRole("heading", { name: "连接管理", exact: true })).toBeVisible();
	const connectionsTable = page.getByRole("table").first();
	await expect(connectionsTable).toBeVisible();
	for (const heading of ["名称", "类型", "连接器", "地址 / JDBC", "归属部门", "状态", "最近验证", "操作"]) {
		await expect(connectionsTable.getByRole("columnheader", { name: heading, exact: true })).toBeVisible();
	}

	await page.goto("/#/foundation/data-sources/e2e-legacy-redirect");
	await expect(page).toHaveURL(/\/#\/foundation\/connections\/e2e-legacy-redirect(?:[?#]|$)/);

	await page.goto("/#/explore/etl/orchestration");
	await expect(page).toHaveURL(/\/#\/foundation\/data-sources(?:[?#]|$)/);
	await expect(page.getByRole("heading", { name: "接入概览", exact: true })).toBeVisible();
	await expect(page.getByText("任务编排", { exact: true })).toHaveCount(0);
	await page.screenshot({ path: "/tmp/sprint103-data-integration-1366x768.png", fullPage: true });

	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto("/#/foundation/data-sources");
	await expect(page.getByRole("heading", { name: "接入概览", exact: true })).toBeVisible();
	await expect(page.getByText("任务编排", { exact: true })).toHaveCount(0);
	await page.screenshot({ path: "/tmp/sprint103-data-integration-390x844.png", fullPage: true });

	expect(pageErrors, `页面运行时异常：\n${pageErrors.join("\n")}`).toEqual([]);
	expect(consoleErrors, `控制台异常：\n${consoleErrors.join("\n")}`).toEqual([]);
	expect(failedRequests, `失败请求：\n${failedRequests.join("\n")}`).toEqual([]);
});
