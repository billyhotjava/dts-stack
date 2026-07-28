import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");

test("published model handoff creates field-anchored indicator drafts and binds only published versions", () => {
	assert.match(PAGE, /createModelFieldIndicatorDraft/);
	assert.doesNotMatch(PAGE, /createIndicatorReference/);
	assert.match(PAGE, /measurementUnitVersion/);
	assert.match(PAGE, /status === "PUBLISHED"/);
	assert.match(PAGE, /bindModelSpecMetricRef/);
	assert.doesNotMatch(PAGE, /businessObject/i);
});

test("workbench exposes one canonical task partition instead of stacking every capability", () => {
	assert.match(PAGE, /resolveMetricWorkbenchView/);
	assert.match(PAGE, /定义与发布/);
	assert.match(PAGE, /从模型创建/);
	assert.match(PAGE, /模板复用/);
	assert.match(PAGE, /运行与消费/);
});
