import assert from "node:assert/strict";
import test from "node:test";
import type { FileUploadResult } from "@/api/ingestion";
import {
	buildPreparedFileParseInput,
	buildRefreshFileParseInput,
	buildSheetChangeFileParseInput,
} from "./transformCreateFileParse.helpers";

test("buildPreparedFileParseInput picks the first sheet as default selection", () => {
	const result = buildPreparedFileParseInput(
		{
			fileId: "file-1",
			fileName: "project.xlsx",
			batchCode: "batch-1",
			sheets: [
				{ index: 0, name: "Sheet1" },
				{ index: 1, name: "Sheet2" },
			],
		},
		50
	);

	assert.deepEqual(result, {
		fileId: "file-1",
		fileName: "project.xlsx",
		batchCode: "batch-1",
		sheets: [
			{ index: 0, name: "Sheet1" },
			{ index: 1, name: "Sheet2" },
		],
		selectedSheet: { index: 0, name: "Sheet1" },
		previewLimit: 50,
	});
});

test("buildRefreshFileParseInput restores current selected sheet from file upload result", () => {
	const result = buildRefreshFileParseInput(
		{
			hostPath: "/tmp/project.csv",
			containerPath: "/data/project.csv",
			fileType: "csv",
			columns: [],
			originalName: "project.xlsx",
			fileId: "file-1",
			batchCode: "batch-1",
			sheets: [{ index: 1, name: "Sheet2" }],
			sheetName: "Sheet2",
			sheetIndex: 1,
		} as FileUploadResult,
		100
	);

	assert.deepEqual(result, {
		fileId: "file-1",
		fileName: "project.xlsx",
		batchCode: "batch-1",
		sheets: [{ index: 1, name: "Sheet2" }],
		selectedSheet: { index: 1, name: "Sheet2" },
		previewLimit: 100,
	});
});

test("buildRefreshFileParseInput returns null when fileId is missing", () => {
	assert.equal(
		buildRefreshFileParseInput(
			{
				hostPath: "/tmp/project.csv",
				containerPath: "/data/project.csv",
				fileType: "csv",
				columns: [],
				originalName: "project.xlsx",
			} as FileUploadResult,
			20
		),
		null
	);
});

test("buildSheetChangeFileParseInput resolves selected sheet from current file upload result", () => {
	const result = buildSheetChangeFileParseInput(
		{
			hostPath: "/tmp/project.csv",
			containerPath: "/data/project.csv",
			fileType: "csv",
			columns: [],
			originalName: "project.xlsx",
			fileId: "file-1",
			batchCode: "batch-1",
			sheets: [
				{ index: 0, name: "Sheet1" },
				{ index: 1, name: "Sheet2" },
			],
		} as FileUploadResult,
		1,
		30
	);

	assert.deepEqual(result, {
		fileId: "file-1",
		fileName: "project.xlsx",
		batchCode: "batch-1",
		sheets: [
			{ index: 0, name: "Sheet1" },
			{ index: 1, name: "Sheet2" },
		],
		selectedSheet: { index: 1, name: "Sheet2" },
		previewLimit: 30,
	});
});

test("buildSheetChangeFileParseInput returns null when target sheet is missing", () => {
	assert.equal(
		buildSheetChangeFileParseInput(
			{
				hostPath: "/tmp/project.csv",
				containerPath: "/data/project.csv",
				fileType: "csv",
				columns: [],
				originalName: "project.xlsx",
				fileId: "file-1",
				batchCode: "batch-1",
				sheets: [{ index: 0, name: "Sheet1" }],
			} as FileUploadResult,
			99,
			30
		),
		null
	);
});
