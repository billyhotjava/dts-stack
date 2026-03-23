import type { FileUploadResult } from "@/api/ingestion";

type SheetOption = { index: number; name: string };

type ParsedFileColumn = {
	name: string;
	dataType?: string;
	label?: string;
};

type ParsedFileResult = {
	csvPath: string;
	csvContainerPath: string;
	errorPath?: string;
	errorContainerPath?: string;
	delimiter?: string;
	columns: ParsedFileColumn[];
	preview?: string[][];
	rowCount?: number;
	errorCount?: number;
	sheetName?: string;
};

const normalizeText = (value?: string) => String(value || "").trim();

const normalizeIdentifier = (value?: string) => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return "";
	let safe = text.replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").replace(/_+/g, "_");
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
	selectedSheet?: { index?: number; name?: string }
): FileUploadResult {
	const columns = (parseResult.columns || [])
		.map((col) => ({
			name: normalizeText(col.name),
			type: normalizeText(col.dataType) || "string",
			label: normalizeText(col.label),
		}))
		.filter((col) => col.name);

	return {
		hostPath: parseResult.csvPath,
		containerPath: parseResult.csvContainerPath,
		fileType: "csv",
		sourceFileType: resolveFileTypeFromName(fileName),
		columns,
		originalName: fileName,
		fileId,
		batchCode,
		sheets,
		sheetName: parseResult.sheetName || selectedSheet?.name,
		sheetIndex: selectedSheet?.index,
		csvPath: parseResult.csvPath,
		csvContainerPath: parseResult.csvContainerPath,
		errorPath: parseResult.errorPath,
		errorContainerPath: parseResult.errorContainerPath,
		delimiter: parseResult.delimiter,
		preview: parseResult.preview,
		rowCount: parseResult.rowCount,
		errorCount: parseResult.errorCount,
	};
}

export function suggestTransformFileTableName(currentValue?: string, syncPrefix?: string, originalName?: string): string | undefined {
	if (normalizeText(currentValue)) {
		return undefined;
	}
	const prefix = normalizeText(syncPrefix);
	const baseName = buildFileBaseName(originalName);
	return `${prefix}${baseName}` || undefined;
}
