import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
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
	const implementationRetention = draft.implementationBase?.settings?.retentionDays;
	const retentionDays =
		typeof implementationRetention === "number"
			? implementationRetention
			: draft.base?.implementationPolicy?.retentionDays;
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
			attributes: draft.attributes,
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
		implementationInputMode: draft.implementationInputMode,
		generationStrategyType: draft.generationStrategyType,
	});
}

export type WorkbenchCatalogGroup = {
	key: string;
	label: string;
	kind: "category" | "domain" | "unassigned";
	models: ModelSpecView[];
	dimensions: DimensionDefinitionView[];
};

export type WorkbenchCatalogGroupingInput = {
	dataDomains: CatalogDomain[];
	categoryRoots: CatalogDomain[];
	domainByCode: Map<string, CatalogDomain>;
	effectiveView: "domain" | "category";
	visibleModels: ModelSpecView[];
	visibleDimensions: DimensionDefinitionView[];
};

/**
 * DataWorks 对齐的目录分组：数据域视角按数据域分组；业务分类视角按根业务分类分组
 * （模型挂在子数据域时通过 parentCode 归到父分类）。只保留有模型的分组，空分组不渲染。
 */
export function buildWorkbenchCatalogGroups({
	dataDomains,
	categoryRoots,
	domainByCode,
	effectiveView,
	visibleModels,
	visibleDimensions,
}: WorkbenchCatalogGroupingInput): WorkbenchCatalogGroup[] {
	const byKey = new Map<string, WorkbenchCatalogGroup>();
	const ensure = (key: string, label: string, kind: WorkbenchCatalogGroup["kind"]): WorkbenchCatalogGroup => {
		const existing = byKey.get(key);
		if (existing) return existing;
		const created: WorkbenchCatalogGroup = { key, label, kind, models: [], dimensions: [] };
		byKey.set(key, created);
		return created;
	};
	if (effectiveView === "domain") {
		for (const domain of dataDomains) ensure(domain.id, domain.name, "domain");
	}
	for (const root of categoryRoots) ensure(root.id, root.name, "category");
	const place = (domainId: string): WorkbenchCatalogGroup => {
		if (effectiveView === "domain") {
			const domain = dataDomains.find((item) => item.id === domainId);
			if (domain) {
				return ensure(domain.id, domain.name, "domain");
			}
			const root = categoryRoots.find((item) => item.id === domainId);
			if (root) {
				return ensure(root.id, root.name, "category");
			}
		} else {
			const root = categoryRoots.find((item) => item.id === domainId);
			if (root) {
				return ensure(root.id, root.name, "category");
			}
			const domain = dataDomains.find((item) => item.id === domainId);
			const parent = domain?.parentCode ? domainByCode.get(domain.parentCode) : undefined;
			if (parent) {
				return ensure(parent.id, parent.name, "category");
			}
		}
		return ensure("__unassigned__", "未归属数据域", "unassigned");
	};
	for (const model of visibleModels) {
		place(model.domainId || "").models.push(model);
	}
	for (const dimension of visibleDimensions) {
		place(dimension.domainId).dimensions.push(dimension);
	}
	return Array.from(byKey.values()).filter(
		(group) => group.models.length > 0 || group.dimensions.length > 0,
	);
}

export type WorkbenchCatalogEmptyInput = {
	effectiveView: "domain" | "category";
	dataDomains: CatalogDomain[];
	categoryRoots: CatalogDomain[];
	hasAnyModels: boolean;
	hasLayerModels: boolean;
	layer: string;
};

export function workbenchCatalogEmptyMessage({
	effectiveView,
	dataDomains,
	categoryRoots,
	hasAnyModels,
	hasLayerModels,
	layer,
}: WorkbenchCatalogEmptyInput): string {
	if (effectiveView === "domain") {
		if (!dataDomains.length) return "当前没有数据域，请先在数仓规划中创建数据域。";
	} else if (!categoryRoots.length) {
		return "当前没有业务分类，请先在数仓规划中创建业务分类。";
	}
	if (!hasAnyModels) return "当前尚无模型。";
	if (!hasLayerModels) return `当前“${layer}”暂无模型；切换分层查看其他模型，或在右侧新建模型。`;
	return "没有符合搜索条件的模型。";
}
