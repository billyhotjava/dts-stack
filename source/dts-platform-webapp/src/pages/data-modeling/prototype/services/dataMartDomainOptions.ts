import { listPlanningCatalogDomains } from "./planningCatalogDomainService";

export type DataMartCategoryOption = { id: string; code: string; name: string };

/**
 * DataWorks-aligned: a data mart refines business categories. Only catalog roots
 * ({@code parentId == null}) are valid scopes; child data domains are not.
 */
export const loadDataMartCategoryOptions = async (): Promise<DataMartCategoryOption[]> => {
	const domains = await listPlanningCatalogDomains();
	return domains
		.filter((item) => !item.parentId)
		.map((item) => ({ id: item.id, code: item.code, name: item.name }));
};
