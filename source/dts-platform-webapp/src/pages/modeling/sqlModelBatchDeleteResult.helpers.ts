import type { SqlModel, SqlModelBatchDeleteResult } from "./sqlModeling.types";

export type SqlModelBatchDeleteResultRow = {
	modelId: string;
	name: string;
	layer: string;
	planName: string;
	modelPath: string;
	status: "success" | "failed";
	message?: string;
};

export type SqlModelBatchDeleteDetail = {
	requested: number;
	deleted: number;
	failed: number;
	rows: SqlModelBatchDeleteResultRow[];
};

export type BuildSqlModelBatchDeleteDetailInput = {
	requestedIds: string[];
	models: Array<Pick<SqlModel, "id" | "name" | "layer" | "planName" | "modelPath">>;
	result?: SqlModelBatchDeleteResult | null;
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
		rows,
	};
}
