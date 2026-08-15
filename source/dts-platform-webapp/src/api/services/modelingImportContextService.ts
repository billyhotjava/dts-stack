import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type { SubjectDomainView } from "@/features/modeling/contracts/subjectDomainContract";
import { listDataMarts } from "../dataMartApi";
import { getDomainTree } from "../platformApi";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "../sprint64GovernanceApi";
import { listSubjectDomains } from "../subjectDomainApi";
import {
	getWarehousePlanCategories,
	getWarehousePlanSources,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanSourceBindingView,
} from "../warehousePlanApi";

export type ModelingImportContextHeader = {
	id: string;
	code: string;
	name: string;
};

export type ModelingImportDomainBinding = WarehousePlanCategoryBindingView;
export type ModelingImportSourceBinding = WarehousePlanSourceBindingView;

export type ModelingImportContext = {
	domains: ModelingImportDomainBinding[];
	sources: ModelingImportSourceBinding[];
	businessProcesses: Sprint64BusinessProcess[];
	dataMarts: DataMartView[];
	subjectDomains: SubjectDomainView[];
};

type CatalogDomainNode = {
	id?: unknown;
	code?: unknown;
	name?: unknown;
	lifecycleStatus?: unknown;
	parentId?: unknown;
	children?: unknown;
};

const text = (value: unknown): string => (value == null ? "" : String(value).trim());

function activeCatalogDataDomains(raw: unknown): WarehousePlanCategoryBindingView[] {
	const roots = Array.isArray(raw)
		? raw
		: raw && typeof raw === "object" && Array.isArray((raw as Record<string, unknown>).data)
			? ((raw as Record<string, unknown>).data as unknown[])
			: [];
	const domains: WarehousePlanCategoryBindingView[] = [];
	const visit = (nodes: unknown[], inheritedParentId: string | null) => {
		for (const value of nodes) {
			if (!value || typeof value !== "object") continue;
			const node = value as CatalogDomainNode;
			const domainId = text(node.id);
			const code = text(node.code);
			const name = text(node.name);
			const parentId = text(node.parentId) || inheritedParentId;
			const lifecycleStatus = text(node.lifecycleStatus).toUpperCase();
			if (domainId && code && name && parentId && (!lifecycleStatus || lifecycleStatus === "ACTIVE")) {
				domains.push({
					domainId,
					confirmationStatus: "CONFIRMED",
					resolutionStatus: "AVAILABLE",
					name,
					code,
				});
			}
			if (Array.isArray(node.children)) visit(node.children, domainId || parentId);
		}
	};
	visit(roots, null);
	return domains;
}

/**
 * Compatibility boundary for the current import API. Prototype pages consume a
 * neutral modeling context and do not expose the retired planning workflow.
 */
export async function listModelingImportContexts(): Promise<ModelingImportContextHeader[]> {
	const contexts = await listWarehousePlans();
	return contexts
		.filter((item) => item.lifecycleStatus !== "ARCHIVED")
		.map((item) => ({ id: item.id, code: item.code, name: item.name }));
}

export async function resolveDefaultModelingContextId(): Promise<string> {
	return (await listModelingImportContexts())[0]?.id || "";
}

type SourcePageLoader = (
	contextId: string,
	page: number,
	size: number,
) => Promise<{ bindings: WarehousePlanSourceBindingView[]; totalPages?: number }>;

export async function collectCurrentWarehousePlanSources(
	contextId: string,
	loadPage: SourcePageLoader = getWarehousePlanSources,
): Promise<WarehousePlanSourceBindingView[]> {
	const pageSize = 200;
	const sources: WarehousePlanSourceBindingView[] = [];
	let page = 0;
	let totalPages = 1;
	do {
		const inventory = await loadPage(contextId, page, pageSize);
		sources.push(
			...inventory.bindings.filter(
				(item) =>
					item.confirmationStatus === "CONFIRMED" &&
					item.resolutionStatus === "AVAILABLE" &&
					item.freshness === "CURRENT",
			),
		);
		totalPages = Math.max(1, inventory.totalPages ?? 1);
		page += 1;
	} while (page < totalPages);
	return sources;
}

export async function loadModelingImportContext(contextId: string): Promise<ModelingImportContext> {
	if (!contextId) return { domains: [], sources: [], businessProcesses: [], dataMarts: [], subjectDomains: [] };
	const [categories, catalogDomains, sources, dataMarts, subjectDomains] = await Promise.all([
		getWarehousePlanCategories(contextId),
		getDomainTree().catch(() => null),
		collectCurrentWarehousePlanSources(contextId),
		listDataMarts({ status: "CURRENT", offset: 0, limit: 100 }),
		listSubjectDomains({ status: "CURRENT", offset: 0, limit: 100 }),
	]);
	const legacyDomains = categories.value.domainBindings.filter(
		(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
	);
	const globalDomains = activeCatalogDataDomains(catalogDomains);
	const domains = globalDomains.length > 0 ? globalDomains : legacyDomains;
	const businessProcesses = (await Promise.all(domains.map((domain) => listBusinessProcessesApi(domain.domainId))))
		.flat()
		.filter((process) => process.confirmed && String(process.lifecycleStatus || "ACTIVE").toUpperCase() !== "RETIRED");
	return {
		domains,
		sources,
		businessProcesses,
		dataMarts,
		subjectDomains,
	};
}
