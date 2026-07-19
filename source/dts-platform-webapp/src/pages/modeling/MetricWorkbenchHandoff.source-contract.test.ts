import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");

test("published model handoff creates field-anchored indicator drafts and binds only published versions", () => {
	assert.match(PAGE, /createIndicator/);
	assert.match(PAGE, /createIndicatorReference/);
	assert.match(PAGE, /MODEL_SPEC_FIELD/);
	assert.match(PAGE, /measurementUnitVersion/);
	assert.match(PAGE, /status === "PUBLISHED"/);
	assert.match(PAGE, /bindModelSpecMetricRef/);
	assert.doesNotMatch(PAGE, /businessObject/i);
});
