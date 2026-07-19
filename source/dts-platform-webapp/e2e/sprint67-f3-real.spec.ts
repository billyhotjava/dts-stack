import path from "node:path";
import { expect, test } from "@playwright/test";

const planId = process.env.E2E_F3_PLAN_ID ?? "";
const domainId = process.env.E2E_F3_DOMAIN_ID ?? "";
const dimensionCode = process.env.E2E_F3_DIMENSION_CODE ?? "";
const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

test("F3 real dimension draft uses authenticated API, PostgreSQL and revision-bound stage gates", async ({ page }) => {
	test.skip(!planId || !domainId || !dimensionCode, "real F3 fixture ids are required");

	const pageErrors: string[] = [];
	const failedRequests: string[] = [];
	const failedResponses: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("requestfailed", (request) =>
		failedRequests.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`),
	);
	page.on("response", (response) => {
		if (response.status() >= 400)
			failedResponses.push(`${response.status()} ${response.request().method()} ${response.url()}`);
	});

	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto(`/#/modeling/dimensions?planId=${planId}&domainId=${domainId}`);
	await expect(page.getByTestId("dimension-catalog-page")).toBeVisible();
	await page.getByRole("button", { name: "登记维度" }).click();
	const drawer = page.getByRole("dialog", { name: "登记维度" });
	await expect(drawer).toBeVisible();

	await page.getByLabel("维度名称").fill(`Sprint67 F3 真实维度 ${dimensionCode}`);
	await page.getByLabel("维度定义").fill("真实后端、权限与数据库联动验收维度");
	await page.getByLabel("每行代表什么").fill("每行代表一个验收组织");
	await page.getByLabel("维度键").fill("organization_id");
	await page.getByLabel("维度编码").fill(dimensionCode);

	const createRequest = page.waitForRequest(
		(request) => request.method() === "POST" && new URL(request.url()).pathname === "/api/modeling/model-specs",
	);
	const createResponse = page.waitForResponse(
		(response) =>
			response.request().method() === "POST" && new URL(response.url()).pathname === "/api/modeling/model-specs",
	);
	await page.getByRole("button", { name: "保存草稿" }).click();
	const [request, response] = await Promise.all([createRequest, createResponse]);
	expect(response.status()).toBe(201);
	const createBody = request.postDataJSON() as Record<string, unknown>;
	expect(createBody).toMatchObject({
		planId,
		domainId,
		modelType: "DIMENSION",
		dimensionProfile: {
			dimensionCode,
			hierarchies: [],
			scdPolicy: { type: "NONE" },
			reuseScope: "PLAN",
		},
	});
	for (const retiredField of ["objectId", "processId", "businessObjectId", "semanticObjectId"]) {
		expect(createBody).not.toHaveProperty(retiredField);
	}

	const createEnvelope = (await response.json()) as {
		data: { id: string; revision: number; checksum: string; dimensionProfile?: unknown };
	};
	const created = createEnvelope.data;
	expect(created.revision).toBe(1);
	expect(created.checksum).toMatch(/^[a-f0-9]{64}$/);
	expect(created.dimensionProfile).toMatchObject({ dimensionCode, reuseScope: "PLAN" });
	await expect(page).toHaveURL(new RegExp(`/modeling/models/${created.id}`));
	await expect(page.getByTestId("model-spec-detail-page")).toBeVisible();

	const draftGate = page.getByTestId("model-spec-gate-draft_save");
	const implementationGate = page.getByTestId("model-spec-gate-implementation_ready");
	const releaseGate = page.getByTestId("model-spec-gate-release_ready");
	await expect(draftGate).toContainText("已满足");
	await expect(implementationGate).toContainText("项待处理");
	await expect(implementationGate).toContainText("请至少选择来源或生成策略");
	await expect(releaseGate).toContainText("项待处理");
	await expect(draftGate).toContainText("r1");
	await page.screenshot({ path: path.join(evidenceDir, "f3-real-dimension-gates-chromium95.png"), fullPage: true });

	await page.getByLabel("维度定义").fill("真实后端、权限与数据库联动验收维度（CAS 已复核）");
	const updateRequest = page.waitForRequest(
		(request) =>
			request.method() === "PUT" && new URL(request.url()).pathname === `/api/modeling/model-specs/${created.id}`,
	);
	const updateResponse = page.waitForResponse(
		(response) =>
			response.request().method() === "PUT" &&
			new URL(response.url()).pathname === `/api/modeling/model-specs/${created.id}`,
	);
	await page.getByRole("button", { name: "保存草稿" }).click();
	const [putRequest, putResponse] = await Promise.all([updateRequest, updateResponse]);
	expect(putResponse.status()).toBe(200);
	expect(putRequest.headers()["if-match"]).toBe(`"model-spec:${created.id}:1:${created.checksum}"`);
	await expect(page.getByText("版本 r2", { exact: true })).toBeVisible();
	await expect(draftGate).toContainText("r2");

	await page.setViewportSize({ width: 390, height: 844 });
	const viewport = await page.evaluate(() => ({
		innerWidth: window.innerWidth,
		documentWidth: document.documentElement.scrollWidth,
		bodyWidth: document.body.scrollWidth,
	}));
	expect(viewport.documentWidth).toBeLessThanOrEqual(viewport.innerWidth);
	expect(viewport.bodyWidth).toBeLessThanOrEqual(viewport.innerWidth);
	await page.screenshot({
		path: path.join(evidenceDir, "f3-real-dimension-gates-chromium95-narrow.png"),
		fullPage: true,
	});

	expect(pageErrors).toEqual([]);
	expect(failedRequests).toEqual([]);
	expect(failedResponses).toEqual([]);
});
