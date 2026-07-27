import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const planId = process.env.E2E_S74_PLAN_ID ?? "15a6bfc4-2b50-4f4b-8bf8-597d4d863a9b";
const applicationModelId =
	process.env.E2E_S74_APPLICATION_MODEL_ID ?? "42fe5aa7-7d89-46fd-88b9-4baae9b314a4";
const correctionModelId =
	process.env.E2E_S74_CORRECTION_MODEL_ID ?? "2a711ffc-5d6b-4247-bc89-87a0714181f3";
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-74-202607-modeling-creation-journey-correction/it/evidence/chrome95",
);

type BrowserFailures = {
	consoleErrors: string[];
	pageErrors: string[];
	requestFailures: string[];
};

function observeFailures(page: Page): BrowserFailures {
	const failures: BrowserFailures = { consoleErrors: [], pageErrors: [], requestFailures: [] };
	page.on("console", (message) => {
		if (message.type() === "error") failures.consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) => {
		failures.requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`);
	});
	return failures;
}

function expectNoBrowserFailures(failures: BrowserFailures) {
	expect(failures.consoleErrors, "console errors").toEqual([]);
	expect(failures.pageErrors, "page errors").toEqual([]);
	expect(failures.requestFailures, "request failures").toEqual([]);
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

async function expectNoHorizontalOverflow(page: Page) {
	await expect
		.poll(() => page.evaluate(() => Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)))
		.toBeLessThanOrEqual(await page.evaluate(() => window.innerWidth));
}

test.beforeAll(() => {
	fs.mkdirSync(evidenceDir, { recursive: true });
});

test.beforeEach(async ({ page }) => {
	await installAuthenticatedChrome95Session(page);
});

test("creation starts without a type and explains all four business purposes", async ({ page }) => {
	const failures = observeFailures(page);
	await page.goto(`/#/modeling/models?planId=${encodeURIComponent(planId)}`);
	await expect(page.getByRole("button", { name: "新建模型" })).toBeVisible();
	await page.getByRole("button", { name: "新建模型" }).click();

	const drawer = page.getByRole("dialog", { name: "新建模型" });
	await expect(drawer).toBeVisible();
	await expect(drawer.getByRole("button", { name: "保存草稿" })).toBeDisabled();
	for (const purpose of ["稳定对象 · 维度表", "业务事件 · 明细表", "聚合结果 · 汇总表", "消费输出 · 应用表"]) {
		await expect(drawer.getByText(purpose, { exact: true })).toBeVisible();
	}
	for (const context of ["适合：", "不适合：", "例子："]) {
		await expect(drawer.locator("span.ant-typography").filter({ hasText: new RegExp(`^${context}`) })).toHaveCount(4);
	}
	await page.screenshot({
		path: path.join(evidenceDir, "it-01-02-create-purpose-cards-chromium95.png"),
		fullPage: true,
	});
	expectNoBrowserFailures(failures);
});

test("logical, implementation and release-result stages keep one owner each", async ({ page }) => {
	const failures = observeFailures(page);
	await page.goto(`/#/modeling/models/${applicationModelId}?activeStage=logical`);
	const logical = page.getByTestId("model-spec-logical-stage");
	await expect(logical).toBeVisible();
	await expect(logical).toContainText("应用表逻辑设计");
	await expect(page.getByText("上游输入尚无已封存的密级证据", { exact: true })).toBeVisible();
	await expect(logical.getByText("目标物理名称", { exact: true })).toHaveCount(0);
	await expect(logical.getByText("装载策略", { exact: true })).toHaveCount(0);

	await page.getByRole("button", { name: "数据实现", exact: true }).click();
	const implementation = page.getByTestId("model-spec-implementation-stage");
	await expect(implementation).toBeVisible();
	await expect(implementation.getByText("ads_s74_finance_dashboard", { exact: true }).first()).toBeVisible();
	await expect(implementation.getByText("目标物理名称", { exact: true })).toBeVisible();
	await expect(implementation.getByText("装载策略", { exact: true }).first()).toBeVisible();
	await expect(implementation.getByRole("button", { name: "进入高级 dbt 工作台" })).toBeVisible();
	await page.screenshot({
		path: path.join(evidenceDir, "it-06-07-data-implementation-chromium95.png"),
		fullPage: true,
	});

	await page.getByRole("button", { name: "发布结果", exact: true }).click();
	const physical = page.getByTestId("model-spec-physical-stage");
	await expect(physical).toBeVisible();
	await expect(physical).toContainText("尚无已登记的真实物理资产");
	await expect(physical).toContainText("编译 · PASSED");
	await expect(physical.getByText("进入高级 dbt 工作台", { exact: true })).toHaveCount(0);
	await page.screenshot({
		path: path.join(evidenceDir, "it-08-release-result-chromium95.png"),
		fullPage: true,
	});

	await page.setViewportSize({ width: 390, height: 844 });
	await expectNoHorizontalOverflow(page);
	await page.screenshot({
		path: path.join(evidenceDir, "it-12-release-result-chromium95-narrow.png"),
		fullPage: true,
	});
	expectNoBrowserFailures(failures);
});

test("type adjustment keeps the current revision and requires preview before apply", async ({ page }) => {
	const failures = observeFailures(page);
	await page.goto(`/#/modeling/models/${correctionModelId}?activeStage=logical`);
	await expect(page.getByRole("button", { name: "调整模型类型" })).toBeVisible();
	await page.getByRole("button", { name: "调整模型类型" }).click();

	const dialog = page.getByRole("dialog", { name: /调整模型类型/ });
	await expect(dialog).toBeVisible();
	await expect(dialog).toContainText("系统会保留当前 r3，成功后追加一个新版本；不会覆盖历史记录。");
	await expect(dialog.getByRole("button", { name: "确认并保留 r3" })).toBeDisabled();
	await expect(dialog.getByRole("button", { name: "预检影响" })).toBeVisible();
	await page.screenshot({
		path: path.join(evidenceDir, "it-09-reclassification-wizard-chromium95.png"),
		fullPage: true,
	});
	expectNoBrowserFailures(failures);
});
