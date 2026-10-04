import assert from "node:assert/strict";
import test from "node:test";
import {
	buildQualityRulePayload,
	legacyQualityRedirect,
	QUALITY_ROUTE_SPECS,
	qualityPath,
	qualityPrimaryRoutesForOwner,
	UNAVAILABLE_CAPABILITIES,
} from "./qualityRoutes.ts";

test("the data-quality workspace exposes eight primary and eleven secondary routes under two menu owners", () => {
	assert.equal(QUALITY_ROUTE_SPECS.length, 19);
	assert.equal(QUALITY_ROUTE_SPECS.filter((route) => route.level === "primary").length, 8);
	assert.equal(QUALITY_ROUTE_SPECS.filter((route) => route.level === "secondary").length, 11);
	assert.deepEqual(
		new Set(QUALITY_ROUTE_SPECS.map((route) => route.menuOwner)),
		new Set(["/governance/rules", "/governance/quality"]),
	);
	assert.equal(new Set(QUALITY_ROUTE_SPECS.map((route) => route.key)).size, 19);
});

test("route lookup preserves canonical deep links", () => {
	assert.equal(qualityPath("overview"), "/governance/rules");
	assert.equal(qualityPath("rule-detail", { ruleId: "rule-7" }), "/governance/rules/catalog/rule-7");
	assert.equal(qualityPath("run-detail", { runId: "run-9" }), "/governance/rules/runs/run-9");
	assert.equal(qualityPath("report-preview"), "/governance/quality/preview");
});

test("quality-control navigation excludes report while the report keeps its independent owner", () => {
	const controlRoutes = qualityPrimaryRoutesForOwner("/governance/rules");
	const reportRoutes = qualityPrimaryRoutesForOwner("/governance/quality");

	assert.equal(controlRoutes.length, 7);
	assert.equal(
		controlRoutes.some((route) => route.key === "report"),
		false,
	);
	assert.deepEqual(
		reportRoutes.map((route) => ({ key: route.key, owner: route.menuOwner })),
		[{ key: "report", owner: "/governance/quality" }],
	);
});

test("legacy quality query links converge to canonical deep routes and preserve unrelated query", () => {
	assert.equal(
		legacyQualityRedirect(new URLSearchParams("tab=rules&keyword=pk")),
		"/governance/rules/catalog?keyword=pk",
	);
	assert.equal(
		legacyQualityRedirect(new URLSearchParams("tab=tasks&enabled=true")),
		"/governance/rules/monitors?enabled=true",
	);
	assert.equal(
		legacyQualityRedirect(new URLSearchParams("tab=repair&runId=run-9&source=alert")),
		"/governance/rules/runs/run-9?source=alert",
	);
	assert.equal(
		legacyQualityRedirect(new URLSearchParams("ruleId=rule-7&source=workflow")),
		"/governance/rules/catalog/rule-7?source=workflow",
	);
	assert.equal(
		legacyQualityRedirect(new URLSearchParams("datasetId=dataset-3&source=access")),
		"/governance/rules/catalog?datasetId=dataset-3&source=access",
	);
});

test("quality rule writes always include version-scoped dataset bindings", () => {
	assert.deepEqual(buildQualityRulePayload({ name: "主键完整性", type: "COMPLETENESS", datasetId: "dataset-3" }), {
		name: "主键完整性",
		type: "COMPLETENESS",
		datasetId: "dataset-3",
		bindings: [{ datasetId: "dataset-3", scopeType: "DATASET" }],
	});
	assert.deepEqual(buildQualityRulePayload({ name: "表级规则", type: "VALIDITY" }).bindings, []);
});

test("quality rule edits preserve backend full-replacement metadata", () => {
	const payload = buildQualityRulePayload(
		{ name: "更新名称", type: "COMPLETENESS", datasetId: "dataset-3" },
		{
			category: "核心数据",
			description: "原说明",
			owner: "data-owner",
			dataLevel: "D2",
			frequencyCron: "0 0 * * *",
			executor: "SQL",
			template: false,
			enabled: false,
		},
	);

	assert.equal(payload.description, "原说明");
	assert.equal(payload.executor, "SQL");
	assert.equal(payload.enabled, false);
});

test("missing backend contracts are explicit and cannot be mistaken for save actions", () => {
	assert.deepEqual(UNAVAILABLE_CAPABILITIES.map((item) => item.key).sort(), [
		"atomic-batch",
		"noise-management",
		"quality-subscription",
		"report-template",
	]);
	assert.ok(UNAVAILABLE_CAPABILITIES.every((item) => item.label === "暂未开放" && item.reason.length > 0));
});
