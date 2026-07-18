import fs from "node:fs";
import path from "node:path";
import { expect, type Page, type Route, test } from "@playwright/test";

const evidenceDir = path.resolve(
	import.meta.dirname,
	"../../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

const PLAN_RESOURCE = "/api/modeling/warehouse-plans";

type PlanMode = "BUSINESS_FIRST" | "ASSET_FIRST";

type ApiScenario = {
	list: (call: number) => unknown | { httpStatus: number; body: unknown };
	getPlan: (planId: string, call: number) => unknown | { httpStatus: number; body: unknown };
	baseline: (planId: string, call: number) => unknown | { httpStatus: number; body: unknown };
	projection: (planId: string, call: number) => unknown | { httpStatus: number; body: unknown };
	create?: (body: Record<string, unknown>, call: number) => Promise<unknown> | unknown;
};

type BrowserProbe = {
	pageErrors: string[];
	consoleErrors: string[];
	requestFailures: string[];
	httpFailures: Array<{ status: number; url: string }>;
};

const plan = (id: string, mode: PlanMode, name: string) => ({
	id,
	tenantId: "tenant-chrome95",
	code: `wp_${id.replaceAll("-", "")}`,
	name,
	objective: mode === "BUSINESS_FIRST" ? "统一经营分析口径" : null,
	scope: mode === "BUSINESS_FIRST" ? "总部经营分析" : null,
	ownerId: "chrome95-reviewer",
	ownerDepartmentId: "quality",
	onboardingMode: mode,
	lifecycleStatus: "DRAFT",
	version: 1,
});

const stageProjection = (planId: string) => ({
	planId,
	currentStage: "WAREHOUSE_PLANNING",
	primaryBlocker: {
		stageCode: "WAREHOUSE_PLANNING",
		code: "PLANNING_POLICY_INCOMPLETE",
		message: "请确认业务分类与数仓分层",
	},
	nextAction: { label: "完善规划基线", path: `/modeling/plans/${planId}/baseline` },
	stages: [
		"DATA_CONNECTION",
		"SOURCE_INVENTORY",
		"WAREHOUSE_PLANNING",
		"DATA_STANDARD",
		"MODEL_DESIGN",
		"BUILD_QUALITY_RELEASE",
		"DATA_ASSET",
		"METRIC_SYSTEM",
		"DATA_SERVICE_OPERATIONS",
	].map((code) => ({
		code,
		status: code === "DATA_CONNECTION" ? "COMPLETE" : code === "WAREHOUSE_PLANNING" ? "BLOCKED" : "NOT_STARTED",
		freshness: "CURRENT",
		evidenceCount: code === "DATA_CONNECTION" ? 1 : 0,
		actionLabel: "继续",
		actionPath: `/modeling/plans/${planId}/baseline`,
	})),
	computedAt: "2026-07-19T03:30:00+08:00",
});

const successful = (data: unknown) => ({ status: 200, data, message: "OK" });

async function fulfill(route: Route, result: unknown) {
	const response =
		result && typeof result === "object" && "httpStatus" in result
			? (result as { httpStatus: number; body: unknown })
			: { httpStatus: 200, body: successful(result) };
	await route.fulfill({
		status: response.httpStatus,
		contentType: "application/json; charset=utf-8",
		body: JSON.stringify(response.body),
	});
}

async function installAuthenticatedChrome95Session(page: Page) {
	await page.addInitScript(() => {
		const now = Date.now();
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "chrome95-reviewer",
						fullName: "Chrome 95 验收用户",
						deptName: "质量保障部",
						roles: ["ROLE_OP_ADMIN"],
						permissions: [],
						enabled: true,
					},
					userToken: { accessToken: `dev-access-chrome95-${now}` },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", String(now));
		localStorage.setItem("dts.platform.session.lastActivity", String(now));
	});
}

function installBrowserProbe(page: Page): BrowserProbe {
	const probe: BrowserProbe = { pageErrors: [], consoleErrors: [], requestFailures: [], httpFailures: [] };
	page.on("pageerror", (error) => probe.pageErrors.push(error.message));
	page.on("console", (message) => {
		if (message.type() === "error") probe.consoleErrors.push(message.text());
	});
	page.on("requestfailed", (request) => {
		probe.requestFailures.push(
			`page=${page.url()} :: ${request.method()} ${request.url()} :: ${request.failure()?.errorText || "unknown"}`,
		);
	});
	page.on("response", (response) => {
		if (response.status() >= 400) probe.httpFailures.push({ status: response.status(), url: response.url() });
	});
	return probe;
}

async function installWarehousePlanApi(page: Page, scenario: ApiScenario) {
	const calls = { list: 0, getPlan: 0, baseline: 0, projection: 0, create: 0 };
	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const url = new URL(request.url());
		const pathName = url.pathname;
		if (!pathName.startsWith(PLAN_RESOURCE)) {
			await fulfill(route, []);
			return;
		}

		if (request.method() === "POST" && pathName === PLAN_RESOURCE) {
			calls.create += 1;
			if (!scenario.create) throw new Error("Unexpected create request");
			await fulfill(route, await scenario.create(request.postDataJSON() as Record<string, unknown>, calls.create));
			return;
		}

		if (request.method() !== "GET") throw new Error(`Unexpected ${request.method()} ${pathName}`);
		if (pathName === PLAN_RESOURCE) {
			calls.list += 1;
			await fulfill(route, scenario.list(calls.list));
			return;
		}

		const suffix = pathName.slice(`${PLAN_RESOURCE}/`.length);
		const [planId, child] = suffix.split("/");
		if (child === "baseline") {
			calls.baseline += 1;
			await fulfill(route, scenario.baseline(planId, calls.baseline));
			return;
		}
		if (child === "stage-projection") {
			calls.projection += 1;
			await fulfill(route, scenario.projection(planId, calls.projection));
			return;
		}
		calls.getPlan += 1;
		await fulfill(route, scenario.getPlan(planId, calls.getPlan));
	});
	return calls;
}

function assertCleanBrowser(
	probe: BrowserProbe,
	expectedHttpFailures: Array<{ status: number; includes: string }> = [],
) {
	expect(probe.pageErrors, "pageerror events").toEqual([]);
	expect(probe.requestFailures, "transport-level request failures").toEqual([]);
	const unexpectedHttp = probe.httpFailures.filter(
		(actual) =>
			!expectedHttpFailures.some(
				(expected) => actual.status === expected.status && actual.url.includes(expected.includes),
			),
	);
	expect(unexpectedHttp, "unexpected HTTP >= 400 responses").toEqual([]);
	const actionableConsoleErrors = probe.consoleErrors.filter(
		(message) => !expectedHttpFailures.some((expected) => message.includes(String(expected.status))),
	);
	expect(actionableConsoleErrors, "unexpected console.error messages").toEqual([]);
}

test.beforeAll(() => fs.mkdirSync(evidenceDir, { recursive: true }));

test.beforeEach(async ({ page }) => {
	await installAuthenticatedChrome95Session(page);
});

test("business-first creates once under repeated clicks and follows the server nextAction", async ({
	page,
	browserName,
}) => {
	expect(browserName).toBe("chromium");
	const probe = installBrowserProbe(page);
	const createdPlan = plan("plan-business", "BUSINESS_FIRST", "经营分析主题数仓");
	let submitted: Record<string, unknown> | null = null;
	const calls = await installWarehousePlanApi(page, {
		list: () => [],
		getPlan: () => createdPlan,
		baseline: () => ({ ready: false, missingCodes: ["PLANNING_POLICY_INCOMPLETE"] }),
		projection: (planId) => stageProjection(planId),
		create: async (body) => {
			submitted = body;
			await new Promise((resolve) => setTimeout(resolve, 500));
			return {
				planId: createdPlan.id,
				plan: createdPlan,
				version: 1,
				etag: '"1"',
				initialSourceBindings: [],
				nextAction: `/modeling/plans/${createdPlan.id}/baseline?tab=business-scope`,
				replayed: false,
			};
		},
	});

	await page.goto("/#/modeling/workbench");
	await expect(page.getByTestId("warehouse-plan-workbench")).toBeVisible();
	await page.getByTestId("warehouse-plan-empty-primary-action").click();
	await expect(page.getByRole("dialog", { name: "新建数据建设规划" })).toBeVisible();
	await page.getByLabel("规划名称").fill("经营分析主题数仓");
	await page.getByLabel("建设目标").fill("统一经营分析口径");
	await page.waitForTimeout(350);
	await page.screenshot({ path: path.join(evidenceDir, "desktop-business-first-form.png"), fullPage: true });
	const submit = page.getByRole("button", { name: "创建并继续" });
	await submit.click();
	await expect(submit).toHaveClass(/ant-btn-loading/);
	await submit.evaluate((button: HTMLButtonElement) => button.click());
	await expect(page).toHaveURL(/\/modeling\/plans\/plan-business\/baseline\?tab=business-scope$/);
	await expect(page.getByText("经营分析主题数仓", { exact: true })).toBeVisible();
	await expect(page.getByText("业务范围", { exact: true })).toBeVisible();
	expect(calls.create).toBe(1);
	expect(submitted?.onboardingMode).toBe("BUSINESS_FIRST");
	expect(submitted).not.toHaveProperty("code");
	await page.screenshot({ path: path.join(evidenceDir, "desktop-business-first.png"), fullPage: true });
	assertCleanBrowser(probe);
});

test("asset-first uses the same create endpoint and carries an initial source", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const createdPlan = plan("plan-asset", "ASSET_FIRST", "存量资产治理计划");
	let submitted: Record<string, unknown> | null = null;
	const calls = await installWarehousePlanApi(page, {
		list: () => [],
		getPlan: () => createdPlan,
		baseline: () => ({ ready: false, missingCodes: ["SOURCE_INVENTORY_INCOMPLETE"] }),
		projection: (planId) => stageProjection(planId),
		create: (body) => {
			submitted = body;
			return {
				planId: createdPlan.id,
				plan: createdPlan,
				version: 1,
				etag: '"1"',
				initialSourceBindings: [],
				nextAction: `/modeling/plans/${createdPlan.id}/baseline?tab=sources`,
				replayed: false,
			};
		},
	});

	await page.goto("/#/modeling/workbench");
	await page.getByTestId("warehouse-plan-empty-primary-action").click();
	await page.getByText("从现有数据开始", { exact: true }).click();
	await page.getByLabel("规划名称").fill("存量资产治理计划");
	await page.getByPlaceholder("来源标识").fill("catalog.sales.orders");
	await page.screenshot({ path: path.join(evidenceDir, "desktop-asset-first-form.png"), fullPage: true });
	await page.getByRole("button", { name: "创建并继续" }).click();
	await expect(page).toHaveURL(/\/modeling\/plans\/plan-asset\/baseline\?tab=sources$/);
	await expect(page.getByText("来源盘点", { exact: true })).toBeVisible();
	expect(calls.create).toBe(1);
	expect(submitted?.onboardingMode).toBe("ASSET_FIRST");
	expect(submitted?.initialSourceRefs).toEqual([{ sourceType: "CATALOG_TABLE", sourceId: "catalog.sales.orders" }]);
	await page.screenshot({ path: path.join(evidenceDir, "desktop-asset-first.png"), fullPage: true });
	assertCleanBrowser(probe);
});

test("exact plan survives list failure, refresh, evidence retry, detail return and narrow layout", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const exactPlan = plan("plan-exact", "BUSINESS_FIRST", "精确恢复计划");
	let listFailedOnce = false;
	let baselineFailedOnce = false;
	await installWarehousePlanApi(page, {
		list: () => {
			if (!listFailedOnce) {
				listFailedOnce = true;
				return { httpStatus: 503, body: { status: 503, message: "计划列表暂时不可用" } };
			}
			return [exactPlan, plan("plan-other", "ASSET_FIRST", "其他计划")];
		},
		getPlan: (planId) => (planId === exactPlan.id ? exactPlan : plan("plan-other", "ASSET_FIRST", "其他计划")),
		baseline: () => {
			if (!baselineFailedOnce) {
				baselineFailedOnce = true;
				return { httpStatus: 503, body: { status: 503, message: "基线证据暂时不可用" } };
			}
			return { ready: false, missingCodes: ["PLANNING_POLICY_INCOMPLETE"] };
		},
		projection: (planId) => stageProjection(planId),
	});

	await page.goto(`/#/modeling/workbench?planId=${exactPlan.id}`);
	await expect(page.getByText("精确恢复计划", { exact: true }).last()).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-list-recovery")).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-empty-primary-action")).toHaveCount(0);
	await page.getByRole("button", { name: "重新加载列表" }).click();
	await expect(page.getByTestId("warehouse-plan-list-recovery")).toHaveCount(0);
	await expect(page.getByTestId("warehouse-plan-next-action")).toBeVisible();
	await page.waitForLoadState("networkidle");
	expect(probe.requestFailures, "before entering detail").toEqual([]);

	await page.getByRole("button", { name: "查看计划详情" }).click();
	await expect(page).toHaveURL(/\/modeling\/plans\/plan-exact\?planId=plan-exact$/);
	await expect(page.getByText("精确恢复计划", { exact: true }).last()).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-evidence-recovery")).toBeVisible();
	await expect(page.getByText("基线状态未知", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: "重新加载证据" }).click();
	await expect(page.getByTestId("warehouse-plan-evidence-recovery")).toHaveCount(0);
	await expect(page.getByText("仍有基线缺口", { exact: true })).toBeVisible();
	await page.waitForLoadState("networkidle");
	expect(probe.requestFailures, "before returning to workbench").toEqual([]);
	await page.getByRole("button", { name: "返回数据建设工作台" }).click();
	await expect(page).toHaveURL(/\/modeling\/workbench\?planId=plan-exact$/);
	await expect(page.getByText("精确恢复计划", { exact: true }).last()).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-next-action")).toBeVisible();
	await page.waitForLoadState("networkidle");
	expect(probe.requestFailures, "before desktop refresh").toEqual([]);
	await page.reload();
	await expect(page.getByText("精确恢复计划", { exact: true }).last()).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-next-action")).toBeVisible();
	await page.waitForLoadState("networkidle");
	expect(probe.requestFailures, "before narrow refresh").toEqual([]);
	await page.screenshot({ path: path.join(evidenceDir, "desktop-recovery.png"), fullPage: true });

	await page.setViewportSize({ width: 390, height: 844 });
	await expect(page.getByTestId("warehouse-plan-workbench")).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-next-action")).toBeVisible();
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({ path: path.join(evidenceDir, "narrow-recovery.png"), fullPage: true });
	assertCleanBrowser(probe, [
		{ status: 503, includes: "/modeling/warehouse-plans" },
		{ status: 503, includes: "/baseline" },
	]);
});
