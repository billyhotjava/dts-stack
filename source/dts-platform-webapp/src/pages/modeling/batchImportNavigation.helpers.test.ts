import assert from "node:assert/strict";
import test from "node:test";
import { extractImportedModelNames, resolveBatchImportNavigation } from "./batchImportNavigation.helpers";

test("extractImportedModelNames returns only successfully imported model names", () => {
	const result = extractImportedModelNames([
		{ name: "ads_a", status: "imported" },
		{ name: "ads_b", status: "skipped" },
		{ name: "ads_c", status: "validation_failed" },
		{ name: "ads_d", status: "write_failed" },
		{ name: "ads_e", status: "imported" },
	]);

	assert.deepEqual(result, ["ads_a", "ads_e"]);
});

test("resolveBatchImportNavigation activates target space and selects first imported model in that space", () => {
	const result = resolveBatchImportNavigation(
		"plan-9",
		["biz_ads_major_project_overview", "biz_ads_delay_reason_trend"],
		[
			{ id: "m1", name: "other_model", planId: "plan-2" },
			{ id: "m2", name: "biz_ads_delay_reason_trend", planId: "plan-9" },
			{ id: "m3", name: "biz_ads_major_project_overview", planId: "plan-9" },
		],
	);

	assert.deepEqual(result, {
		activeSpaceKey: "space-plan-9",
		activeModelKey: "m3",
	});
});

test("resolveBatchImportNavigation still switches to target space when imported models are not found", () => {
	const result = resolveBatchImportNavigation("plan-9", ["missing_model"], [
		{ id: "m1", name: "other_model", planId: "plan-2" },
		{ id: "m2", name: "biz_ads_delay_reason_trend", planId: "plan-9" },
	]);

	assert.deepEqual(result, {
		activeSpaceKey: "space-plan-9",
		activeModelKey: null,
	});
});
