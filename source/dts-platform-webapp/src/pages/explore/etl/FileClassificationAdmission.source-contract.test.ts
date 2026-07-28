import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const API_SOURCE = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");
const FILE_STEP_SOURCE = readFileSync(new URL("./steps/FileUnifiedStep.tsx", import.meta.url), "utf8");
const CREATE_SOURCE = readFileSync(new URL("./TransformCreatePage.tsx", import.meta.url), "utf8");
const DETAIL_SOURCE = readFileSync(new URL("./TransformDetailPage.tsx", import.meta.url), "utf8");
const ACTIONS_SOURCE = readFileSync(new URL("./components/TransformAdmissionActions.tsx", import.meta.url), "utf8");

test("offline file upload requires classification on the secure upload-and-parse path", () => {
	assert.match(API_SOURCE, /formData\.append\("classification"/);
	assert.match(FILE_STEP_SOURCE, /FileClassificationIntake/);
	assert.match(CREATE_SOURCE, /uploadAndParseFile/);
	assert.doesNotMatch(CREATE_SOURCE, /excelPrepare/);
});

test("file task creation remains draft until explicit classification admission", () => {
	assert.match(CREATE_SOURCE, /draft:\s*true/);
	assert.match(CREATE_SOURCE, /runNow:\s*false/);
	assert.match(CREATE_SOURCE, /const updatePayload[\s\S]{0,800}status:\s*"draft"/);
	assert.match(CREATE_SOURCE, /已转为草稿，请重新完成密级与准入/);
	assert.match(DETAIL_SOURCE, /TransformAdmissionActions/);
	assert.match(DETAIL_SOURCE, /admitTask/);
	assert.match(ACTIONS_SOURCE, /完成密级与准入/);
	assert.match(
		ACTIONS_SOURCE,
		/onClick=\{onRebuildDag\}[\s\S]{0,180}disabled=\{taskDeleted \|\| !admission\.canExecute/,
	);
	assert.match(API_SOURCE, /api\.post\(\{ url: `\/ingestion\/tasks\/\$\{id\}\/admit` \}\)/);
});
