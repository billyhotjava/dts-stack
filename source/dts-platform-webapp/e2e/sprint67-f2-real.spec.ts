import path from "node:path";
import { expect, test } from "@playwright/test";

const planId = process.env.E2E_F2_PLAN_ID ?? "";
const domainId = process.env.E2E_F2_DOMAIN_ID ?? "";
const domainName = process.env.E2E_F2_DOMAIN_NAME ?? "";
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

test("F2 real category and policy baseline keeps CAS and one server-owned next action", async ({ page }) => {
	test.skip(!planId || !domainId || !domainName, "real F2 fixture ids are required");

	const pageErrors: string[] = [];
	const requestFailures: string[] = [];
	const httpFailures: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) =>
		requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`),
	);
	page.on("response", (response) => {
		if (response.status() < 400) return;
		const pathname = new URL(response.url()).pathname;
		if (
			response.status() === 409 &&
			response.request().method() === "PUT" &&
			pathname === `/api/modeling/warehouse-plans/${planId}/baseline/categories`
		)
			return;
		httpFailures.push(`${response.status()} ${response.request().method()} ${response.url()}`);
	});

	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto(`/#/modeling/plans/${planId}/baseline?tab=categories`);
	const categoryForm = page.getByTestId("warehouse-plan-category-form");
	await expect(categoryForm).toBeVisible();
	await categoryForm.getByRole("button", { name: "添加业务分类" }).click();
	await categoryForm.getByRole("combobox", { name: /业务分类/ }).click();
	await page.locator(".ant-select-item-option").filter({ hasText: domainName }).click();
	await categoryForm.locator(".ant-form-item").filter({ hasText: "使用状态" }).locator(".ant-select-selector").click();
	await page.locator(".ant-select-item-option").filter({ hasText: "已确认" }).click();

	const categorySave = page.waitForResponse(
		(response) =>
			response.request().method() === "PUT" &&
			new URL(response.url()).pathname === `/api/modeling/warehouse-plans/${planId}/baseline/categories` &&
			response.status() === 200,
	);
	await categoryForm.getByRole("button", { name: "保存业务分类" }).click();
	const categoryResponse = await categorySave;
	const categoryEnvelope = (await categoryResponse.json()) as {
		data: { version: number; value: { readiness: string; domainBindings: Array<{ domainId: string }> } };
	};
	expect(categoryEnvelope.data.version).toBe(2);
	expect(categoryEnvelope.data.value.readiness).toBe("READY");
	expect(categoryEnvelope.data.value.domainBindings).toContainEqual(expect.objectContaining({ domainId }));

	const staleWrite = await page.evaluate(
		async ({ fixturePlanId, fixtureDomainId }) => {
			const response = await fetch(`/api/modeling/warehouse-plans/${fixturePlanId}/baseline/categories`, {
				method: "PUT",
				headers: { "Content-Type": "application/json", "If-Match": '"category-scope:1"' },
				body: JSON.stringify({
					domainBindings: [{ domainId: fixtureDomainId, confirmationStatus: "CONFIRMED" }],
				}),
			});
			return { status: response.status, body: await response.json() };
		},
		{ fixturePlanId: planId, fixtureDomainId: domainId },
	);
	expect(staleWrite.status).toBe(409);
	expect(staleWrite.body).toMatchObject({
		code: "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT",
		data: { currentVersion: 2 },
	});

	await page.getByRole("tab", { name: "数仓分层" }).click();
	const policyForm = page.getByTestId("warehouse-plan-policy-form");
	await expect(policyForm).toBeVisible();
	await policyForm.getByLabel("分层方案").click();
	await page.getByText("经典数仓：ODS → DWD → DWS → ADS", { exact: true }).last().click();
	await policyForm.getByRole("switch", { name: "来源未齐时允许概念设计" }).click();
	await policyForm.getByLabel("命名规则（进入实现前补齐）").click();
	await page.getByText("小写下划线", { exact: true }).last().click();
	await policyForm.getByLabel("历史保留（进入实现前补齐）").click();
	await page.getByText("保留业务历史", { exact: true }).last().click();
	await policyForm.getByLabel("默认时区（可选）").fill("Asia/Shanghai");

	const policySave = page.waitForResponse(
		(response) =>
			response.request().method() === "PUT" &&
			new URL(response.url()).pathname === `/api/modeling/warehouse-plans/${planId}/baseline/policy` &&
			response.status() === 200,
	);
	await policyForm.getByRole("button", { name: "保存分层策略" }).click();
	const policyResponse = await policySave;
	const policyEnvelope = (await policyResponse.json()) as { data: { version: number; value: { readiness: string } } };
	expect(policyEnvelope.data.version).toBe(2);
	expect(policyEnvelope.data.value.readiness).toBe("IMPLEMENTATION_READY");

	const projection = await page.evaluate(async (fixturePlanId) => {
		const response = await fetch(`/api/modeling/warehouse-plans/${fixturePlanId}/stage-projection`);
		return response.json();
	}, planId);
	expect(projection.data.currentStage).toBe("MODEL_DESIGN");
	expect(projection.data.primaryBlocker.code).toBe("MODEL_SPEC_REQUIRED");
	expect(projection.data.nextAction.path).toBe(`/modeling/models?planId=${planId}`);
	expect(projection.data.stages).toHaveLength(9);
	await page.screenshot({ path: path.join(evidenceDir, "f2-real-category-policy-chromium95.png"), fullPage: true });

	await page.setViewportSize({ width: 390, height: 844 });
	const viewport = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewport.documentWidth).toBeLessThanOrEqual(viewport.innerWidth);
	expect(viewport.bodyWidth).toBeLessThanOrEqual(viewport.innerWidth);
	await page.screenshot({
		path: path.join(evidenceDir, "f2-real-category-policy-chromium95-narrow.png"),
		fullPage: true,
	});

	expect(pageErrors).toEqual([]);
	expect(requestFailures).toEqual([]);
	expect(httpFailures).toEqual([]);
});
