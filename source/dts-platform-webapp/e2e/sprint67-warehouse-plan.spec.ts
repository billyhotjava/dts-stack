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
	update?: (
		planId: string,
		body: Record<string, unknown>,
		headers: Record<string, string>,
		call: number,
	) => Promise<unknown> | unknown;
	archive?: (planId: string, headers: Record<string, string>, call: number) => Promise<unknown> | unknown;
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

const DIMENSION_CHECKSUM_V1 = "a".repeat(64);
const DIMENSION_CHECKSUM_V2 = "b".repeat(64);

const dimension = (
	overrides: Partial<{
		id: string;
		planId: string;
		domainId: string;
		name: string;
		description: string;
		status: "DRAFT" | "PUBLISHED";
		revision: number;
		checksum: string;
	}> = {},
) => ({
	contractVersion: 2,
	id: overrides.id || "40000000-0000-0000-0000-000000000067",
	planId: overrides.planId || "10000000-0000-0000-0000-000000000067",
	domainId: overrides.domainId || "20000000-0000-0000-0000-000000000067",
	modelType: "DIMENSION",
	layer: "DWD",
	name: overrides.name || "组织维度",
	description: overrides.description || "统一组织机构分析口径",
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	businessActivityRef: null,
	consumptionScenario: null,
	grain: { statement: "每行代表一个组织机构", keys: ["organization_id"] },
	factShape: null,
	timeSemantics: null,
	generationStrategy: null,
	fields: [{ name: "organization_id", dataType: "string", nullable: false, role: "KEY" }],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
	status: overrides.status || "DRAFT",
	revision: overrides.revision || 1,
	checksum: overrides.checksum || DIMENSION_CHECKSUM_V1,
	createdAt: "2026-07-19T03:00:00Z",
	updatedAt: "2026-07-19T04:00:00Z",
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
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
	const calls = { list: 0, getPlan: 0, baseline: 0, projection: 0, create: 0, update: 0, archive: 0 };
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

		const suffix = pathName.startsWith(`${PLAN_RESOURCE}/`) ? pathName.slice(`${PLAN_RESOURCE}/`.length) : "";
		const [planId, ...childSegments] = suffix.split("/");
		const child = childSegments.join("/");
		if (request.method() === "PATCH" && planId && !child) {
			calls.update += 1;
			if (!scenario.update) throw new Error("Unexpected update request");
			await fulfill(
				route,
				await scenario.update(
					planId,
					request.postDataJSON() as Record<string, unknown>,
					request.headers(),
					calls.update,
				),
			);
			return;
		}
		if (request.method() === "POST" && planId && child === "archive") {
			calls.archive += 1;
			if (!scenario.archive) throw new Error("Unexpected archive request");
			await fulfill(route, await scenario.archive(planId, request.headers(), calls.archive));
			return;
		}

		if (request.method() !== "GET") throw new Error(`Unexpected ${request.method()} ${pathName}`);
		if (pathName === PLAN_RESOURCE) {
			calls.list += 1;
			await fulfill(route, scenario.list(calls.list));
			return;
		}

		if (child === "baseline") {
			calls.baseline += 1;
			await fulfill(route, scenario.baseline(planId, calls.baseline));
			return;
		}
		if (child === "baseline/categories") {
			await fulfill(route, {
				value: { domainBindings: [], readiness: "DRAFT", issues: [] },
				version: 0,
			});
			return;
		}
		if (child === "baseline/policy") {
			await fulfill(route, {
				value: {
					layerScheme: null,
					namingPolicy: null,
					historyPolicy: null,
					defaultTimeZone: null,
					conceptualDesignAllowed: false,
					readiness: "DRAFT",
					issues: [],
				},
				version: 0,
			});
			return;
		}
		if (child === "baseline/sources") {
			await fulfill(route, {
				bindings: [],
				readiness: "DRAFT",
				issues: [],
				version: 0,
				etag: '"sources:0"',
				checkedAt: "2026-07-19T03:30:00+08:00",
			});
			return;
		}
		if (child === "stage-projection") {
			calls.projection += 1;
			await fulfill(route, scenario.projection(planId, calls.projection));
			return;
		}
		if (child) throw new Error(`Unexpected GET ${pathName}`);
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

test("warehouse plan ledger preserves edits across 409 and archives with fresh CAS", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const draft = plan("plan-ledger-draft", "BUSINESS_FIRST", "经营分析建设规划");
	const published = {
		...plan("plan-ledger-published", "ASSET_FIRST", "已发布资产规划"),
		lifecycleStatus: "PUBLISHED" as const,
		version: 3,
	};
	let serverDraft = draft;
	const updateEtags: string[] = [];
	const archiveEtags: string[] = [];
	const calls = await installWarehousePlanApi(page, {
		list: () => [serverDraft, published],
		getPlan: () => serverDraft,
		baseline: () => ({ ready: false, missingCodes: ["PLANNING_POLICY_INCOMPLETE"] }),
		projection: (planId) => stageProjection(planId),
		update: (_planId, body, headers, call) => {
			updateEtags.push(headers["if-match"]);
			if (call === 1) {
				serverDraft = { ...serverDraft, version: 2 };
				return {
					httpStatus: 409,
					body: {
						code: "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
						data: { currentVersion: 2 },
					},
				};
			}
			serverDraft = { ...serverDraft, ...body, version: 3 } as typeof draft;
			return serverDraft;
		},
		archive: (_planId, headers, call) => {
			archiveEtags.push(headers["if-match"]);
			if (call === 1) {
				serverDraft = { ...serverDraft, version: 4 };
				return {
					httpStatus: 409,
					body: {
						code: "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
						data: { currentVersion: 4 },
					},
				};
			}
			serverDraft = { ...serverDraft, lifecycleStatus: "ARCHIVED", version: 5 };
			return serverDraft;
		},
	});

	await page.goto("/#/modeling/plans");
	await expect(page.getByTestId("warehouse-plan-ledger")).toBeVisible();
	const draftRow = page.getByRole("row").filter({ hasText: "经营分析建设规划" });
	const publishedRow = page.getByRole("row").filter({ hasText: "已发布资产规划" });
	await expect(draftRow).toBeVisible();
	await expect(publishedRow.getByRole("button", { name: "编辑" })).toHaveCount(0);

	await draftRow.getByRole("button", { name: "编辑" }).click();
	const editor = page.getByTestId("warehouse-plan-header-editor");
	await expect(editor).toBeVisible();
	await editor.getByLabel("规划名称").fill("经营分析建设规划（修订）");
	await page.getByRole("button", { name: "保存规划" }).click();
	await expect(editor.getByText("规划已被其他用户更新")).toBeVisible();
	await expect(editor.getByLabel("规划名称")).toHaveValue("经营分析建设规划（修订）");
	await editor.getByRole("button", { name: "保留当前输入并基于版本 2 重试" }).click();
	await expect(editor).not.toBeVisible();
	await expect(page.getByText("经营分析建设规划（修订）", { exact: true })).toBeVisible();
	expect(updateEtags).toEqual(['"plan-head:1"', '"plan-head:2"']);

	const revisedRow = page.getByRole("row").filter({ hasText: "经营分析建设规划（修订）" });
	await revisedRow.getByRole("button", { name: "归档" }).click();
	await page.getByRole("button", { name: "确认归档" }).click();
	await expect(page.getByText("规划已变化，请重新确认归档")).toBeVisible();
	await page.getByRole("button", { name: "基于最新版归档" }).click();
	await expect(page.getByText("经营分析建设规划（修订）", { exact: true })).not.toBeVisible();
	await expect(page.getByRole("dialog")).toHaveCount(0);
	expect(archiveEtags).toEqual(['"plan-head:3"', '"plan-head:4"']);
	expect(calls.update).toBe(2);
	expect(calls.archive).toBe(2);

	const lifecycleFilter = page.getByRole("combobox", { name: "生命周期" });
	await lifecycleFilter.focus();
	await lifecycleFilter.press("ArrowDown");
	const archivedOption = page.getByTitle("已归档");
	await expect(archivedOption).toBeVisible();
	await archivedOption.click();
	await expect(page.getByText("经营分析建设规划（修订）", { exact: true })).toBeVisible();
	await page.screenshot({ path: path.join(evidenceDir, "warehouse-plan-ledger-desktop.png"), fullPage: true });
	await page.setViewportSize({ width: 390, height: 844 });
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({ path: path.join(evidenceDir, "warehouse-plan-ledger-narrow.png"), fullPage: true });
	assertCleanBrowser(probe, [{ status: 409, includes: "/warehouse-plans/plan-ledger-draft" }]);
});

test("warehouse plan ledger keeps read-only users away from mutations", async ({ page }) => {
	await page.addInitScript(() => {
		const raw = localStorage.getItem("dts.platform.userStore");
		if (!raw) return;
		const stored = JSON.parse(raw);
		stored.state.userInfo.roles = [];
		localStorage.setItem("dts.platform.userStore", JSON.stringify(stored));
	});
	const probe = installBrowserProbe(page);
	const readonlyPlan = plan("plan-ledger-readonly", "BUSINESS_FIRST", "只读建设规划");
	const calls = await installWarehousePlanApi(page, {
		list: () => [readonlyPlan],
		getPlan: () => readonlyPlan,
		baseline: () => ({ ready: false, missingCodes: [] }),
		projection: (planId) => stageProjection(planId),
	});

	await page.goto("/#/modeling/plans");
	await expect(page.getByTestId("warehouse-plan-ledger")).toBeVisible();
	const row = page.getByRole("row").filter({ hasText: "只读建设规划" });
	await expect(row.getByRole("button", { name: "查看" })).toBeVisible();
	await expect(row.getByRole("button", { name: "编辑" })).toHaveCount(0);
	await expect(row.getByRole("button", { name: "归档" })).toHaveCount(0);
	await expect(page.getByTestId("warehouse-plan-ledger-primary-action")).toBeDisabled();
	expect(calls.update).toBe(0);
	expect(calls.archive).toBe(0);
	assertCleanBrowser(probe);
});

test("warehouse plan ledger distinguishes load and projection failures from an empty ledger", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const recoveredPlan = plan("plan-ledger-recovery", "ASSET_FIRST", "恢复后的建设规划");
	const calls = await installWarehousePlanApi(page, {
		list: (call) => (call === 1 ? { httpStatus: 503, body: { code: "SERVICE_UNAVAILABLE" } } : [recoveredPlan]),
		getPlan: () => recoveredPlan,
		baseline: () => ({ ready: false, missingCodes: [] }),
		projection: () => ({ httpStatus: 503, body: { code: "PROJECTION_UNAVAILABLE" } }),
	});

	await page.goto("/#/modeling/plans");
	await expect(page.getByTestId("warehouse-plan-ledger-load-error")).toBeVisible();
	await expect(page.getByTestId("warehouse-plan-ledger-empty")).toHaveCount(0);
	await page.getByRole("button", { name: "重新加载" }).click();
	await expect(page.getByText("恢复后的建设规划", { exact: true })).toBeVisible();
	await expect(page.getByText("证据未知", { exact: true })).toBeVisible();
	expect(calls.list).toBe(2);
	assertCleanBrowser(probe, [{ status: 503, includes: "/warehouse-plans" }]);
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
				nextAction: `/modeling/plans/${createdPlan.id}/baseline`,
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
	await expect(page).toHaveURL(/\/modeling\/plans\/plan-business\/baseline$/);
	await expect(page.getByText("经营分析主题数仓", { exact: true })).toBeVisible();
	await expect(page.getByRole("tab", { name: "业务分类" })).toHaveAttribute("aria-selected", "true");
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
				nextAction: `/modeling/plans/${createdPlan.id}/baseline`,
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
	await expect(page).toHaveURL(/\/modeling\/plans\/plan-asset\/baseline$/);
	await expect(page.getByRole("tab", { name: "来源盘点" })).toHaveAttribute("aria-selected", "true");
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

test("plan modeling section starts with the dimension catalog and keeps the exact plan context", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelingPlan = plan("plan-modeling", "BUSINESS_FIRST", "维度建模计划");
	await installWarehousePlanApi(page, {
		list: () => [modelingPlan],
		getPlan: () => modelingPlan,
		baseline: () => ({ ready: true, missingCodes: [] }),
		projection: (planId) => stageProjection(planId),
	});

	await page.goto(`/#/modeling/plans/${modelingPlan.id}/models`);
	const modelingSection = page
		.getByRole("heading", { name: "事实与维度" })
		.locator("xpath=ancestor::div[contains(@class, 'ant-card')][1]");
	await expect(modelingSection.getByRole("button")).toHaveText(["维度目录", "模型中心"]);
	await modelingSection.getByRole("button", { name: "维度目录" }).click();
	await expect(page).toHaveURL(new RegExp(`/modeling/dimensions\\?planId=${modelingPlan.id}$`));
	await expect(page.getByTestId("dimension-catalog-page")).toBeVisible();
	await expect(
		page.getByText("已锁定当前建设计划；登记时可在计划已确认的业务分类内选择", { exact: true }),
	).toBeVisible();
	await page.screenshot({ path: path.join(evidenceDir, "dimension-catalog-entry-desktop.png"), fullPage: true });
	await page.goBack();
	await expect(page).toHaveURL(new RegExp(`/modeling/plans/${modelingPlan.id}/models$`));
	await page.getByRole("button", { name: "模型中心" }).click();
	await expect(page).toHaveURL(new RegExp(`/modeling/models\\?planId=${modelingPlan.id}$`));
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	await page.goBack();
	await page.getByRole("button", { name: "维度目录" }).click();
	await expect(page.getByTestId("dimension-catalog-page")).toBeVisible();

	await page.setViewportSize({ width: 390, height: 844 });
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({ path: path.join(evidenceDir, "dimension-catalog-entry-narrow.png"), fullPage: true });
	assertCleanBrowser(probe);
});

test("model detail deep-link keeps canonical tabs, server plan context and narrow standard table", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelId = "40000000-0000-0000-0000-000000000001";
	const serverPlanId = "10000000-0000-0000-0000-000000000001";
	const domainId = "20000000-0000-0000-0000-000000000001";
	const measurementUnitId = "50000000-0000-0000-0000-000000000001";
	const standardElementId = "51000000-0000-0000-0000-000000000001";
	let savedAmountBinding: Record<string, unknown> | undefined;
	const model = {
		contractVersion: 2,
		id: modelId,
		planId: serverPlanId,
		domainId,
		modelType: "FACT",
		layer: "DWD",
		name: "订单明细",
		description: "记录订单事实",
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: null,
		consumptionScenario: null,
		grain: { statement: "一行代表一笔订单", keys: ["order_id"] },
		factShape: "TRANSACTION",
		timeSemantics: { type: "EVENT_TIME", fields: ["created_at"] },
		fields: [
			{ name: "order_id", dataType: "string", nullable: false, role: "KEY", securityLevel: "RESTRICTED" },
			{ name: "amount", dataType: "decimal(18,2)", nullable: false, role: "MEASURE" },
		],
		sourceRefs: [
			{
				kind: "TABLE",
				ref: "ods.orders",
				layer: "ODS",
				role: "PRIMARY",
				sortOrder: 0,
				sourceBindingId: "30000000-0000-0000-0000-000000000001",
				resolvedVersion: "v1",
			},
		],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [
			{
				fieldName: "amount",
				measurementUnitId,
				measurementUnitVersion: 3,
				securityLevel: "INTERNAL",
			},
		],
		generationStrategy: null,
		status: "DRAFT",
		revision: 2,
		checksum: "model-checksum-2",
		createdAt: "2026-07-19T03:00:00Z",
		updatedAt: "2026-07-19T04:00:00Z",
		compatibilityMode: "CANONICAL",
		legacyRefs: null,
	};

	await page.route("**/api/**", async (route) => {
		const pathName = new URL(route.request().url()).pathname;
		if (pathName === `/api/modeling/model-specs/${modelId}`) {
			if (route.request().method() === "PUT") {
				const command = route.request().postDataJSON() as { standardBindings?: Record<string, unknown>[] };
				savedAmountBinding = command.standardBindings?.find((binding) => binding.fieldName === "amount");
				await fulfill(route, {
					...model,
					standardBindings: command.standardBindings || [],
					revision: 3,
					checksum: "model-checksum-3",
				});
				return;
			}
			await fulfill(route, model);
			return;
		}
		if (pathName === "/api/modeling/model-specs") {
			await fulfill(route, [model]);
			return;
		}
		if (pathName === "/api/modeling/metadata-standards") {
			await fulfill(route, {
				content: [{ id: standardElementId, fieldNameCn: "订单金额", fieldNameEn: "order_amount", version: 3 }],
				total: 1,
				page: 0,
				size: 500,
			});
			return;
		}
		if (pathName === "/api/governance/reference-codes") {
			await fulfill(route, {
				content: [{ codeTypeId: "PAYMENT_STATUS", codeTypeName: "支付状态", version: "v4", status: 1 }],
				total: 1,
				page: 0,
				size: 500,
			});
			return;
		}
		if (pathName === "/api/governance/measurement-units") {
			await fulfill(route, [
				{ id: measurementUnitId, code: "CNY", name: "人民币", symbol: "¥", version: 3, status: "ACTIVE" },
			]);
			return;
		}
		await fulfill(route, []);
	});

	await page.goto(`/#/modeling/models/${modelId}?tab=standards&planId=forged-plan`);
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();
	await expect(page.getByRole("tab", { name: "字段标准" })).toHaveAttribute("aria-selected", "true");
	await expect(page.getByText(`${measurementUnitId} · v3`, { exact: true })).toBeVisible();
	await expect(page.getByText("当前显示已保存版本 r2", { exact: true })).toBeVisible();
	await expect(page.getByText("RESTRICTED", { exact: true })).toBeVisible();
	await expect(page.getByText("已关联", { exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "前往数据元" })).toBeVisible();
	await page.getByRole("button", { name: "配置字段标准" }).nth(1).click();
	const standardDialog = page.getByRole("dialog", { name: "配置字段标准：amount" });
	await expect(standardDialog).toBeVisible();
	await standardDialog.getByRole("combobox").nth(0).click();
	await page.getByText("订单金额 · v3", { exact: true }).last().click();
	await standardDialog.getByRole("combobox").nth(1).click();
	await page.getByText("支付状态 · v4", { exact: true }).last().click();
	await standardDialog.getByRole("button", { name: /保\s*存\s*绑\s*定/ }).click();
	await expect(standardDialog).toBeHidden();
	await expect(page.getByText(`${standardElementId} · v3`, { exact: true })).toBeVisible();
	await expect(page.getByText("PAYMENT_STATUS · v4", { exact: true })).toBeVisible();
	expect(savedAmountBinding).toMatchObject({
		fieldName: "amount",
		standardElementId,
		standardElementVersion: 3,
		referenceCode: "PAYMENT_STATUS",
		referenceCodeVersion: 4,
		measurementUnitId,
		measurementUnitVersion: 3,
	});
	await page.screenshot({ path: path.join(evidenceDir, "model-detail-standards.png"), fullPage: true });

	await page.getByRole("tab", { name: "字段设计" }).click();
	await expect(page).toHaveURL(new RegExp(`tab=fields&planId=${serverPlanId}$`));
	await expect(page.locator("#fields_0_name")).toBeDisabled();
	await expect(page.getByRole("button", { name: "删除字段 1" })).toBeDisabled();
	await page.getByRole("button", { name: "添加字段" }).click();
	await page.getByPlaceholder("例如：customer_id").last().fill("discount_amount");
	await page.getByRole("tab", { name: "模型设计" }).click();
	await page.getByRole("tab", { name: "字段设计" }).click();
	await expect(page.locator('input[value="discount_amount"]')).toBeVisible();

	await page.getByRole("tab", { name: "字段标准" }).click();
	await page.setViewportSize({ width: 390, height: 844 });
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({ path: path.join(evidenceDir, "model-detail-standards-narrow.png"), fullPage: true });

	await page.getByRole("button", { name: "返回模型中心" }).click();
	await expect(page).toHaveURL(new RegExp(`/modeling/models\\?planId=${serverPlanId}$`));
	assertCleanBrowser(probe);
});

test("summary detail derives current stale and unavailable upstream states from dependency graph", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelId = "41000000-0000-0000-0000-000000000001";
	const currentId = "42000000-0000-0000-0000-000000000001";
	const staleId = "42000000-0000-0000-0000-000000000002";
	const unknownId = "42000000-0000-0000-0000-000000000003";
	const model = {
		contractVersion: 2,
		id: modelId,
		planId: "10000000-0000-0000-0000-000000000001",
		domainId: "20000000-0000-0000-0000-000000000001",
		modelType: "SUMMARY",
		layer: "DWS",
		name: "订单日汇总",
		description: "按天汇总订单",
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: null,
		consumptionScenario: null,
		grain: { statement: "每行代表一天", keys: ["summary_date"] },
		factShape: null,
		timeSemantics: null,
		fields: [
			{ name: "summary_date", dataType: "date", nullable: false, role: "KEY" },
			{ name: "order_amount", dataType: "decimal(18,2)", nullable: false, role: "MEASURE" },
		],
		sourceRefs: [],
		dependsOn: [
			{ modelSpecId: currentId, revision: 2 },
			{ modelSpecId: staleId, revision: 3 },
			{ modelSpecId: unknownId, revision: 1 },
		],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [],
		generationStrategy: null,
		dimensionProfile: null,
		status: "DRAFT",
		revision: 4,
		checksum: "c".repeat(64),
		createdAt: "2026-07-20T00:00:00Z",
		updatedAt: "2026-07-20T00:00:00Z",
		compatibilityMode: "CANONICAL",
		legacyRefs: null,
	};

	await page.route("**/api/**", async (route) => {
		const pathName = new URL(route.request().url()).pathname;
		if (pathName === `/api/modeling/model-specs/${modelId}/dependencies`) {
			await fulfill(route, {
				rootModelSpecId: modelId,
				nodes: [
					{
						modelSpecId: modelId,
						pinnedRevision: 4,
						currentRevision: 4,
						name: model.name,
						modelType: "SUMMARY",
						status: "DRAFT",
						restricted: false,
					},
					{
						modelSpecId: currentId,
						pinnedRevision: 2,
						currentRevision: 2,
						name: "订单明细",
						modelType: "FACT",
						status: "PUBLISHED",
						restricted: false,
					},
					{
						modelSpecId: staleId,
						pinnedRevision: 3,
						currentRevision: 5,
						name: "客户维度",
						modelType: "DIMENSION",
						status: "PUBLISHED",
						restricted: false,
					},
					{
						modelSpecId: unknownId,
						pinnedRevision: 1,
						currentRevision: null,
						name: null,
						modelType: null,
						status: null,
						restricted: true,
					},
				],
				edges: [
					{
						fromModelSpecId: modelId,
						toModelSpecId: currentId,
						pinnedRevision: 2,
						currentRevision: 2,
						state: "CURRENT",
					},
					{ fromModelSpecId: modelId, toModelSpecId: staleId, pinnedRevision: 3, currentRevision: 5, state: "STALE" },
					{
						fromModelSpecId: modelId,
						toModelSpecId: unknownId,
						pinnedRevision: 1,
						currentRevision: null,
						state: "UNKNOWN",
					},
				],
			});
			return;
		}
		if (pathName === `/api/modeling/model-specs/${modelId}`) {
			await fulfill(route, model);
			return;
		}
		if (pathName === "/api/modeling/model-specs") {
			await fulfill(route, [model]);
			return;
		}
		await fulfill(route, []);
	});

	await page.goto(`/#/modeling/models/${modelId}?tab=design`);
	await expect(page.getByTestId("model-spec-dependency-panel")).toBeVisible();
	await expect(page.getByText("当前版本", { exact: true })).toBeVisible();
	await expect(page.getByText("版本漂移", { exact: true })).toBeVisible();
	await expect(page.getByText("引用不可用", { exact: true })).toBeVisible();
	await expect(page.getByText("受限上游", { exact: true })).toBeVisible();
	await page.screenshot({ path: path.join(evidenceDir, "model-detail-dependencies.png"), fullPage: true });

	await page.setViewportSize({ width: 390, height: 844 });
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({ path: path.join(evidenceDir, "model-detail-dependencies-narrow.png"), fullPage: true });
	assertCleanBrowser(probe);
});

test("dimension draft journey stays object-free, saves with CAS and returns with server context on narrow screens", async ({
	page,
}) => {
	const probe = installBrowserProbe(page);
	const planId = "10000000-0000-0000-0000-000000000067";
	const domainId = "20000000-0000-0000-0000-000000000067";
	const modelId = "40000000-0000-0000-0000-000000000067";
	const modelingPlan = plan(planId, "BUSINESS_FIRST", "通用维度建模计划");
	let currentModel = dimension({ id: modelId, planId, domainId });
	let createBody: Record<string, unknown> | null = null;
	let updateBody: Record<string, unknown> | null = null;
	let updateIfMatch = "";

	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const pathName = new URL(request.url()).pathname;
		if (request.method() === "GET" && pathName === "/api/catalog/domains/tree") {
			await fulfill(route, [{ key: domainId, name: "项目管理" }]);
			return;
		}
		if (request.method() === "GET" && pathName === PLAN_RESOURCE) {
			await fulfill(route, [modelingPlan]);
			return;
		}
		if (request.method() === "GET" && pathName === `${PLAN_RESOURCE}/${planId}/baseline/categories`) {
			await fulfill(route, {
				value: {
					domainBindings: [
						{
							domainId,
							confirmationStatus: "CONFIRMED",
							resolutionStatus: "AVAILABLE",
							name: "项目管理",
							code: "PM",
						},
					],
					readiness: "READY",
					issues: [],
				},
				version: 1,
			});
			return;
		}
		if (pathName === "/api/modeling/model-specs" && request.method() === "GET") {
			await fulfill(route, createBody ? [currentModel] : []);
			return;
		}
		if (pathName === "/api/modeling/model-specs" && request.method() === "POST") {
			createBody = request.postDataJSON() as Record<string, unknown>;
			const createViewFields = { ...createBody };
			delete createViewFields.idempotencyKey;
			currentModel = {
				...currentModel,
				...createViewFields,
				id: modelId,
				status: "DRAFT",
				revision: 1,
				checksum: DIMENSION_CHECKSUM_V1,
				compatibilityMode: "CANONICAL",
				legacyRefs: null,
			};
			await fulfill(route, currentModel);
			return;
		}
		if (pathName === `/api/modeling/model-specs/${modelId}` && request.method() === "GET") {
			await fulfill(route, currentModel);
			return;
		}
		if (pathName === `/api/modeling/model-specs/${modelId}` && request.method() === "PUT") {
			updateBody = request.postDataJSON() as Record<string, unknown>;
			updateIfMatch = request.headers()["if-match"] || "";
			currentModel = {
				...currentModel,
				...updateBody,
				revision: 2,
				checksum: DIMENSION_CHECKSUM_V2,
				updatedAt: "2026-07-19T05:00:00Z",
			};
			await fulfill(route, currentModel);
			return;
		}
		await fulfill(route, []);
	});

	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto(`/#/modeling/dimensions?planId=${planId}&domainId=${domainId}`);
	await expect(page.getByTestId("dimension-catalog-page")).toBeVisible();
	await page.getByRole("button", { name: "登记维度" }).click();
	const drawer = page.getByRole("dialog", { name: "登记维度" });
	await expect(drawer).toBeVisible();
	await expect(drawer.locator(".ant-select-selection-item").filter({ hasText: "项目管理" })).toBeVisible();
	await page.getByLabel("维度名称").fill("组织维度");
	await page.getByLabel("维度定义").fill("统一组织机构分析口径");
	await page.getByLabel("每行代表什么").fill("每行代表一个组织机构");
	await page.getByLabel("维度键").fill("organization_id");
	await page.getByLabel("维度编码").fill("DIM_ORGANIZATION");
	await expect
		.poll(
			async () => {
				const box = await drawer.boundingBox();
				return box ? box.x + box.width : Number.POSITIVE_INFINITY;
			},
			{ message: "dimension drawer finishes its entrance motion inside the viewport" },
		)
		.toBeLessThanOrEqual(390);
	const narrowMetrics = await page.evaluate(() => {
		return {
			innerWidth: window.innerWidth,
			documentWidth: document.documentElement.scrollWidth,
			bodyWidth: document.body.scrollWidth,
		};
	});
	await page.screenshot({ path: path.join(evidenceDir, "dimension-draft-create-narrow.png"), fullPage: true });
	const drawerBox = await drawer.boundingBox();
	expect(drawerBox, "dimension drawer bounding box").not.toBeNull();
	expect(drawerBox?.x || 0).toBeGreaterThanOrEqual(0);
	expect((drawerBox?.x || 0) + (drawerBox?.width || 0)).toBeLessThanOrEqual(390);
	expect(narrowMetrics.documentWidth).toBeLessThanOrEqual(narrowMetrics.innerWidth);
	expect(narrowMetrics.bodyWidth).toBeLessThanOrEqual(narrowMetrics.innerWidth);
	await page.getByRole("button", { name: "保存草稿" }).click();

	await expect(page).toHaveURL(new RegExp(`/modeling/models/${modelId}$`));
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();
	expect(createBody).toMatchObject({
		planId,
		domainId,
		modelType: "DIMENSION",
		description: "统一组织机构分析口径",
		grain: { statement: "每行代表一个组织机构", keys: ["organization_id"] },
		dimensionProfile: {
			dimensionCode: "DIM_ORGANIZATION",
			hierarchies: [],
			scdPolicy: { type: "NONE" },
			reuseScope: "PLAN",
		},
	});
	expect((createBody?.fields || []) as unknown[]).toContainEqual({
		name: "organization_id",
		dataType: "string",
		nullable: false,
		role: "KEY",
	});
	for (const retiredField of [
		"businessObjectId",
		"semanticObjectId",
		"objectId",
		"businessProcessId",
		"processId",
		"businessActivityRef",
	]) {
		expect(createBody).not.toHaveProperty(retiredField);
	}

	await expect(page.getByRole("button", { name: "保存草稿" })).toBeVisible();
	await page.getByLabel("维度定义").fill("统一组织机构分析口径（已复核）");
	await page.getByRole("button", { name: "保存草稿" }).click();
	await expect(page.getByText("版本 r2", { exact: true })).toBeVisible();
	expect(updateIfMatch).toBe(`"model-spec:${modelId}:1:${DIMENSION_CHECKSUM_V1}"`);
	expect(updateBody).toMatchObject({
		description: "统一组织机构分析口径（已复核）",
		grain: { statement: "每行代表一个组织机构", keys: ["organization_id"] },
	});
	expect(updateBody).not.toHaveProperty("idempotencyKey");

	await page.getByRole("button", { name: "返回维度目录" }).click();
	await expect(page).toHaveURL(new RegExp(`/modeling/dimensions\\?planId=${planId}&domainId=${domainId}$`));
	await expect(page.getByText("组织维度", { exact: true })).toBeVisible();
	assertCleanBrowser(probe);
});

test("published dimension is read-only and ignores forged catalog context", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelId = "40000000-0000-0000-0000-000000000068";
	const planId = "10000000-0000-0000-0000-000000000068";
	const domainId = "20000000-0000-0000-0000-000000000068";
	const published = dimension({
		id: modelId,
		planId,
		domainId,
		name: "已发布组织维度",
		status: "PUBLISHED",
		revision: 7,
		checksum: "c".repeat(64),
	});
	let writes = 0;

	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const pathName = new URL(request.url()).pathname;
		if (request.method() === "PUT" || request.method() === "POST") writes += 1;
		if (pathName === `/api/modeling/model-specs/${modelId}`) {
			await fulfill(route, published);
			return;
		}
		if (pathName === "/api/modeling/model-specs") {
			await fulfill(route, [published]);
			return;
		}
		if (pathName === "/api/catalog/domains/tree") {
			await fulfill(route, [{ key: domainId, name: "服务端业务分类" }]);
			return;
		}
		if (pathName === PLAN_RESOURCE) {
			await fulfill(route, [plan(planId, "BUSINESS_FIRST", "服务端建设计划")]);
			return;
		}
		await fulfill(route, []);
	});

	await page.goto(`/#/modeling/models/${modelId}?planId=forged-plan&domainId=forged-domain`);
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();
	await expect(page.getByText("当前状态为已发布；只有草稿可编辑", { exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "保存草稿" })).toHaveCount(0);
	await expect(page.getByLabel("维度名称")).toBeDisabled();
	await expect(page.getByLabel("维度定义")).toBeDisabled();
	await page.getByRole("button", { name: "返回维度目录" }).click();
	await expect(page).toHaveURL(new RegExp(`/modeling/dimensions\\?planId=${planId}&domainId=${domainId}$`));
	expect(writes).toBe(0);
	assertCleanBrowser(probe);
});

test("dimension catalog keeps model-list failure distinct from empty state and retries", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const planId = "10000000-0000-0000-0000-000000000069";
	const domainId = "20000000-0000-0000-0000-000000000069";
	const existing = dimension({ id: "40000000-0000-0000-0000-000000000069", planId, domainId, name: "重试后维度" });
	let modelListCalls = 0;

	await page.route("**/api/**", async (route) => {
		const pathName = new URL(route.request().url()).pathname;
		if (pathName === "/api/modeling/model-specs") {
			modelListCalls += 1;
			if (modelListCalls === 1) {
				await fulfill(route, { httpStatus: 503, body: { status: 503, message: "维度目录暂时不可用" } });
				return;
			}
			await fulfill(route, [existing]);
			return;
		}
		if (pathName === PLAN_RESOURCE) {
			await fulfill(route, [plan(planId, "BUSINESS_FIRST", "目录恢复计划")]);
			return;
		}
		if (pathName === "/api/catalog/domains/tree") {
			await fulfill(route, [{ key: domainId, name: "项目管理" }]);
			return;
		}
		await fulfill(route, []);
	});

	await page.goto(`/#/modeling/dimensions?planId=${planId}&domainId=${domainId}`);
	await expect(page.getByText("维度目录加载失败，请稍后重试", { exact: true })).toBeVisible();
	await expect(page.getByText("还没有维度，点击“登记维度”开始", { exact: true })).toHaveCount(0);
	await expect(page.locator(".ant-table")).toHaveCount(0);
	await page.getByRole("button", { name: "重试" }).click();
	await expect(page.getByText("重试后维度", { exact: true })).toBeVisible();
	expect(modelListCalls).toBe(2);
	assertCleanBrowser(probe, [{ status: 503, includes: "/modeling/model-specs" }]);
});

test("measurement-unit owner exposes version and reference entry points with safe model return", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelId = "40000000-0000-0000-0000-000000000081";
	const unitId = "50000000-0000-0000-0000-000000000081";
	const unit = {
		id: unitId,
		code: "CNY",
		name: "人民币",
		symbol: "¥",
		quantityKind: "CURRENCY",
		conversionFactor: 1,
		baseUnitRef: null,
		precision: 2,
		status: "ACTIVE",
		version: 3,
		checksum: "d".repeat(64),
		createdAt: "2026-07-20T00:00:00Z",
		updatedAt: "2026-07-20T01:00:00Z",
	};
	await page.route("**/api/**", async (route) => {
		const pathName = new URL(route.request().url()).pathname;
		if (pathName === "/api/governance/measurement-units") {
			await fulfill(route, [unit]);
			return;
		}
		if (pathName === `/api/governance/measurement-units/${unitId}/versions`) {
			await fulfill(route, [unit, { ...unit, version: 2, checksum: "c".repeat(64) }]);
			return;
		}
		if (pathName === `/api/governance/measurement-units/${unitId}/references`) {
			await fulfill(route, {
				totalReferences: 1,
				restrictedReferences: 0,
				items: [
					{
						resourceType: "MODEL_SPEC_FIELD",
						resourceId: modelId,
						displayName: "订单事实.amount",
						referencedVersion: 2,
						currentVersion: 3,
						driftStatus: "STALE",
						repairRoute: `/modeling/models/${modelId}?tab=standards`,
						restricted: false,
					},
				],
			});
			return;
		}
		await fulfill(route, []);
	});

	const returnTo = `/modeling/models/${modelId}?tab=standards`;
	await page.goto(`/#/governance/standards/units?modelSpecId=${modelId}&returnTo=${encodeURIComponent(returnTo)}`);
	await expect(page.getByTestId("measurement-units-page")).toBeVisible();
	await expect(page.getByText("人民币", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: /版\s*本/ }).click();
	await expect(page.getByRole("dialog", { name: "版本历史" })).toBeVisible();
	await page.getByRole("button", { name: "Close" }).click();
	await page.getByRole("button", { name: /引\s*用/ }).click();
	await expect(page.getByText("订单事实.amount", { exact: true })).toBeVisible();
	await expect(page.getByText("STALE", { exact: true })).toBeVisible();
	await page.screenshot({ path: path.join(evidenceDir, "f6-measurement-unit-owner-chromium95.png"), fullPage: true });

	await page.setViewportSize({ width: 390, height: 844 });
	const viewportMetrics = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewportMetrics.documentWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	expect(viewportMetrics.bodyWidth).toBeLessThanOrEqual(viewportMetrics.innerWidth);
	await page.screenshot({
		path: path.join(evidenceDir, "f6-measurement-unit-owner-chromium95-narrow.png"),
		fullPage: true,
	});
	assertCleanBrowser(probe);
});

test("published metric model creates field-anchored draft and binds exact owner version", async ({ page }) => {
	const probe = installBrowserProbe(page);
	const modelId = "40000000-0000-0000-0000-000000000082";
	const unitId = "50000000-0000-0000-0000-000000000082";
	const indicatorId = "60000000-0000-0000-0000-000000000082";
	const draftIndicatorId = "60000000-0000-0000-0000-000000000083";
	const publishedModel = {
		contractVersion: 2,
		id: modelId,
		planId: "10000000-0000-0000-0000-000000000082",
		domainId: "20000000-0000-0000-0000-000000000082",
		modelType: "FACT",
		layer: "DWD",
		name: "订单事实",
		description: "已发布订单事实",
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: "ORDER_CREATED",
		consumptionScenario: null,
		grain: { statement: "每行一笔订单", keys: ["order_id"] },
		factShape: "TRANSACTION",
		timeSemantics: { type: "EVENT_TIME", fields: ["created_at"] },
		fields: [
			{ name: "order_id", dataType: "string", nullable: false, role: "KEY" },
			{ name: "created_at", dataType: "timestamp", nullable: false, role: "TIME" },
			{ name: "amount", dataType: "decimal(18,2)", nullable: false, role: "MEASURE" },
		],
		sourceRefs: [],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [{ fieldName: "amount", measurementUnitId: unitId, measurementUnitVersion: 3 }],
		generationStrategy: null,
		dimensionProfile: null,
		status: "PUBLISHED",
		revision: 5,
		checksum: "e".repeat(64),
		createdAt: "2026-07-20T00:00:00Z",
		updatedAt: "2026-07-20T01:00:00Z",
		compatibilityMode: "CANONICAL",
		legacyRefs: null,
	};
	const publishedIndicator = {
		id: indicatorId,
		code: "ORDER_AMOUNT",
		name: "订单金额",
		status: "PUBLISHED",
		version: "v2",
	};
	let referenceBody: Record<string, unknown> | null = null;
	let metricIfMatch = "";

	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const pathName = new URL(request.url()).pathname;
		if (request.method() === "GET" && pathName === "/api/modeling/model-specs") {
			await fulfill(route, [publishedModel]);
			return;
		}
		if (request.method() === "GET" && pathName === "/api/governance/indicators") {
			await fulfill(route, { content: [publishedIndicator], total: 1, page: 0, size: 200, totalPages: 1 });
			return;
		}
		if (request.method() === "GET" && pathName === "/api/governance/measurement-units") {
			await fulfill(route, [
				{
					id: unitId,
					code: "CNY",
					name: "人民币",
					symbol: "¥",
					quantityKind: "CURRENCY",
					conversionFactor: 1,
					baseUnitRef: null,
					precision: 2,
					status: "ACTIVE",
					version: 3,
					checksum: "f".repeat(64),
					createdAt: "2026-07-20T00:00:00Z",
					updatedAt: "2026-07-20T01:00:00Z",
				},
			]);
			return;
		}
		if (request.method() === "POST" && pathName === "/api/governance/indicators") {
			await fulfill(route, {
				id: draftIndicatorId,
				code: "ORDER_AMOUNT_DRAFT",
				name: "订单事实-amount",
				status: "DRAFT",
				version: "v1",
			});
			return;
		}
		if (request.method() === "POST" && pathName === `/api/governance/indicators/${draftIndicatorId}/references`) {
			referenceBody = request.postDataJSON() as Record<string, unknown>;
			await fulfill(route, { id: "reference-1" });
			return;
		}
		if (request.method() === "PUT" && pathName === `/api/modeling/model-specs/${modelId}/metric-refs`) {
			metricIfMatch = request.headers()["if-match"] || "";
			await fulfill(route, {
				...publishedModel,
				metricRefs: [{ metricId: indicatorId, version: 2 }],
				revision: 6,
				checksum: "a".repeat(64),
			});
			return;
		}
		await fulfill(route, []);
	});

	await page.goto(`/#/modeling/metric-workbench?modelSpecId=${modelId}`);
	await expect(page.getByText("人民币 (¥) v3", { exact: false })).toBeVisible();
	await page.getByRole("button", { name: "创建原子指标草稿" }).click();
	await page.getByRole("button", { name: /确\s*定/ }).click();
	await expect(page).toHaveURL(/governance\/indicators\/dictionary/);
	expect(referenceBody).toMatchObject({
		refType: "MODEL_SPEC_FIELD",
		refTarget: `${modelId}@5#amount`,
	});
	const notes = JSON.parse(String(referenceBody?.notes || "{}"));
	expect(notes).toMatchObject({ measurementUnitId: unitId, measurementUnitVersion: 3 });

	await page.goto(`/#/modeling/metric-workbench?modelSpecId=${modelId}`);
	await page.getByRole("button", { name: "关联已发布指标" }).click();
	await page.getByRole("dialog", { name: "关联已发布指标版本" }).locator(".ant-select-selector").click();
	await page.getByText("订单金额 · v2", { exact: true }).click();
	await page.getByRole("button", { name: /确\s*定/ }).click();
	await expect(page.getByRole("dialog", { name: "关联已发布指标版本" })).toBeHidden();
	await expect(page.getByText("当前", { exact: true }).first()).toBeVisible();
	expect(metricIfMatch).toBe(`"model-spec:${modelId}:5:${"e".repeat(64)}"`);
	await page.screenshot({ path: path.join(evidenceDir, "f6-model-metric-handoff-chromium95.png"), fullPage: true });
	assertCleanBrowser(probe);
});
