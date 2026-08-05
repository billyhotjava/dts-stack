import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import {
	type ConceptDimensionDraft,
	isConceptDimensionDraft,
	isDimensionTableDraft,
	type ModelDraft,
	type ModelSpecDraft,
} from "./services/modelWorkbenchService";

export { isConceptDimensionDraft, isDimensionTableDraft };

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
	draft: ModelSpecDraft;
	domains: CatalogDomain[];
	definition: DimensionDefinitionView | null;
	currentOwnerId: string;
};

export type ConceptDimensionPresentation = {
	warehouseLayer: string;
	businessCategory: string;
	systemCode: string;
};

type ConceptDimensionPresentationInput = {
	draft: ConceptDimensionDraft;
	domains: CatalogDomain[];
};

const configured = (value: string | null | undefined) => value?.trim() || UNCONFIGURED;

const businessCategoryFor = (domainId: string, domains: CatalogDomain[]) => {
	const domain = domains.find((item) => item.id === domainId);
	return configured(domains.find((item) => item.code === domain?.parentCode)?.name);
};

export function resolveConceptDimensionPresentation({
	draft,
	domains,
}: ConceptDimensionPresentationInput): ConceptDimensionPresentation {
	return {
		warehouseLayer: "公共层 / 维度层",
		businessCategory: businessCategoryFor(draft.domainId, domains),
		systemCode: draft.definitionBase?.systemCode || "保存后生成",
	};
}

export function resolveDimensionFormPresentation({
	draft,
	domains,
	definition,
	currentOwnerId,
}: DimensionFormPresentationInput): DimensionFormPresentation {
	const retentionDays = draft.base?.implementationPolicy?.retentionDays;
	const owner = definition?.ownerId || (!draft.base ? currentOwnerId : "");

	return {
		warehouseLayer: "公共层 / 维度层",
		businessCategory: businessCategoryFor(draft.domainId, domains),
		tableNamingRule: /^[a-z][a-z0-9_]*$/.test(draft.physicalName) ? "DIM 表命名规范" : "不符合 DIM 表命名规范",
		lifecycle: retentionDays == null ? UNCONFIGURED : `${retentionDays} 天`,
		owner: configured(owner),
	};
}

export function modelDraftFingerprint(draft: ModelDraft): string {
	if (isConceptDimensionDraft(draft)) {
		return JSON.stringify({
			domainId: draft.domainId,
			name: draft.name,
			description: draft.description,
			reuseScope: draft.reuseScope,
		});
	}
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
