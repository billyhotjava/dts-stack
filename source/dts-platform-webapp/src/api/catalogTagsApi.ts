import api from "@/api/apiClient";

export type CatalogTagCategoryDto = {
	id: string;
	code: string;
	name: string;
	parentId?: string | null;
	sortOrder: number;
	builtin: boolean;
	enabled: boolean;
	description?: string | null;
	tagCount: number;
	children: CatalogTagCategoryDto[];
};

export type CatalogTagCategoryRequest = {
	code: string;
	name: string;
	parentId?: string | null;
	sortOrder: number;
	enabled?: boolean;
	description?: string | null;
};

export type CatalogTagDto = {
	id: string;
	categoryId: string;
	code: string;
	name: string;
	color?: string | null;
	builtin: boolean;
	enabled: boolean;
	description?: string | null;
	usageCount: number;
};

export type CatalogTagRequest = {
	categoryId: string;
	code: string;
	name: string;
	color?: string | null;
	enabled?: boolean;
	description?: string | null;
};

export type CatalogTagPageDto = {
	content: CatalogTagDto[];
	total: number;
	page: number;
	size: number;
};

export type CatalogTagListQuery = {
	categoryId?: string;
	keyword?: string;
	enabled?: boolean;
	page?: number;
	size?: number;
};

export type AssetRef = {
	assetType: string;
	assetKey: string;
};

export type AssetRefPage = {
	content: AssetRef[];
	total: number;
	page: number;
	size: number;
};

export type AssetTagSearchQuery = {
	tagIds: string[];
	assetType?: string;
	page?: number;
	size?: number;
};

export type AssetTagCapability = {
	canTag: boolean;
};

export type AssetTagRequest = AssetRef & {
	tagIds: string[];
};

export type BatchAssetTagRequest = {
	assets: AssetRef[];
	tagIds: string[];
};

export type AssetTagMutationResult = {
	created: number;
	skipped: number;
	removed: number;
};

export type BatchAssetTagResult = {
	assetCount: number;
	created: number;
	skipped: number;
	results: Array<AssetRef & { created: number; skipped: number }>;
};

export type CatalogTagSeedReport = {
	packageCode: string;
	packageVersion: string;
	applied: boolean;
	categoriesCreated: number;
	tagsCreated: number;
	categoriesSkipped: number;
	tagsSkipped: number;
	installedCodes: string[];
	conflicts: Array<{
		itemType: string;
		code: string;
		reason: string;
		expectedCategoryCode?: string | null;
		actualCategoryCode?: string | null;
	}>;
};

const quietGet = <T>(url: string, params?: unknown) =>
	api.get<T>({ url, ...(params === undefined ? {} : { params }), _skipErrorToast: true } as any);

const quietPost = <T>(url: string, data: unknown) => api.post<T>({ url, data, _skipErrorToast: true } as any);

const quietPut = <T>(url: string, data: unknown) => api.put<T>({ url, data, _skipErrorToast: true } as any);

const quietDelete = <T>(url: string, config: Record<string, unknown> = {}) =>
	api.delete<T>({ url, ...config, _skipErrorToast: true } as any);

export const listTagCategories = () => quietGet<CatalogTagCategoryDto[]>("/catalog/tag-categories");

export const createTagCategory = (data: CatalogTagCategoryRequest) =>
	quietPost<CatalogTagCategoryDto>("/catalog/tag-categories", data);

export const updateTagCategory = (id: string, data: CatalogTagCategoryRequest) =>
	quietPut<CatalogTagCategoryDto>(`/catalog/tag-categories/${encodeURIComponent(id)}`, data);

export const deleteTagCategory = (id: string) =>
	quietDelete<boolean>(`/catalog/tag-categories/${encodeURIComponent(id)}`);

export const listCatalogTags = (params: CatalogTagListQuery = {}) =>
	quietGet<CatalogTagPageDto>("/catalog/tags", params);

const ALL_TAGS_PAGE_SIZE = 100;
const ALL_TAGS_MAX_PAGES = 1000;

export async function listAllEnabledCatalogTags(): Promise<CatalogTagDto[]> {
	const tagsById = new Map<string, CatalogTagDto>();
	let page = 0;
	let expectedPages = 1;

	while (page < expectedPages && page < ALL_TAGS_MAX_PAGES) {
		const result = await listCatalogTags({
			enabled: true,
			page,
			size: ALL_TAGS_PAGE_SIZE,
		});
		const content = Array.isArray(result?.content) ? result.content : [];
		for (const tag of content) {
			if (tag?.enabled && tag.id) tagsById.set(tag.id, tag);
		}

		const advertisedTotal = Number(result?.total);
		if (Number.isFinite(advertisedTotal) && advertisedTotal > 0) {
			expectedPages = Math.max(expectedPages, Math.ceil(advertisedTotal / ALL_TAGS_PAGE_SIZE));
		}
		page++;
		if (content.length < ALL_TAGS_PAGE_SIZE) break;
	}

	if (page >= ALL_TAGS_MAX_PAGES && page < expectedPages) {
		throw new Error("可用业务数据标签数量超过安全加载上限，请缩小标签目录规模");
	}
	return Array.from(tagsById.values());
}

export const createCatalogTag = (data: CatalogTagRequest) => quietPost<CatalogTagDto>("/catalog/tags", data);

export const updateCatalogTag = (id: string, data: CatalogTagRequest) =>
	quietPut<CatalogTagDto>(`/catalog/tags/${encodeURIComponent(id)}`, data);

export const deleteCatalogTag = (id: string, force = false) =>
	quietDelete<boolean>(`/catalog/tags/${encodeURIComponent(id)}`, {
		params: { force },
	});

export const listAssetTags = (params: AssetRef) => quietGet<CatalogTagDto[]>("/catalog/asset-tags", params);

export const searchCatalogAssetsByTags = (params: AssetTagSearchQuery) =>
	api.get<AssetRefPage>({
		url: "/catalog/asset-tags/search",
		params,
		paramsSerializer: { indexes: null },
		_skipErrorToast: true,
	} as any);

export const getAssetTagCapability = (params: AssetRef) =>
	quietGet<AssetTagCapability>("/catalog/asset-tags/capability", params);

export const tagAsset = (data: AssetTagRequest) => quietPost<AssetTagMutationResult>("/catalog/asset-tags", data);

export const untagAsset = (data: AssetTagRequest) =>
	quietDelete<AssetTagMutationResult>("/catalog/asset-tags", { data });

export const batchTagAssets = (data: BatchAssetTagRequest) =>
	quietPost<BatchAssetTagResult>("/catalog/asset-tags/batch", data);

export const installBuiltinCatalogTags = () =>
	quietPost<CatalogTagSeedReport>("/catalog/tags/builtin/install", undefined);

const normalizeAssetKeySegment = (value: string): string =>
	value
		.trim()
		.toLowerCase()
		.replace(/[^a-z0-9_.:-]+/g, "_")
		.replace(/_+/g, "_")
		.replace(/^_+|_+$/g, "");

export const buildCatalogDomainAssetKey = (domainCode: string): string =>
	`tenant:default/env:prod/dialect:generic/catalog_domain:${normalizeAssetKeySegment(domainCode)}`;
