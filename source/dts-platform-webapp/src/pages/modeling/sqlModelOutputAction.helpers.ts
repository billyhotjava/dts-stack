import { normalizeText } from "@/utils/textUtils";
import type { DbtConfigView, DbtOutputRelation, SqlModel } from "./sqlModeling.types";

export function buildTruncateOutputRelationPreview(
	model: SqlModel | null | undefined,
	dbtConfig: DbtConfigView | null | undefined,
): DbtOutputRelation {
	const schema = normalizeText(model?.schemaName) || normalizeText(dbtConfig?.config?.schema);
	const identifier = normalizeText(model?.alias) || normalizeText(model?.name);
	const selector = normalizeText(model?.dagSelector) || (normalizeText(model?.name) ? `model:${normalizeText(model?.name)}` : "all");

	return {
		modelId: normalizeText(model?.id) || undefined,
		modelName: normalizeText(model?.name) || undefined,
		selector,
		database: normalizeText(dbtConfig?.config?.database) || undefined,
		schema: schema || undefined,
		identifier: identifier || undefined,
		qualifiedName: buildQualifiedName(schema, identifier),
		materialized: normalizeText(model?.materialized) || "table",
		relationType: undefined,
		exists: false,
		truncateAllowed: true,
		downstreamRefCount: 0,
		message: "将通过 dbt run-operation truncate_relation 异步清空产出 relation，执行时会在 dbt 侧检查 relation 是否可清空。",
		checkSkipped: true,
		checkMessage: "清空产出表将直接提交后台任务，不再预先检查目标库 relation。",
	};
}

export function buildRebuildOutputRelationPreview(
	model: SqlModel | null | undefined,
	dbtConfig: DbtConfigView | null | undefined,
): DbtOutputRelation {
	const schema = normalizeText(model?.schemaName) || normalizeText(dbtConfig?.config?.schema);
	const identifier = normalizeText(model?.alias) || normalizeText(model?.name);
	const selector = normalizeText(model?.dagSelector) || (normalizeText(model?.name) ? `model:${normalizeText(model?.name)}` : "all");

	return {
		modelId: normalizeText(model?.id) || undefined,
		modelName: normalizeText(model?.name) || undefined,
		selector,
		database: normalizeText(dbtConfig?.config?.database) || undefined,
		schema: schema || undefined,
		identifier: identifier || undefined,
		qualifiedName: buildQualifiedName(schema, identifier),
		materialized: normalizeText(model?.materialized) || "table",
		relationType: undefined,
		exists: false,
		truncateAllowed: false,
		downstreamRefCount: 0,
		message: "将通过 dbt --full-refresh 安全重建产出 relation，当前步骤不依赖平台直连目标数仓。",
		checkSkipped: true,
		checkMessage: "重建产出表将直接提交 dbt --full-refresh，不再预先检查目标库 relation。",
	};
}

export function resolveOutputActionLoadErrorMessage(error: unknown, action: "truncate" | "rebuild") {
	const fromError = normalizeText((error as any)?.message) || normalizeText((error as any)?.response?.data?.message);
	if (fromError) {
		return fromError;
	}
	if (action === "truncate") {
		return "检查产出表失败，请检查目标数仓连接、JDBC 驱动和数据源配置。";
	}
	return "重建产出表准备失败，请稍后重试。";
}

function buildQualifiedName(schema?: string, identifier?: string) {
	const normalizedIdentifier = normalizeText(identifier);
	if (!normalizedIdentifier) {
		return undefined;
	}
	const normalizedSchema = normalizeText(schema);
	if (!normalizedSchema) {
		return quoteIdentifier(normalizedIdentifier);
	}
	return `${quoteIdentifier(normalizedSchema)}.${quoteIdentifier(normalizedIdentifier)}`;
}

function quoteIdentifier(identifier: string) {
	return `"${identifier.replaceAll('"', "")}"`;
}
