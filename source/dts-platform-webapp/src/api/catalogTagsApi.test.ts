import { afterEach, describe, expect, it, vi } from "vitest";

const { get, post, put, del } = vi.hoisted(() => ({
	get: vi.fn(),
	post: vi.fn(),
	put: vi.fn(),
	del: vi.fn(),
}));

vi.mock("./apiClient", () => ({ default: { get, post, put, delete: del } }));

import {
	batchTagAssets,
	buildCatalogDomainAssetKey,
	createCatalogTag,
	createTagCategory,
	deleteCatalogTag,
	deleteTagCategory,
	getAssetTagCapability,
	installBuiltinCatalogTags,
	listAllEnabledCatalogTags,
	listAssetTags,
	listCatalogTags,
	listTagCategories,
	searchCatalogAssetsByTags,
	tagAsset,
	untagAsset,
	updateCatalogTag,
	updateTagCategory,
} from "./catalogTagsApi";

describe("catalog tags API", () => {
	afterEach(() => vi.clearAllMocks());

	it("uses the catalog category and paged tag contracts", async () => {
		get.mockResolvedValue([]);
		post.mockResolvedValue({});
		put.mockResolvedValue({});
		del.mockResolvedValue(true);

		const category = {
			code: "BUSINESS",
			name: "业务域",
			parentId: null,
			sortOrder: 10,
			enabled: true,
			description: "按业务域维护标签",
		};
		const tag = {
			categoryId: "category-1",
			code: "BUSINESS-FINANCE",
			name: "财务",
			color: "#1677ff",
			enabled: true,
			description: "财务主题资产",
		};

		await listTagCategories();
		await createTagCategory(category);
		await updateTagCategory("category / 1", category);
		await deleteTagCategory("category / 1");
		await listCatalogTags({
			categoryId: "category-1",
			keyword: "财务",
			enabled: true,
			page: 0,
			size: 10,
		});
		await createCatalogTag(tag);
		await updateCatalogTag("tag / 1", tag);
		await deleteCatalogTag("tag / 1", true);
		await installBuiltinCatalogTags();

		expect(get).toHaveBeenNthCalledWith(1, {
			url: "/catalog/tag-categories",
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/catalog/tag-categories",
			data: category,
			_skipErrorToast: true,
		});
		expect(put).toHaveBeenCalledWith({
			url: "/catalog/tag-categories/category%20%2F%201",
			data: category,
			_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({
			url: "/catalog/tag-categories/category%20%2F%201",
			_skipErrorToast: true,
		});
		expect(get).toHaveBeenNthCalledWith(2, {
			url: "/catalog/tags",
			params: {
				categoryId: "category-1",
				keyword: "财务",
				enabled: true,
				page: 0,
				size: 10,
			},
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/catalog/tags",
			data: tag,
			_skipErrorToast: true,
		});
		expect(put).toHaveBeenCalledWith({
			url: "/catalog/tags/tag%20%2F%201",
			data: tag,
			_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({
			url: "/catalog/tags/tag%20%2F%201",
			params: { force: true },
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/catalog/tags/builtin/install",
			data: undefined,
			_skipErrorToast: true,
		});
	});

	it("uses one stable asset identity for list, add, remove and batch writes", async () => {
		get.mockResolvedValue([]);
		post.mockResolvedValue({});
		del.mockResolvedValue({});
		const request = {
			assetType: "DATASET",
			assetKey: "source:source-1/schema:public/table:orders",
			tagIds: ["tag-1", "tag-2"],
		};
		const batch = {
			assets: [
				{ assetType: request.assetType, assetKey: request.assetKey },
				{ assetType: "METRIC", assetKey: "metric:core/revenue" },
			],
			tagIds: ["tag-1"],
		};

		await listAssetTags({ assetType: request.assetType, assetKey: request.assetKey });
		await tagAsset(request);
		await untagAsset(request);
		await batchTagAssets(batch);

		expect(get).toHaveBeenCalledWith({
			url: "/catalog/asset-tags",
			params: { assetType: request.assetType, assetKey: request.assetKey },
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/catalog/asset-tags",
			data: request,
			_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({
			url: "/catalog/asset-tags",
			data: request,
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/catalog/asset-tags/batch",
			data: batch,
			_skipErrorToast: true,
		});
	});

	it("loads the write capability for the exact asset identity", async () => {
		get.mockResolvedValue({ canTag: true });
		const asset = {
			assetType: "CATALOG_DOMAIN",
			assetKey: "tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa",
		};

		await expect(getAssetTagCapability(asset)).resolves.toEqual({ canTag: true });

		expect(get).toHaveBeenCalledWith({
			url: "/catalog/asset-tags/capability",
			params: asset,
			_skipErrorToast: true,
		});
	});

	it("searches exact tag matches with repeated tag parameters", async () => {
		get.mockResolvedValue({
			content: [
				{
					assetType: "METRIC",
					assetKey: "tenant:default/env:prod/dialect:generic/metric:revenue",
				},
			],
			total: 1,
			page: 0,
			size: 100,
		});

		await searchCatalogAssetsByTags({
			tagIds: ["tag-1", "tag-2"],
			page: 0,
			size: 100,
		});

		expect(get).toHaveBeenCalledWith({
			url: "/catalog/asset-tags/search",
			params: {
				tagIds: ["tag-1", "tag-2"],
				page: 0,
				size: 100,
			},
			paramsSerializer: { indexes: null },
			_skipErrorToast: true,
		});
	});

	it("builds CATALOG_DOMAIN keys with the backend code-asset normalization rules", () => {
		expect(buildCatalogDomainAssetKey("  PJM @ Quality / 2026  ")).toBe(
			"tenant:default/env:prod/dialect:generic/catalog_domain:pjm_quality_2026",
		);
		expect(buildCatalogDomainAssetKey("PJM-QA.v2:Core")).toBe(
			"tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa.v2:core",
		);
	});

	it("keeps the backend Problem Details message unchanged", async () => {
		const backendError = new Error("标签已用于 3 个资产，请确认后强制删除");
		del.mockRejectedValueOnce(backendError);

		await expect(deleteCatalogTag("tag-1", false)).rejects.toBe(backendError);
	});

	it("loads every enabled tag page and de-duplicates rows by id", async () => {
		const firstPage = Array.from({ length: 100 }, (_, index) => ({
			id: `tag-${index}`,
			categoryId: "category-1",
			code: `TAG-${index}`,
			name: `标签 ${index}`,
			builtin: false,
			enabled: true,
			usageCount: 0,
		}));
		get
			.mockResolvedValueOnce({
				content: firstPage,
				total: 101,
				page: 0,
				size: 100,
			})
			.mockResolvedValueOnce({
				content: [
					firstPage[99],
					{
						id: "tag-100",
						categoryId: "category-1",
						code: "TAG-100",
						name: "标签 100",
						builtin: false,
						enabled: true,
						usageCount: 0,
					},
				],
				total: 101,
				page: 1,
				size: 100,
			});

		const rows = await listAllEnabledCatalogTags();

		expect(rows).toHaveLength(101);
		expect(rows.at(-1)?.id).toBe("tag-100");
		expect(get).toHaveBeenNthCalledWith(1, {
			url: "/catalog/tags",
			params: { enabled: true, page: 0, size: 100 },
			_skipErrorToast: true,
		});
		expect(get).toHaveBeenNthCalledWith(2, {
			url: "/catalog/tags",
			params: { enabled: true, page: 1, size: 100 },
			_skipErrorToast: true,
		});
	});
});
