import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");
const MODEL_SPEC_API = readFileSync(new URL("../../api/modelSpecApi.ts", import.meta.url), "utf8");
const COMPATIBILITY = readFileSync(new URL("./ModelingCompatibilityPage.tsx", import.meta.url), "utf8");
const ROUTES = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");

test("metric workbench is anchored on canonical models and metric references", () => {
	assert.match(WORKBENCH, /listModelSpecs/);
	assert.match(WORKBENCH, /metricRefs/);
	assert.match(WORKBENCH, /FACT|SUMMARY|APPLICATION/);
	assert.match(WORKBENCH, /modelSpecId/);
	assert.doesNotMatch(WORKBENCH, /listSemanticBusinessObjects|businessObjectId|\bobjectId\b/);
	assert.doesNotMatch(WORKBENCH, /updateSemanticMetric|listSemanticModels|listSemanticSubjectDomains/);
	assert.match(MODEL_SPEC_API, /\/modeling\/model-specs/);
});

test("legacy semantic routes remain redirect-only compatibility entries", () => {
	for (const path of [
		"modeling/semantic/subjects",
		"modeling/semantic/objects",
		"modeling/semantic/metrics",
		"modeling/semantic/models",
		"modeling/semantic/publish",
		"modeling/semantic/runs",
	]) {
		assert.match(ROUTES, new RegExp(path.replaceAll("/", "\\/")));
	}
	assert.match(COMPATIBILITY, /listModelSpecs/);
	assert.doesNotMatch(COMPATIBILITY, /createSemanticBusinessObject|updateSemanticBusinessObject/);
});

test("metric workbench carries only canonical end-to-end context", () => {
	assert.match(WORKBENCH, /journey/);
	assert.match(WORKBENCH, /modelSpecId/);
	assert.match(WORKBENCH, /standardDraftId/);
	assert.match(WORKBENCH, /metricId/);
	assert.doesNotMatch(WORKBENCH, /围绕定义、模型生成、模板复用和业务消费/);
	assert.doesNotMatch(WORKBENCH, /processId|businessObject/);
});

test("metric workbench stays compatible with Chrome 95", () => {
	assert.doesNotMatch(WORKBENCH, /oklch|:has\(|@container/);
});
