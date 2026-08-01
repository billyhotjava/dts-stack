import type { IngestionTaskDTO } from "@/api/ingestion";
import {
	type ClassificationLevel,
	classificationRank,
	normalizeClassification,
} from "@/utils/classification";
import { resolveTaskAdmissionState } from "./fileClassificationAdmission.helpers.ts";

export type TaskAdmissionBasis = {
	status: "admitted" | "pending" | "blocked";
	statusLabel: "已准入" | "待确认" | "不可准入";
	statusReason: string;
	evidenceStatus: "complete" | "incomplete" | "unverifiable";
	evidenceStatusLabel: "依据完整" | "依据不完整" | "字段覆盖不可核验";
	evidenceReason: string;
	effectiveLevel?: ClassificationLevel;
	sourceLabel: string;
	fieldCoverageLabel: string;
	coveredFieldCount: number;
	totalFieldCount?: number;
	highestFieldLevel?: ClassificationLevel;
	snapshotVersion?: number;
	sealedAt?: string;
};

const asRecord = (value: unknown): Record<string, unknown> =>
	value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};

const normalizeOptionalLevel = (value: unknown): ClassificationLevel | undefined => {
	if (typeof value !== "string" || !value.trim()) return undefined;
	const publicFallback = normalizeClassification(value, "PUBLIC");
	const confidentialFallback = normalizeClassification(value, "CONFIDENTIAL");
	return publicFallback === confidentialFallback ? publicFallback : undefined;
};

const columnName = (value: unknown): string => {
	if (typeof value === "string") return value.trim();
	const record = asRecord(value);
	return typeof record.name === "string" ? record.name.trim() : "";
};

const sourceColumnNames = (task: IngestionTaskDTO): string[] => {
	const sourceConfig = asRecord(task.sourceConfig);
	const rawColumns = Array.isArray(sourceConfig._fileColumns)
		? sourceConfig._fileColumns
		: Array.isArray(sourceConfig.columns)
			? sourceConfig.columns
			: [];
	return Array.from(new Set(rawColumns.map(columnName).filter(Boolean)));
};

type FieldLevelEvidence = {
	levels: Map<string, ClassificationLevel>;
	hasInvalidEntries: boolean;
};

const validFieldLevels = (task: IngestionTaskDTO): FieldLevelEvidence => {
	const levels = new Map<string, ClassificationLevel>();
	let hasInvalidEntries = false;
	for (const [name, rawLevel] of Object.entries(asRecord(task.fieldClassifications))) {
		const level = normalizeOptionalLevel(rawLevel);
		if (name.trim() && level) {
			levels.set(name.trim(), level);
		} else {
			hasInvalidEntries = true;
		}
	}
	return { levels, hasInvalidEntries };
};

const highestFieldLevel = (levels: Iterable<ClassificationLevel>): ClassificationLevel | undefined => {
	let highest: ClassificationLevel | undefined;
	for (const level of levels) {
		if (!highest || (classificationRank(level) ?? -1) > (classificationRank(highest) ?? -1)) {
			highest = level;
		}
	}
	return highest;
};

type SealEvidenceValidation = {
	valid: boolean;
	reason: string;
	effectiveLevel?: ClassificationLevel;
	fileFloor?: ClassificationLevel;
	snapshotVersion?: number;
	sealedAt?: string;
};

const validateSealEvidence = (seal: Record<string, unknown>): SealEvidenceValidation => {
	const effectiveLevel = normalizeOptionalLevel(seal.effectiveLevel);
	const fileFloor = normalizeOptionalLevel(seal.fileFloor);
	const rawVersion = seal.snapshotVersion;
	const snapshotVersion =
		typeof rawVersion === "number" && Number.isInteger(rawVersion) && rawVersion >= 0 ? rawVersion : undefined;
	const rawSealedAt = typeof seal.sealedAt === "string" ? seal.sealedAt.trim() : "";
	const sealedAt = rawSealedAt && Number.isFinite(Date.parse(rawSealedAt)) ? rawSealedAt : undefined;
	const result = { effectiveLevel, fileFloor, snapshotVersion, sealedAt };

	if (Object.keys(seal).length === 0) {
		return { ...result, valid: false, reason: "缺少密级封存" };
	}
	if (
		typeof seal.sealId !== "string" ||
		!seal.sealId.trim() ||
		typeof seal.subjectType !== "string" ||
		!seal.subjectType.trim() ||
		typeof seal.subjectKey !== "string" ||
		!seal.subjectKey.trim()
	) {
		return { ...result, valid: false, reason: "密级封存标识不完整" };
	}
	if (!effectiveLevel) {
		return { ...result, valid: false, reason: "密级封存的有效密级无效" };
	}
	if (snapshotVersion === undefined) {
		return { ...result, valid: false, reason: "密级封存版本无效" };
	}
	const checksum = typeof seal.checksum === "string" ? seal.checksum.trim() : "";
	if (checksum.length < 16 || checksum.length > 128) {
		return { ...result, valid: false, reason: "密级封存校验值无效" };
	}
	if (!sealedAt) {
		return { ...result, valid: false, reason: "密级封存时间无效" };
	}
	const fileFloorProvided =
		seal.fileFloor !== undefined && seal.fileFloor !== null && String(seal.fileFloor).trim().length > 0;
	if (fileFloorProvided && !fileFloor) {
		return { ...result, valid: false, reason: "文件密级下限无效" };
	}
	return { ...result, valid: true, reason: "密级封存有效" };
};

const validateFieldEvidence = (
	task: IngestionTaskDTO,
	seal: Record<string, unknown>,
	fields: Map<string, ClassificationLevel>,
	effectiveLevel: ClassificationLevel,
	fileFloor?: ClassificationLevel,
): string | undefined => {
	const sourceType = String(task.sourceType || "")
		.trim()
		.toLowerCase();
	const subjectType = String(seal.subjectType || "")
		.trim()
		.toUpperCase();
	const subjectKey = String(seal.subjectKey || "")
		.trim()
		.toLowerCase();
	const requiresFileFields =
		sourceType.includes("file") ||
		Boolean(fileFloor) ||
		subjectType === "FILE" ||
		subjectKey.startsWith("ingestion-file:");
	if (requiresFileFields && fields.size === 0) {
		return "文件任务缺少字段密级封存";
	}
	for (const [fieldName, fieldLevel] of fields) {
		if (fileFloor && (classificationRank(fieldLevel) ?? -1) < (classificationRank(fileFloor) ?? -1)) {
			return `字段 ${fieldName} 的密级低于文件密级 ${fileFloor}`;
		}
		if ((classificationRank(fieldLevel) ?? -1) > (classificationRank(effectiveLevel) ?? -1)) {
			return `密级封存低于字段最高密级 ${fieldLevel}`;
		}
	}
	return undefined;
};

const resolveSourceLabel = (seal: Record<string, unknown>): string => {
	const subjectType = String(seal.subjectType || "")
		.trim()
		.toUpperCase();
	const subjectKey = String(seal.subjectKey || "")
		.trim()
		.toLowerCase();
	const fileFloor = normalizeOptionalLevel(seal.fileFloor);
	if (fileFloor || subjectType === "FILE" || subjectKey.startsWith("ingestion-file:")) {
		return "文件上传声明";
	}
	if (subjectType === "DATA_SOURCE" || subjectKey.startsWith("data-source:")) {
		return "数据源密级继承";
	}
	return Object.keys(seal).length ? "治理封存快照" : "未提供密级封存";
};

export function resolveTaskAdmissionBasis(task: IngestionTaskDTO): TaskAdmissionBasis {
	const admission = resolveTaskAdmissionState(task);
	const seal = asRecord(task.classificationSeal);
	const fields = validFieldLevels(task);
	const columns = sourceColumnNames(task);
	const columnSet = new Set(columns);
	const coveredFieldCount = columns.length
		? columns.filter((name) => fields.levels.has(name)).length
		: fields.levels.size;
	const totalFieldCount = columns.length || undefined;
	const hasUnmatchedFields =
		columns.length > 0 && Array.from(fields.levels.keys()).some((name) => !columnSet.has(name));
	const fieldCoverageLabel = totalFieldCount
		? `${coveredFieldCount}/${totalFieldCount}`
		: fields.levels.size
			? `已封存 ${fields.levels.size} 项（总数不可核验）`
			: "不可核验（仅任务级）";

	const status = admission.canExecute ? "admitted" : admission.canAdmit ? "pending" : "blocked";
	const statusLabel = status === "admitted" ? "已准入" : status === "pending" ? "待确认" : "不可准入";
	const sealEvidence = validateSealEvidence(seal);
	const fieldEvidenceReason =
		sealEvidence.valid && sealEvidence.effectiveLevel
			? validateFieldEvidence(task, seal, fields.levels, sealEvidence.effectiveLevel, sealEvidence.fileFloor)
			: undefined;
	const evidenceStatus =
		!sealEvidence.valid || fields.hasInvalidEntries || Boolean(fieldEvidenceReason) || hasUnmatchedFields
			? "incomplete"
			: totalFieldCount === undefined
				? "unverifiable"
				: coveredFieldCount === totalFieldCount
					? "complete"
					: "incomplete";
	const evidenceStatusLabel =
		evidenceStatus === "complete" ? "依据完整" : evidenceStatus === "unverifiable" ? "字段覆盖不可核验" : "依据不完整";
	const evidenceReason = !sealEvidence.valid
		? sealEvidence.reason
		: fields.hasInvalidEntries
			? "存在无效字段密级"
			: fieldEvidenceReason
				? fieldEvidenceReason
				: hasUnmatchedFields
					? "字段密级包含未匹配的源字段"
					: totalFieldCount === undefined
						? "未提供可核验的字段全集"
						: coveredFieldCount !== totalFieldCount
							? "字段密级覆盖不完整"
							: "密级封存与字段覆盖完整";

	return {
		status,
		statusLabel,
		statusReason: admission.reason,
		evidenceStatus,
		evidenceStatusLabel,
		evidenceReason,
		effectiveLevel: sealEvidence.effectiveLevel,
		sourceLabel: resolveSourceLabel(seal),
		fieldCoverageLabel,
		coveredFieldCount,
		totalFieldCount,
		highestFieldLevel: highestFieldLevel(fields.levels.values()),
		snapshotVersion: sealEvidence.snapshotVersion,
		sealedAt: sealEvidence.sealedAt,
	};
}
