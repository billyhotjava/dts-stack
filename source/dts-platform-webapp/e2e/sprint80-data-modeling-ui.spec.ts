import { readFileSync } from "node:fs";
import { expect, type Page, test } from "@playwright/test";

type SeedNode = {
	key: string;
	path: string;
	icon?: string;
	titleKey: string;
	title: string;
	externalLink?: string;
	children?: SeedNode[];
};

const seed = JSON.parse(
	readFileSync(
		new URL("../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: SeedNode[] };

let menuId = 1;

function toMenuTree(node: SeedNode, sectionKey: string, parentPath = "") {
	const path = node.externalLink || `${parentPath}/${node.path}`.replace(/\/{2,}/g, "/");
	const children = (node.children ?? []).map((child) => toMenuTree(child, sectionKey, path));
	return {
		id: menuId++,
		name: node.titleKey,
		displayName: node.title,
		path,
		icon: node.icon,
		deleted: false,
		metadata: JSON.stringify({
			key: node.key,
			sectionKey,
			entryKey: node.key,
			titleKey: node.titleKey,
			title: node.title,
			icon: node.icon,
			...(node.externalLink ? { externalLink: node.externalLink } : {}),
		}),
		children,
	};
}

const menuTree = seed.portalNavSections.map((node) => toMenuTree(node, node.key));

async function installSprint80Menu(page: Page) {
	await page.route(/\/api\/menu\/tree(?:\?.*)?$/, (route) =>
		route.fulfill({
			status: 200,
			contentType: "application/json",
			body: JSON.stringify({ status: 200, data: menuTree, message: "OK" }),
		}),
	);
}

const routes: Array<[string, string]> = [
	["/data-modeling/home/workspace", "建模概览"],
	["/data-modeling/planning/business-categories", "业务分类"],
	["/data-modeling/planning/layers", "数仓分层"],
	["/data-modeling/planning/domains", "数据域"],
	["/data-modeling/planning/processes", "业务过程"],
	["/data-modeling/planning/marts", "数据集市"],
	["/data-modeling/planning/subjects", "主题域"],
	["/data-modeling/planning/system", "规划参数配置"],
	["/data-modeling/standards/fields", "字段标准"],
	["/data-modeling/standards/codes", "标准代码"],
	["/data-modeling/standards/roots", "词根"],
	["/data-modeling/standards/dictionary", "命名词典"],
	["/data-modeling/standards/mappings", "标准映射"],
	["/data-modeling/dimensions/workbench", "维度建模"],
	["/data-modeling/dimensions/reverse", "逆向建模"],
	["/data-modeling/metrics/composite", "复合指标"],
	["/data-modeling/metrics/derived", "派生指标"],
	["/data-modeling/metrics/atomic", "原子指标"],
	["/data-modeling/metrics/modifiers", "修饰词"],
	["/data-modeling/metrics/periods", "时间周期"],
	["/data-modeling/tools/toolbox", "工具箱"],
	["/data-modeling/tools/imports", "导入记录"],
	["/data-modeling/tools/exports", "导出记录"],
	["/data-modeling/graphs/models", "模型关系"],
	["/data-modeling/graphs/standards", "标准关系"],
	["/data-modeling/graphs/metrics", "指标血缘"],
];

test.describe("Sprint-80 prototype-driven data modeling UI", () => {
	test.beforeEach(async ({ page }) => {
		menuId = 1;
		await installSprint80Menu(page);
	});

	test("renders data modeling as a root menu outside Data Studio", async ({ page }) => {
		await page.goto("/#/data-modeling/home/workspace");
		await expect(page.getByTestId("data-modeling-page")).toBeVisible();

		const sidebar = page.getByRole("navigation").first();
		const modelingLabels = sidebar.getByText("数据建模", { exact: true });
		await expect(modelingLabels).toHaveCount(1);
		await expect(sidebar.getByText("数据开发与运维", { exact: true }).first()).toBeVisible();

		if (!(await sidebar.getByText("建模概览", { exact: true }).last().isVisible())) {
			await modelingLabels.last().click();
		}
		for (const label of ["建模概览", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
			await expect(sidebar.getByText(label, { exact: true }).last()).toBeVisible();
		}
		await expect(sidebar.getByText("首页", { exact: true })).toHaveCount(0);
		await expect(sidebar.getByText("最近访问", { exact: true })).toHaveCount(0);
		await expect(sidebar.getByText("我的任务", { exact: true })).toHaveCount(0);
	});

	test("owns all 26 prototype leaf routes", async ({ page }) => {
		test.setTimeout(180_000);
		for (const [path, title] of routes) {
			await page.goto(`/#${path}`);
			await expect(page.getByTestId("data-modeling-page")).toBeVisible();
			await expect(page.getByTestId("data-modeling-page").locator("h1")).toHaveText(title);
			await expect(page).toHaveURL(new RegExp(`#${path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`));
		}
	});

	test("redirects representative legacy pages without rendering the retired UI", async ({ page }) => {
		for (const [legacyPath, targetPath] of [
			["/modeling/workbench?module=metrics", "/data-modeling/metrics/atomic"],
			["/studio/sql-modeling", "/data-modeling/dimensions/workbench"],
			["/modeling/plans/demo/baseline", "/data-modeling/planning/spaces"],
		]) {
			await page.goto(`/#${legacyPath}`);
			await expect(page).toHaveURL(new RegExp(`#${targetPath}$`));
		}
		// 菜单内的重定向目标正常渲染建模页；spaces 是隐藏占位（菜单外），路由级授权显示无访问权限页，属预期。
		await page.goto("/#/modeling/workbench?module=metrics");
		await expect(page.getByTestId("data-modeling-page")).toBeVisible({ timeout: 15_000 });
	});

	test("keeps in-page modeling switches addressable in the URL", async ({ page }) => {
		for (const [path, title] of [
			["/data-modeling/metrics/atomic", "原子指标"],
			["/data-modeling/metrics/derived", "派生指标"],
			["/data-modeling/dimensions/workbench", "维度建模"],
			["/data-modeling/dimensions/reverse", "逆向建模"],
		]) {
			await page.goto(`/#${path}`);
			await expect(page.getByTestId("data-modeling-page").locator("h1")).toHaveText(title);
			await expect(page).toHaveURL(new RegExp(`#${path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`));
		}
	});

	test("keeps save, submit, publish, and materialization fail-closed without a persisted draft", async ({ page }) => {
		await page.goto("/#/data-modeling/dimensions/workbench");
		await expect(page.getByTestId("data-modeling-page").locator("h1")).toHaveText("维度建模");
		// 测试租户目录可能已有保存对象（工作台会自动选中），不再断言空态；
		// 写动作的 fail-closed 通过下方“新草稿”断言覆盖，不依赖目录状态。

		// 概念维度：新草稿无变更时保存禁用；未持久化时不出现确认定义/提交/发布。
		await page.getByRole("button", { name: "新建" }).click();
		await page.getByRole("button", { name: "创建维度", exact: true }).click();
		await expect(page.getByRole("button", { name: "保存" })).toBeDisabled();
		await expect(page.getByRole("button", { name: "确认定义" })).toHaveCount(0);
		await expect(page.getByRole("button", { name: "提交" })).toHaveCount(0);
		await expect(page.getByRole("button", { name: "发布" })).toHaveCount(0);

		// 逻辑模型：新草稿保存禁用；提交/发布需要已持久化模型。
		await page.getByRole("button", { name: "新建" }).click();
		await page.getByRole("button", { name: "创建明细表" }).click();
		await expect(page.getByRole("button", { name: "保存" })).toBeDisabled();
		await expect(page.getByRole("button", { name: "提交" })).toBeDisabled();
		await expect(page.getByRole("button", { name: "发布" })).toBeDisabled();
	});
});
