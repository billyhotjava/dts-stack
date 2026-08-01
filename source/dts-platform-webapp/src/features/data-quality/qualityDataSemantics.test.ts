import assert from "node:assert/strict";
import test from "node:test";
import { getQualityRunCounts, hasEffectiveQualityScore, isExecutableQualityRule } from "./qualityTypes.ts";

test("derives passed rows from the real QualityRun DTO fields", () => {
	assert.deepEqual(getQualityRunCounts({ rowsTotal: 120, failingRowCount: 7 }), {
		total: 120,
		failed: 7,
		passed: 113,
		passRate: 94.17,
		hasStatistics: true,
	});
});

test("queued, running, and empty runs do not expose placeholder statistics", () => {
	assert.equal(getQualityRunCounts({ status: "QUEUED", rowsTotal: 120, failingRowCount: 7 }).hasStatistics, false);
	assert.equal(getQualityRunCounts({ status: "RUNNING", rowsTotal: 0, failingRowCount: 0 }).hasStatistics, false);
	assert.equal(getQualityRunCounts({ status: "PASSED", rowsTotal: 0, failingRowCount: 0 }).hasStatistics, false);
});

test("only enabled published rules are executable, and monitor selection must match the dataset", () => {
	const published = {
		enabled: true,
		bindings: [{ datasetId: "dataset-1" }],
		latestVersion: { status: "PUBLISHED" },
	};
	assert.equal(isExecutableQualityRule(published, "dataset-1"), true);
	assert.equal(isExecutableQualityRule({ ...published, enabled: false }, "dataset-1"), false);
	assert.equal(isExecutableQualityRule({ ...published, latestVersion: { status: "DRAFT" } }, "dataset-1"), false);
	assert.equal(isExecutableQualityRule(published, "dataset-2"), false);
	assert.equal(isExecutableQualityRule({ ...published, bindings: [] }), false);
});

test("does not present the backend zero placeholder as a measured score", () => {
	assert.equal(hasEffectiveQualityScore({ overall: 0, dimensions: [], trend: [] }), false);
	assert.equal(
		hasEffectiveQualityScore({ overall: 0, dimensions: [{ type: "COMPLETENESS", score: 0 }], trend: [] }),
		true,
	);
});
