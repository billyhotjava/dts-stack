import { expect, type Page, type TestInfo, test } from "@playwright/test";
import {
	installSprint79ProductionReadOnlyBarrier,
	type Sprint79ReadOnlyFailures,
} from "./support/sprint79ProductionReadOnly";

type ApiObservation = {
	method: string;
	pathname: string;
	status: number;
	contentType: string;
};

type RouteExpectation = {
	path: string;
	title: string;
	section?: string;
	marker: string;
};

const representativeRoutes: RouteExpectation[] = [
	{ path: "/data-modeling/home/workspace", title: "建模概览", marker: "最近模型" },
	{
		path: "/data-modeling/planning/business-categories",
		title: "业务分类",
		section: "数仓规划",
		marker: "新建业务分类",
	},
	{ path: "/data-modeling/planning/layers", title: "数仓分层", section: "数仓规划", marker: "新建数仓分层" },
	{ path: "/data-modeling/planning/domains", title: "数据域", section: "数仓规划", marker: "新建数据域" },
	{ path: "/data-modeling/planning/processes", title: "业务过程", section: "数仓规划", marker: "新建业务过程" },
	{ path: "/data-modeling/planning/marts", title: "数据集市", section: "数仓规划", marker: "新建数据集市" },
	{ path: "/data-modeling/planning/subjects", title: "主题域", section: "数仓规划", marker: "新建主题域" },
	{ path: "/data-modeling/planning/spaces", title: "建模空间", section: "数仓规划", marker: "新建建模空间" },
	{
		path: "/data-modeling/planning/system",
		title: "规划参数配置",
		section: "数仓规划",
		marker: "新增配置",
	},
	{ path: "/data-modeling/standards/fields", title: "字段标准", section: "数据标准", marker: "新建字段标准" },
	{ path: "/data-modeling/standards/codes", title: "标准代码", section: "数据标准", marker: "新建标准代码" },
	{ path: "/data-modeling/standards/roots", title: "词根", section: "数据标准", marker: "新建词根" },
	{
		path: "/data-modeling/standards/dictionary",
		title: "命名词典",
		section: "数据标准",
		marker: "新建命名词条",
	},
	{
		path: "/data-modeling/standards/mappings",
		title: "标准映射",
		section: "数据标准",
		marker: "新建标准映射",
	},
	{
		path: "/data-modeling/dimensions/workbench",
		title: "维度建模",
		section: "维度建模",
		marker: "模型目录",
	},
	{
		path: "/data-modeling/dimensions/reverse",
		title: "逆向建模",
		section: "维度建模",
		marker: "快速开始",
	},
	{ path: "/data-modeling/metrics/composite", title: "复合指标", section: "数据指标", marker: "复合指标基本信息" },
	{ path: "/data-modeling/metrics/derived", title: "派生指标", section: "数据指标", marker: "派生指标基本信息" },
	{ path: "/data-modeling/metrics/atomic", title: "原子指标", section: "数据指标", marker: "原子指标基本信息" },
	{ path: "/data-modeling/metrics/modifiers", title: "修饰词", section: "数据指标", marker: "修饰词基本信息" },
	{ path: "/data-modeling/metrics/periods", title: "时间周期", section: "数据指标", marker: "时间周期基本信息" },
	{ path: "/data-modeling/tools/toolbox", title: "工具箱", section: "通用工具", marker: "模型批量导入" },
	{ path: "/data-modeling/tools/imports", title: "导入记录", section: "通用工具", marker: "IMPORT-20260730-03" },
	{ path: "/data-modeling/tools/exports", title: "导出记录", section: "通用工具", marker: "EXPORT-20260730-01" },
	{ path: "/data-modeling/graphs/models", title: "模型关系", section: "关系图", marker: "monthly_execution_rate" },
	{ path: "/data-modeling/graphs/standards", title: "标准关系", section: "关系图", marker: "ACCOUNT_CODE" },
	{ path: "/data-modeling/graphs/metrics", title: "指标血缘", section: "关系图", marker: "monthly_execution_rate" },
];

function collectApiObservations(page: Page): ApiObservation[] {
	const observations: ApiObservation[] = [];
	page.on("response", (response) => {
		const url = new URL(response.url());
		if (!url.pathname.startsWith("/api/")) return;
		observations.push({
			method: response.request().method(),
			pathname: url.pathname,
			status: response.status(),
			contentType: response.headers()["content-type"] ?? "",
		});
	});
	return observations;
}

async function expectNoHorizontalPageOverflow(page: Page) {
	const widths = await page.evaluate(() => ({
		viewport: document.documentElement.clientWidth,
		document: document.documentElement.scrollWidth,
		body: document.body.scrollWidth,
	}));
	expect(widths.document).toBeLessThanOrEqual(widths.viewport + 1);
	expect(widths.body).toBeLessThanOrEqual(widths.viewport + 1);
}

async function expectRealRoute(page: Page, route: RouteExpectation) {
	const workspace = page.locator('main[class*="dmx-"]').first();
	await expect(workspace).toBeVisible({ timeout: 20_000 });
	await expect(workspace.locator("h1")).toHaveText(route.title);
	await expect(page).toHaveURL(new RegExp(`#${route.path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`));
	await expect(workspace).toContainText(route.marker);
	await expect(workspace).not.toContainText(/建设计划上下文|新建建设计划|功能开发中|后端待接入/);
	await page.waitForLoadState("networkidle", { timeout: 15_000 });
}

async function navigateThroughRealMenu(page: Page, route: RouteExpectation) {
	const navigation = page.getByRole("navigation").first();
	const target = navigation.locator(`a[href*="${route.path}"]`).last();
	if (!(await target.isVisible())) {
		for (const label of ["数据建模", route.section].filter((value): value is string => Boolean(value))) {
			const control = navigation.getByText(label, { exact: true }).last();
			if (await control.isVisible()) await control.click();
			if (await target.isVisible()) break;
		}
	}
	await expect(target, `${route.path} must be reachable from the real menu`).toBeVisible();
	await target.click();
}

function expectNoReadOnlyFailures(failures: Sprint79ReadOnlyFailures, consoleErrors: string[]) {
	expect(failures.pageErrors, "page errors").toEqual([]);
	expect(failures.requestFailures, "request failures").toEqual([]);
	expect(failures.httpFailures, "HTTP API failures").toEqual([]);
	expect(failures.modelingWrites, "unexpected API writes").toEqual([]);
	expect(consoleErrors, "console errors").toEqual([]);
}

async function capture(page: Page, testInfo: TestInfo, name: string) {
	await page.screenshot({ path: testInfo.outputPath(name), fullPage: true });
}

test.describe("Sprint-84 prototype-owned data-modeling smoke", () => {
	test("clicks the real menu and renders all 27 modeling leaves without writes", async ({ page }, testInfo) => {
		test.setTimeout(180_000);
		await page.setViewportSize({ width: 1366, height: 768 });
		const failures = await installSprint79ProductionReadOnlyBarrier(page);
		const consoleErrors: string[] = [];
		page.on("console", (message) => {
			if (message.type() === "error") consoleErrors.push(message.text());
		});
		const observations = collectApiObservations(page);
		await page.goto("/#/workbench");
		await expect
			.poll(
				() =>
					observations.some(
						(observation) =>
							observation.pathname === "/api/menu/tree" &&
							observation.status >= 200 &&
							observation.status < 300 &&
							observation.contentType.toLowerCase().includes("application/json"),
					),
				{ message: "the deployed menu tree must be read from the real API", timeout: 20_000 },
			)
			.toBe(true);

		for (const [index, route] of representativeRoutes.entries()) {
			await navigateThroughRealMenu(page, route);
			await expectRealRoute(page, route);
			await expectNoHorizontalPageOverflow(page);
			if ([0, 1, 9, 14, 16, 21, 24].includes(index)) {
				await capture(
					page,
					testInfo,
					`entry-${index + 1}-${route.path.split("/").filter(Boolean).at(-1)}-1366x768.png`,
				);
			}
		}

		const navigation = page.getByRole("navigation").first();
		await expect(navigation.getByText("数据建模", { exact: true })).toHaveCount(1);
		for (const label of ["建模概览", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
			await expect(navigation.getByText(label, { exact: true }).last()).toBeVisible();
		}
		await expect(navigation.getByText("最近访问", { exact: true })).toHaveCount(0);
		await expect(navigation.getByText("我的任务", { exact: true })).toHaveCount(0);
		const authEvidence = await page.evaluate(() => {
			const raw = window.localStorage.getItem("dts.platform.userStore");
			const stored = raw ? JSON.parse(raw) : null;
			const storedRoles = stored?.state?.userInfo?.roles;
			const storedPermissions = stored?.state?.userInfo?.permissions;
			return {
				origin: window.location.origin,
				authenticated: Boolean(stored?.state?.userInfo?.username),
				roleCount: Array.isArray(storedRoles) ? storedRoles.length : 0,
				permissionCount: Array.isArray(storedPermissions) ? storedPermissions.length : 0,
			};
		});
		await testInfo.attach("auth-evidence.json", {
			body: Buffer.from(JSON.stringify(authEvidence, null, 2)),
			contentType: "application/json",
		});
		await testInfo.attach("api-observations.json", {
			body: Buffer.from(JSON.stringify(observations, null, 2)),
			contentType: "application/json",
		});
		expectNoReadOnlyFailures(failures, consoleErrors);
	});

	test("keeps the real dimension workbench usable in a narrow viewport", async ({ page }, testInfo) => {
		await page.setViewportSize({ width: 768, height: 900 });
		const failures = await installSprint79ProductionReadOnlyBarrier(page);
		const consoleErrors: string[] = [];
		page.on("console", (message) => {
			if (message.type() === "error") consoleErrors.push(message.text());
		});
		const observations = collectApiObservations(page);

		await page.goto("/#/workbench");
		const workbenchRoute = representativeRoutes.find((route) => route.path.endsWith("/dimensions/workbench"));
		if (!workbenchRoute) throw new Error("dimension workbench route contract is missing");
		await navigateThroughRealMenu(page, workbenchRoute);
		await expectRealRoute(page, workbenchRoute);
		const editor = page.locator(".dmx-model-editor");
		const dimensionDefinitionResponsesBefore = observations.filter(
			(observation) => observation.pathname === "/api/modeling/dimension-definitions",
		).length;

		await page.getByRole("button", { name: "新建" }).click();
		await page.getByRole("button", { name: "创建维度", exact: true }).click();
		await expect(editor.getByRole("heading", { name: "基本信息" })).toBeVisible();
		for (const label of ["数仓分层", "业务分类", "数据域", "系统编码", "中文名称", "描述"])
			await expect(editor).toContainText(label);
		await expect(editor.getByLabel("系统编码")).toHaveValue("保存后生成");
		await expect(editor.getByRole("toolbar").getByRole("button")).toHaveCount(1);
		await expect(editor).not.toContainText(/字段管理|存储策略|表名规则|表中文名|生命周期|负责人/);
		const conceptRail = page.locator(".dmx-record-rail");
		await expect(conceptRail).not.toBeVisible();
		await capture(page, testInfo, "create-concept-dimension-768x900.png");

		await page.getByRole("button", { name: "新建" }).click();
		await page.getByRole("button", { name: "创建维度表", exact: true }).click();
		await expect(editor.getByRole("heading", { name: "基本信息" })).toBeVisible();
		await expect(editor.locator("fieldset")).toBeVisible();
		const selectedDomainId = await editor.getByLabel("数据域").inputValue();
		if (selectedDomainId) {
			await expect
				.poll(
					() =>
						observations.filter((observation) => observation.pathname === "/api/modeling/dimension-definitions").length,
					{ message: "the selected dimension draft must finish loading its dimension definitions", timeout: 15_000 },
				)
				.toBeGreaterThan(dimensionDefinitionResponsesBefore);
		}
		for (const label of [
			"数仓分层",
			"业务分类",
			"数据域",
			"存储策略",
			"维度",
			"表名规则",
			"表名",
			"表中文名",
			"生命周期",
			"负责人",
			"描述",
			"字段名称",
			"类型",
			"字段显示名",
			"主键",
			"非空",
			"维度属性编码",
		])
			await expect(editor).toContainText(label);
		await expect(editor).not.toContainText(
			/模型类型|目标分层|业务定义|模型粒度|物化方式|SCD 策略|复用范围|安全等级|物理预览|高级 dbt|质量门禁|发布与物化|提交检查/,
		);
		await expectNoHorizontalPageOverflow(page);
		await capture(page, testInfo, "dimension-workbench-768x900.png");
		await testInfo.attach("narrow-api-observations.json", {
			body: Buffer.from(JSON.stringify(observations, null, 2)),
			contentType: "application/json",
		});
		expectNoReadOnlyFailures(failures, consoleErrors);
	});
});
