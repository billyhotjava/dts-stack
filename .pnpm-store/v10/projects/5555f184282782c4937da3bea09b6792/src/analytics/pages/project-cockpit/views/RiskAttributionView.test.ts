import assert from "node:assert/strict";
import test from "node:test";
import { buildDelayReasonMatrixSummary } from "./riskAttributionView.helpers";

test("buildDelayReasonMatrixSummary sorts matrix rows by total and extracts dominant reason", () => {
	const summary = buildDelayReasonMatrixSummary([
		{ dept: "导航室", total: 5, technical: 3, quality: 1, change: 1 },
		{ dept: "光学室", total: 2, technical: 0, quality: 2, change: 0 },
	]);

	assert.equal(summary.rows[0].dept, "导航室");
	assert.equal(summary.rows[0].dominantReason, "technical");
	assert.equal(summary.rows[1].dominantReason, "quality");
});
