import { expect, test } from "vitest";
import type { IngestionTaskDTO } from "@/api/ingestion";
import { resolveTaskAdmissionState } from "./fileClassificationAdmission.helpers";

const TASK_SEAL = {
	sealId: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
	subjectType: "ASSET",
	subjectKey: "ingestion-file:external-exchange-file:file-001",
	effectiveLevel: "CONFIDENTIAL",
	fileFloor: "SECRET",
	snapshotVersion: 3,
	checksum: "0123456789abcdef0123456789abcdef",
	sealedAt: "2026-07-28T08:00:00Z",
	propagationStatus: "SEALED",
};

test("optional file quality failure does not block plan activation", () => {
	const task = {
		id: 43,
		name: "quality-optional-file",
		sourceType: "txtfilereader",
		sourceConfig: {},
		classificationSeal: TASK_SEAL,
		fieldClassifications: { identity_no: "CONFIDENTIAL", name: "SECRET" },
		qualityPreCheckEnabled: true,
		preCheckStatus: "FAILED",
		syncMode: "full_refresh",
		status: "draft",
	} as IngestionTaskDTO;

	const admission = resolveTaskAdmissionState(task);

	expect(admission.canAdmit).toBe(true);
	expect(admission.reason).not.toContain("质量预检");
});
