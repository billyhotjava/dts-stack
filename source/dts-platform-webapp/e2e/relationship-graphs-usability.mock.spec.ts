import { expect, type Page, test } from "@playwright/test";

const PLAN_1 = "10000000-0000-0000-0000-000000000001";
const PLAN_2 = "10000000-0000-0000-0000-000000000002";

const plans = [
	{
		id: PLAN_1,
		tenantId: "server-tenant",
		code: "PATENT",
		name: "专利分析数仓",
		ownerId: "graph-reviewer",
		onboardingMode: "BUSINESS_FIRST",
		lifecycleStatus: "DESIGNING",
		version: 1,
	},
	{
		id: PLAN_2,
		tenantId: "server-tenant",
		code: "PROJECT",
		name: "项目管理数仓",
		ownerId: "graph-reviewer",
		onboardingMode: "BUSINESS_FIRST",
		lifecycleStatus: "ARCHIVED",
		version: 1,
	},
];

const modelNodes = Array.from({ length: 30 }, (_, index) => ({
	id: `MODEL:model-${index}:r1`,
	kind: "MODEL",
	label: `专利模型${index + 1}`,
	status: index === 29 ? "ARCHIVED" : "CURRENT",
	route: `/modeling/workbench?assetId=model-${index}&revision=1`,
}));
const graph = {
	planId: PLAN_1,
	nodes: [
		...modelNodes,
		...Array.from({ length: 100 }, (_, index) => ({
			id: `MODEL:isolated-${index}:r1`,
			kind: "MODEL",
			label: `无关系归档模型${index + 1}`,
			status: "ARCHIVED",
		})),
		{ id: "DIMENSION:patent-status:r1", kind: "DIMENSION", label: "专利生命周期状态", status: "CURRENT" },
		{ id: "STANDARD:patent-code:v1", kind: "STANDARD", label: "专利状态编码标准", status: "CURRENT" },
		{ id: "INDICATOR:patent-count:v1", kind: "INDICATOR", label: "有效专利数量", status: "PUBLISHED" },
	],
	edges: [
		...Array.from({ length: 29 }, (_, index) => ({
			source: `MODEL:model-${index + 1}:r1`,
			target: `MODEL:model-${index}:r1`,
			kind: "DEPENDS_ON",
		})),
		{
			source: "MODEL:model-0:r1",
			target: "DIMENSION:patent-status:r1",
			kind: "DIMENSION_DEFINITION_REFERENCE",
		},
		{ source: "MODEL:model-0:r1", target: "STANDARD:patent-code:v1", kind: "STANDARD_BINDING" },
		{ source: "MODEL:model-0:r1", target: "INDICATOR:patent-count:v1", kind: "INDICATOR_REFERENCE" },
	],
	truncated: false,
};

async function installAuthenticatedState(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "graph-reviewer",
						fullName: "关系图验收员",
						roles: ["ROLE_OP_ADMIN"],
						permissions: ["modeling.manage"],
						enabled: true,
					},
					userToken: { accessToken: "relationship-graph-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApi(page: Page) {
	await page.route("**/*", async (route) => {
		const url = new URL(route.request().url());
		if (!url.pathname.startsWith("/api/")) return route.continue();
		const fulfill = (data: unknown) =>
			route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify({ status: 200, data, message: "OK" }),
			});
		if (url.pathname === "/api/session/status") return fulfill({ authenticated: true, remainingSeconds: 3600 });
		if (url.pathname === "/api/keycloak/auth/refresh") return fulfill({ authenticated: true, expiresIn: 3600 });
		if (url.pathname === "/api/menu/tree") return fulfill([]);
		if (url.pathname === "/api/modeling/warehouse-plans") return fulfill(plans);
		if (/\/api\/modeling\/warehouse-plans\/[^/]+\/relationship-graph$/.test(url.pathname)) {
			return fulfill({ ...graph, planId: url.pathname.includes(PLAN_2) ? PLAN_2 : PLAN_1 });
		}
		return fulfill([]);
	});
}

const assertNoPageOverflow = async (page: Page) => {
	const widths = await page.evaluate(() => ({
		document: document.documentElement.scrollWidth,
		viewport: document.documentElement.clientWidth,
	}));
	expect(widths.document).toBeLessThanOrEqual(widths.viewport + 1);
};

test.beforeEach(async ({ page }) => {
	await installAuthenticatedState(page);
	await installApi(page);
});

test("keeps model, standard and indicator relationship pages readable", async ({ page }, testInfo) => {
	const consoleErrors: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	await page.setViewportSize({ width: 1366, height: 768 });

	await page.goto("/#/data-modeling/graphs/models");
	await expect(page.getByRole("heading", { name: "模型关系" })).toBeVisible();
	await expect(page.getByLabel("选择数仓规划")).toHaveValue(PLAN_1);
	await expect(page.getByLabel("选择数仓规划").locator("option:checked")).toHaveText("专利分析数仓 · 设计中");
	await expect(page.getByText("31 个节点 · 30 条关系")).toBeVisible();
	await expect(page.locator(".dmx-graph-canvas canvas").first()).toBeVisible();
	const renderedGraphPixels = await page
		.locator(".dmx-graph-canvas canvas")
		.first()
		.evaluate((canvas: HTMLCanvasElement) => {
			const pixels = canvas.getContext("2d")?.getImageData(0, 0, canvas.width, canvas.height).data || [];
			let ink = 0;
			for (let index = 3; index < pixels.length; index += 4) if (pixels[index] > 0) ink += 1;
			return ink;
		});
	expect(renderedGraphPixels).toBeGreaterThan(12_000);
	await page.getByLabel("选择数仓规划").selectOption(PLAN_2);
	await expect(page.getByLabel("选择数仓规划")).toHaveValue(PLAN_2);
	await page.screenshot({ path: testInfo.outputPath("model-relationships-1366.png"), fullPage: true });

	await page.goto("/#/data-modeling/graphs/standards");
	await expect(page.getByRole("heading", { name: "标准关系" })).toBeVisible();
	await expect(page.getByText("2 个节点 · 1 条关系")).toBeVisible();
	await expect(page.getByText("无关系归档模型1")).toHaveCount(0);

	await page.goto("/#/data-modeling/graphs/metrics");
	await expect(page.getByRole("heading", { name: "指标血缘" })).toBeVisible();
	await expect(page.getByText("2 个节点 · 1 条关系")).toBeVisible();
	await page.getByLabel("搜索关系节点").fill("有效专利数量");
	await expect(page.getByText("2 个节点 · 1 条关系")).toBeVisible();
	await assertNoPageOverflow(page);

	await page.setViewportSize({ width: 1024, height: 768 });
	await assertNoPageOverflow(page);
	expect(consoleErrors).toEqual([]);
});
