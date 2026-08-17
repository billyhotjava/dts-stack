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
						card: { id: 11, name: "项目综合分析", type: "analysis", display: "table" },
					}],
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

test("publishes governed analysis and dashboard, then safely retries registration", async ({ page }, testInfo) => {
	analysisPublished = false;
	dashboardPublished = false;
	dashboardRegistration = "NOT_REGISTERED";
	await installIdentity(page);
	await installApis(page);
	const failures = collectFailures(page);

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
	await expect(page.getByText("DASHBOARD_AUDIENCE_REQUIRED", { exact: true })).toBeVisible();
	await enterFirstTag(page, "发布仪表板", "D1");
	await page.locator(".ant-drawer").filter({ hasText: "发布仪表板" }).getByRole("button", { name: "重新校验" }).click();
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
	const noPageOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noPageOverflow).toBe(true);
	await page.screenshot({ path: testInfo.outputPath("dashboard-registration-retry-768x900.png"), fullPage: true });

	expect(failures.pageErrors).toEqual([]);
	expect(failures.consoleErrors).toEqual([]);
	expect(failures.failedResponses).toEqual([]);
});
