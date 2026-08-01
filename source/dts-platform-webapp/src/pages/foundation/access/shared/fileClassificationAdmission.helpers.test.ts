import assert from "node:assert/strict";
import test from "node:test";
import type { FileUploadResult, IngestionTaskDTO } from "@/api/ingestion";
import {
	mergeFileClassificationAdmission,
	renameFileFieldClassification,
	resolveFileAdmissionState,
	resolveTaskAdmissionState,
	restoreFileAdmissionFromTask,
	setFileFieldClassification,
} from "./fileClassificationAdmission.helpers.ts";

const FILE_SEAL = {
	sealId: "11111111-2222-3333-4444-555555555555",
	subjectType: "FILE",
	subjectKey: "external-exchange-file:file-001",
	effectiveLevel: "SECRET",
	snapshotVersion: 3,
	checksum: "0123456789abcdef0123456789abcdef",
	sealedAt: "2026-07-28T08:00:00Z",
	propagationStatus: "SEALED",
};

const TASK_SEAL = {
	...FILE_SEAL,
	sealId: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
	subjectType: "ASSET",
	subjectKey: "ingestion-file:external-exchange-file:file-001",
	effectiveLevel: "CONFIDENTIAL",
	fileFloor: "SECRET",
};

const parsedFile = (patch: Partial<FileUploadResult> = {}): FileUploadResult => ({
	hostPath: "/tmp/project.csv",
	containerPath: "/data/project.csv",
	fileType: "csv",
	columns: [
		{ name: "identity_no", type: "string" },
		{ name: "name", type: "string" },
		{ name: "phone", type: "string" },
	],
	originalName: "project.xlsx",
	fileId: "file-001",
	classification: "SECRET",
	classificationSeal: FILE_SEAL,
	fieldClassifications: {
		identity_no: "PUBLIC",
		phone: "CONFIDENTIAL",
	},
	...patch,
});

test("mergeFileClassificationAdmission applies the file floor and preserves prior field upgrades across reparse", () => {
	const previous = parsedFile({
		columns: [
			{ name: "ods_identity_no", type: "string", label: "identity_no" },
			{ name: "name", type: "string" },
			{ name: "removed_column", type: "string" },
		],
		fieldClassifications: {
			ods_identity_no: "CONFIDENTIAL",
			name: "SECRET",
			removed_column: "CONFIDENTIAL",
		},
	});

	const result = mergeFileClassificationAdmission(parsedFile(), previous);

	assert.equal(result.classification, "SECRET");
	assert.deepEqual(result.classificationSeal, FILE_SEAL);
	assert.deepEqual(result.fieldClassifications, {
		identity_no: "CONFIDENTIAL",
		name: "SECRET",
		phone: "CONFIDENTIAL",
	});
});

test("setFileFieldClassification rejects a downgrade below the file floor and permits an upgrade", () => {
	const normalized = mergeFileClassificationAdmission(parsedFile());

	assert.equal(
		setFileFieldClassification(normalized, "identity_no", "PUBLIC").fieldClassifications?.identity_no,
		"SECRET",
	);
	assert.equal(
		setFileFieldClassification(normalized, "identity_no", "CONFIDENTIAL").fieldClassifications?.identity_no,
		"CONFIDENTIAL",
	);
});

test("renameFileFieldClassification moves the field seal to the renamed column", () => {
	const normalized = mergeFileClassificationAdmission(parsedFile());
	const renamed = renameFileFieldClassification(normalized, "phone", "ods_phone");

	assert.equal(renamed.fieldClassifications?.phone, undefined);
	assert.equal(renamed.fieldClassifications?.ods_phone, "CONFIDENTIAL");
});

test("resolveFileAdmissionState blocks task creation without an authoritative FILE seal", () => {
	assert.deepEqual(resolveFileAdmissionState(mergeFileClassificationAdmission(parsedFile())), {
		ready: true,
		classification: "SECRET",
		reason: "文件与字段密级已封存",
	});

	const missingSeal = resolveFileAdmissionState(parsedFile({ classificationSeal: undefined }));
	assert.equal(missingSeal.ready, false);
	assert.match(missingSeal.reason, /缺少文件密级封存/);
});

test("restoreFileAdmissionFromTask prefers the FILE seal retained in source config", () => {
	const restored = restoreFileAdmissionFromTask(
		parsedFile({
			classification: undefined,
			classificationSeal: undefined,
			fieldClassifications: undefined,
		}),
		{
			id: 42,
			name: "file-task",
			sourceType: "txtfilereader",
			sourceConfig: {
				classification: "SECRET",
				classificationSeal: FILE_SEAL,
				fieldClassifications: { identity_no: "CONFIDENTIAL", name: "SECRET", phone: "SECRET" },
			},
			classificationSeal: TASK_SEAL,
			fieldClassifications: { identity_no: "CONFIDENTIAL", name: "SECRET", phone: "SECRET" },
			syncMode: "full_refresh",
			status: "draft",
		},
	);

	assert.equal(restored?.classification, "SECRET");
	assert.deepEqual(restored?.classificationSeal, FILE_SEAL);
	assert.equal(restored?.fieldClassifications?.identity_no, "CONFIDENTIAL");
});

test("resolveTaskAdmissionState only enables admission for valid drafts and execution for admitted active tasks", () => {
	const draftTask = {
		id: 42,
		name: "file-task",
		sourceType: "txtfilereader",
		sourceConfig: {},
		classificationSeal: TASK_SEAL,
		fieldClassifications: { identity_no: "CONFIDENTIAL", name: "SECRET" },
		syncMode: "full_refresh",
		status: "draft",
	} as IngestionTaskDTO;

	assert.deepEqual(resolveTaskAdmissionState(draftTask), {
		canAdmit: true,
		canExecute: false,
		classification: "CONFIDENTIAL",
		reason: "密级封存有效，待完成准入",
	});
	assert.deepEqual(resolveTaskAdmissionState({ ...draftTask, status: "active" }), {
		canAdmit: false,
		canExecute: true,
		classification: "CONFIDENTIAL",
		reason: "密级与准入已完成",
	});
});

test("resolveTaskAdmissionState requires an enabled file quality pre-check to pass before admission", () => {
	const task = {
		id: 43,
		name: "quality-gated-file",
		sourceType: "txtfilereader",
		sourceConfig: {},
		classificationSeal: TASK_SEAL,
		fieldClassifications: { identity_no: "CONFIDENTIAL", name: "SECRET" },
		qualityPreCheckEnabled: true,
		preCheckStatus: "FAILED",
		syncMode: "full_refresh",
		status: "draft",
	} as IngestionTaskDTO;

	assert.equal(resolveTaskAdmissionState(task).canAdmit, false);
	assert.match(resolveTaskAdmissionState(task).reason, /质量预检尚未通过/);
	assert.equal(resolveTaskAdmissionState({ ...task, preCheckStatus: "PASSED" }).canAdmit, true);
});

test("resolveTaskAdmissionState fails closed for a missing or stale seal", () => {
	const baseTask = {
		id: 42,
		name: "file-task",
		sourceType: "txtfilereader",
		sourceConfig: {},
		syncMode: "full_refresh",
		status: "active",
	} as IngestionTaskDTO;

	const missing = resolveTaskAdmissionState(baseTask);
	assert.equal(missing.canAdmit, false);
	assert.equal(missing.canExecute, false);
	assert.match(missing.reason, /缺少密级封存/);

	const stale = resolveTaskAdmissionState({
		...baseTask,
		classificationSeal: { ...TASK_SEAL, effectiveLevel: "SECRET" },
		fieldClassifications: { identity_no: "CONFIDENTIAL" },
	});
	assert.equal(stale.canExecute, false);
	assert.match(stale.reason, /低于字段最高密级/);
});
