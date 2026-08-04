import assert from "node:assert/strict";
import test from "node:test";
import type { IngestionTaskDTO } from "@/api/ingestion";
import { resolveTaskAdmissionBasis } from "./taskAdmissionBasis.helpers.ts";

const BASE_SEAL = {
	sealId: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
	subjectType: "ASSET",
	subjectKey: "ingestion-file:file-001",
	effectiveLevel: "CONFIDENTIAL",
	snapshotVersion: 3,
	checksum: "0123456789abcdef0123456789abcdef",
	sealedAt: "2026-07-29T08:00:00Z",
	propagationStatus: "SEALED",
	fileFloor: "SECRET",
};

test("resolveTaskAdmissionBasis exposes persistent file admission evidence", () => {
	const task = {
		id: 42,
		name: "file-task",
		sourceType: "txtfilereader",
		sourceConfig: {
			columns: [{ name: "project_code" }, { name: "budget_amount" }, { name: "remark" }],
		},
		classificationSeal: BASE_SEAL,
		fieldClassifications: {
			project_code: "SECRET",
			budget_amount: "CONFIDENTIAL",
			remark: "SECRET",
		},
		syncMode: "full_refresh",
		status: "active",
	} as IngestionTaskDTO;

	assert.deepEqual(resolveTaskAdmissionBasis(task), {
		status: "admitted",
		statusLabel: "已准入",
		statusReason: "密级与准入已完成",
		evidenceStatus: "complete",
		evidenceStatusLabel: "依据完整",
		evidenceReason: "密级封存与字段覆盖完整",
		effectiveLevel: "CONFIDENTIAL",
		sourceLabel: "文件上传声明",
		fieldCoverageLabel: "3/3",
		coveredFieldCount: 3,
		totalFieldCount: 3,
		highestFieldLevel: "CONFIDENTIAL",
		snapshotVersion: 3,
		sealedAt: "2026-07-29T08:00:00Z",
	});
});

test("resolveTaskAdmissionBasis identifies datasource-level evidence without inventing field coverage", () => {
	const task = {
		id: 43,
		name: "mysql-task",
		sourceType: "mysqlreader",
		sourceConfig: {},
		sourceDataSourceId: "11111111-2222-3333-4444-555555555555",
		classificationSeal: {
			...BASE_SEAL,
			subjectKey: "data-source:11111111-2222-3333-4444-555555555555",
			effectiveLevel: "SECRET",
			fileFloor: undefined,
		},
		fieldClassifications: { id: "SECRET" },
		syncMode: "full_refresh",
		status: "draft",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.status, "pending");
	assert.equal(basis.statusLabel, "待确认");
	assert.equal(basis.effectiveLevel, "SECRET");
	assert.equal(basis.sourceLabel, "数据源密级继承");
	assert.equal(basis.fieldCoverageLabel, "已封存 1 项（总数不可核验）");
	assert.equal(basis.evidenceStatus, "unverifiable");
	assert.equal(basis.evidenceStatusLabel, "字段覆盖不可核验");
	assert.equal(basis.evidenceReason, "未提供可核验的字段全集");
	assert.equal(basis.highestFieldLevel, "SECRET");
});

test("resolveTaskAdmissionBasis reports incomplete file field coverage without hiding the admitted workflow state", () => {
	const task = {
		id: 45,
		name: "incomplete-file-task",
		sourceType: "txtfilereader",
		sourceConfig: {
			_fileColumns: [{ name: "project_code" }, { name: "budget_amount" }, { name: "remark" }],
		},
		classificationSeal: BASE_SEAL,
		fieldClassifications: {
			project_code: "SECRET",
			budget_amount: "CONFIDENTIAL",
		},
		syncMode: "full_refresh",
		status: "active",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.status, "admitted");
	assert.equal(basis.statusLabel, "已准入");
	assert.equal(basis.fieldCoverageLabel, "2/3");
	assert.equal(basis.evidenceStatus, "incomplete");
	assert.equal(basis.evidenceStatusLabel, "依据不完整");
	assert.equal(basis.evidenceReason, "字段密级覆盖不完整");
});

test("resolveTaskAdmissionBasis treats an invalid field classification as incomplete evidence", () => {
	const task = {
		id: 46,
		name: "invalid-field-task",
		sourceType: "txtfilereader",
		sourceConfig: {
			columns: [{ name: "project_code" }],
		},
		classificationSeal: BASE_SEAL,
		fieldClassifications: {
			project_code: "UNKNOWN_LEVEL",
		},
		syncMode: "full_refresh",
		status: "active",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.status, "blocked");
	assert.equal(basis.fieldCoverageLabel, "0/1");
	assert.equal(basis.evidenceStatus, "incomplete");
	assert.equal(basis.evidenceStatusLabel, "依据不完整");
	assert.match(basis.evidenceReason, /字段.*密级/);
	assert.equal(basis.highestFieldLevel, undefined);
});

test("resolveTaskAdmissionBasis does not turn an unknown effective level into an internal classification", () => {
	const task = {
		id: 47,
		name: "invalid-effective-level-task",
		sourceType: "mysqlreader",
		sourceConfig: {},
		classificationSeal: {
			...BASE_SEAL,
			subjectKey: "ingestion-task:47",
			effectiveLevel: "UNKNOWN_LEVEL",
			fileFloor: undefined,
		},
		syncMode: "full_refresh",
		status: "active",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.effectiveLevel, undefined);
	assert.equal(basis.evidenceStatus, "incomplete");
	assert.equal(basis.evidenceReason, "密级封存的有效密级无效");
	assert.equal(basis.sourceLabel, "治理封存快照");
});

test("resolveTaskAdmissionBasis keeps complete evidence separate from a blocked workflow state", () => {
	const task = {
		id: 48,
		name: "paused-file-task",
		sourceType: "txtfilereader",
		sourceConfig: {
			columns: [{ name: "project_code" }, { name: "budget_amount" }, { name: "remark" }],
		},
		classificationSeal: BASE_SEAL,
		fieldClassifications: {
			project_code: "SECRET",
			budget_amount: "CONFIDENTIAL",
			remark: "SECRET",
		},
		syncMode: "full_refresh",
		status: "paused",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.status, "blocked");
	assert.equal(basis.statusLabel, "不可准入");
	assert.match(basis.statusReason, /paused/);
	assert.equal(basis.evidenceStatus, "complete");
	assert.equal(basis.evidenceStatusLabel, "依据完整");
	assert.equal(basis.evidenceReason, "密级封存与字段覆盖完整");
});

test("resolveTaskAdmissionBasis rejects malformed snapshot evidence independently of field coverage", () => {
	const task = {
		id: 49,
		name: "invalid-snapshot-task",
		sourceType: "mysqlreader",
		sourceConfig: {
			columns: [{ name: "id" }],
		},
		classificationSeal: {
			...BASE_SEAL,
			subjectKey: "ingestion-task:49",
			checksum: "short",
			fileFloor: undefined,
		},
		fieldClassifications: {
			id: "CONFIDENTIAL",
		},
		syncMode: "full_refresh",
		status: "paused",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.fieldCoverageLabel, "1/1");
	assert.equal(basis.evidenceStatus, "incomplete");
	assert.equal(basis.evidenceReason, "密级封存校验值无效");
});

test("resolveTaskAdmissionBasis rejects null and empty snapshot versions instead of coercing them to zero", () => {
	for (const snapshotVersion of [null, ""]) {
		const task = {
			id: 50,
			name: "invalid-version-task",
			sourceType: "mysqlreader",
			sourceConfig: {
				columns: [{ name: "id" }],
			},
			classificationSeal: {
				...BASE_SEAL,
				subjectKey: "ingestion-task:50",
				snapshotVersion,
				fileFloor: undefined,
			},
			fieldClassifications: {
				id: "CONFIDENTIAL",
			},
			syncMode: "full_refresh",
			status: "paused",
		} as IngestionTaskDTO;

		const basis = resolveTaskAdmissionBasis(task);
		assert.equal(basis.snapshotVersion, undefined);
		assert.equal(basis.evidenceStatus, "incomplete");
		assert.equal(basis.evidenceReason, "密级封存版本无效");
	}
});

test("resolveTaskAdmissionBasis presents an unclassified datasource as a normal workflow", () => {
	const task = {
		id: 44,
		name: "unclassified-task",
		sourceType: "mysqlreader",
		sourceConfig: {},
		syncMode: "full_refresh",
		status: "draft",
	} as IngestionTaskDTO;

	const basis = resolveTaskAdmissionBasis(task);
	assert.equal(basis.status, "pending");
	assert.equal(basis.statusLabel, "待确认");
	assert.equal(basis.evidenceStatus, "not_required");
	assert.equal(basis.evidenceStatusLabel, "无需密级依据");
	assert.equal(basis.evidenceReason, "当前数据源未配置密级，按普通接入流程处理");
	assert.equal(basis.effectiveLevel, undefined);
	assert.equal(basis.sourceLabel, "普通数据源");
	assert.equal(basis.fieldCoverageLabel, "不适用");
	assert.match(basis.statusReason, /普通接入流程/);
});
