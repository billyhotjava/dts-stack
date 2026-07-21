import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const journeyA = {
	planId: process.env.E2E_F6_JOURNEY_A_PLAN_ID ?? "",
	modelId: process.env.E2E_F6_JOURNEY_A_MODEL_ID ?? "",
	modelName: process.env.E2E_F6_JOURNEY_A_MODEL_NAME ?? "",
	metricName: process.env.E2E_F6_JOURNEY_A_METRIC_NAME ?? "",
};
const journeyAMetricId = "29000000-0000-4000-8000-000000000029";
const journeyB = {
	planId: process.env.E2E_F6_JOURNEY_B_PLAN_ID ?? "",
	factModelId: process.env.E2E_F6_JOURNEY_B_FACT_MODEL_ID ?? "",
};
const legacyObjectId = process.env.E2E_F6_LEGACY_OBJECT_ID ?? "";
const evidenceRoot = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/evidence/chrome95",
);

type BrowserFailures = {
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
};

const observeFailures = (page: Page, allowedStatus: number[] = []): BrowserFailures => {
	const failures: BrowserFailures = { pageErrors: [], requestFailures: [], httpFailures: [] };
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) =>
		failures.requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`),
	);
	page.on("response", (response) => {
		if (response.status() >= 400 && !allowedStatus.includes(response.status())) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${response.url()}`);
		}
	});
	return failures;
};

const expectNoUnexpectedFailures = (failures: BrowserFailures) => {
	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
	expect(failures.httpFailures).toEqual([]);
};

const expectNarrowViewport = async (page: Page) => {
	const width = await page.evaluate(() => ({
		viewport: window.innerWidth,
		document: document.documentElement.scrollWidth,
		body: document.body.scrollWidth,
	}));
	expect(width.document).toBeLessThanOrEqual(width.viewport);
	expect(width.body).toBeLessThanOrEqual(width.viewport);
};

test("Journey A real business-first model is published with a stable metric owner", async ({ page }) => {
	test.skip(
		!journeyA.planId || !journeyA.modelId || !journeyA.modelName || !journeyA.metricName,
		"Journey A real record ids are required",
	);
	const evidenceDir = path.join(evidenceRoot, "journey-a");
	fs.mkdirSync(evidenceDir, { recursive: true });
	const failures = observeFailures(page);

	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto(
		`/#/modeling/metric-workbench?journey=BUSINESS_FIRST&planId=${journeyA.planId}&modelSpecId=${journeyA.modelId}&metricId=${journeyAMetricId}`,
	);
	await expect(page.getByTestId("metric-workbench-page")).toBeVisible();
	await expect(page.getByTestId("metric-workbench-main")).toContainText(journeyA.modelName);
	await expect(page.getByTestId("metric-workbench-main")).toContainText(journeyA.metricName);
	await expect(page.getByTestId("metric-workbench-main")).toContainText("引用 v1");
	await expect(page.getByTestId("metric-workbench-main")).toContainText("当前");

	const record = await page.evaluate(async ({ modelId }) => {
		const [modelResponse, timelineResponse] = await Promise.all([
			fetch(`/api/modeling/model-specs/${modelId}`),
			fetch(`/api/modeling/model-specs/${modelId}/lifecycle`),
		]);
		return {
			modelStatus: modelResponse.status,
			model: (await modelResponse.json()).data,
			timelineStatus: timelineResponse.status,
			timeline: (await timelineResponse.json()).data,
		};
	}, { modelId: journeyA.modelId });
	expect(record.modelStatus).toBe(200);
	expect(record.timelineStatus).toBe(200);
	expect(record.model).toMatchObject({
		id: journeyA.modelId,
		planId: journeyA.planId,
		status: "PUBLISHED",
		compatibilityMode: "CANONICAL",
		metricRefs: [{ metricId: journeyAMetricId, version: 1 }],
	});
	for (const retiredField of ["objectId", "processId", "businessObjectId", "semanticObjectId"]) {
		expect(record.model).not.toHaveProperty(retiredField);
	}
	expect(record.timeline.events).toEqual(
		expect.arrayContaining([
			expect.objectContaining({ eventType: "COMPILE", status: "PASSED" }),
			expect.objectContaining({ eventType: "TEST", status: "PASSED" }),
			expect.objectContaining({ eventType: "RELEASE", status: "PUBLISHED" }),
			expect.objectContaining({ eventType: "RUN", status: "QUEUED", comment: "RUNTIME_DISABLED" }),
		]),
	);

	await page.screenshot({ path: path.join(evidenceDir, "business-first-metric-owner-chromium95.png"), fullPage: true });
	await page.setViewportSize({ width: 390, height: 844 });
	await expectNarrowViewport(page);
	await page.screenshot({
		path: path.join(evidenceDir, "business-first-metric-owner-chromium95-narrow.png"),
		fullPage: true,
	});
	expectNoUnexpectedFailures(failures);
});

test("Journey B real asset-first plan keeps four direct model types and publishes without object identifiers", async ({
	page,
}) => {
	test.skip(!journeyB.planId || !journeyB.factModelId, "Journey B real record ids are required");
	const evidenceDir = path.join(evidenceRoot, "journey-b");
	fs.mkdirSync(evidenceDir, { recursive: true });
	const failures = observeFailures(page);

	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto(`/#/modeling/models?planId=${journeyB.planId}`);
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	for (const name of [
		"Sprint67 F3 真实维度 S67_F6_B_ORG_20260720",
		"s67_f6_asset_order_detail",
		"s67_f6_asset_order_daily",
		"s67_f6_asset_order_dashboard",
	]) {
		await expect(page.getByText(name, { exact: true })).toBeVisible();
	}
	for (const type of ["维度表", "明细表", "汇总表", "应用表"]) {
		await expect(page.getByText(type, { exact: true }).first()).toBeVisible();
	}

	const record = await page.evaluate(async ({ planId, factModelId }) => {
		const [modelsResponse, timelineResponse] = await Promise.all([
			fetch(`/api/modeling/model-specs?planId=${planId}`),
			fetch(`/api/modeling/model-specs/${factModelId}/lifecycle`),
		]);
		return {
			modelsStatus: modelsResponse.status,
			models: (await modelsResponse.json()).data,
			timelineStatus: timelineResponse.status,
			timeline: (await timelineResponse.json()).data,
		};
	}, journeyB);
	expect(record.modelsStatus).toBe(200);
	expect(record.timelineStatus).toBe(200);
	expect(record.models).toHaveLength(4);
	expect(record.models.map((model: { modelType: string }) => model.modelType).sort()).toEqual([
		"APPLICATION",
		"DIMENSION",
		"FACT",
		"SUMMARY",
	]);
	for (const model of record.models) {
		for (const retiredField of ["objectId", "processId", "businessObjectId", "semanticObjectId"]) {
			expect(model).not.toHaveProperty(retiredField);
		}
	}
	const publishedFact = record.models.find((model: { id: string }) => model.id === journeyB.factModelId);
	expect(publishedFact).toMatchObject({ status: "PUBLISHED", revision: 2, compatibilityMode: "CANONICAL" });
	expect(record.timeline.events).toEqual(
		expect.arrayContaining([
			expect.objectContaining({ eventType: "COMPILE", status: "PASSED" }),
			expect.objectContaining({ eventType: "TEST", status: "PASSED" }),
			expect.objectContaining({
				eventType: "RELEASE",
				status: "PUBLISHED",
				details: { registrationCount: 3 },
			}),
			expect.objectContaining({ eventType: "RUN", status: "QUEUED", comment: "RUNTIME_DISABLED" }),
		]),
	);

	await page.screenshot({ path: path.join(evidenceDir, "asset-first-four-models-chromium95.png"), fullPage: true });
	await page.setViewportSize({ width: 390, height: 844 });
	await expectNarrowViewport(page);
	await page.screenshot({
		path: path.join(evidenceDir, "asset-first-four-models-chromium95-narrow.png"),
		fullPage: true,
	});
	expectNoUnexpectedFailures(failures);
});

test("Journey C real legacy route recovers safely and old writes remain frozen", async ({ page }) => {
	test.skip(!legacyObjectId, "a real unmapped legacy object id is required");
	const evidenceDir = path.join(evidenceRoot, "journey-c");
	fs.mkdirSync(evidenceDir, { recursive: true });
	const failures = observeFailures(page, [410]);

	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto(`/#/modeling/semantic/objects?objectId=${legacyObjectId}`);
	await expect(page.getByTestId("modeling-compatibility-recovery")).toBeVisible();
	await expect(page.getByText("旧模型需要确认业务分类", { exact: true })).toBeVisible();
	await expect(page.getByText("NEEDS_CLASSIFICATION", { exact: true })).toBeVisible();
	await expect(page.getByText(legacyObjectId, { exact: true })).toBeVisible();

	const retirement = await page.evaluate(async () => {
		const [writeResponse, dryRunResponse, usageResponse, gateResponse] = await Promise.all([
			fetch("/api/semantic/business-objects", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: "{}",
			}),
			fetch("/api/modeling/migrations/legacy-objects/dry-run"),
			fetch("/api/modeling/migrations/legacy-objects/usage"),
			fetch("/api/modeling/migrations/legacy-objects/exit-gate"),
		]);
		return {
			write: {
				status: writeResponse.status,
				deprecation: writeResponse.headers.get("deprecation"),
				link: writeResponse.headers.get("link"),
				body: await writeResponse.json(),
			},
			dryRun: (await dryRunResponse.json()).data,
			usage: (await usageResponse.json()).data,
			gate: (await gateResponse.json()).data,
		};
	});
	expect(retirement.write).toMatchObject({
		status: 410,
		deprecation: "true",
		body: { code: "BUSINESS_OBJECT_RETIRED", data: { repairPath: "/modeling/workbench" } },
	});
	expect(retirement.write.link).toContain("/api/modeling/model-specs");
	expect(retirement.dryRun.decisions).toEqual(
		expect.arrayContaining([
			expect.objectContaining({ classification: "NEEDS_CLASSIFICATION", ready: false }),
			expect.objectContaining({ classification: "ARCHIVE_ONLY", ready: true }),
		]),
	);
	expect(retirement.usage.calls).toBeGreaterThan(0);
	expect(retirement.gate).toMatchObject({ decision: "NO-DROP", backupApproved: false });
	expect(retirement.gate.blockers).toEqual(
		expect.arrayContaining([
			"LEGACY_RECORDS_STILL_READONLY",
			"LEGACY_CONSUMERS_NOT_ZERO",
			"BACKUP_APPROVAL_REQUIRED",
		]),
	);

	await page.screenshot({ path: path.join(evidenceDir, "legacy-recovery-and-freeze-chromium95.png"), fullPage: true });
	await page.setViewportSize({ width: 390, height: 844 });
	await expectNarrowViewport(page);
	await page.screenshot({
		path: path.join(evidenceDir, "legacy-recovery-and-freeze-chromium95-narrow.png"),
		fullPage: true,
	});
	expectNoUnexpectedFailures(failures);
});
