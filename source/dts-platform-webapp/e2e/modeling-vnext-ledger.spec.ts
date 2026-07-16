import { expect, test } from "@playwright/test";

const ok = (data: unknown) => ({ status: "SUCCESS", message: "OK", data });

test.describe("modeling vNext · PJM ledger path", () => {
	 test.beforeEach(async ({ page }) => {
		await page.route("**/api/semantic/subject-domains*", (route) =>
			route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(ok([{ id: "pjm-domain", code: "PJM", name: "项目管理" }])) }),
		);
		await page.route("**/api/semantic/business-objects*", (route) =>
			route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify(ok([
					{
						id: "pjm-project-node",
						code: "project_node",
						name: "项目节点",
						objectKind: "FACT",
						processId: "project-node-plan-loop",
						businessKey: ["project_no", "subsystem", "node_task", "plan_date"],
						grain: "项目 + 子系统 + 节点任务 + 计划日期",
						mainTable: "ods_project_subject_domain_v2",
						implementationMode: "DESIGNER_GENERATED",
						status: "DRAFT",
					},
				])),
			}),
		);
	await page.route("**/api/semantic/business-objects/*/table-mappings", (route) =>
			route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(ok([])) }),
		);
		await page.route("**/api/modeling/vnext/model-specs*", (route) =>
			route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify(ok([
					{
						id: "pjm-project-node-dws",
						objectId: "pjm-project-node",
						processId: "project-node-plan-loop",
						layer: "DWS",
						modelType: "SUMMARY",
						implementationMode: "DESIGNER_GENERATED",
						name: "project_progress_monthly",
						grain: { statement: "项目月度进度", keys: ["project_no", "plan_month"] },
						standardBindings: [],
						sourceRefs: [{ kind: "DBT_MODEL", ref: "project_node_detail", layer: "DWD" }],
						materialization: "table",
						revision: 1,
					},
				])),
			}),
		);
		await page.route("**/api/modeling/vnext/model-specs/*/release-gate", (route) =>
			route.fulfill({
				status: 200,
				contentType: "application/json",
				body: JSON.stringify(ok({ modelSpecId: "pjm-project-node-dws", publishable: true, status: "RELEASE_READY", blockers: [] })),
			}),
		);
	});

	test("普通用户能在业务对象台账看到 PJM 粒度、来源和下一步", async ({ page }) => {
		await page.goto("/#/modeling/semantic/objects?processId=project-node-plan-loop");

		await expect(page.getByTestId("semantic-objects-page")).toBeVisible();
		await expect(page.getByText("业务对象台账")).toBeVisible();
		await expect(page.getByText("项目节点")).toBeVisible();
		await expect(page.getByText("项目 + 子系统 + 节点任务 + 计划日期")).toBeVisible();
		await expect(page.getByText("ods_project_subject_domain_v2")).toBeVisible();
		await expect(page.getByText("进入模型管理")).toBeVisible();
	});

	test("高级开发能从模型台账看到发布门禁并进入 dbt SQL", async ({ page }) => {
		await page.goto("/#/modeling/semantic/models?processId=project-node-plan-loop");

		await expect(page.getByTestId("semantic-models-page")).toBeVisible();
		await expect(page.getByRole("cell", { name: "project_progress_monthly" }).first()).toBeVisible();
		await expect(page.getByText("可发布")).toBeVisible();
		await page.getByTestId("semantic-model-dbt-entry").click();
		await expect(page).toHaveURL(/\/modeling\/dbt-files\?modelId=pjm-project-node-dws&from=model-ledger/);
	});

	test("直接进入业务对象台账时，新建按钮会引导选择业务过程", async ({ page }) => {
		await page.goto("/#/modeling/semantic/objects");

		const createButton = page.getByRole("button", { name: "选择业务过程后新建" });
		await expect(createButton).toBeVisible();
		await expect(createButton).toBeEnabled();
		await createButton.click();
		await expect(page).toHaveURL(/\/governance\/subjects\?focus=business-processes&from=business-object-ledger/);
	});
});
