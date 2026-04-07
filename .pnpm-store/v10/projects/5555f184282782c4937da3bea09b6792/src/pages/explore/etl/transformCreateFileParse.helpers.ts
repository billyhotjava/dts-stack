import type { FileUploadResult } from "@/api/ingestion";
import type { ExcelImportPrepareResponse, ExcelSheetInfo } from "@/api/services/dataSourcesService";

type FileParseInput = {
	fileId: string;
	fileName: string;
	batchCode: string;
	sheets: ExcelSheetInfo[] | undefined;
	selectedSheet?: { index?: number; name?: string };
	previewLimit: number;
};

export function buildPreparedFileParseInput(
	prepare: ExcelImportPrepareResponse,
	previewLimit: number
): FileParseInput {
	const sheets = prepare.sheets || [];
	return {
		fileId: prepare.fileId,
		fileName: prepare.fileName,
		batchCode: prepare.batchCode,
		sheets,
		selectedSheet: sheets.length ? sheets[0] : undefined,
		previewLimit,
	};
}

export function buildRefreshFileParseInput(
	fileUploadResult: FileUploadResult | null,
	previewLimit: number
): FileParseInput | null {
	if (!fileUploadResult?.fileId) {
		return null;
	}
	return {
		fileId: fileUploadResult.fileId,
		fileName: fileUploadResult.originalName,
		batchCode: fileUploadResult.batchCode || "",
		sheets: fileUploadResult.sheets,
		selectedSheet: fileUploadResult.sheetName
			? { index: fileUploadResult.sheetIndex, name: fileUploadResult.sheetName }
			: undefined,
		previewLimit,
	};
}

export function buildSheetChangeFileParseInput(
	fileUploadResult: FileUploadResult | null,
	sheetIndex: number,
	previewLimit: number
): FileParseInput | null {
	if (!fileUploadResult?.fileId) {
		return null;
	}
	const targetSheet = fileUploadResult.sheets?.find((item) => item.index === sheetIndex);
	if (!targetSheet) {
		return null;
	}
	return {
		fileId: fileUploadResult.fileId,
		fileName: fileUploadResult.originalName,
		batchCode: fileUploadResult.batchCode || "",
		sheets: fileUploadResult.sheets,
		selectedSheet: { index: targetSheet.index, name: targetSheet.name },
		previewLimit,
	};
}
