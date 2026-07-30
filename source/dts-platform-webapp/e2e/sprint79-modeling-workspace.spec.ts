import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/it/evidence/IT-01",
);

type BrowserFailures = {
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
};

const observeFailures = (page: Page): BrowserFailures => {
	const failures: BrowserFailures = { pageErrors: [], requestFailures: [], httpFailures: [] };
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) => {
		const errorText = request.failure()?.errorText ?? "unknown";
		if (errorText === "net::ERR_ABORTED") return;
		failures.requestFailures.push(`${request.method()} ${request.url()} ${errorText}`);
	});
	page.on("response", (response) => {
		if (response.status() < 400) return;
		const pathname = new URL(response.url()).pathname;
		if (pathname.startsWith("/api/")) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${pathname}`);
		}
	});
	return failures;
};

const workspaceSearch = (page: Page) => {
	const hash = new URL(page.url()).hash;
	return new URLSearchParams(hash.includes("?") ? hash.slice(hash.indexOf("?") + 1) : "");
};

const expectWorkspaceState = async (
	page: Page,
	expected: { module: string; workspaceView?: string; planId?: string },
) => {
	await expect
		.poll(() => {
			const search = workspaceSearch(page);
			return {
				module: search.get("module"),
				workspaceView: search.get("workspaceView") || undefined,
				planId: search.get("planId") || undefined,
			};
		})
		.toEqual(expected);
};

test.beforeAll(() => {
	fs.mkdirSync(evidenceDir, { recursive: true });
});

test("seven-module workspace preserves planning context and opens canonical owners", async ({ page }) => {
	const failures = observeFailures(page);

	await page.goto("/#/modeling/workbench?module=home");
	const shell = page.getByTestId("modeling-workspace-shell");
	await expect(shell).toBeVisible();

	const workspaceTabs = shell.getByRole("tab");
	await expect(workspaceTabs).toHaveCount(7);
	for (const label of ["首页", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
		await expect(shell.getByRole("tab", { name: label, exact: true })).toBeVisible();
	}

	const planId = workspaceSearch(page).get("planId") || undefined;
	await page.screenshot({ path: path.join(evidenceDir, "workspace-home.png"), fullPage: true });

	await shell.getByRole("tab", { name: "数仓规划", exact: true }).click();
	await expectWorkspaceState(page, { module: "planning", planId });
	const panelNav = page.getByRole("navigation", { name: "当前模块功能" });
	await expect(
		page.getByText("请先在顶部选择建设计划", { exact: true }).or(page.getByText("规划基线", { exact: true })),
	).toBeVisible();
	await panelNav.getByRole("button", { name: "来源盘点", exact: true }).click();
	await expectWorkspaceState(page, { module: "planning", workspaceView: "sources", planId });
	await page.reload();
	await expect(page.getByTestId("modeling-workspace-shell")).toBeVisible();
	await expectWorkspaceState(page, { module: "planning", workspaceView: "sources", planId });
	await expect(page.getByRole("navigation", { name: "当前模块功能" }).getByRole("button", { name: "来源盘点" })).toHaveAttribute(
		"aria-current",
		"page",
	);

	await page.getByRole("tab", { name: "数据标准", exact: true }).click();
	await expectWorkspaceState(page, { module: "standards", planId });
	await expect(page.getByTestId("governance-elements-view-references").first()).toBeVisible();

	await page.getByRole("tab", { name: "维度建模", exact: true }).click();
	await expectWorkspaceState(page, { module: "models", planId });
	await expect(page.getByTestId("dimension-catalog-page")).toBeVisible();
	await page.getByRole("navigation", { name: "当前模块功能" }).getByRole("button", { name: "逻辑模型" }).click();
	await expectWorkspaceState(page, { module: "models", workspaceView: "model-specs", planId });
	await expect(page.getByTestId("model-center-page")).toBeVisible();

	await page.getByRole("tab", { name: "数据指标", exact: true }).click();
	await expectWorkspaceState(page, { module: "metrics", planId });
	await expect(page.getByText("指标定义与发布", { exact: true })).toBeVisible();

	await page.getByRole("tab", { name: "通用工具", exact: true }).click();
	await expectWorkspaceState(page, { module: "tools", planId });
	await page.getByRole("navigation", { name: "当前模块功能" }).getByRole("button", { name: "建模工具" }).click();
	await expectWorkspaceState(page, { module: "tools", workspaceView: "utilities", planId });
	await page.getByRole("button", { name: /导入模型包/ }).click();
	await expect(page.getByText("导入已有模型", { exact: true })).toBeVisible();
	await expect
		.poll(() => {
			const search = workspaceSearch(page);
			return {
				module: search.get("module"),
				workspaceView: search.get("workspaceView"),
				planId: search.get("planId") || undefined,
				modelImport: search.get("modelImport"),
			};
		})
		.toEqual({ module: "tools", workspaceView: "utilities", planId, modelImport: "open" });
	await page.screenshot({ path: path.join(evidenceDir, "workspace-tools-import.png"), fullPage: true });
	await page.keyboard.press("Escape");

	await page.getByRole("tab", { name: "关系图", exact: true }).click();
	await expectWorkspaceState(page, { module: "graph", planId });
	const relationshipGraph = page.getByTestId("relationship-graph-panel");
	await expect(relationshipGraph.getByText("建设计划关系图", { exact: true })).toBeVisible();
	await expect(relationshipGraph.getByText(/^[1-9]\d* 个节点$/)).toBeVisible();
	await expect(relationshipGraph.getByText(/^[1-9]\d* 条关系$/)).toBeVisible();
	await page.screenshot({ path: path.join(evidenceDir, "workspace-seven-modules.png"), fullPage: true });

	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
	expect(failures.httpFailures).toEqual([]);
});
