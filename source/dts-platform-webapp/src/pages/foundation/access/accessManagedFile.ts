import type { FileUploadResult, IngestionTaskDTO, ManagedFileUploadResult } from "@/api/ingestion";
import { normalizeClassification } from "@/utils/classification";
import {
	mergeFileClassificationAdmission,
	resolveFileAdmissionState,
	restoreFileAdmissionFromTask,
} from "./shared/fileClassificationAdmission.helpers";

const asRecord = (value: unknown): Record<string, unknown> => {
	if (value && typeof value === "object" && !Array.isArray(value)) return value as Record<string, unknown>;
	if (typeof value !== "string" || !value.trim()) return {};
	try {
		const parsed = JSON.parse(value);
		return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : {};
	} catch {
		return {};
	}
};

/** Legacy admission helpers only inspect metadata. Empty paths never leave this adapter. */
export const toAdmissionFile = (file: ManagedFileUploadResult): FileUploadResult => ({
	...file,
	hostPath: "",
	containerPath: "",
});

export const toManagedFile = (file: ManagedFileUploadResult | FileUploadResult): ManagedFileUploadResult => {
	if (!file.fileId || !file.fileType || !file.originalName || !Array.isArray(file.columns)) {
		throw new Error("文件上传响应无效");
	}
	return {
		fileId: file.fileId,
		fileType: file.fileType,
		originalName: file.originalName,
		columns: file.columns,
		batchCode: file.batchCode,
		sheetName: file.sheetName,
		sheetIndex: file.sheetIndex,
		fileHash: file.fileHash,
		fileSize: file.fileSize,
		keyVersion: file.keyVersion,
		encrypted: file.encrypted,
		delimiter: file.delimiter,
		preview: file.preview,
		rowCount: file.rowCount,
		errorCount: file.errorCount,
		sourceFileType: file.sourceFileType,
		sheets: file.sheets,
		classification: file.classification,
		classificationSeal: file.classificationSeal,
		fieldClassifications: file.fieldClassifications,
	};
};

export const mergeManagedFileAdmission = (
	next: ManagedFileUploadResult,
	previous?: ManagedFileUploadResult | null,
): ManagedFileUploadResult =>
	toManagedFile(
		mergeFileClassificationAdmission(toAdmissionFile(next), previous ? toAdmissionFile(previous) : undefined),
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
	const sourceConfig = asRecord(task.sourceConfig);
	const seal = asRecord(sourceConfig.classificationSeal);
	const taskSeal = asRecord(task.classificationSeal);
	const fileId = sourceConfig._fileId || seal.fileId || taskSeal.fileId;
	if (typeof fileId !== "string" || !fileId.trim()) return null;
	const originalName =
		typeof sourceConfig._originalName === "string" && sourceConfig._originalName.trim()
			? sourceConfig._originalName
			: task.name || "uploaded_file";
	const columns = Array.isArray(sourceConfig._fileColumns)
		? sourceConfig._fileColumns.flatMap((value) => {
				const column = asRecord(value);
				const name = typeof column.name === "string" ? column.name.trim() : "";
				return name
					? [
							{
								name,
								type: typeof column.type === "string" && column.type.trim() ? column.type : "string",
								label: typeof column.label === "string" ? column.label : undefined,
							},
						]
					: [];
			})
		: [];
	return toManagedFile({
		fileId,
		fileType: typeof sourceConfig._fileType === "string" ? sourceConfig._fileType : "file",
		originalName,
		columns,
		fileHash: typeof sourceConfig._fileHash === "string" ? sourceConfig._fileHash : undefined,
		fileSize: typeof sourceConfig._fileSize === "number" ? sourceConfig._fileSize : undefined,
		keyVersion: typeof sourceConfig._keyVersion === "string" ? sourceConfig._keyVersion : undefined,
		encrypted: typeof sourceConfig._encrypted === "boolean" ? sourceConfig._encrypted : undefined,
		classification: normalizeClassification(
			typeof sourceConfig.classification === "string"
				? sourceConfig.classification
				: typeof taskSeal.fileFloor === "string"
					? taskSeal.fileFloor
					: typeof taskSeal.effectiveLevel === "string"
						? taskSeal.effectiveLevel
						: undefined,
			undefined,
		),
		classificationSeal: (Object.keys(seal).length
			? seal
			: task.classificationSeal) as ManagedFileUploadResult["classificationSeal"],
		fieldClassifications: (sourceConfig.fieldClassifications ||
			task.fieldClassifications) as ManagedFileUploadResult["fieldClassifications"],
	});
};

export const buildManagedFileSourceConfig = (file: ManagedFileUploadResult, autoId: boolean) => ({
	_fileId: file.fileId,
	_fileHash: file.fileHash,
	_fileSize: file.fileSize,
	_keyVersion: file.keyVersion,
	_encrypted: file.encrypted,
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
