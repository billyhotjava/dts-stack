import { expect, type Page, type Route, test } from "@playwright/test";

const dataset = {
	datasetId: "6d5770dc-6daf-4f8b-8441-2be60caa61bd",
	name: "项目经营分析数据集",
	version: 1,
	semanticContractVersion: "dts.query-dataset-contract/v1",
	contractChecksum: "checksum-dataset-v1",
	warehouseLayer: "DWS",
	classification: "DATA_INTERNAL",
};

const initialQuerySpec = {
	apiVersion: "dts.analysis/v1" as const,
	dataset: {
		id: dataset.datasetId,
		version: 1,
		contractVersion: dataset.semanticContractVersion,
		checksum: dataset.contractChecksum,
	},
	dimensions: [],
	metrics: [],
	derivedMetrics: [],
	filters: [],
	timeRange: null,
	orderBy: [],
	limit: 5000,
	visualization: { type: "table", settings: {} },
};

let analysisPublished = false;
let savedQuerySpec: Record<string, unknown> = structuredClone(initialQuerySpec);
let savedDashboardBody: Record<string, unknown> | null = null;
const dashboardQueries: Array<{ dashcardId: number; body: Record<string, unknown> }> = [];

function platformEnvelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

async function json(route: Route, data: unknown, envelope = false) {
	await route.fulfill({
		status: 200,
		contentType: "application/json",
		body: envelope ? platformEnvelope(data) : JSON.stringify(data),
	});
}

function analysisDto() {
	return {
		id: 11,
		name: "项目综合分析",
		description: "用于项目经营驾驶舱",
		lifecycleStatus: analysisPublished ? "PUBLISHED" : "DRAFT",
		versionNo: analysisPublished ? 3 : 2,
		publishedRevisionId: analysisPublished ? 91 : null,
		queryDatasetId: dataset.datasetId,
		queryDatasetVersion: 1,
		contractVersion: dataset.semanticContractVersion,
		visualization: (savedQuerySpec.visualization as Record<string, unknown>) ?? initialQuerySpec.visualization,
		querySpec: savedQuerySpec,
		createdBy: "sprint95-maintainer",
		updatedAt: "2026-08-20T06:00:00Z",
		permissions: { read: true, write: !analysisPublished, publish: !analysisPublished, export: analysisPublished },
	};
}

function queryResult(spec: Record<string, unknown>) {
	const dimensions = Array.isArray(spec.dimensions) ? spec.dimensions as Array<{ field: string }> : [];
	const metrics = Array.isArray(spec.metrics) ? spec.metrics as Array<{ code: string }> : [];
	const derivedMetrics = Array.isArray(spec.derivedMetrics) ? spec.derivedMetrics as Array<{ code: string }> : [];
	const columns = [
		...dimensions.map((item) => ({ name: item.field, display_name: item.field, base_type: "type/Text" })),
		...metrics.map((item) => ({ name: item.code, display_name: item.code, base_type: "type/Integer" })),
		...derivedMetrics.map((item) => ({ name: item.code, display_name: item.code, base_type: "type/Float" })),
	];
	return {
		queryId: "query-sprint95",
		columns,
		rows: [
			columns.map((column, index) => column.base_type === "type/Text" ? "研发中心" : 8 + index),
			columns.map((column, index) => column.base_type === "type/Text" ? "销售中心" : 5 + index),
		],
		rowCount: 2,
		truncated: false,
		cacheHit: false,
		durationMs: 18,
		contractChecksum: dataset.contractChecksum,
	};
}

async function installIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "sprint95-maintainer",
						fullName: "Sprint 95 Maintainer",
						roles: ["ROLE_OP_ADMIN", "ROLE_ANALYST"],
						permissions: ["read", "write", "export"],
						enabled: true,
					},
					userToken: { accessToken: "sprint95-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page) {
	const apiRequest = /^https?:\/\/[^/]+\/(?:bi\/api\/|api\/(?:sql\/|session\/status(?:\?.*)?$|menu\/tree(?:\?.*)?$))/;
	await page.route(apiRequest, async (route) => {
		const request = route.request();
		const url = new URL(request.url());
		const path = url.pathname;

		if (path === `/api/sql/query-datasets/${dataset.datasetId}/published/1`) {
			return json(route, {
				dataset,
				dimensions: [{ code: "department", label: "责任部门", dataType: "STRING", filterOps: ["EQ", "IN"] }],
				metrics: [{ code: "project_count", label: "项目数" }],
				joins: [],
				policyRefs: ["project-rls-v1"],
			}, true);
		}
		if (path === "/api/session/status") return json(route, { authenticated: true, remainingSeconds: 3600 }, true);
		if (path === "/api/menu/tree") return json(route, [], true);

		if (path === "/bi/api/analysis/11" && request.method() === "GET") return json(route, analysisDto());
		if (path === "/bi/api/analysis/11" && request.method() === "PUT") {
			const body = JSON.parse(request.postData() ?? "{}") as { querySpec?: Record<string, unknown> };
			savedQuerySpec = structuredClone(body.querySpec ?? initialQuerySpec);
			return json(route, analysisDto());
		}
		if (path === "/bi/api/analysis/preview") {
			const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>;
			return json(route, queryResult(body));
		}
		if (path === "/bi/api/analysis/11/validate") {
			const body = JSON.parse(request.postData() ?? "{}") as { deptCodes?: string[]; roleCodes?: string[] };
			const valid = Boolean(body.deptCodes?.length || body.roleCodes?.length);
			return json(route, {
				valid,
				blockers: valid ? [] : [{ code: "ANALYSIS_AUDIENCE_REQUIRED", path: "audience", message: "audience required" }],
				warnings: [],
				dependencySnapshot: { datasetVersion: 1, contractChecksum: dataset.contractChecksum },
			});
		}
		if (path === "/bi/api/analysis/11/publish") {
			analysisPublished = true;
			return json(route, {
				analysisId: 11,
				revisionId: 91,
				versionNo: 3,
				status: "PUBLISHED",
				contractChecksum: "analysis-revision-checksum",
				dependencySnapshot: { datasetVersion: 1 },
				publishedAt: "2026-08-20T06:05:00Z",
			});
		}
		if (path === "/bi/api/analysis/11/query/csv") {
			return route.fulfill({
				status: 200,
				contentType: "text/csv; charset=utf-8",
				headers: { "Content-Disposition": "attachment; filename=project-analysis.csv" },
				body: "department,project_count\n研发中心,8\n",
			});
		}
		if (path === "/bi/api/collection") return json(route, []);
		if (path === "/bi/api/card") {
			return json(route, [
				{ id: 11, name: "来源部门分析", type: "analysis", display: "bar", lifecycle_status: "PUBLISHED", published_revision_id: 91 },
				{ id: 12, name: "目标趋势分析", type: "analysis", display: "bar", lifecycle_status: "PUBLISHED", published_revision_id: 92 },
			]);
		}
		if (path === "/bi/api/dashboard/22" && request.method() === "GET") {
			const savedDashcards = savedDashboardBody && Array.isArray(savedDashboardBody.dashcards)
				? savedDashboardBody.dashcards as Array<Record<string, unknown>>
				: null;
			return json(route, {
				id: 22,
				name: "项目经营驾驶舱",
				description: "定向联动验收",
				collection_id: null,
				lifecycle_status: "DRAFT",
				published_revision_id: null,
				registration_status: "NOT_REGISTERED",
				version_no: 1,
				parameters: [],
				ordered_cards: [
					{
						id: 1, card_id: 11, row: 0, col: 0, size_x: 6, size_y: 4,
						parameter_mappings: [], visualization_settings: savedDashcards?.[0]?.visualization_settings ?? {},
						card: { id: 11, name: "来源部门分析", type: "analysis", display: "bar" },
					},
					{
						id: 2, card_id: 12, row: 0, col: 6, size_x: 6, size_y: 4,
						parameter_mappings: [], visualization_settings: savedDashcards?.[1]?.visualization_settings ?? {},
						card: { id: 12, name: "目标趋势分析", type: "analysis", display: "bar" },
					},
				],
			});
		}
		if (path === "/bi/api/dashboard/save") {
			savedDashboardBody = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>;
			return json(route, { id: 22, name: "项目经营驾驶舱", lifecycle_status: "DRAFT", ordered_cards: savedDashboardBody.dashcards });
		}
		const dashcardMatch = path.match(/^\/bi\/api\/dashboard\/22\/dashcard\/(\d+)\/card\/(\d+)\/query$/);
		if (dashcardMatch) {
			const dashcardId = Number(dashcardMatch[1]);
			dashboardQueries.push({ dashcardId, body: JSON.parse(request.postData() ?? "{}") as Record<string, unknown> });
			return json(route, {
				status: "completed",
				row_count: 2,
				running_time: 12,
				data: {
					rows: [["研发中心", 8], ["销售中心", 5]],
					cols: [
						{ name: "department", display_name: "责任部门", base_type: "type/Text" },
						{ name: "project_count", display_name: "项目数", base_type: "type/Integer" },
					],
				},
			});
		}
		return json(route, path.startsWith("/bi/api/") ? {} : {}, !path.startsWith("/bi/api/"));
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

async function enterFirstTag(page: Page, drawerTitle: string, value: string) {
	const drawer = page.locator(".ant-drawer").filter({ hasText: drawerTitle });
	const input = drawer.locator(".ant-select-selection-search-input").first();
	await input.fill(value);
	await input.press("Enter");
}

test("completes governed BI authoring, publication, export, and targeted linkage", async ({ page }, testInfo) => {
	analysisPublished = false;
	savedQuerySpec = structuredClone(initialQuerySpec);
	savedDashboardBody = null;
	dashboardQueries.length = 0;
	await installIdentity(page);
	await installApis(page);
	const failures = collectFailures(page);

	await page.goto("/#/bi/questions/11/edit");
	await expect(page.getByRole("heading", { name: "治理分析编辑器" })).toBeVisible();
	await page.getByTestId("analysis-field-dimension-department").dragTo(page.getByTestId("analysis-shelf-x"));
	await page.getByTestId("analysis-field-metric-project_count").dragTo(page.getByTestId("analysis-shelf-y"));
	await expect(page.getByText(/2 行 · 18 ms · 实时查询/)).toBeVisible();

	const visualization = page.locator(".ant-select").filter({ hasText: "明细表" }).first();
	await visualization.click();
	await page.getByText("柱状图", { exact: true }).click();
	await page.getByRole("checkbox", { name: "显示数值" }).check();
	await page.getByPlaceholder("编码，如 conversion_rate").fill("avg_projects");
	await page.getByPlaceholder("表达式，如 approved_count / NULLIF(total_count, 0)").fill("project_count / 2");
	await page.getByRole("button", { name: "添加计算指标" }).click();
	await expect(page.getByTestId("analysis-shelf-y")).toContainText("avg_projects");
	await page.getByRole("button", { name: "保存草稿" }).click();
	await expect(page.getByText("分析草稿已保存", { exact: true })).toBeVisible();

	const savedVisualization = savedQuerySpec.visualization as { type?: string; settings?: Record<string, unknown> };
	expect(savedVisualization.type).toBe("bar");
	expect(savedVisualization.settings?.["graph.show_values"]).toBe(true);
	expect(savedQuerySpec.derivedMetrics).toEqual([
		expect.objectContaining({ code: "avg_projects", expression: "project_count / 2" }),
	]);

	await page.getByRole("button", { name: /^发\s*布$/ }).click();
	await expect(page.getByText("ANALYSIS_AUDIENCE_REQUIRED", { exact: true })).toBeVisible();
	await enterFirstTag(page, "发布分析", "D1");
	const publishDrawer = page.locator(".ant-drawer").filter({ hasText: "发布分析" });
	await publishDrawer.getByRole("button", { name: "重新校验" }).click();
	await expect(page.getByText("校验通过，可以发布", { exact: true })).toBeVisible();
	await publishDrawer.getByRole("button", { name: "确认发布" }).click();
	await expect(page.getByText("该分析已发布，只能查看", { exact: false })).toBeVisible();

	const downloadPromise = page.waitForEvent("download");
	await page.getByRole("button", { name: "导出 CSV" }).click();
	const download = await downloadPromise;
	expect(download.suggestedFilename()).toBe("project-analysis.csv");
	await expect(page.getByText("已导出 project-analysis.csv", { exact: true })).toBeVisible();
	await page.screenshot({ path: testInfo.outputPath("analysis-authoring-published-1366x768.png"), fullPage: true });

	await page.goto("/#/bi/dashboards/22/edit");
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	const sourceCard = page.getByTestId("dashboard-card-1");
	await sourceCard.getByRole("button", { name: "联动" }).click();
	const popover = page.locator(".ant-popover").filter({ hasText: "图表联动" });
	await popover.getByRole("checkbox", { name: "点击图表后筛选其他组件" }).check();
	await popover.locator(".ant-select").click();
	await page.getByText("目标趋势分析", { exact: true }).last().click();
	await page.keyboard.press("Escape");
	await popover.getByRole("button", { name: "保存联动配置" }).click();
	await page.getByRole("button", { name: "保存草稿" }).click();
	await expect.poll(() => savedDashboardBody).not.toBeNull();

	const savedDashcards = savedDashboardBody?.dashcards as Array<Record<string, unknown>>;
	expect(savedDashcards[0].visualization_settings).toMatchObject({
		"dts.cross_filter.enabled": true,
		"dts.cross_filter.target_card_ids": [2],
	});

	await page.goto("/#/bi/dashboards/22/edit");
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	await page.getByRole("button", { name: /^预\s*览$/ }).click();
	await expect(sourceCard.locator("canvas")).toBeVisible();
	dashboardQueries.length = 0;
	await sourceCard.locator("canvas").click({ position: { x: 180, y: 150 } });
	await expect(page.getByText(/department.*研发中心|责任部门.*研发中心/)).toBeVisible();
	await expect.poll(() => dashboardQueries.length).toBeGreaterThanOrEqual(2);
	const sourceQuery = dashboardQueries.find((item) => item.dashcardId === 1);
	const targetQuery = dashboardQueries.find((item) => item.dashcardId === 2);
	expect(sourceQuery?.body.parameters).toEqual([]);
	expect(targetQuery?.body.parameters).toEqual([
		expect.objectContaining({ type: "category", value: "研发中心" }),
	]);

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	const noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
	await page.screenshot({ path: testInfo.outputPath("dashboard-targeted-linkage-768x900.png"), fullPage: true });

	expect(failures.pageErrors).toEqual([]);
	expect(failures.consoleErrors).toEqual([]);
	expect(failures.failedResponses).toEqual([]);
});
