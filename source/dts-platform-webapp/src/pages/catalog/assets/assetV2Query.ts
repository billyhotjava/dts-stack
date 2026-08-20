import { LEDGER_PAGE_SIZE } from "./assetPageShared";

export type AssetV2FilterState = {
	keyword?: string;
	assetFamily?: string;
	domainId?: string;
	domainUnassigned?: boolean;
	assetType?: string;
	classification?: string;
	warehouseLayer?: string;
	governanceStatus?: string;
	matchStatus?: string;
	unclassified?: boolean;
	stale?: boolean;
	eligibility?: string;
	servingStatus?: string;
	qualityStatus?: string;
	tagIds?: string[];
};

export const buildAssetV2Query = (filters: AssetV2FilterState, page = 0, size = LEDGER_PAGE_SIZE) => ({
	page,
	size,
	keyword: filters.keyword?.trim() || undefined,
	assetFamily: filters.assetFamily || undefined,
	domainId: filters.domainId,
	domainUnassigned: filters.domainUnassigned || undefined,
	type: filters.assetType === "ALL" ? undefined : filters.assetType,
	classification: filters.classification === "ALL" ? undefined : filters.classification,
	warehouseLayer: filters.warehouseLayer === "ALL" ? undefined : filters.warehouseLayer,
	governanceStatus: filters.governanceStatus === "ALL" ? undefined : filters.governanceStatus,
	matchStatus: filters.matchStatus === "ALL" ? undefined : filters.matchStatus,
	unclassified: filters.unclassified || undefined,
	stale: filters.stale || undefined,
	eligibility: filters.eligibility === "ALL" ? undefined : filters.eligibility,
	servingStatus: filters.servingStatus === "ALL" ? undefined : filters.servingStatus,
	qualityStatus: filters.qualityStatus === "ALL" ? undefined : filters.qualityStatus,
	tagIds: filters.tagIds?.length ? filters.tagIds : undefined,
});
