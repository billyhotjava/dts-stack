import { expect, type Page, type Route, test } from "@playwright/test";

const dataset = {
	datasetId: "6d5770dc-6daf-4f8b-8441-2be60caa61bd",
	name: "项目经营分析数据集",
	description: "用于项目经营分析",
	version: 1,
	semanticContractVersion: "dts.query-dataset-contract/v1",
	contractChecksum: "checksum-dataset-v1",
	warehouseLayer: "DWS",
	classification: "DATA_INTERNAL",
	ownerDept: "项目管理部",
	bizDomain: "项目经营",
	semanticModelNames: ["项目经营模型"],
	refreshStrategy: "每日刷新",
	updatedAt: "2026-08-17T06:00:00Z",
};

const querySpec = {
	apiVersion: "dts.analysis/v1",
	dataset: {
		id: dataset.datasetId,
		version: 1,
		contractVersion: dataset.semanticContractVersion,
		checksum: dataset.contractChecksum,
	},
	dimensions: [{ field: "department", alias: null }],
	metrics: [{ code: "project_count", alias: null }],
	derivedMetrics: [],
	filters: [],
	timeRange: null,
	orderBy: [],
	limit: 5000,
	visualization: { type: "table", settings: {} },
};

let analysisPublished = false;
let dashboardPublished = false;
let dashboardRegistration = "NOT_REGISTERED";

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

function audienceValidation(body: string | null, dashboard: boolean) {
	const value = body ? JSON.parse(body) as { deptCodes?: string[]; roleCodes?: string[] } : {};
	const valid = Boolean(value.deptCodes?.length || value.roleCodes?.length);
	return {
		valid,
		blockers: valid ? [] : [{
			code: dashboard ? "DASHBOARD_AUDIENCE_REQUIRED" : "ANALYSIS_AUDIENCE_REQUIRED",
			path: "audience",
			message: "at least one department or role is required",
		}],
		warnings: [],
		dependencySnapshot: dashboard
			? { dashboardId: 22, analyses: [{ analysisId: 11, analysisRevisionId: 91 }], queryBudgetVersion: "analysis-query-budget/v1" }
			: { datasetId: dataset.datasetId, datasetVersion: 1, contractChecksum: dataset.contractChecksum },
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
						username: "sprint94-maintainer",
						fullName: "Sprint 94 Maintainer",
						roles: ["ROLE_OP_ADMIN", "ROLE_ANALYST"],
						permissions: ["read", "write", "export"],
						enabled: true,
					},
					userToken: { accessToken: "sprint94-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page) {
	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const url = new URL(request.url());
		const path = url.pathname;

		if (path.startsWith("/bi/api/")) {
			if (path === "/bi/api/analysis/preview") {
				return json(route, {
					queryId: request.headers()["x-correlation-id"] ?? "sprint94-preview",
					columns: [{ name: "project_count", display_name: "项目数", base_type: "type/Integer" }],
					rows: [[8]],
					rowCount: 1,
					truncated: false,
					cacheHit: false,
					durationMs: 12,
					contractChecksum: dataset.contractChecksum,
				});
			}
			if (path === "/bi/api/analysis" && request.method() === "GET") {
				return json(route, {
					items: [{
						id: 11,
						name: "项目综合分析",
						description: "用于项目经营驾驶舱",
						lifecycleStatus: "PUBLISHED",
						versionNo: 2,
						publishedRevisionId: 91,
						queryDatasetId: dataset.datasetId,
						queryDatasetVersion: 1,
						contractVersion: dataset.semanticContractVersion,
						visualization: querySpec.visualization,
						querySpec,
						createdBy: "sprint94-maintainer",
						updatedAt: "2026-08-17T06:00:00Z",
						permissions: { read: true, write: true, publish: true },
					}],
					page: 0,
					size: 10,
					totalElements: 1,
					totalPages: 1,
				});
			}
			if (path === "/bi/api/analysis/11" && request.method() === "GET") {
				return json(route, {
					id: 11,
					name: "项目综合分析",
					description: "用于项目经营驾驶舱",
					lifecycleStatus: analysisPublished ? "PUBLISHED" : "DRAFT",
					versionNo: analysisPublished ? 2 : 1,
					publishedRevisionId: analysisPublished ? 91 : null,
					queryDatasetId: dataset.datasetId,
					queryDatasetVersion: 1,
					contractVersion: dataset.semanticContractVersion,
					visualization: querySpec.visualization,
					querySpec,
					createdBy: "sprint94-maintainer",
					updatedAt: "2026-08-17T06:00:00Z",
					permissions: { read: true, write: true, publish: true },
				});
			}
			if (path === "/bi/api/analysis/11/validate") {
				return json(route, audienceValidation(request.postData(), false));
			}
			if (path === "/bi/api/analysis/11/publish") {
				analysisPublished = true;
				return json(route, {
					analysisId: 11,
					revisionId: 91,
					versionNo: 2,
					status: "PUBLISHED",
					contractChecksum: "analysis-revision-checksum",
					dependencySnapshot: { datasetVersion: 1 },
					publishedAt: "2026-08-17T06:05:00Z",
				});
			}
			if (path === "/bi/api/analysis/11/versions") {
				return json(route, [{
					revisionId: 91,
					versionNo: 2,
					status: "PUBLISHED",
					contractChecksum: "analysis-revision-checksum",
					dependencySnapshot: { datasetVersion: 1 },
					publishedBy: 7,
					publishedAt: "2026-08-17T06:05:00Z",
					createdAt: "2026-08-17T06:05:00Z",
				}]);
			}
			if (path === "/bi/api/collection") return json(route, []);
			if (path === "/bi/api/card") {
				return json(route, [{
					id: 11,
					name: "项目综合分析",
					type: "analysis",
					display: "table",
					lifecycle_status: "PUBLISHED",
					published_revision_id: 91,
				}]);
			}
			if (path === "/bi/api/dashboard" && request.method() === "GET") {
				return json(route, [{
					id: 22,
					name: "项目经营驾驶舱",
					description: "受治理项目经营看板",
					archived: false,
					updated_at: "2026-08-17T06:00:00Z",
				}]);
			}
			if (path === "/bi/api/dashboard/22" && request.method() === "GET") {
				return json(route, {
					id: 22,
					name: "项目经营驾驶舱",
					description: "受治理项目经营看板",
					collection_id: null,
					lifecycle_status: dashboardPublished ? "PUBLISHED" : "DRAFT",
					published_revision_id: dashboardPublished ? 101 : null,
					registration_status: dashboardPublished ? dashboardRegistration : "NOT_REGISTERED",
					version_no: dashboardPublished ? 1 : 0,
					parameters: [],
					ordered_cards: [{
						id: 1,
						card_id: 11,
						row: 0,
						col: 0,
						size_x: 6,
						size_y: 4,
						parameter_mappings: [],
						visualization_settings: {},
						card: {
							id: 11,
							name: "项目综合分析",
							type: "analysis",
							display: "table",
							lifecycle_status: "PUBLISHED",
							published_revision_id: 91,
						},
					}],
				});
			}
			if (path === "/bi/api/dashboard/save") {
				const body = JSON.parse(request.postData() ?? "{}") as {
					dashboard?: Record<string, unknown>;
					dashcards?: Array<Record<string, unknown>>;
				};
				return json(route, {
					id: 22,
					name: "项目经营驾驶舱",
					lifecycle_status: "DRAFT",
					registration_status: "NOT_REGISTERED",
					parameters: body.dashboard?.parameters ?? [],
					ordered_cards: (body.dashcards ?? []).map((dashcard, index) => ({
						...dashcard,
						id: index + 1,
						card: {
							id: 11,
							name: "项目综合分析",
							type: "analysis",
							display: "table",
							lifecycle_status: "PUBLISHED",
							published_revision_id: 91,
						},
					})),
				});
			}
			if (path === "/bi/api/dashboard/22/validate") {
				return json(route, audienceValidation(request.postData(), true));
			}
			if (path === "/bi/api/dashboard/22/publish") {
				dashboardPublished = true;
				dashboardRegistration = "PENDING_REGISTRATION";
				return json(route, {
					dashboardId: 22,
					revisionId: 101,
					versionNo: 1,
					lifecycleStatus: "PUBLISHED",
					registrationStatus: "PENDING_REGISTRATION",
					contractChecksum: "dashboard-revision-checksum",
					dependencySnapshot: { analyses: [{ analysisRevisionId: 91 }] },
					publishedAt: "2026-08-17T06:10:00Z",
				});
			}
			if (path === "/bi/api/dashboard/22/versions") {
				return json(route, [{
					revisionId: 101,
					versionNo: 1,
					status: "PUBLISHED",
					contractChecksum: "dashboard-revision-checksum",
					dependencySnapshot: { analyses: [{ analysisRevisionId: 91 }] },
					publishedBy: 7,
					publishedAt: "2026-08-17T06:10:00Z",
					createdAt: "2026-08-17T06:10:00Z",
				}]);
			}
			if (path === "/bi/api/dashboard/22/registration/retry") {
				dashboardRegistration = "PENDING_REGISTRATION";
				return json(route, { queued: true });
			}
			if (path === "/bi/api/dashboard/22/dashcard/1/card/11/query") {
				return json(route, {
					status: "completed",
					row_count: 1,
					running_time: 12,
					data: {
						rows: [[8]],
						cols: [{ name: "project_count", display_name: "项目数", base_type: "type/Integer" }],
					},
				});
			}
			return json(route, {});
		}

		if (path === "/api/sql/query-datasets/published") {
			return json(route, {
				items: [dataset],
				page: 0,
				size: 10,
				totalElements: 1,
				totalPages: 1,
			}, true);
		}
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
		if (path === "/api/directory/orgs") {
			return json(route, [{ id: 1, name: "项目管理部", deptCode: "D1", children: [] }], true);
		}
		if (path === "/api/directory/roles") {
			return json(route, [{ id: "analyst", name: "ROLE_ANALYST", description: "分析人员" }], true);
		}
		return json(route, {}, true);
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

async function expectStandardTableActions(page: Page, labels: string[]) {
	const table = page.locator(".dts-compact-table");
	await expect(table).toBeVisible();
	await expect(table.locator(".ant-table-tbody .ant-table-cell-fix-right").first()).toBeVisible();
	for (const label of labels) {
		const accessibleName = new RegExp(`^${[...label].join("\\s*")}$`);
		const button = table.getByRole("button", { name: accessibleName });
		await expect(button).toBeVisible();
		await expect(button).toHaveClass(/ant-btn-sm/);
		await expect(button).not.toHaveClass(/ant-btn-link/);
	}
	const actionWrap = await table.locator(".ant-table-tbody .ant-table-cell-fix-right .ant-space").first().evaluate((element) =>
		getComputedStyle(element).flexWrap,
	);
	expect(actionWrap).not.toBe("wrap");
	const noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
}

test("aligns all BI analysis management tables with the standard action style", async ({ page }, testInfo) => {
	await installIdentity(page);
	await installApis(page);
	const failures = collectFailures(page);
	const routes = [
		{ path: "/#/bi/questions", ready: "分析", actions: ["查看", "编辑", "归档"], screenshot: "analysis-list" },
		{
			path: "/#/bi/dashboards",
			ready: "项目经营驾驶舱",
			actions: ["查看", "编辑", "发布", "分享", "删除"],
			screenshot: "dashboard-list",
		},
		{ path: "/#/bi/data", ready: "项目经营分析数据集", actions: ["查看详情", "创建分析"], screenshot: "dataset-list" },
	];

	for (const viewport of [{ width: 1366, height: 768 }, { width: 768, height: 900 }]) {
		await page.setViewportSize(viewport);
		for (const route of routes) {
			await page.goto(route.path);
			await expect(page.getByText(route.ready, { exact: true }).first()).toBeVisible();
			await expectStandardTableActions(page, route.actions);
			await page.screenshot({
				path: testInfo.outputPath(`${route.screenshot}-${viewport.width}x${viewport.height}.png`),
				fullPage: true,
			});
		}
	}

	expect(failures.pageErrors).toEqual([]);
	expect(failures.consoleErrors).toEqual([]);
	expect(failures.failedResponses).toEqual([]);
});

test("publishes governed analysis and dashboard, then safely retries registration", async ({ page }, testInfo) => {
	analysisPublished = false;
	dashboardPublished = false;
	dashboardRegistration = "NOT_REGISTERED";
	await installIdentity(page);
	await installApis(page);
	const failures = collectFailures(page);

	await page.goto("/#/bi/questions");
	await expect(page.getByRole("heading", { name: "分析", exact: true })).toBeVisible();
	await expect(page.getByRole("button", { name: "从已发布数据集创建分析" })).toBeVisible();
	await expect(page.getByRole("link", { name: "项目综合分析" })).toBeVisible();
	await expect(page.getByText("已发布", { exact: true })).toBeVisible();
	await expect(page.getByRole("link", { name: /^查\s*看$/ })).toBeVisible();
	let noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
	await page.screenshot({ path: testInfo.outputPath("analysis-list-1366x768.png"), fullPage: true });

	await page.setViewportSize({ width: 768, height: 900 });
	noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
	const actionWrap = await page.locator(".ant-table-tbody .ant-space").first().evaluate((element) =>
		getComputedStyle(element).flexWrap,
	);
	expect(actionWrap).not.toBe("wrap");
	await page.screenshot({ path: testInfo.outputPath("analysis-list-768x900.png"), fullPage: true });
	await page.setViewportSize({ width: 1366, height: 768 });

	await page.goto("/#/bi/questions/11/edit");
	await expect(page.getByRole("heading", { name: "治理分析编辑器" })).toBeVisible();
	await page.getByRole("button", { name: /^校\s*验$/ }).click();
	await expect(page.getByText("ANALYSIS_AUDIENCE_REQUIRED", { exact: true })).toBeVisible();
	await enterFirstTag(page, "发布分析", "D1");
	await page.locator(".ant-drawer").filter({ hasText: "发布分析" }).getByRole("button", { name: "重新校验" }).click();
	await expect(page.getByText("校验通过，可以发布", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: "确认发布" }).click();
	await expect(page.getByText("该分析已发布，只能查看", { exact: false })).toBeVisible();
	await page.getByRole("button", { name: "版本历史" }).click();
	await expect(page.getByText("v2", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: "Close" }).click();
	await page.screenshot({ path: testInfo.outputPath("analysis-published-1366x768.png"), fullPage: true });

	await page.goto("/#/bi/dashboards/22/edit");
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	await expect(page.getByRole("textbox", { name: "未命名看板" })).toHaveValue("项目经营驾驶舱");
	await page.getByRole("button", { name: /^校\s*验$/ }).click();
	await expect(page.getByText("请选择至少一个可见部门或可见角色。", { exact: true })).toBeVisible();
	const dashboardDrawer = page.locator(".ant-drawer").filter({ hasText: "发布仪表板" });
	await dashboardDrawer.locator(".ant-select").first().click();
	await page.getByText("项目管理部", { exact: true }).last().click();
	await dashboardDrawer.getByRole("button", { name: "重新校验" }).click();
	await expect(page.getByText("校验通过，可以发布", { exact: true })).toBeVisible();
	await page.getByRole("button", { name: "确认发布" }).click();
	await expect(page.getByText("业务入口正在注册", { exact: true })).toBeVisible();
	await page.screenshot({ path: testInfo.outputPath("dashboard-pending-1366x768.png"), fullPage: true });

	dashboardRegistration = "REGISTRATION_FAILED";
	await page.reload();
	await expect(page.getByRole("button", { name: "重试注册" })).toBeVisible();
	await page.getByRole("button", { name: "重试注册" }).click();
	await expect(page.getByText("业务入口正在注册", { exact: true })).toBeVisible();

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
	await page.screenshot({ path: testInfo.outputPath("dashboard-registration-retry-768x900.png"), fullPage: true });

	expect(failures.pageErrors).toEqual([]);
	expect(failures.consoleErrors).toEqual([]);
	expect(failures.failedResponses).toEqual([]);
});
