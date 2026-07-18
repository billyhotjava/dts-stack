import path from "node:path";
import { expect, type Locator, type Page, type Route, test } from "@playwright/test";

const SCREEN_ID = "sprint66-generic-drilldown";
const EVIDENCE_DIR = path.resolve(
	import.meta.dirname,
	"../../../workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/chrome95",
);

type Component = {
	id: string;
	type: string;
	name: string;
	x: number;
	y: number;
	width: number;
	height: number;
	zIndex: number;
	locked: boolean;
	visible: boolean;
	config: Record<string, unknown>;
	dataSource?: Record<string, unknown>;
	drillDown?: Record<string, unknown>;
	actions?: Array<Record<string, unknown>>;
};

function component(overrides: Partial<Component>): Component {
	return {
		id: "component",
		type: "title",
		name: "Component",
		x: 40,
		y: 40,
		width: 320,
		height: 120,
		zIndex: 1,
		locked: false,
		visible: true,
		config: {},
		...overrides,
	};
}

function genericDrill(sourcePath: string, url = "/bi/api/sprint-66/drill-next") {
	return {
		enabled: true,
		levels: [
			{
				label: "明细",
				dataSource: {
					type: "api",
					apiConfig: {
						url,
						method: "GET",
						params: { selectedKey: "{{selectedKey}}" },
						responsePath: "data",
					},
				},
				mappings: [{ sourcePath, variableKey: "selectedKey", transform: "string" }],
				inheritContext: true,
			},
		],
	};
}

const rootMetric = component({
	id: "root-metric",
	type: "number-card",
	name: "Root metric",
	x: 20,
	y: 20,
	width: 220,
	height: 120,
	config: { title: "根层指标", value: 101, suffix: "" },
	drillDown: genericDrill("value"),
});

const viewAction = component({
	id: "detail-view-action",
	type: "title",
	name: "Detail view action",
	x: 280,
	y: 40,
	width: 260,
	height: 70,
	config: { text: "进入内部明细视图", fontSize: 24, textAlign: "center", color: "#d7ecff" },
	actions: [
		{
			type: "drill-view",
			label: "进入明细",
			drillViewId: "detail-view",
			drillViewLabel: "明细视图",
			mappings: [{ sourcePath: "text", variableKey: "selectedLabel", transform: "string" }],
		},
	],
});

const failureMetric = component({
	id: "failure-metric",
	type: "number-card",
	name: "Failure recovery metric",
	x: 580,
	y: 20,
	width: 220,
	height: 120,
	config: { title: "失败恢复指标", value: 303, suffix: "" },
	drillDown: genericDrill("value", "/bi/api/sprint-66/drill-fail"),
});

const barChart = component({
	id: "bar-chart",
	type: "bar-chart",
	name: "Bar chart",
	x: 20,
	y: 170,
	width: 340,
	height: 240,
	config: {
		xAxisData: ["BAR-A"],
		series: [{ name: "Amount", data: [20] }],
		legendDisplay: "hide",
		animation: false,
	},
	drillDown: genericDrill("name"),
});

const pieChart = component({
	id: "pie-chart",
	type: "pie-chart",
	name: "Pie chart",
	x: 390,
	y: 170,
	width: 340,
	height: 240,
	config: {
		data: [{ name: "PIE-A", value: 20 }],
		legendDisplay: "hide",
		pieLabelShow: false,
		animation: false,
	},
	drillDown: genericDrill("name"),
});

const lineChart = component({
	id: "line-chart",
	type: "line-chart",
	name: "Line chart",
	x: 760,
	y: 170,
	width: 340,
	height: 240,
	config: {
		xAxisData: ["LINE-A", "LINE-B"],
		series: [{ name: "Amount", data: [10, 20] }],
		legendDisplay: "hide",
		animation: false,
	},
	drillDown: genericDrill("name"),
});

const mapChart = component({
	id: "map-chart",
	type: "map-chart",
	name: "Map chart",
	x: 20,
	y: 450,
	width: 420,
	height: 300,
	config: {
		title: "区域数据",
		mapName: "sprint-66-unregistered-map",
		usePresetGeoJson: false,
		regions: [
			{ name: "MAP-A", code: "A", value: 30 },
			{ name: "MAP-B", code: "B", value: 10 },
		],
	},
	drillDown: genericDrill("name"),
});

const table = component({
	id: "table",
	type: "table",
	name: "Table",
	x: 470,
	y: 450,
	width: 600,
	height: 300,
	config: {
		header: ["key", "amount"],
		data: [["TABLE-A", 20]],
		pageSize: 10,
	},
	drillDown: genericDrill("key"),
});

const screen = {
	id: SCREEN_ID,
	name: "通用下钻 Chrome 95 回归",
	description: "",
	width: 1120,
	height: 780,
	theme: "legacy-dark",
	backgroundColor: "#08121f",
	backgroundImage: null,
	classification: "INTERNAL",
	components: [],
	pages: [
		{
			id: "root-view",
			name: "根视图",
			components: [rootMetric, viewAction, failureMetric, barChart, pieChart, lineChart, mapChart, table],
		},
		{
			id: "detail-view",
			name: "明细视图",
			components: [
				component({
					id: "detail-title",
					type: "title",
					name: "Detail title",
					x: 220,
					y: 160,
					width: 560,
					height: 120,
					config: { text: "内部明细视图已打开", fontSize: 34, textAlign: "center", color: "#d7ecff" },
				}),
			],
		},
	],
	globalVariables: [],
	carouselConfig: {
		enabled: false,
		intervalSeconds: 30,
		transition: "none",
		transitionDuration: 0,
		loop: false,
	},
	schemaVersion: 2,
	sourceMode: "draft",
	updatedAt: "2026-07-18T05:00:00.000Z",
	canRead: true,
	canEdit: true,
	canPublish: true,
	canManage: true,
	canDelete: true,
	isOwner: true,
};

async function fulfillJson(route: Route, body: unknown, status = 200) {
	await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

async function clickChartDataItemNear(canvas: Locator, center: { x: number; y: number }, radius = 24) {
	const point = await canvas.evaluate(
		(element, args) => {
			const rect = element.getBoundingClientRect();
			for (let y = args.center.y - args.radius; y <= args.center.y + args.radius; y += 3) {
				for (let x = args.center.x - args.radius; x <= args.center.x + args.radius; x += 3) {
					element.dispatchEvent(
						new MouseEvent("mousemove", {
							clientX: rect.left + x,
							clientY: rect.top + y,
							bubbles: true,
						}),
					);
					if (getComputedStyle(element).cursor === "pointer") return { x, y };
				}
			}
			return null;
		},
		{ center, radius },
	);
	if (!point) throw new Error(`未在 (${center.x}, ${center.y}) 周边找到可点击图表数据项`);
	await canvas.click({ position: point, force: true });
}

async function installRuntimeFixture(page: Page, drillRequests: string[]) {
	await page.addInitScript(() => {
		const userStore = {
			state: {
				userInfo: {
					username: "chrome95-regression",
					fullName: "Chrome 95 Regression",
					roles: ["ROLE_OP_ADMIN"],
					permissions: [],
					enabled: true,
				},
				userToken: { authenticated: true },
			},
			version: 0,
		};
		window.localStorage.setItem("dts.platform.userStore", JSON.stringify(userStore));
		window.localStorage.setItem("dts.platform.session.loginTs", String(Date.now()));
		window.localStorage.setItem("dts.platform.session.lastActivity", String(Date.now()));
	});

	await page.route("**/runtime-config.js", (route) =>
		route.fulfill({ status: 200, contentType: "application/javascript", body: "window.__RUNTIME_CONFIG__ = {};" }),
	);
	await page.route("**/infra/screen-fonts", (route) => fulfillJson(route, []));
	await page.route("**/api/**", async (route) => {
		const url = new URL(route.request().url());
		if (url.pathname === `/bi/api/screens/${SCREEN_ID}`) {
			return fulfillJson(route, screen);
		}
		if (url.pathname === "/bi/api/sprint-66/drill-next") {
			drillRequests.push(url.toString());
			return fulfillJson(route, { data: [{ key: "101", value: 202 }] });
		}
		if (url.pathname === "/bi/api/sprint-66/drill-fail") {
			return fulfillJson(route, { message: "fixture failure" }, 500);
		}
		return fulfillJson(route, { data: [], authenticated: true });
	});
}

test("Chrome 95 runs generic drill, reset, and internal view navigation without errors", async ({ browser, page }) => {
	expect(browser.version()).toMatch(/^95\./);
	const pageErrors: string[] = [];
	const failedRequests: string[] = [];
	const drillRequests: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) => failedRequests.push(`${request.method()} ${request.url()}`));
	await installRuntimeFixture(page, drillRequests);

	await page.goto(`/#/bi/screens/${SCREEN_ID}/preview`);
	await expect(page.getByTestId("analytics-screen-preview")).toBeVisible({ timeout: 20_000 });
	const metric = page.getByRole("button", { name: /根层指标/ });
	await expect(metric).toBeVisible();
	await expect(page.locator("canvas")).toHaveCount(3);

	const assertSingleDrill = async (trigger: () => Promise<void>, expectedValue: string) => {
		const before = drillRequests.length;
		await trigger();
		await expect.poll(() => drillRequests.length).toBe(before + 1);
		expect(new URL(drillRequests.at(-1) ?? "http://invalid").searchParams.get("selectedKey")).toBe(expectedValue);
		await expect(page.getByRole("button", { name: "重置下钻" })).toBeVisible();
		await page.getByRole("button", { name: "重置下钻" }).click();
		await expect(page.getByRole("button", { name: "重置下钻" })).toHaveCount(0);
		expect(pageErrors).toEqual([]);
		await expect(page.getByText("MAP-A", { exact: true })).toBeVisible();
	};

	await metric.evaluate((element) => {
		(element as HTMLElement).click();
		(element as HTMLElement).click();
	});
	await expect(page.getByText("明细: 101")).toBeVisible();
	await expect.poll(() => drillRequests.length).toBe(1);
	expect(new URL(drillRequests[0]).searchParams.get("selectedKey")).toBe("101");
	await expect(page.getByRole("button", { name: "重置下钻" })).toBeVisible();
	await page.screenshot({ path: path.join(EVIDENCE_DIR, "desktop-1366x768-drill.png") });

	await page.getByRole("button", { name: "重置下钻" }).click();
	await expect(page.getByText("明细: 101")).toHaveCount(0);
	await expect(page.getByText("MAP-A", { exact: true })).toBeVisible();

	const canvases = page.locator("canvas");
	await assertSingleDrill(() => canvases.nth(0).click({ position: { x: 170, y: 140 } }), "BAR-A");
	await assertSingleDrill(() => canvases.nth(1).click({ position: { x: 260, y: 120 } }), "PIE-A");
	await assertSingleDrill(() => clickChartDataItemNear(canvases.nth(2), { x: 170, y: 60 }), "LINE-A");
	await assertSingleDrill(() => page.getByText("MAP-A", { exact: true }).click(), "MAP-A");
	await assertSingleDrill(() => page.getByText("TABLE-A").click(), "TABLE-A");

	await page.getByRole("button", { name: /失败恢复指标/ }).click();
	await expect(page.getByText("明细: 303")).toBeVisible();
	await expect(page.getByText(/API 请求失败: HTTP 500/)).toBeVisible();
	await expect(page.getByRole("button", { name: "重置下钻" })).toBeVisible();
	await page.getByRole("button", { name: "重置下钻" }).click();
	await expect(page.getByText(/API 请求失败: HTTP 500/)).toHaveCount(0);
	await expect(page.getByRole("button", { name: /失败恢复指标/ })).toBeVisible();

	await page.getByRole("button", { name: /进入内部明细视图/ }).click();
	await expect(page.getByText("内部明细视图已打开")).toBeVisible();
	await expect(page.getByRole("navigation", { name: "内部视图导航" })).toBeVisible();
	await page.getByRole("button", { name: "返回上一层" }).click();
	await expect(metric).toBeVisible();

	await page.setViewportSize({ width: 390, height: 844 });
	await expect(metric).toBeVisible();
	const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
	expect(overflow).toBeLessThanOrEqual(2);
	await page.screenshot({ path: path.join(EVIDENCE_DIR, "narrow-390x844-root.png") });

	expect(pageErrors).toEqual([]);
	expect(failedRequests).toEqual([]);
});
