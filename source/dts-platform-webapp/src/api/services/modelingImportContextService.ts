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
};

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
	if (!contextId) return { domains: [], sources: [] };
	const [categories, sources] = await Promise.all([
		getWarehousePlanCategories(contextId),
		collectCurrentWarehousePlanSources(contextId),
	]);
	return {
		domains: categories.value.domainBindings.filter(
			(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
		),
		sources,
	};
}
