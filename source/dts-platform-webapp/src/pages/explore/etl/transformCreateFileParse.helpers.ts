import type { FileUploadResult } from "@/api/ingestion";

type FileParseInput = {
	fileId: string;
	fileName: string;
	batchCode: string;
	sheets: FileUploadResult["sheets"] | undefined;
	selectedSheet?: { index?: number; name?: string };
	previewLimit: number;
};

export function buildPreparedFileParseInput(
	prepare: FileUploadResult,
	previewLimit: number
): FileParseInput {
	const fileId = prepare.fileId || prepare.batchCode || "";
	const fileName = prepare.originalName || "";
	const batchCode = prepare.batchCode || "";
	const sheets = prepare.sheets || [];
	return {
		fileId,
		fileName,
		batchCode,
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
