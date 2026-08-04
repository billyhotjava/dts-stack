import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STEP_SOURCE = readFileSync(new URL("./FileAccessStep.tsx", import.meta.url), "utf8");
const GRID_SOURCE = readFileSync(new URL("./FileFieldMappingEditor.tsx", import.meta.url), "utf8");
const HELPER_SOURCE = readFileSync(new URL("./shared/filePreview.ts", import.meta.url), "utf8");

test("offline file preview stays inside the existing horizontal field grid", () => {
	assert.match(STEP_SOURCE, /<FileFieldMappingEditor/);
	assert.match(STEP_SOURCE, /key=\{fileUploadResult\.fileId\}/);
	assert.match(STEP_SOURCE, /preview=\{fileUploadResult\.preview\}/);
	assert.doesNotMatch(STEP_SOURCE, /<FileDataPreview/);
});

test("preview row selection is presentation-only and bounded to the parsed sample", () => {
	assert.match(GRID_SOURCE, /useState\(DEFAULT_FILE_PREVIEW_LIMIT\)/);
	assert.match(GRID_SOURCE, /min=\{1\}/);
	assert.match(GRID_SOURCE, /max=\{MAX_FILE_PREVIEW_LIMIT\}/);
	assert.match(GRID_SOURCE, /buildFilePreviewRows\(preview, rowLimit\)/);
	assert.doesNotMatch(GRID_SOURCE, /uploadAndParseFile|ingestionTaskAPI|sourceConfig|destinationConfig/);
	assert.match(HELPER_SOURCE, /DEFAULT_FILE_PREVIEW_LIMIT = 10/);
	assert.match(HELPER_SOURCE, /MAX_FILE_PREVIEW_LIMIT = 20/);
});
