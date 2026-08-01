import type { ClassificationSealReference, FileUploadResult, IngestionTaskDTO } from "@/api/ingestion";
import {
	type ClassificationLevel,
	classificationRank,
	normalizeClassification,
} from "@/utils/classification";

type AdmissionValidation = {
	valid: boolean;
	classification?: ClassificationLevel;
	reason: string;
};

export type FileAdmissionState = {
	ready: boolean;
	classification?: ClassificationLevel;
	reason: string;
};

export type TaskAdmissionState = {
	canAdmit: boolean;
	canExecute: boolean;
	classification?: ClassificationLevel;
	reason: string;
};

const asRecord = (value: unknown): Record<string, any> => {
	if (value && typeof value === "object" && !Array.isArray(value)) {
		return value as Record<string, any>;
	}
	if (typeof value !== "string" || !value.trim()) {
		return {};
	}
	try {
		const parsed = JSON.parse(value);
		return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? (parsed as Record<string, any>) : {};
	} catch {
		return {};
	}
};

const normalizedLevel = (value: unknown): ClassificationLevel | undefined =>
	normalizeClassification(typeof value === "string" ? value : undefined, undefined);

const highestLevel = (...values: unknown[]): ClassificationLevel | undefined => {
	let highest: ClassificationLevel | undefined;
	for (const value of values) {
		const candidate = normalizedLevel(value);
		if (
			candidate &&
			(highest === undefined || (classificationRank(candidate) ?? -1) > (classificationRank(highest) ?? -1))
		) {
			highest = candidate;
		}
	}
	return highest;
};

const sourceColumnKey = (column?: FileUploadResult["columns"][number]): string =>
	String(column?.label || column?.name || "")
		.trim()
		.toLowerCase();

const sealFrom = (value: unknown): ClassificationSealReference | undefined => {
	const record = asRecord(value);
	return Object.keys(record).length ? (record as ClassificationSealReference) : undefined;
};

const fieldMapFrom = (value: unknown): Record<string, ClassificationLevel | string> => {
	const record = asRecord(value);
	const result: Record<string, ClassificationLevel | string> = {};
	for (const [name, level] of Object.entries(record)) {
		if (name.trim() && typeof level === "string" && level.trim()) {
			result[name] = level;
		}
	}
	return result;
};

const fileFloorFrom = (classification: unknown, seal?: ClassificationSealReference): ClassificationLevel | undefined =>
	highestLevel(
		classification,
		seal?.fileFloor,
		seal?.subjectType?.toUpperCase() === "FILE" ? seal.effectiveLevel : undefined,
	);

const validateSealAndFields = (
	sealValue: unknown,
	fieldClassificationsValue: unknown,
	requireFileFields: boolean,
	requireEffectiveCoversFields: boolean = true,
): AdmissionValidation => {
	const seal = sealFrom(sealValue);
	if (!seal) {
		return { valid: false, reason: "缺少密级封存" };
	}
	if (
		!String(seal.sealId || "").trim() ||
		!String(seal.subjectType || "").trim() ||
		!String(seal.subjectKey || "").trim()
	) {
		return { valid: false, reason: "密级封存标识不完整" };
	}
	const classification = normalizedLevel(seal.effectiveLevel);
	if (!classification) {
		return { valid: false, reason: "密级封存的有效密级无效" };
	}
	const version = Number(seal.snapshotVersion);
	if (!Number.isInteger(version) || version < 0) {
		return { valid: false, classification, reason: "密级封存版本无效" };
	}
	const checksum = String(seal.checksum || "").trim();
	if (checksum.length < 16 || checksum.length > 128) {
		return { valid: false, classification, reason: "密级封存校验值无效" };
	}
	const sealedAt = Date.parse(String(seal.sealedAt || ""));
	if (!Number.isFinite(sealedAt)) {
		return { valid: false, classification, reason: "密级封存时间无效" };
	}

	const fields = fieldMapFrom(fieldClassificationsValue);
	const fileFloor = fileFloorFrom(undefined, seal);
	if ((requireFileFields || fileFloor) && Object.keys(fields).length === 0) {
		return { valid: false, classification, reason: "文件任务缺少字段密级封存" };
	}
	for (const [fieldName, rawLevel] of Object.entries(fields)) {
		const fieldLevel = normalizedLevel(rawLevel);
		if (!fieldLevel) {
			return { valid: false, classification, reason: `字段 ${fieldName} 的密级无效` };
		}
		if (fileFloor && (classificationRank(fieldLevel) ?? -1) < (classificationRank(fileFloor) ?? -1)) {
			return {
				valid: false,
				classification,
				reason: `字段 ${fieldName} 的密级低于文件密级 ${fileFloor}`,
			};
		}
		if (
			requireEffectiveCoversFields &&
			(classificationRank(fieldLevel) ?? -1) > (classificationRank(classification) ?? -1)
		) {
			return {
				valid: false,
				classification,
				reason: `密级封存低于字段最高密级 ${fieldLevel}`,
			};
		}
	}
	return { valid: true, classification, reason: "密级封存有效" };
};

export function mergeFileClassificationAdmission(
	next: FileUploadResult,
	previous?: FileUploadResult | null,
): FileUploadResult {
	const nextSeal = sealFrom(next.classificationSeal);
	const previousSeal = sealFrom(previous?.classificationSeal);
	const classification = highestLevel(
		next.classification,
		nextSeal?.effectiveLevel,
		previous?.classification,
		previousSeal?.effectiveLevel,
	);
	const classificationSeal = nextSeal || previousSeal;
	const fileFloor = fileFloorFrom(classification, classificationSeal);
	const nextFields = fieldMapFrom(next.fieldClassifications);
	const previousFields = fieldMapFrom(previous?.fieldClassifications);
	const previousBySourceColumn = new Map<string, string>();
	for (const column of previous?.columns || []) {
		const sourceKey = sourceColumnKey(column);
		const fieldLevel = previousFields[column.name];
		if (sourceKey && fieldLevel) {
			previousBySourceColumn.set(sourceKey, fieldLevel);
		}
	}

	const fieldClassifications: Record<string, ClassificationLevel> = {};
	for (const column of next.columns || []) {
		const level = highestLevel(
			fileFloor,
			nextFields[column.name],
			previousFields[column.name],
			previousBySourceColumn.get(sourceColumnKey(column)),
		);
		if (level) {
			fieldClassifications[column.name] = level;
		}
	}

	return {
		...next,
		classification,
		classificationSeal,
		fieldClassifications,
	};
}

export function setFileFieldClassification(
	file: FileUploadResult,
	fieldName: string,
	requestedLevel: string,
): FileUploadResult {
	const normalized = mergeFileClassificationAdmission(file);
	const fileFloor = fileFloorFrom(normalized.classification, normalized.classificationSeal);
	const level = highestLevel(fileFloor, requestedLevel);
	if (!fieldName.trim() || !level) {
		return normalized;
	}
	return {
		...normalized,
		fieldClassifications: {
			...(normalized.fieldClassifications || {}),
			[fieldName]: level,
		},
	};
}

export function renameFileFieldClassification(
	file: FileUploadResult,
	previousName: string,
	nextName: string,
): FileUploadResult {
	const inheritedLevel = normalizeClassification(fieldMapFrom(file.fieldClassifications)[previousName], undefined);
	const normalized = mergeFileClassificationAdmission(file);
	const cleanNextName = nextName.trim();
	if (!cleanNextName || cleanNextName === previousName) {
		return normalized;
	}
	const fields = { ...(normalized.fieldClassifications || {}) };
	delete fields[previousName];
	if (inheritedLevel) {
		fields[cleanNextName] = highestLevel(fields[cleanNextName], inheritedLevel) || inheritedLevel;
	}
	return { ...normalized, fieldClassifications: fields };
}

export function resolveFileAdmissionState(file: FileUploadResult | null): FileAdmissionState {
	if (!file) {
		return { ready: false, reason: "请先上传文件" };
	}
	const seal = sealFrom(file.classificationSeal);
	if (
		!seal ||
		String(seal.subjectType || "")
			.trim()
			.toUpperCase() !== "FILE"
	) {
		return {
			ready: false,
			classification: normalizedLevel(file.classification),
			reason: "缺少文件密级封存，请重新上传文件",
		};
	}
	const validation = validateSealAndFields(seal, file.fieldClassifications, true, false);
	return {
		ready: validation.valid,
		classification: validation.classification,
		reason: validation.valid ? "文件与字段密级已封存" : validation.reason,
	};
}

export function restoreFileAdmissionFromTask(
	file: FileUploadResult | null,
	task: IngestionTaskDTO,
): FileUploadResult | null {
	if (!file) {
		return null;
	}
	const sourceConfig = asRecord(task.sourceConfig);
	const sourceSeal = sealFrom(sourceConfig.classificationSeal);
	const taskSeal = sealFrom(task.classificationSeal);
	const fileSeal =
		sourceSeal?.subjectType?.toUpperCase() === "FILE"
			? sourceSeal
			: taskSeal?.subjectType?.toUpperCase() === "FILE"
				? taskSeal
				: undefined;
	return mergeFileClassificationAdmission(
		{
			...file,
			classification: highestLevel(sourceConfig.classification, fileSeal?.effectiveLevel, taskSeal?.fileFloor),
			classificationSeal: fileSeal,
			fieldClassifications: fieldMapFrom(sourceConfig.fieldClassifications) as Record<string, ClassificationLevel>,
		},
		{
			...file,
			fieldClassifications: fieldMapFrom(task.fieldClassifications) as Record<string, ClassificationLevel>,
		},
	);
}

export function resolveTaskAdmissionState(task: IngestionTaskDTO | null): TaskAdmissionState {
	if (!task) {
		return { canAdmit: false, canExecute: false, reason: "任务尚未加载" };
	}
	const sourceType = String(task.sourceType || "")
		.trim()
		.toLowerCase();
	const seal = sealFrom(task.classificationSeal);
	const fileTask =
		sourceType.includes("file") ||
		Boolean(seal?.fileFloor) ||
		String(seal?.subjectKey || "").startsWith("ingestion-file:");
	const validation = validateSealAndFields(seal, task.fieldClassifications, fileTask);
	if (!validation.valid) {
		return {
			canAdmit: false,
			canExecute: false,
			classification: validation.classification,
			reason: validation.reason,
		};
	}

	const status = String(task.status || "")
		.trim()
		.toLowerCase();
	if (status === "draft") {
		if (
			fileTask &&
			task.qualityPreCheckEnabled === true &&
			String(task.preCheckStatus || "").trim().toUpperCase() !== "PASSED"
		) {
			return {
				canAdmit: false,
				canExecute: false,
				classification: validation.classification,
				reason: "文件质量预检尚未通过，请先在预检暂存区完成检查",
			};
		}
		return {
			canAdmit: true,
			canExecute: false,
			classification: validation.classification,
			reason: "密级封存有效，待完成准入",
		};
	}
	if (status === "active") {
		return {
			canAdmit: false,
			canExecute: true,
			classification: validation.classification,
			reason: "密级与准入已完成",
		};
	}
	return {
		canAdmit: false,
		canExecute: false,
		classification: validation.classification,
		reason: `任务状态 ${task.status || "unknown"} 不允许准入或执行`,
	};
}
