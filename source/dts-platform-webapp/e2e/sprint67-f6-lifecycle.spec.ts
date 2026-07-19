import fs from "node:fs";
import path from "node:path";
import { expect, type Page, type Route, test } from "@playwright/test";

const MODEL_ID = "67000000-0000-4000-8000-000000000002";
const PLAN_ID = "67000000-0000-4000-8000-000000000001";
const CHECKSUM = "6".repeat(64);
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

const modelSpec = {
	contractVersion: 2,
	id: MODEL_ID,
	planId: PLAN_ID,
	domainId: "67000000-0000-4000-8000-000000000003",
	modelType: "FACT",
	layer: "DWD",
	name: "项目节点明细",
	description: "Sprint-67 F6 构建发布运行闭环验收模型",
	implementationMode: "DBT_MANAGED",
	materialization: "table",
	businessActivityRef: "project-node-lifecycle",
	consumptionScenario: "项目节点进度分析",
	grain: { statement: "每行代表一个项目节点在一个计划日的状态", keys: ["project_id", "node_id", "plan_date"] },
	factShape: "TRANSACTION",
	timeSemantics: { type: "EVENT_TIME", fields: ["plan_date"] },
	generationStrategy: null,
	fields: [
		{ name: "project_id", dataType: "string", nullable: false, role: "KEY" },
		{ name: "node_id", dataType: "string", nullable: false, role: "KEY" },
		{ name: "plan_date", dataType: "date", nullable: false, role: "TIME" },
	],
	sourceRefs: [{ kind: "TABLE", ref: "ods_project_node", layer: "ODS", role: "PRIMARY", sortOrder: 0, sourceBindingId: "67000000-0000-4000-8000-000000000004", resolvedVersion: "v1" }],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
	status: "DRAFT",
	revision: 3,
	checksum: CHECKSUM,
	createdAt: "2026-07-20T00:00:00Z",
	updatedAt: "2026-07-20T00:10:00Z",
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
};

const stageGates = ["DRAFT_SAVE", "IMPLEMENTATION_READY", "RELEASE_READY"].map((stage) => ({
	modelSpecId: MODEL_ID,
	revision: 3,
	checksum: CHECKSUM,
	stage,
	status: stage === "RELEASE_READY" ? "BLOCKED" : "READY",
	blockers: stage === "RELEASE_READY" ? [{ code: "TEST_EVIDENCE_MISSING", field: "tests", message: "等待真实 dbt 测试", repairRoute: "/studio/sql-modeling" }] : [],
}));

const successful = (data: unknown) => ({ status: 200, data, message: "OK" });

async function fulfill(route: Route, data: unknown) {
	await route.fulfill({
		status: 200,
		contentType: "application/json; charset=utf-8",
		body: JSON.stringify(successful(data)),
	});
}

async function installSession(page: Page) {
	await page.addInitScript(() => {
		const now = Date.now();
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: { username: "chrome95-reviewer", fullName: "Chrome 95 验收用户", roles: ["ROLE_OP_ADMIN"], permissions: [], enabled: true },
					userToken: { accessToken: `dev-access-chrome95-${now}` },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", String(now));
		localStorage.setItem("dts.platform.session.lastActivity", String(now));
	});
}

async function installApi(page: Page) {
	await page.route("**/api/**", async (route) => {
		const url = new URL(route.request().url());
		const pathname = url.pathname;
		if (pathname === `/api/modeling/model-specs/${MODEL_ID}/stage-gates`) return fulfill(route, stageGates);
		if (pathname === `/api/modeling/model-specs/${MODEL_ID}/dependencies`) {
			return fulfill(route, { rootModelSpecId: MODEL_ID, nodes: [], edges: [] });
		}
		if (pathname === `/api/modeling/model-specs/${MODEL_ID}`) return fulfill(route, modelSpec);
		if (pathname === "/api/modeling/model-specs") return fulfill(route, [modelSpec]);
		if (pathname === "/api/modeling/sql-models") {
			return fulfill(route, [{ id: MODEL_ID, planId: PLAN_ID, planName: "项目主题规划", name: "project_node_detail", layer: "DWD", materialized: "table", sql: "select * from ods_project_node", enabled: true, status: "DRAFT" }]);
		}
		if (pathname === "/api/modeling/plans") return fulfill(route, [{ id: PLAN_ID, name: "项目主题规划", status: "DRAFT" }]);
		if (pathname === "/api/modeling/templates/layers") return fulfill(route, [{ layer: "DWD", name: "DWD", description: "明细数据层" }]);
		if (pathname === "/api/etl/dbt/config") return fulfill(route, { enabled: true, config: { enabled: true }, workspaceStatus: { ok: true } });
		if (pathname === "/api/etl/dbt/sync/status") return fulfill(route, { latestRun: null });
		if (pathname === "/api/infra/data-source-selections") return fulfill(route, { items: [{ id: "warehouse", name: "验收数仓", recommended: true }] });
		if (pathname === "/api/ops/instances") {
			return fulfill(route, [{ id: "dbt-run-failed-67", entryKey: "DBT_RUN", artifactId: MODEL_ID, artifactName: "project_node_detail", externalRunId: "dbt-invocation-67", status: "FAILED", startedAt: "2026-07-20T00:20:00Z", finishedAt: "2026-07-20T00:21:00Z", durationMs: 60000, message: "dbt test failed" }]);
		}
		return fulfill(route, []);
	});
}

test("F6 lifecycle preserves exact ModelSpec context and failed-run repair path in Chrome 95", async ({ page }) => {
	fs.mkdirSync(evidenceDir, { recursive: true });
	await installSession(page);
	await installApi(page);

	const pageErrors: string[] = [];
	const consoleErrors: string[] = [];
	const requestFailures: string[] = [];
	const httpFailures: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("console", (message) => { if (message.type() === "error") consoleErrors.push(message.text()); });
	page.on("requestfailed", (request) => requestFailures.push(`${request.method()} ${request.url()}`));
	page.on("response", (response) => { if (response.status() >= 400) httpFailures.push(`${response.status()} ${response.url()}`); });

	await page.setViewportSize({ width: 1366, height: 768 });
	await page.goto(`/#/modeling/models/${MODEL_ID}?tab=design&planId=${PLAN_ID}`);
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();
	await expect(page.getByTestId("model-spec-enter-implementation")).toBeVisible();
	await page.getByTestId("model-spec-enter-implementation").click();
	await expect(page.getByTestId("platform-sql-modeling-page")).toBeVisible();
	await expect(page.getByTestId("canonical-model-implementation-context")).toContainText("ModelSpec r3");
	await expect(page.getByTestId("canonical-model-implementation-context")).toContainText(`modelSpecId=${MODEL_ID}`);
	await expect(page).toHaveURL(new RegExp(`modelSpecId=${MODEL_ID}.*revision=3.*implementationMode=DBT_MANAGED`));
	await page.screenshot({ path: path.join(evidenceDir, "f6-model-implementation-context-chromium95.png"), fullPage: true });

	await page.goto(`/#/ops/instances?entryKey=DBT_RUN&modelSpecId=${MODEL_ID}&planId=${PLAN_ID}`);
	await expect(page.getByText("任务实例监控", { exact: true })).toBeVisible();
	await expect(page.getByTestId("ops-return-model-repair")).toBeVisible();
	await page.getByTestId("ops-return-model-repair").click();
	await expect(page).toHaveURL(new RegExp(`/modeling/models/${MODEL_ID}\\?tab=design&planId=${PLAN_ID}`));
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();

	await page.setViewportSize({ width: 390, height: 844 });
	const width = await page.evaluate(() => ({ viewport: window.innerWidth, document: document.documentElement.scrollWidth }));
	expect(width.document).toBeLessThanOrEqual(width.viewport);
	await page.screenshot({ path: path.join(evidenceDir, "f6-model-repair-chromium95-narrow.png"), fullPage: true });

	expect(pageErrors).toEqual([]);
	expect(consoleErrors).toEqual([]);
	expect(requestFailures).toEqual([]);
	expect(httpFailures).toEqual([]);
});
