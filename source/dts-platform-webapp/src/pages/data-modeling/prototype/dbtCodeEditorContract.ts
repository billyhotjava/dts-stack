export const dbtEditorLanguage = (path: string) => {
	const lower = path.toLowerCase();
	if (lower.endsWith(".sql")) return "sql";
	if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
	return "plaintext";
};

export const isDbtSaveShortcut = ({ key, ctrlKey, metaKey }: Pick<KeyboardEvent, "key" | "ctrlKey" | "metaKey">) =>
	key.toLowerCase() === "s" && (ctrlKey || metaKey);

export type DbtMarkerInput = {
	severity: string;
	message: string;
	line?: number | null;
	column?: number | null;
};

export type DbtMarkerData = {
	severity: "ERROR" | "WARNING";
	message: string;
	startLineNumber: number;
	startColumn: number;
	endLineNumber: number;
	endColumn: number;
};

export const toDbtMarkerData = (diagnostics: readonly DbtMarkerInput[]): DbtMarkerData[] =>
	diagnostics.flatMap((diagnostic) => {
		if (!Number.isInteger(diagnostic.line) || Number(diagnostic.line) < 1) return [];
		const line = Number(diagnostic.line);
		const column = Number.isInteger(diagnostic.column) && Number(diagnostic.column) > 0 ? Number(diagnostic.column) : 1;
		return [
			{
				severity: diagnostic.severity === "ERROR" ? "ERROR" : "WARNING",
				message: diagnostic.message,
				startLineNumber: line,
				startColumn: column,
				endLineNumber: line,
				endColumn: column + 1,
			},
		];
	});

export const isDbtDraftConflictStatus = (status: number) => status === 409 || status === 412;

export const dbtDraftStatusLabel = ({
	conflict,
	dirty,
	committed,
	validated,
}: {
	conflict: boolean;
	dirty: boolean;
	committed: boolean;
	validated: boolean;
}) => {
	if (conflict) return "版本冲突";
	if (dirty) return "有未保存修改";
	if (committed) return "实现已提交";
	if (validated) return "校验通过";
	return "已保存待校验";
};
