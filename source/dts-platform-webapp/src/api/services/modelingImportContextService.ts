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

export async function loadModelingImportContext(contextId: string): Promise<ModelingImportContext> {
	if (!contextId) return { domains: [], sources: [] };
	const [categories, sources] = await Promise.all([
		getWarehousePlanCategories(contextId),
		getWarehousePlanSources(contextId),
	]);
	return {
		domains: categories.value.domainBindings.filter(
			(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
		),
		sources: sources.bindings.filter(
			(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
		),
	};
}
