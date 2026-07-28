import assert from "node:assert/strict";
import test from "node:test";
import {
	buildTransformFileSourceConfig,
	buildTransformFileTaskAdmissionFields,
	buildTransformFileUploadResult,
	suggestTransformFileTableName,
	uploadTransformFileWithAdmission,
} from "./transformCreateFileFlow.helpers.ts";

test("buildTransformFileUploadResult normalizes parsed file metadata and preserves selected sheet", () => {
	const classificationSeal = {
		sealId: "11111111-2222-3333-4444-555555555555",
		subjectType: "FILE",
		subjectKey: "external-exchange-file:file-001",
		effectiveLevel: "SECRET",
		snapshotVersion: 3,
		checksum: "0123456789abcdef0123456789abcdef",
		sealedAt: "2026-07-28T08:00:00Z",
		propagationStatus: "SEALED",
	};
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
			classification: "SECRET",
			classificationSeal,
			fieldClassifications: {
				项目编号: "SECRET",
				计划日期: "CONFIDENTIAL",
			},
		},
		{ index: 0, name: "Sheet1" },
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
	assert.equal(result.classification, "SECRET");
	assert.deepEqual(result.classificationSeal, classificationSeal);
	assert.deepEqual(result.fieldClassifications, {
		项目编号: "SECRET",
		计划日期: "CONFIDENTIAL",
	});
});

test("suggestTransformFileTableName builds normalized ods table names from prefix and filename", () => {
	assert.equal(suggestTransformFileTableName("", "ods_", "项目计划.xlsx"), "ods_file");
	assert.equal(suggestTransformFileTableName("", "ods_", "project-plan.xlsx"), "ods_project_plan");
	assert.equal(suggestTransformFileTableName("custom_name", "ods_", "project-plan.xlsx"), undefined);
});

test("buildTransformFileSourceConfig keeps the authoritative seal and field map in task source config", () => {
	const classificationSeal = {
		sealId: "11111111-2222-3333-4444-555555555555",
		subjectType: "FILE",
		subjectKey: "external-exchange-file:file-001",
		effectiveLevel: "SECRET",
		snapshotVersion: 3,
		checksum: "0123456789abcdef0123456789abcdef",
		sealedAt: "2026-07-28T08:00:00Z",
		propagationStatus: "SEALED",
	};
	const result = buildTransformFileSourceConfig(
		{
			hostPath: "/tmp/project.csv",
			containerPath: "/data/project.csv",
			fileType: "csv",
			columns: [{ name: "identity_no", type: "string" }],
			originalName: "project.xlsx",
			fileId: "file-001",
			fileHash: "hash-001",
			fileSize: 128,
			keyVersion: "v1",
			encrypted: true,
			classification: "SECRET",
			classificationSeal,
			fieldClassifications: { identity_no: "CONFIDENTIAL" },
		},
		true,
	);

	assert.deepEqual(result, {
		_fileId: "file-001",
		_filePath: "/tmp/project.csv",
		_containerPath: "/data/project.csv",
		_keyVersion: "v1",
		_encrypted: true,
		_fileHash: "hash-001",
		_fileSize: 128,
		_fileType: "csv",
		_fileColumns: [{ name: "identity_no", type: "string" }],
		_originalName: "project.xlsx",
		_autoId: true,
		classification: "SECRET",
		classificationSeal,
		fieldClassifications: { identity_no: "CONFIDENTIAL" },
	});
});

test("buildTransformFileTaskAdmissionFields keeps the FILE floor on update", () => {
	const classificationSeal = {
		sealId: "11111111-2222-3333-4444-555555555555",
		subjectType: "FILE",
		subjectKey: "external-exchange-file:file-001",
		effectiveLevel: "SECRET",
		snapshotVersion: 3,
		checksum: "0123456789abcdef0123456789abcdef",
		sealedAt: "2026-07-28T08:00:00Z",
	};
	assert.deepEqual(
		buildTransformFileTaskAdmissionFields({
			hostPath: "/tmp/project.csv",
			containerPath: "/data/project.csv",
			fileType: "csv",
			columns: [{ name: "identity_no", type: "string" }],
			originalName: "project.csv",
			classification: "SECRET",
			classificationSeal,
			fieldClassifications: { identity_no: "CONFIDENTIAL" },
		}),
		{
			classificationSeal: { ...classificationSeal, fileFloor: "SECRET" },
			fieldClassifications: { identity_no: "CONFIDENTIAL" },
		},
	);
});

test("uploadTransformFileWithAdmission passes classification and restores admitted field mappings", async () => {
	const classificationSeal = {
		sealId: "11111111-2222-3333-4444-555555555555",
		subjectType: "FILE",
		subjectKey: "external-exchange-file:file-001",
		effectiveLevel: "SECRET",
		snapshotVersion: 3,
		checksum: "0123456789abcdef0123456789abcdef",
		sealedAt: "2026-07-28T08:00:00Z",
	};
	let receivedClassification: string | undefined;
	const result = await uploadTransformFileWithAdmission({
		file: new File(["identity_no"], "project.csv"),
		classification: "SECRET",
		previewLimit: 20,
		previousFile: {
			hostPath: "/old/project.csv",
			containerPath: "/old/project.csv",
			fileType: "csv",
			columns: [
				{
					name: "ods_identity_no",
					label: "身份证号",
					type: "string",
					_odsMatched: true,
				},
			],
			originalName: "project.csv",
			classification: "SECRET",
			classificationSeal,
			fieldClassifications: { ods_identity_no: "CONFIDENTIAL" },
		},
		preserveSavedMapping: true,
		uploadAndParse: async (_file, options) => {
			receivedClassification = options.classification;
			return {
				hostPath: "/tmp/project.csv",
				containerPath: "/data/project.csv",
				fileType: "csv",
				columns: [{ name: "identity_no", label: "身份证号", type: "string" }],
				originalName: "project.csv",
				classification: "SECRET",
				classificationSeal,
				fieldClassifications: { identity_no: "SECRET" },
			};
		},
	});

	assert.equal(receivedClassification, "SECRET");
	assert.equal(result.columns[0].name, "ods_identity_no");
	assert.equal(result.fieldClassifications?.ods_identity_no, "CONFIDENTIAL");
});
