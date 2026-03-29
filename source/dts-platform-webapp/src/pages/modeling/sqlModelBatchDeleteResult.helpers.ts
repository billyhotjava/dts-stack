import type {
	SqlModel,
	SqlModelBatchDeleteResult,
	SqlModelGovernanceExecuteResult,
	SqlModelGovernancePreviewItem,
} from "./sqlModeling.types";

export type SqlModelBatchDeleteResultRow = {
	modelId: string;
	name: string;
	layer: string;
	planName: string;
	modelPath: string;
	status: "success" | "failed" | "skipped";
	message?: string;
};

export type SqlModelBatchDeleteDetail = {
	requested: number;
	deleted: number;
	failed: number;
	skipped: number;
	rows: SqlModelBatchDeleteResultRow[];
};

export type BuildSqlModelBatchDeleteDetailInput = {
	requestedIds: string[];
	models: Array<Pick<SqlModel, "id" | "name" | "layer" | "planName" | "modelPath">>;
	result?: SqlModelBatchDeleteResult | null;
};

export type BuildSqlModelGovernanceDetailInput = {
	requestedIds: string[];
	preview: SqlModelGovernancePreviewItem[];
	result?: SqlModelGovernanceExecuteResult | null;
};

const defaultText = (value?: string, fallback = "-") => {
	const text = String(value || "").trim();
	return text || fallback;
};

export function buildSqlModelBatchDeleteDetail(input: BuildSqlModelBatchDeleteDetailInput): SqlModelBatchDeleteDetail {
	const requestedIds = (input.requestedIds || []).map((id) => String(id || "").trim()).filter(Boolean);
	const modelMap = new Map(
		(input.models || [])
			.filter((model) => String(model?.id || "").trim())
			.map((model) => [String(model?.id || "").trim(), model]),
	);
	const failureMap = new Map(
		(input.result?.failures || [])
			.map((failure) => ({
				modelId: String(failure?.modelId || "").trim(),
				message: defaultText(failure?.message, "删除失败"),
			}))
			.filter((failure) => failure.modelId)
			.map((failure) => [failure.modelId, failure.message]),
	);
	const rows = requestedIds.map((modelId) => {
		const model = modelMap.get(modelId);
		const failedMessage = failureMap.get(modelId);
		return {
			modelId,
			name: defaultText(model?.name, modelId),
			layer: defaultText(model?.layer, "未分层"),
			planName: defaultText(model?.planName, "未归档"),
			modelPath: defaultText(model?.modelPath),
			status: failedMessage ? "failed" : "success",
			message: failedMessage,
		} satisfies SqlModelBatchDeleteResultRow;
	});
	return {
		requested: Number(input.result?.requested ?? requestedIds.length ?? 0),
		deleted: Number(input.result?.deleted ?? rows.filter((row) => row.status === "success").length ?? 0),
		failed: Number(input.result?.failed ?? rows.filter((row) => row.status === "failed").length ?? 0),
		skipped: 0,
		rows,
	};
}

export function buildSqlModelGovernanceDetail(input: BuildSqlModelGovernanceDetailInput): SqlModelBatchDeleteDetail {
	const requestedIds = (input.requestedIds || []).map((id) => String(id || "").trim()).filter(Boolean);
	const previewMap = new Map(
		(input.preview || [])
			.map((item) => [String(item?.modelId || "").trim(), item] as const)
			.filter(([modelId]) => modelId),
	);
	const resultItems = (input.result?.items || []).map((item) => ({
		modelId: String(item?.modelId || "").trim(),
		name: defaultText(item?.name),
		result: defaultText(item?.result).toUpperCase(),
		message: defaultText(item?.message, ""),
	}));
	const itemsById = new Map(resultItems.filter((item) => item.modelId).map((item) => [item.modelId, item]));
	const orderedIds = Array.from(
		new Set([
			...resultItems.map((item) => item.modelId).filter(Boolean),
			...requestedIds,
		]),
	);
	const rows = orderedIds.map((modelId) => {
		const preview = previewMap.get(modelId);
		const item = itemsById.get(modelId);
		const status =
			item?.result === "DELETED"
				? "success"
				: item?.result === "FAILED"
					? "failed"
					: "skipped";
		return {
			modelId,
			name: defaultText(preview?.name, item?.name || modelId),
			layer: defaultText(preview?.layer, "未分层"),
			planName: defaultText(preview?.planName, "未归档"),
			modelPath: defaultText(preview?.modelPath),
			status,
			message: item?.message || (status === "success" ? undefined : "已跳过"),
		} satisfies SqlModelBatchDeleteResultRow;
	});
	return {
		requested: Number(input.result?.requested ?? requestedIds.length ?? 0),
		deleted: Number(input.result?.deleted ?? rows.filter((row) => row.status === "success").length ?? 0),
		failed: Number(input.result?.failed ?? rows.filter((row) => row.status === "failed").length ?? 0),
		skipped: Number(input.result?.skipped ?? rows.filter((row) => row.status === "skipped").length ?? 0),
		rows,
	};
}
