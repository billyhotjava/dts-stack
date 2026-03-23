import type { FileUploadResult } from "@/api/ingestion";
import { suggestTransformFileTableName } from "./transformCreateFileFlow.helpers";

type FilePostParseOutcomeInput = {
	parsed: FileUploadResult;
	currentFileTableName?: string;
	syncPrefix?: string;
	reason: "upload" | "sheet-change";
	sheetName?: string;
};

export type FilePostParseOutcome = {
	readerType: "txtfilereader";
	shouldResetOds: true;
	suggestedFileTableName?: string;
	successMessage: string;
};

export function buildFilePostParseOutcome(input: FilePostParseOutcomeInput): FilePostParseOutcome {
	return {
		readerType: "txtfilereader",
		shouldResetOds: true,
		suggestedFileTableName: suggestTransformFileTableName(
			input.currentFileTableName,
			input.syncPrefix,
			input.parsed.originalName
		),
		successMessage:
			input.reason === "sheet-change"
				? `已切换到 ${input.sheetName}，检测到 ${input.parsed.columns?.length || 0} 列`
				: `文件解析成功，检测到 ${input.parsed.columns?.length || 0} 列`,
	};
}
