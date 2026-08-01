import assert from "node:assert/strict";
import test from "node:test";
import { buildIssueUpdatePayload, findRunIssue, runNeedsDisposition } from "./runIssueDisposition.ts";

test("finds only a QUALITY_RUN issue linked to the current run", () => {
	const issue = findRunIssue(
		[
			{ id: "1", sourceType: "QUALITY_RULE", sourceId: "run-7" },
			{ id: "2", sourceType: "QUALITY_RUN", sourceId: "run-7" },
		],
		"run-7",
	);
	assert.equal(issue?.id, "2");
});

test("healthy terminal runs do not require issue disposition", () => {
	assert.equal(runNeedsDisposition("PASSED"), false);
	assert.equal(runNeedsDisposition("SUCCESS"), false);
	assert.equal(runNeedsDisposition("SKIPPED"), false);
	assert.equal(runNeedsDisposition("QUEUED"), false);
	assert.equal(runNeedsDisposition("RUNNING"), false);
	assert.equal(runNeedsDisposition("FAILED"), true);
});

test("issue status updates preserve the backend full-replacement fields", () => {
	const payload = buildIssueUpdatePayload(
		{
			id: "issue-1",
			sourceType: "QUALITY_RUN",
			sourceId: "run-7",
			datasetId: "dataset-3",
			title: "质量失败",
			status: "OPEN",
			severity: "HIGH",
			priority: "HIGH",
			dataLevel: "DATA_INTERNAL",
		},
		{ status: "IN_PROGRESS", assignedTo: "operator" },
	);
	assert.equal(payload.sourceId, "run-7");
	assert.equal(payload.title, "质量失败");
	assert.equal(payload.status, "IN_PROGRESS");
	assert.equal(payload.assignedTo, "operator");
});
