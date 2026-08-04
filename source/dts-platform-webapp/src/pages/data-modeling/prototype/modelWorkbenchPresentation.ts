import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import { MODEL_KIND_CONFIG, type ModelDraft } from "./services/modelWorkbenchService";

const UNCONFIGURED = "未配置";

export const DIMENSION_STORAGE_OPTIONS = [
	{ value: "table", label: "表存储" },
	{ value: "incremental", label: "增量表" },
	{ value: "view", label: "视图" },
	{ value: "ephemeral", label: "临时模型" },
] as const;

export type DimensionFormPresentation = {
	warehouseLayer: string;
	businessCategory: string;
	tableNamingRule: string;
	lifecycle: string;
	owner: string;
};

type DimensionFormPresentationInput = {
	draft: ModelDraft;
	domains: CatalogDomain[];
	definition: DimensionDefinitionView | null;
	currentOwnerId: string;
};

const configured = (value: string | null | undefined) => value?.trim() || UNCONFIGURED;

export const isDimensionDraft = (draft: ModelDraft): boolean =>
	MODEL_KIND_CONFIG[draft.createKind].modelType === "DIMENSION";

export function resolveDimensionFormPresentation({
	draft,
	domains,
	definition,
	currentOwnerId,
}: DimensionFormPresentationInput): DimensionFormPresentation {
	const domain = domains.find((item) => item.code === draft.domainId);
	const businessCategory = configured(domains.find((item) => item.code === domain?.parentCode)?.name);
	const retentionDays = draft.base?.implementationPolicy?.retentionDays;
	const owner = definition?.ownerId || (!draft.base ? currentOwnerId : "");

	return {
		warehouseLayer: "公共层 / 维度层",
		businessCategory,
		tableNamingRule: /^[a-z][a-z0-9_]*$/.test(draft.physicalName)
			? "DIM 表命名规范"
			: "不符合 DIM 表命名规范",
		lifecycle: retentionDays == null ? UNCONFIGURED : `${retentionDays} 天`,
		owner: configured(owner),
	};
}

export function modelDraftFingerprint(draft: ModelDraft): string {
	return JSON.stringify({
		domainId: draft.domainId,
		name: draft.name,
		description: draft.description,
		physicalName: draft.physicalName,
		materialization: draft.materialization,
		grainStatement: draft.grainStatement,
		fields: draft.fields,
		partitionFields: draft.partitionFields,
		loadStrategy: draft.loadStrategy,
		scdType: draft.scdType,
		reuseScope: draft.reuseScope,
		dimensionDefinitionId: draft.dimensionDefinitionId,
		standardBindings: draft.standardBindings,
	});
}
