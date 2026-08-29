import { mkdirSync } from "node:fs";
import path from "node:path";
import { expect, type Locator, type Page, type Route, test } from "@playwright/test";

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

type CardQueryData = {
	cols: Array<{ name: string; display_name: string; base_type: string }>;
	rows: unknown[][];
};

const DEFAULT_CARD_QUERY_DATA: CardQueryData = {
	cols: [
		{ name: "region", display_name: "区域", base_type: "type/Text" },
		{ name: "amount", display_name: "销售额", base_type: "type/Decimal" },
	],
	rows: [
		["华东", 128],
		["华南", 96],
		["华北", 74],
	],
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

async function installEditorRoutes(
	page: Page,
	onCardQuery: () => void,
	cardQueryData: CardQueryData = DEFAULT_CARD_QUERY_DATA,
) {
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
			return fulfillJson(route, { data: cardQueryData });
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

async function readTextContrast(locator: Locator) {
	return locator.evaluate((element) => {
		type Rgba = { r: number; g: number; b: number; a: number };
		const parse = (value: string): Rgba | null => {
			const match = value.match(/rgba?\(([^)]+)\)/);
			if (!match) return null;
			const parts = match[1].split(",").map((part) => Number(part.trim()));
			if (parts.length < 3 || parts.some((part) => Number.isNaN(part))) return null;
			return { r: parts[0], g: parts[1], b: parts[2], a: parts[3] ?? 1 };
		};
		const blend = (front: Rgba, back: Rgba): Rgba => {
			const alpha = front.a + back.a * (1 - front.a);
			if (alpha === 0) return { r: 0, g: 0, b: 0, a: 0 };
			return {
				r: (front.r * front.a + back.r * back.a * (1 - front.a)) / alpha,
				g: (front.g * front.a + back.g * back.a * (1 - front.a)) / alpha,
				b: (front.b * front.a + back.b * back.a * (1 - front.a)) / alpha,
				a: alpha,
			};
		};
		const luminance = (color: Rgba) => {
			const channel = (value: number) => {
				const normalized = value / 255;
				return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
			};
			return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
		};

		const ancestors: Element[] = [];
		for (let current: Element | null = element; current; current = current.parentElement) ancestors.push(current);
		let background: Rgba = { r: 255, g: 255, b: 255, a: 1 };
		for (const ancestor of ancestors.reverse()) {
			const layer = parse(window.getComputedStyle(ancestor).backgroundColor);
			if (layer && layer.a > 0) background = blend(layer, background);
		}
		const style = window.getComputedStyle(element);
		const foreground = parse(style.color);
		if (!foreground) return { ratio: 0, fontSize: Number.parseFloat(style.fontSize), color: style.color };
		const renderedText = blend(foreground, background);
		const light = Math.max(luminance(renderedText), luminance(background));
		const dark = Math.min(luminance(renderedText), luminance(background));
		return {
			ratio: (light + 0.05) / (dark + 0.05),
			fontSize: Number.parseFloat(style.fontSize),
			color: style.color,
		};
	});
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

test("Chrome 95 keeps stale mappings honest and the narrow editor operable", async ({ browser, page }) => {
	expect(browser.version()).toContain("95.0.4638.0");
	mkdirSync(EVIDENCE_DIR, { recursive: true });

	const pageErrors: string[] = [];
	const requestFailures: string[] = [];
	const consoleErrors: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) => requestFailures.push(`${request.method()} ${request.url()}`));
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	await installIdentity(page);
	await installEditorRoutes(page, () => undefined, {
		cols: [{ name: "region", display_name: "区域", base_type: "type/Text" }],
		rows: [],
	});

	await page.goto(`/#/bi/screens/${SCREEN_ID}/edit`, { waitUntil: "domcontentloaded" });
	const workflow = await openDataWorkflow(page);
	await expect(workflow.getByText("已读取 1 个字段 · 0 行样例", { exact: false })).toBeVisible();
	await expect(workflow.getByText("1 项有效 · 1 失效", { exact: false })).toBeVisible();
	await expect(workflow.getByText("有 1 个映射字段在当前数据源中不存在", { exact: false })).toBeVisible();
	await expect(workflow.getByText("已映射 2 个展示字段", { exact: false })).toHaveCount(0);

	for (const locator of [
		workflow.getByText("1 数据来源", { exact: true }),
		workflow.getByText("样例数据仅用于当前编辑会话，不写入大屏配置。", { exact: true }),
	]) {
		const metrics = await readTextContrast(locator);
		expect(metrics.fontSize).toBeGreaterThanOrEqual(11);
		expect(metrics.ratio).toBeGreaterThanOrEqual(4.5);
	}

	await page.setViewportSize({ width: 768, height: 900 });
	const workspace = page.getByTestId("analytics-screen-workspace");
	const canvasWorkspace = page.getByTestId("analytics-screen-canvas-workspace");
	const inspector = page.getByTestId("analytics-screen-inspector-panel");
	const [workspaceBox, canvasBox, inspectorBox] = await Promise.all([
		workspace.boundingBox(),
		canvasWorkspace.boundingBox(),
		inspector.boundingBox(),
	]);
	expect(workspaceBox).not.toBeNull();
	expect(canvasBox?.width ?? 0).toBeGreaterThanOrEqual(760);
	expect(inspectorBox?.height ?? 0).toBeGreaterThanOrEqual((workspaceBox?.height ?? 0) - 1);

	const primarySave = page.getByTestId("analytics-screen-primary-action-button");
	await expect(primarySave).toBeHidden();
	const operationButton = page.getByRole("button", { name: "操作", exact: true });
	await expect(operationButton).toBeVisible();
	await operationButton.click();
	await expect(page.getByRole("button", { name: "保存", exact: true })).toBeVisible();

	const narrowMetrics = await page.evaluate(() => ({
		clientWidth: document.documentElement.clientWidth,
		scrollWidth: document.documentElement.scrollWidth,
	}));
	expect(narrowMetrics.scrollWidth).toBe(narrowMetrics.clientWidth);
	expect(pageErrors).toEqual([]);
	expect(requestFailures).toEqual([]);
	expect(consoleErrors).toEqual([]);
	await page.screenshot({
		path: path.join(EVIDENCE_DIR, "editor-data-workflow-fixed-768x900.png"),
		fullPage: true,
	});
});
