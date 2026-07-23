import type { WarehousePlanSourceBindingView, WarehousePlanSourceType } from "../../api/warehousePlanApi.ts";
import type { ModelSpecSourceKind } from "./modelSpecV2Contract.ts";
import type { ModelSpecSourceDraft } from "./modelSpecWorkbench.ts";

const SOURCE_TYPE_LABELS: Record<WarehousePlanSourceType, string> = {
	CONNECTION_TABLE: "连接中的表",
	CATALOG_TABLE: "资产目录表",
	EXCEL_FILE: "Excel 文件",
	DBT_NODE: "dbt 节点",
};

const SOURCE_KIND_BY_TYPE: Record<WarehousePlanSourceType, ModelSpecSourceKind> = {
	CONNECTION_TABLE: "TABLE",
	CATALOG_TABLE: "TABLE",
	EXCEL_FILE: "DATASET",
	DBT_NODE: "DBT_MODEL",
};

export type ModelSpecSourceChoice = {
	value: string;
	label: string;
	disabled?: boolean;
	kind: ModelSpecSourceKind;
	ref: string;
	resolvedVersion: string;
	sourceType?: WarehousePlanSourceType;
};

export type ModelSpecSourceInventoryState = "READY" | "EMPTY" | "FORBIDDEN" | "UNAVAILABLE";

const text = (value: string | null | undefined): string => value?.trim() || "";

const sourceLabel = (binding: WarehousePlanSourceBindingView): string => {
	const displayName = text(binding.displayName);
	const sourceId = text(binding.sourceId);
	const identity = displayName && displayName !== sourceId ? `${displayName}（${sourceId}）` : displayName || sourceId;
	return `${identity} · ${SOURCE_TYPE_LABELS[binding.sourceType]}`;
};

export function selectableModelSpecSources(
	bindings: readonly WarehousePlanSourceBindingView[] | null | undefined,
): ModelSpecSourceChoice[] {
	return (bindings ?? [])
		.filter(
			(binding) =>
				binding.confirmationStatus === "CONFIRMED" &&
				binding.freshness === "CURRENT" &&
				binding.resolutionStatus === "AVAILABLE" &&
				Boolean(text(binding.bindingId)) &&
				Boolean(text(binding.sourceId)) &&
				Boolean(text(binding.resolvedVersion)),
		)
		.map((binding) => ({
			value: text(binding.bindingId),
			label: sourceLabel(binding),
			kind: SOURCE_KIND_BY_TYPE[binding.sourceType],
			ref: text(binding.sourceId),
			resolvedVersion: text(binding.resolvedVersion),
			sourceType: binding.sourceType,
		}));
}

export function modelSpecSourceInventoryState(
	bindings: readonly WarehousePlanSourceBindingView[] | null | undefined,
): ModelSpecSourceInventoryState {
	if (!bindings || bindings.length === 0) return "EMPTY";
	if (selectableModelSpecSources(bindings).length > 0) return "READY";
	if (bindings.some((binding) => binding.resolutionStatus === "FORBIDDEN")) return "FORBIDDEN";
	return "UNAVAILABLE";
}

export function modelSpecSourcePermissionDenied(error: unknown): boolean {
	if (!error || typeof error !== "object") return false;
	const status = (error as { response?: { status?: unknown } }).response?.status;
	return status === 401 || status === 403;
}

export function modelSpecSourceDraftFromChoice(
	choice: ModelSpecSourceChoice,
	previous?: Partial<ModelSpecSourceDraft>,
): ModelSpecSourceDraft {
	return {
		kind: choice.kind,
		ref: choice.ref,
		layer: previous?.layer || "ODS",
		role: previous?.role || "PRIMARY",
		sourceBindingId: choice.value,
		resolvedVersion: choice.resolvedVersion,
		alias: previous?.alias,
		joinType: previous?.joinType,
		joinExpression: previous?.joinExpression,
	};
}

export function modelSpecSourceMatchesChoice(
	source: ModelSpecSourceDraft,
	choice: ModelSpecSourceChoice | null | undefined,
): boolean {
	return Boolean(
		choice &&
			!choice.disabled &&
			text(source.sourceBindingId) === choice.value &&
			source.kind === choice.kind &&
			text(source.ref) === choice.ref &&
			text(source.resolvedVersion) === choice.resolvedVersion,
	);
}

export function modelSpecSourcesAreCurrent(
	choices: readonly ModelSpecSourceChoice[],
	existingSources: readonly ModelSpecSourceDraft[] | null | undefined,
): boolean {
	const selectableByBindingId = new Map(
		choices.filter((choice) => !choice.disabled).map((choice) => [choice.value, choice]),
	);
	return (existingSources ?? []).every((source) =>
		modelSpecSourceMatchesChoice(source, selectableByBindingId.get(text(source.sourceBindingId))),
	);
}

export function withPinnedExistingSources(
	choices: readonly ModelSpecSourceChoice[],
	existingSources: readonly ModelSpecSourceDraft[] | null | undefined,
): ModelSpecSourceChoice[] {
	const result = [...choices];
	const existingChoiceIds = new Set(result.map((choice) => choice.value));
	for (const source of existingSources ?? []) {
		const bindingId = text(source.sourceBindingId);
		if (!bindingId || existingChoiceIds.has(bindingId)) continue;
		result.push({
			value: bindingId,
			label: `${text(source.ref) || "已保存来源"}（当前不可用，请先修复来源盘点）`,
			disabled: true,
			kind: source.kind,
			ref: text(source.ref),
			resolvedVersion: text(source.resolvedVersion),
		});
		existingChoiceIds.add(bindingId);
	}
	return result;
}
