import type { FileUploadResult, IngestionTaskDTO, ManagedFileUploadResult } from "@/api/ingestion";
import { normalizeManagedFileUploadResult } from "@/api/ingestion";
import {
	mergeFileClassificationAdmission,
	resolveFileAdmissionState,
	restoreFileAdmissionFromTask,
} from "../../explore/etl/fileClassificationAdmission.helpers";
import { extractFileUploadResult } from "../../explore/etl/ingestionFormHelpers";

/** Legacy admission helpers only inspect metadata. Empty paths never leave this adapter. */
export const toAdmissionFile = (file: ManagedFileUploadResult): FileUploadResult => ({
	...file,
	hostPath: "",
	containerPath: "",
});

export const toManagedFile = (file: ManagedFileUploadResult | FileUploadResult): ManagedFileUploadResult =>
	normalizeManagedFileUploadResult(file);

export const mergeManagedFileAdmission = (
	next: ManagedFileUploadResult,
	previous?: ManagedFileUploadResult | null,
): ManagedFileUploadResult =>
	toManagedFile(
		mergeFileClassificationAdmission(
			toAdmissionFile(next),
			previous ? toAdmissionFile(previous) : undefined,
		),
	);

export const resolveManagedFileAdmissionState = (file: ManagedFileUploadResult | null) =>
	resolveFileAdmissionState(file ? toAdmissionFile(file) : null);

export const restoreManagedFileAdmissionFromTask = (
	file: ManagedFileUploadResult | null,
	task: IngestionTaskDTO,
): ManagedFileUploadResult | null => {
	if (!file) return null;
	const restored = restoreFileAdmissionFromTask(toAdmissionFile(file), task);
	return restored ? toManagedFile(restored) : null;
};

export const extractManagedFileFromTask = (task: IngestionTaskDTO): ManagedFileUploadResult | null => {
	const extracted = extractFileUploadResult(task);
	if (!extracted) return null;
	return toManagedFile(extracted);
};

export const buildManagedFileSourceConfig = (file: ManagedFileUploadResult, autoId: boolean) => ({
	_fileId: file.fileId,
	_fileHash: file.fileHash,
	_fileSize: file.fileSize,
	_fileType: file.fileType,
	_fileColumns: file.columns,
	_originalName: file.originalName,
	_autoId: autoId,
	classification: file.classification,
	classificationSeal: file.classificationSeal,
	fieldClassifications: file.fieldClassifications,
});

export const buildManagedFileAdmissionFields = (file: ManagedFileUploadResult) => ({
	classificationSeal: file.classificationSeal
		? { ...file.classificationSeal, fileFloor: file.classification }
		: undefined,
	fieldClassifications: file.fieldClassifications,
});
