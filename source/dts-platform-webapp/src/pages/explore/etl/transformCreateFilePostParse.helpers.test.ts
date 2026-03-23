import assert from "node:assert/strict";
import test from "node:test";
import type { FileUploadResult } from "@/api/ingestion";
import { buildFilePostParseOutcome } from "./transformCreateFilePostParse.helpers";

const parsedFile = {
	hostPath: "/tmp/project.csv",
	containerPath: "/data/project.csv",
	fileType: "csv",
	columns: [{ name: "col_1", type: "string" }, { name: "col_2", type: "string" }],
	originalName: "project-plan.xlsx",
} as FileUploadResult;

test("buildFilePostParseOutcome suggests table name for uploaded file and resets ods state", () => {
	const outcome = buildFilePostParseOutcome({
		parsed: parsedFile,
		currentFileTableName: "",
		syncPrefix: "ods_",
		reason: "upload",
	});

	assert.equal(outcome.readerType, "txtfilereader");
	assert.equal(outcome.shouldResetOds, true);
	assert.equal(outcome.suggestedFileTableName, "ods_project_plan");
	assert.equal(outcome.successMessage, "文件解析成功，检测到 2 列");
});

test("buildFilePostParseOutcome keeps existing table name and includes sheet name for sheet switch", () => {
	const outcome = buildFilePostParseOutcome({
		parsed: parsedFile,
		currentFileTableName: "custom_table",
		syncPrefix: "ods_",
		reason: "sheet-change",
		sheetName: "Sheet2",
	});

	assert.equal(outcome.suggestedFileTableName, undefined);
	assert.equal(outcome.successMessage, "已切换到 Sheet2，检测到 2 列");
});
