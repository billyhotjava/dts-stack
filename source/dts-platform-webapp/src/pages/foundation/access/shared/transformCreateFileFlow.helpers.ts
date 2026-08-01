import type { ClassificationSealReference, FileUploadResult, IngestionTaskDTO } from "@/api/ingestion";
import type { ClassificationLevel } from "@/utils/classification";
import { normalizeClassification } from "@/utils/classification";
import { normalizeText } from "@/utils/textUtils";
import { mergeFileClassificationAdmission } from "./fileClassificationAdmission.helpers.ts";

type ParsedFileColumn = {
	name: string;
	dataType?: string;
	label?: string;
};

type ParsedFileResult = {
	hostPath?: string;
	containerPath?: string;
	fileType?: string;
	csvPath?: string;
	csvContainerPath?: string;
	columns?: ParsedFileColumn[];
	sheetName?: string;
	sheetIndex?: number;
	preview?: string[][];
	rowCount?: number;
	errorCount?: number;
	errorPath?: string;
	errorContainerPath?: string;
	delimiter?: string;
	classification?: ClassificationLevel;
	classificationSeal?: ClassificationSealReference;
	fieldClassifications?: Record<string, ClassificationLevel>;
};

type SheetOption = {
	index: number;
	name: string;
};

const normalizeIdentifier = (value?: string) => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return "";
	let safe = text
		.replace(/[^a-z0-9_]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.replace(/_+/g, "_");
	if (!safe) return "";
	if (/^\d/.test(safe)) {
		safe = `col_${safe}`;
	}
	return safe;
};

const resolveFileTypeFromName = (filename?: string) => {
	const ext = normalizeText(filename).split(".").pop()?.toLowerCase();
	if (ext === "xlsx" || ext === "xls") return "excel";
	if (ext === "csv" || ext === "txt") return "csv";
	return "file";
};

const buildFileBaseName = (filename?: string) => {
	const base = normalizeText(filename || "file").replace(/\.[^.]+$/, "");
	const safe = normalizeIdentifier(base);
	return safe || "file";
};

export function buildTransformFileUploadResult(
	fileName: string,
	batchCode: string,
	fileId: string,
	sheets: SheetOption[] | undefined,
	parseResult: ParsedFileResult,
	selectedSheet?: { index?: number; name?: string },
): FileUploadResult {
	const columns = (parseResult.columns || [])
		.map((col) => ({
			name: normalizeText(col.name),
			type: normalizeText(col.dataType) || "string",
			label: normalizeText(col.label),
		}))
		.filter((col) => col.name);

	const hostPath = normalizeText(parseResult.hostPath) || parseResult.csvPath || "";
	const containerPath = normalizeText(parseResult.containerPath) || parseResult.csvContainerPath || "";

	return {
		hostPath,
		containerPath,
		fileType: parseResult.fileType || resolveFileTypeFromName(fileName),
		sourceFileType: resolveFileTypeFromName(fileName),
		columns,
		originalName: fileName,
		fileId,
		batchCode,
		sheets,
		sheetName: parseResult.sheetName || selectedSheet?.name,
		sheetIndex: parseResult.sheetIndex ?? selectedSheet?.index,
		errorPath: parseResult.errorPath,
		errorContainerPath: parseResult.errorContainerPath,
		delimiter: parseResult.delimiter,
		preview: parseResult.preview,
		rowCount: parseResult.rowCount,
		errorCount: parseResult.errorCount,
		csvPath: parseResult.csvPath || hostPath,
		csvContainerPath: parseResult.csvContainerPath || containerPath,
		classification: parseResult.classification,
		classificationSeal: parseResult.classificationSeal,
		fieldClassifications: parseResult.fieldClassifications,
	};
}

export function buildTransformFileSourceConfig(
	fileUploadResult: FileUploadResult,
	autoId: boolean,
): Record<string, any> {
	const normalized = mergeFileClassificationAdmission(fileUploadResult);
	return {
		_fileId: normalized.fileId,
		_filePath: normalized.hostPath,
		_containerPath: normalized.containerPath,
		_keyVersion: normalized.keyVersion,
		_encrypted: normalized.encrypted,
		_fileHash: normalized.fileHash,
		_fileSize: normalized.fileSize,
		_fileType: normalized.fileType || "csv",
		_fileColumns: normalized.columns,
		_originalName: normalized.originalName,
		_autoId: autoId,
		classification: normalized.classification,
		classificationSeal: normalized.classificationSeal,
		fieldClassifications: normalized.fieldClassifications,
	};
}

export function buildTransformFileTaskAdmissionFields(
	fileUploadResult: FileUploadResult,
): Pick<IngestionTaskDTO, "classificationSeal" | "fieldClassifications"> {
	const normalized = mergeFileClassificationAdmission(fileUploadResult);
	return {
		classificationSeal: normalized.classificationSeal
			? { ...normalized.classificationSeal, fileFloor: normalized.classification }
			: undefined,
		fieldClassifications: normalized.fieldClassifications,
	};
}

type TransformFileUploadInput = {
	file: File;
	classification?: string;
	previewLimit: number;
	previousFile?: FileUploadResult | null;
	preserveSavedMapping?: boolean;
	uploadAndParse: (
		file: File,
		options: { classification: ClassificationLevel; previewLimit: number },
	) => Promise<FileUploadResult>;
};

export async function uploadTransformFileWithAdmission({
	file,
	classification: classificationValue,
	previewLimit,
	previousFile,
	preserveSavedMapping,
	uploadAndParse,
}: TransformFileUploadInput): Promise<FileUploadResult> {
	const classification = normalizeClassification(classificationValue, undefined);
	if (!classification) {
		throw new Error("请先选择文件密级");
	}
	const uploaded = mergeFileClassificationAdmission(
		await uploadAndParse(file, { classification, previewLimit }),
		previousFile,
	);
	if (!preserveSavedMapping || !previousFile?.columns?.length || !uploaded.columns?.length) {
		return uploaded;
	}
	const previousByLabel = new Map<string, FileUploadResult["columns"][number]>();
	for (const column of previousFile.columns) {
		const label = normalizeText(column.label).toLowerCase();
		if (label) previousByLabel.set(label, column);
	}
	const columns = uploaded.columns.map((column) => {
		const previous = previousByLabel.get(normalizeText(column.label || column.name).toLowerCase());
		return previous?.name && (previous as Record<string, any>)._odsMatched
			? { ...column, name: previous.name, _odsMatched: true }
			: column;
	});
	return mergeFileClassificationAdmission({ ...uploaded, columns }, previousFile);
}

export function suggestTransformFileTableName(
	currentValue?: string,
	syncPrefix?: string,
	originalName?: string,
): string | undefined {
	if (normalizeText(currentValue)) {
		return undefined;
	}
	const prefix = normalizeText(syncPrefix);
	const baseName = buildFileBaseName(originalName);
	return `${prefix}${baseName}` || undefined;
}
