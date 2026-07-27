import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const panel = readFileSync(new URL("./components/ReleaseCandidateWorkbenchPanel.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../api/modelSpecApi.ts", import.meta.url), "utf8");

test("workbench renders server-owned per-model build and relation evidence separately", () => {
	assert.match(api, /entryEvidence: ReleaseCandidateEntryEvidence\[\]/);
	assert.match(api, /pipelineRunGroupId/);
	assert.match(api, /airflowRunId/);
	assert.match(api, /relationState/);
	assert.match(panel, /dataIndex: "runStatus"/);
	assert.match(panel, /row\.relationState/);
	assert.match(panel, /dbt 成功与物理关系存在是两条独立证据/);
});

test("workbench actions remain role-aware and never rewrite candidate scope", () => {
	assert.match(panel, /allowedActions\.includes\("START_BUILD"\)/);
	assert.match(panel, /allowedActions\.includes\("RETRY_BUILD"\)/);
	assert.match(panel, /lockReleaseCandidate/);
	assert.match(panel, /retryReleaseCandidate/);
	assert.doesNotMatch(panel, /updateReleaseCandidateScope|createReleaseCandidate/);
});

test("deep-link evidence fails closed and polling stops when the page is hidden", () => {
	assert.match(panel, /candidate\.id !== requestedCandidateId/);
	assert.match(panel, /entry\.modelSpecId === requestedModelSpecId/);
	assert.match(panel, /document\.visibilityState === "visible"/);
	assert.match(panel, /window\.clearInterval/);
	assert.match(panel, /页面不会用当前候选替代原候选证据/);
});
