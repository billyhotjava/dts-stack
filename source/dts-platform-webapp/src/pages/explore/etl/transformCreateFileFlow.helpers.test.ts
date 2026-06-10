import assert from "node:assert/strict";
import test from "node:test";
import {
	buildTransformFileUploadResult,
	suggestTransformFileTableName,
} from "./transformCreateFileFlow.helpers";

test("buildTransformFileUploadResult normalizes parsed file metadata and preserves selected sheet", () => {
	const result = buildTransformFileUploadResult(
		"项目计划.xlsx",
		"batch-001",
		"file-001",
		[{ index: 0, name: "Sheet1" }],
		{
			hostPath: "/tmp/project.csv",
			containerPath: "/data/project.csv",
			fileType: "excel",
			columns: [
				{ name: "项目编号", dataType: "string", label: "项目编号" },
				{ name: "计划日期", dataType: "datetime" },
				{ name: "  ", dataType: "string" },
			],
			sheetName: "导入页",
			rowCount: 12,
		},
		{ index: 0, name: "Sheet1" }
	);

	assert.equal(result.hostPath, "/tmp/project.csv");
	assert.equal(result.containerPath, "/data/project.csv");
	assert.equal(result.originalName, "项目计划.xlsx");
	assert.equal(result.sourceFileType, "excel");
	assert.equal(result.sheetName, "导入页");
	assert.equal(result.sheetIndex, 0);
	assert.equal(result.fileType, "excel");
	assert.deepEqual(result.columns, [
		{ name: "项目编号", type: "string", label: "项目编号" },
		{ name: "计划日期", type: "datetime", label: "" },
	]);
});

test("suggestTransformFileTableName builds normalized ods table names from prefix and filename", () => {
	assert.equal(suggestTransformFileTableName("", "ods_", "项目计划.xlsx"), "ods_file");
	assert.equal(suggestTransformFileTableName("", "ods_", "project-plan.xlsx"), "ods_project_plan");
	assert.equal(suggestTransformFileTableName("custom_name", "ods_", "project-plan.xlsx"), undefined);
});
