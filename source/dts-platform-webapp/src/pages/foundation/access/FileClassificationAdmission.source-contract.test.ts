import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const API_SOURCE = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");
const FILE_STEP_SOURCE = readFileSync(new URL("./FileAccessStep.tsx", import.meta.url), "utf8");
const WIZARD_SOURCE = readFileSync(new URL("./useAccessPlanWizard.ts", import.meta.url), "utf8");
const PAYLOAD_SOURCE = readFileSync(new URL("./accessPlanPayload.ts", import.meta.url), "utf8");
const DETAIL_SOURCE = readFileSync(new URL("./AccessPlanDetailPage.tsx", import.meta.url), "utf8");
const GOVERNANCE_SOURCE = readFileSync(new URL("./AccessGovernancePanels.tsx", import.meta.url), "utf8");

test("offline file upload requires classification on the secure upload-and-parse path", () => {
	assert.match(API_SOURCE, /formData\.append\("classification"/);
	assert.match(FILE_STEP_SOURCE, /<SecureUpload[\s\S]{0,240}secretModule/);
	assert.match(FILE_STEP_SOURCE, /userClassificationRank=\{userClassificationRank\}/);
	assert.match(FILE_STEP_SOURCE, /validateOfflineFile/);
	assert.match(WIZARD_SOURCE, /uploadTransformFileWithAdmission/);
	assert.match(WIZARD_SOURCE, /uploadAndParseFile/);
	assert.doesNotMatch(WIZARD_SOURCE, /excelPrepare/);
});

test("file task activation follows successful pre-check without a manual admission action", () => {
	assert.match(PAYLOAD_SOURCE, /const buildFileRequest[\s\S]{0,1200}runNow:\s*false/);
	assert.match(PAYLOAD_SOURCE, /const buildFileRequest[\s\S]{0,1200}draft:\s*true/);
	assert.match(PAYLOAD_SOURCE, /buildManagedFileAdmissionFields/);
	assert.match(GOVERNANCE_SOURCE, /result\.status === "PASSED"[\s\S]{0,240}ingestionTaskAPI\.admitTask\(taskId\)/);
	assert.doesNotMatch(DETAIL_SOURCE, /runAccessPlanOperation\("admit"/);
	assert.doesNotMatch(DETAIL_SOURCE, /准入草稿|密级准入|TaskAdmissionBasis/);
	assert.doesNotMatch(GOVERNANCE_SOURCE, /当前 Revision|准入 Revision/);
	assert.match(DETAIL_SOURCE, /disabled=\{taskDeleted \|\| !canExecuteActiveRevision \|\| operation !== null\}/);
	assert.match(API_SOURCE, /api\.post\(\{ url: `\/ingestion\/tasks\/\$\{id\}\/admit` \}\)/);
});
