import { mkdirSync } from "node:fs";
import path from "node:path";
import { expect, type Page, type Route, test } from "@playwright/test";

const SCREEN_ID = "data-workflow-e2e";
const APP_ORIGIN = new URL(process.env.E2E_BASE_URL ?? "http://127.0.0.1:4173").origin;
const EVIDENCE_DIR = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/data-binding",
);

const screen = {
	id: SCREEN_ID,
	name: "数据配置流程验收大屏",
	description: "",
	width: 800,
	height: 450,
	theme: "legacy-dark",
	backgroundColor: "#0f172a",
	backgroundImage: null,
	classification: "INTERNAL",
	components: [
		{
			id: "sales-chart",
			type: "bar-chart",
			name: "区域销售额",
			x: 80,
			y: 60,
			width: 560,
			height: 300,
			zIndex: 1,
			locked: false,
			visible: true,
			config: {
				title: "区域销售额",
				_fieldMapping: { dimension: "region", measures: ["amount"] },
			},
			dataSource: {
				type: "card",
				sourceType: "card",
				cardConfig: { cardId: 42 },
			},
		},
	],
	globalVariables: [],
	schemaVersion: 1,
	sourceMode: "draft",
	updatedAt: "2026-08-29T03:45:00.000Z",
	canRead: true,
	canEdit: true,
	canPublish: true,
	canManage: true,
	canDelete: true,
	isOwner: true,
};

function fulfillJson(route: Route, body: unknown, status = 200) {
	return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

function fulfillPlatformJson(route: Route, data: unknown) {
	return fulfillJson(route, { status: 200, data, message: "OK" });
}

async function installIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "screen-data-workflow-e2e",
						fullName: "大屏数据流程验收用户",
						roles: ["ROLE_OP_ADMIN", "ROLE_ANALYST"],
						permissions: ["read", "write", "publish"],
						enabled: true,
					},
					userToken: { authenticated: true, accessToken: "screen-data-workflow-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installEditorRoutes(page: Page, onCardQuery: () => void) {
	await page.route("**/*", async (route) => {
		const request = route.request();
		const url = new URL(request.url());
		const requestPath = url.pathname;
		if (url.origin !== APP_ORIGIN) return route.continue();

		if (requestPath === "/api/session/status") {
			return fulfillPlatformJson(route, { authenticated: true, remainingSeconds: 3600 });
		}
		if (requestPath === "/api/menu/tree") return fulfillPlatformJson(route, []);
		if (requestPath === "/api/infra/screen-fonts" || requestPath === "/infra/screen-fonts") {
			return fulfillJson(route, []);
		}
		if (requestPath === "/bi/api/screen-plugins") return fulfillJson(route, []);
		if (requestPath === "/bi/api/card/42/query") {
			onCardQuery();
			return fulfillJson(route, {
				data: {
					cols: [
						{ name: "region", display_name: "区域", base_type: "type/Text" },
						{ name: "amount", display_name: "销售额", base_type: "type/Decimal" },
					],
					rows: [
						["华东", 128],
						["华南", 96],
						["华北", 74],
					],
				},
			});
		}
		if (requestPath === "/bi/api/card") {
			return fulfillJson(route, [
				{ id: 42, name: "区域销售分析", display: "bar", type: "question", lifecycle_status: "PUBLISHED" },
			]);
		}
		if (requestPath === `/bi/api/screens/${SCREEN_ID}/edit-lock/acquire`) {
			return fulfillJson(route, {
				active: true,
				mine: true,
				ownerId: "e2e",
				ownerName: "验收用户",
				expiresAt: "2026-08-29T04:45:00.000Z",
			});
		}
		if (requestPath === `/bi/api/screens/${SCREEN_ID}/edit-lock/heartbeat`) {
			return fulfillJson(route, {
				active: true,
				mine: true,
				ownerId: "e2e",
				ownerName: "验收用户",
				expiresAt: "2026-08-29T04:45:00.000Z",
			});
		}
		if (requestPath === `/bi/api/screens/${SCREEN_ID}/edit-lock/release`) {
			return fulfillJson(route, { active: false, mine: false });
		}
		if (requestPath === `/bi/api/screens/${SCREEN_ID}/edit-lock`) {
			return fulfillJson(route, { active: true, mine: true, ownerId: "e2e", ownerName: "验收用户" });
		}
		if (requestPath === `/bi/api/screens/${SCREEN_ID}`) return fulfillJson(route, screen);
		if (requestPath.startsWith("/api/") || requestPath.startsWith("/bi/api/")) return fulfillJson(route, []);
		return route.continue();
	});
}

async function openDataWorkflow(page: Page) {
	await page.getByTestId("analytics-screen-designer").waitFor({ state: "visible", timeout: 15_000 });
	await page.getByTestId("analytics-screen-component-sales-chart").click();
	await page.locator(".designer-right-panel-tab").filter({ hasText: "数据" }).click();
	return page.getByRole("region", { name: "数据配置流程" });
}

test("Chrome 95 keeps the data-first editor readable and query-neutral", async ({ browser, page }) => {
	expect(browser.version()).toContain("95.0.4638.0");
	mkdirSync(EVIDENCE_DIR, { recursive: true });

	let cardQueryCount = 0;
	const pageErrors: string[] = [];
	const requestFailures: string[] = [];
	const consoleErrors: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) => requestFailures.push(`${request.method()} ${request.url()}`));
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	await installIdentity(page);
	await installEditorRoutes(page, () => {
		cardQueryCount += 1;
	});

	await page.goto(`/#/bi/screens/${SCREEN_ID}/edit`, { waitUntil: "domcontentloaded" });
	const workflow = await openDataWorkflow(page);
	await expect(workflow.getByText("已读取 2 个字段 · 3 行样例", { exact: false })).toBeVisible();
	await expect(workflow.getByRole("table", { name: "当前组件样例数据" })).toBeVisible();
	await expect(workflow.getByText("已映射 2 个展示字段", { exact: false })).toBeVisible();
	expect(cardQueryCount).toBe(1);
	expect(pageErrors).toEqual([]);
	expect(requestFailures).toEqual([]);
	expect(consoleErrors).toEqual([]);

	const workflowSize = await workflow.evaluate((element) => ({
		clientWidth: element.clientWidth,
		scrollWidth: element.scrollWidth,
	}));
	expect(workflowSize.scrollWidth).toBe(workflowSize.clientWidth);
	await page.screenshot({ path: path.join(EVIDENCE_DIR, "editor-data-workflow-1366x768.png"), fullPage: true });

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(workflow).toBeVisible();
	const narrowMetrics = await page.evaluate(() => ({
		clientWidth: document.documentElement.clientWidth,
		scrollWidth: document.documentElement.scrollWidth,
	}));
	expect(narrowMetrics.scrollWidth).toBe(narrowMetrics.clientWidth);
	await page.screenshot({ path: path.join(EVIDENCE_DIR, "editor-data-workflow-768x900.png"), fullPage: true });

	consoleErrors.length = 0;
	let failedQueryCount = 0;
	await page.route("**/bi/api/card/42/query", (route) => {
		failedQueryCount += 1;
		return fulfillJson(route, { message: "样例查询超时，请稍后重试" }, 504);
	});
	page.once("dialog", (dialog) => dialog.accept());
	await page.setViewportSize({ width: 1366, height: 768 });
	await page.reload({ waitUntil: "domcontentloaded" });
	const errorWorkflow = await openDataWorkflow(page);
	await expect(errorWorkflow.getByRole("alert")).toContainText("数据读取失败：样例查询超时，请稍后重试", {
		timeout: 20_000,
	});
	await expect(errorWorkflow.getByRole("table")).toHaveCount(0);
	expect(failedQueryCount).toBeGreaterThan(0);
	expect(pageErrors).toEqual([]);
	expect(requestFailures).toEqual([]);
	expect(consoleErrors.every((message) => message.includes("504"))).toBe(true);
	await page.screenshot({
		path: path.join(EVIDENCE_DIR, "editor-data-workflow-error-1366x768.png"),
		fullPage: true,
	});
});
