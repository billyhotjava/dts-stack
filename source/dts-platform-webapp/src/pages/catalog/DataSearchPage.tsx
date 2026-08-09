import { Alert, Button, Card, Input, Select, Space, Tabs, Tag } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import { type AssetRefPage, type CatalogTagDto, searchCatalogAssetsByTags } from "@/api/catalogTagsApi";
import { listCatalogAssetsV2, listDomains, searchCatalog } from "@/api/platformApi";
import { AssetTagChips } from "@/components/catalog/tags/AssetTagChips";
import { AssetTagFilter } from "@/components/catalog/tags/AssetTagFilter";
import { readTagIds, writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { ASSET_PORTAL_V2_ENABLED } from "./assets/assetPageShared";
import { buildAssetV2Query } from "./assets/assetV2Query";

type SearchRow = {
	id: string;
	name: string;
	type: string;
	assetKind: "ASSET" | "DATASET" | "TABLE" | "COLUMN";
	domainId?: string;
	domain?: string;
	owner?: string;
	datasetName?: string;
	assetType?: string;
	assetKey?: string;
	assetTags?: CatalogTagDto[];
	datasetAssetKey?: string;
	datasetAssetTags?: CatalogTagDto[];
	classification?: string;
	warehouseLayer?: string;
	source?: string;
	updatedAt?: string;
};

const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
	{ label: "资产", value: "ASSET" },
	{ label: "数据集", value: "DATASET" },
	{ label: "表", value: "TABLE" },
	{ label: "字段", value: "COLUMN" },
];

const DATASET_TYPE_OPTIONS = [
	{ label: "全部系统", value: "ALL" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

const CLASSIFICATION_OPTIONS = [
	{ label: "全部密级", value: "ALL" },
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const LAYER_OPTIONS = [
	{ label: "全部分层", value: "ALL" },
	{ label: "ODS", value: "ODS" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const SEARCH_FORM_STORAGE_KEY = "catalog.search.form.v1";
const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v2";

type StoredSearchForm = {
	keyword: string;
	domain: string | undefined;
	assetType: string;
	datasetType: string;
	classification: string;
	warehouseLayer: string;
};

const EMPTY_SEARCH_FORM: StoredSearchForm = {
	keyword: "",
	domain: undefined,
	assetType: "ALL",
	datasetType: "ALL",
	classification: "ALL",
	warehouseLayer: "ALL",
};

function readStoredSearchForm(): StoredSearchForm {
	if (typeof window === "undefined") return EMPTY_SEARCH_FORM;
	try {
		const raw = localStorage.getItem(SEARCH_FORM_STORAGE_KEY);
		if (!raw) return EMPTY_SEARCH_FORM;
		const saved = JSON.parse(raw);
		return {
			keyword: typeof saved?.keyword === "string" ? saved.keyword : "",
			domain: typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined,
			assetType: typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL",
			datasetType: typeof saved?.datasetType === "string" && saved.datasetType ? saved.datasetType : "ALL",
			classification: typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL",
			warehouseLayer: typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL",
		};
	} catch {
		return EMPTY_SEARCH_FORM;
	}
}

export default function DataSearchPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const selectedTagIds = useMemo(() => readTagIds(searchParams), [searchParams]);
	const effectiveSelectedTagIds = ASSET_PORTAL_V2_ENABLED ? selectedTagIds : [];
	const selectedTagIdsKey = effectiveSelectedTagIds.join("\u0000");
	const [initialSearchForm] = useState(readStoredSearchForm);
	const [keyword, setKeyword] = useState(initialSearchForm.keyword);
	const [domain, setDomain] = useState<string | undefined>(initialSearchForm.domain);
	const [assetType, setAssetType] = useState<string>(initialSearchForm.assetType);
	const [datasetType, setDatasetType] = useState<string>(initialSearchForm.datasetType);
	const [classification, setClassification] = useState<string>(initialSearchForm.classification);
	const [warehouseLayer, setWarehouseLayer] = useState<string>(initialSearchForm.warehouseLayer);
	// 与台账同协议的治理缺口深链：?unclassified=1 / ?stale=1 / ?governance= / ?layer= 等由 URL 优先注入
	const [unclassifiedFilter, setUnclassifiedFilter] = useState<boolean>(() => searchParams.get("unclassified") === "1");
	const [staleFilter, setStaleFilter] = useState<boolean>(() => searchParams.get("stale") === "1");
	const [governanceFilter, setGovernanceFilter] = useState<string>(() => searchParams.get("governance") || "ALL");
	const [loading, setLoading] = useState(false);
	const [results, setResults] = useState<SearchRow[]>([]);
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [searched, setSearched] = useState(false);
	const searchRequestSequence = useRef(0);

	useEffect(() => {
		void (async () => {
			try {
				const resp: any = await listDomains(0, 200, "");
				const list = Array.isArray(resp?.content) ? resp.content : [];
				setDomains(
					list
						.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
						.filter((item: any) => item.id && item.name),
				);
			} catch {
				// error toast handled by global interceptor
			}
		})();
	}, []);

	const domainOptions = useMemo(() => {
		return [{ label: "全部主题域", value: "ALL" }, ...domains.map((item) => ({ label: item.name, value: item.id }))];
	}, [domains]);

	const grouped = useMemo(
		() => ({
			ASSET: results.filter((r) => r.assetKind === "ASSET"),
			DATASET: results.filter((r) => r.assetKind === "DATASET"),
			TABLE: results.filter((r) => r.assetKind === "TABLE"),
			COLUMN: results.filter((r) => r.assetKind === "COLUMN"),
		}),
		[results],
	);

	const persistCurrentQuery = () => {
		const payload = {
			keyword,
			domain: domain || "",
			assetType,
			datasetType,
			classification,
			warehouseLayer,
		};
		localStorage.setItem(SEARCH_FORM_STORAGE_KEY, JSON.stringify(payload));
	};

	const saveCurrentQuery = () => {
		persistCurrentQuery();
		toast.success("已保存当前检索条件");
	};

	const restoreSavedQuery = () => {
		try {
			const raw = localStorage.getItem(SEARCH_FORM_STORAGE_KEY);
			if (!raw) {
				toast.warning("暂无已保存的检索条件");
				return;
			}
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setDatasetType(typeof saved?.datasetType === "string" && saved.datasetType ? saved.datasetType : "ALL");
			setClassification(
				typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL",
			);
			setWarehouseLayer(
				typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL",
			);
			toast.success("已恢复检索条件");
		} catch {
			toast.error("检索条件恢复失败");
		}
	};

	const applyAssetFilters = () => {
		try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) {
				toast.warning("未找到资产列表筛选条件");
				return;
			}
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : keyword);
			setDomain(undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(
				typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL",
			);
			setWarehouseLayer(
				typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL",
			);
			toast.success("已应用资产列表筛选条件");
		} catch {
			toast.error("资产筛选条件读取失败");
		}
	};

	const normalizeRows = (payload: any): SearchRow[] => {
		const rows: SearchRow[] = [];
		const datasets = Array.isArray(payload?.datasets) ? payload.datasets : [];
		const tables = Array.isArray(payload?.tables) ? payload.tables : [];
		const columns = Array.isArray(payload?.columns) ? payload.columns : [];
		datasets.forEach((item: any) => {
			if (!item) return;
			rows.push({
				id: String(item.id || `dataset-${rows.length}`),
				name: String(item.name || item.hiveTable || "-"),
				type: item.type ? String(item.type) : "DATASET",
				assetKind: "DATASET",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.owner || item.ownerDept || undefined,
				updatedAt: item.updatedAt || undefined,
				assetType: item.assetType || "DATASET",
				assetKey: item.assetKey || undefined,
				assetTags: Array.isArray(item.assetTags) ? item.assetTags : [],
			});
		});
		tables.forEach((item: any) => {
			if (!item) return;
			rows.push({
				id: String(item.id || `table-${rows.length}`),
				name: String(item.name || "-"),
				type: "TABLE",
				assetKind: "TABLE",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.owner || item.datasetOwnerDept || undefined,
				datasetName: item.datasetName || undefined,
				datasetAssetKey: item.datasetAssetKey || undefined,
				datasetAssetTags: Array.isArray(item.datasetAssetTags) ? item.datasetAssetTags : [],
			});
		});
		columns.forEach((item: any) => {
			if (!item) return;
			const columnName = String(item.name || "-");
			const tableName = String(item.tableName || item.datasetName || "").trim();
			rows.push({
				id: String(item.id || `column-${rows.length}`),
				name: tableName ? `${tableName}.${columnName}` : columnName,
				type: "COLUMN",
				assetKind: "COLUMN",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.datasetOwnerDept || undefined,
				datasetName: item.datasetName || undefined,
				datasetAssetKey: item.datasetAssetKey || undefined,
				datasetAssetTags: Array.isArray(item.datasetAssetTags) ? item.datasetAssetTags : [],
			});
		});
		return rows;
	};

	const normalizeAssetRows = (payload: any): SearchRow[] => {
		const content = Array.isArray(payload?.content) ? payload.content : [];
		return content.map((item: any, index: number) => ({
			id: String(item.id || item.assetKey || item.fqn || `asset-${index}`),
			name: String(item.displayName || item.name || item.table || item.fqn || "-"),
			type: item.type ? String(item.type) : "ASSET",
			assetKind: "ASSET",
			domainId: item.domainId ? String(item.domainId) : undefined,
			domain: item.domainName || undefined,
			owner: item.owner || item.ownerDept || undefined,
			assetType: item.assetType || item.grantAssetType || item.type || undefined,
			assetKey: item.assetKey || item.fqn || undefined,
			assetTags: Array.isArray(item.assetTags) ? item.assetTags : [],
			classification: item.classification || undefined,
			warehouseLayer: item.warehouseLayer || undefined,
			source: item.metadataSource || "assets-v2",
			updatedAt: item.lastSyncedAt || item.lastModifiedDate || item.createdDate || undefined,
		}));
	};

	const normalizeExactTagRows = (payload: AssetRefPage | undefined): SearchRow[] => {
		const content = Array.isArray(payload?.content) ? payload.content : [];
		const rows: SearchRow[] = [];
		for (const item of content) {
			const assetType = String(item.assetType || "ASSET")
				.trim()
				.toUpperCase();
			const assetKey = String(item.assetKey || "").trim();
			if (!assetKey) continue;
			const keySegments = assetKey.split("/");
			rows.push({
				id: `tag-hit-${assetType}-${assetKey}`,
				name: keySegments[keySegments.length - 1] || assetKey,
				type: assetType,
				assetKind: "ASSET",
				assetType,
				assetKey,
				assetTags: [],
				source: "标签索引",
			});
		}
		return rows;
	};

	const runSearch = async (notifyWhenEmpty = true) => {
		const sequence = ++searchRequestSequence.current;
		const trimmed = keyword.trim();
		if (!trimmed && effectiveSelectedTagIds.length === 0) {
			setResults([]);
			setSearched(false);
			setLoading(false);
			if (notifyWhenEmpty) {
				toast.error(ASSET_PORTAL_V2_ENABLED ? "请输入关键词或选择业务数据标签后再搜索" : "请输入关键词后再搜索");
			}
			return;
		}
		setLoading(true);
		setSearched(true);
		try {
			const shouldSearchAssetsV2 =
				ASSET_PORTAL_V2_ENABLED &&
				(assetType === "ALL" || assetType === "ASSET" || assetType === "DATASET" || assetType === "TABLE");
			const shouldSearchLegacyCatalog = assetType !== "ASSET";
			const hasExactTagIncompatibleFilters =
				Boolean(trimmed) ||
				Boolean(domain && domain !== "ALL") ||
				datasetType !== "ALL" ||
				classification !== "ALL" ||
				warehouseLayer !== "ALL";
			const exactAssetType = assetType === "DATASET" ? "DATASET" : undefined;
			const shouldSearchExactTags =
				effectiveSelectedTagIds.length > 0 &&
				!hasExactTagIncompatibleFilters &&
				(assetType === "ALL" || assetType === "ASSET" || assetType === "DATASET");
			const [tagSearchResult, assetsResult, legacyResult] = await Promise.allSettled([
				shouldSearchExactTags
					? searchCatalogAssetsByTags({
							tagIds: effectiveSelectedTagIds,
							assetType: exactAssetType,
							page: 0,
							size: 100,
						})
					: Promise.resolve({ content: [], total: 0, page: 0, size: 100 }),
				shouldSearchAssetsV2
					? listCatalogAssetsV2(
							buildAssetV2Query(
								{
									keyword: trimmed,
									tagIds: effectiveSelectedTagIds,
									domainId: domain && domain !== "ALL" ? domain : undefined,
									assetType: datasetType,
									classification,
									warehouseLayer,
									governanceStatus: governanceFilter,
									unclassified: unclassifiedFilter,
									stale: staleFilter,
								},
								0,
								100,
							),
						)
					: Promise.resolve({ content: [] }),
				shouldSearchLegacyCatalog
					? searchCatalog({
							keyword: trimmed || undefined,
							tagIds: effectiveSelectedTagIds,
							types: assetType === "ALL" ? undefined : assetType,
							domainId: domain && domain !== "ALL" ? domain : undefined,
							classification: classification === "ALL" ? undefined : classification,
							warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
							datasetType: datasetType === "ALL" ? undefined : datasetType,
							enabledOnly: true,
							limit: 200,
						})
					: Promise.resolve({ datasets: [], tables: [], columns: [] }),
			]);
			if (sequence !== searchRequestSequence.current) return;
			const exactTagRows = tagSearchResult.status === "fulfilled" ? normalizeExactTagRows(tagSearchResult.value) : [];
			const assetRows = assetsResult.status === "fulfilled" ? normalizeAssetRows(assetsResult.value || {}) : [];
			const legacyRows = legacyResult.status === "fulfilled" ? normalizeRows(legacyResult.value || {}) : [];
			const resolvedAssetKeys = new Set(
				[...assetRows, ...legacyRows]
					.filter((row) => row.assetKey)
					.map((row) => `${row.assetType || row.type}\u0000${row.assetKey}`),
			);
			const unresolvedExactTagRows = exactTagRows.filter(
				(row) => !resolvedAssetKeys.has(`${row.assetType || row.type}\u0000${row.assetKey}`),
			);
			setResults([...assetRows, ...legacyRows, ...unresolvedExactTagRows]);
			persistCurrentQuery();
		} catch {
			if (sequence !== searchRequestSequence.current) return;
			setResults([]);
			// error toast handled by global interceptor
		} finally {
			if (sequence === searchRequestSequence.current) setLoading(false);
		}
	};

	const runSearchRef = useRef(runSearch);
	runSearchRef.current = runSearch;
	useEffect(() => {
		void selectedTagIdsKey;
		void runSearchRef.current(false);
	}, [selectedTagIdsKey]);

	// 统一筛选状态协议：与台账一致，URL 深链参数（layer/governance/unclassified/stale/domain/classification/assetType/datasetType）优先于本地缓存。
	// 仅当 URL 显式携带时注入表单并立即检索，保证资产概览等入口的下钻在搜索页真实生效。
	const deepLinkFilterKey = [
		searchParams.get("layer"),
		searchParams.get("governance"),
		searchParams.get("unclassified"),
		searchParams.get("stale"),
		searchParams.get("domain"),
		searchParams.get("classification"),
		searchParams.get("assetType"),
		searchParams.get("datasetType"),
	].join("\u0000");
	useEffect(() => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		const params = searchParams;
		let merged = false;
		const layer = params.get("layer");
		if (layer) {
			setWarehouseLayer(layer);
			merged = true;
		}
		const governance = params.get("governance");
		if (governance) {
			setGovernanceFilter(governance);
			merged = true;
		}
		const domainParam = params.get("domain");
		if (domainParam) {
			setDomain(domainParam);
			merged = true;
		}
		const classificationParam = params.get("classification");
		if (classificationParam) {
			setClassification(classificationParam);
			merged = true;
		}
		const assetTypeParam = params.get("assetType");
		if (assetTypeParam) {
			setAssetType(assetTypeParam);
			merged = true;
		}
		const datasetTypeParam = params.get("datasetType");
		if (datasetTypeParam) {
			setDatasetType(datasetTypeParam);
			merged = true;
		}
		if (params.get("unclassified") === "1") {
			setUnclassifiedFilter(true);
			merged = true;
		}
		if (params.get("stale") === "1") {
			setStaleFilter(true);
			merged = true;
		}
		if (merged) {
			runSearchRef.current(false);
		}
	}, [deepLinkFilterKey]);

	return (
		<div className="space-y-4">
			<PageHeader title="数据资产门户 · 数据搜索" />

			<Card title="搜索条件">
				{!ASSET_PORTAL_V2_ENABLED ? (
					<Alert
						className="mb-3"
						type="warning"
						showIcon
						message="旧版资产门户未启用业务数据标签筛选"
						description="当前仍可按关键词检索数据集；如需按业务数据标签检索全部资产类型，请启用新版资产门户。"
					/>
				) : null}
				<Space size={12} wrap>
					<Input.Search
						placeholder="输入关键词"
						style={{ width: 320 }}
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						onSearch={() => void runSearch(true)}
						allowClear
					/>
					<Select
						allowClear
						placeholder="主题域"
						style={{ minWidth: 180 }}
						value={domain || "ALL"}
						onChange={(value) => setDomain(value === "ALL" ? undefined : value)}
						options={domainOptions}
					/>
					<Select
						allowClear
						placeholder="资产类型"
						style={{ minWidth: 180 }}
						value={assetType}
						onChange={(value) => setAssetType(value || "ALL")}
						options={TYPE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="数据源类型"
						style={{ minWidth: 180 }}
						value={datasetType}
						onChange={(value) => setDatasetType(value || "ALL")}
						options={DATASET_TYPE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="密级"
						style={{ minWidth: 180 }}
						value={classification}
						onChange={(value) => setClassification(value || "ALL")}
						options={CLASSIFICATION_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="分层"
						style={{ minWidth: 180 }}
						value={warehouseLayer}
						onChange={(value) => setWarehouseLayer(value || "ALL")}
						options={LAYER_OPTIONS}
					/>
					<AssetTagFilter
						value={selectedTagIds}
						disabled={!ASSET_PORTAL_V2_ENABLED}
						onChange={(nextIds) => {
							setSearchParams(writeTagIds(searchParams, nextIds), { replace: true });
						}}
					/>
					<Button type="primary" onClick={() => void runSearch(true)} loading={loading}>
						搜索
					</Button>
					<Button onClick={saveCurrentQuery}>保存条件</Button>
					<Button onClick={restoreSavedQuery}>恢复条件</Button>
					<Button onClick={applyAssetFilters}>应用资产筛选</Button>
				</Space>
			</Card>

			<Card title="搜索结果">
				{unclassifiedFilter || staleFilter ? (
					<Alert
						type="info"
						showIcon
						className="mb-3"
						message={`当前搜索已按治理缺口筛选：${[
							unclassifiedFilter ? "未定密" : null,
							staleFilter ? "已失效（DEPRECATED/ARCHIVED/BLOCKED）" : null,
						]
							.filter(Boolean)
							.join("、")}`}
						description="该筛选来自资产概览的治理缺口下钻；可清除 URL 参数 ?unclassified/?stale 回到全量检索。"
					/>
				) : null}
				{searched ? (
					<Tabs
						items={[
							{
								key: "ASSET",
								label: `资产（${grouped.ASSET.length}）`,
								children: grouped.ASSET.length ? (
									<div className="grid grid-cols-1 gap-2 md:grid-cols-2">
										{grouped.ASSET.map((row) =>
											row.source === "标签索引" ? (
												<div key={row.id} className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-left">
													<div className="font-semibold text-sm text-slate-900">{row.name}</div>
													<div className="mt-1 truncate font-mono text-xs text-slate-500">{row.assetKey || row.id}</div>
													<div className="mt-2 flex flex-wrap gap-1">
														<Tag style={{ fontSize: 10 }}>{row.type || "ASSET"}</Tag>
														<Tag color="blue" style={{ fontSize: 10 }}>
															{row.source}
														</Tag>
													</div>
												</div>
											) : (
												<Link
													key={row.id}
													to={`/catalog/datasets/${row.id}`}
													className="cursor-pointer rounded-lg border border-slate-200 bg-white px-4 py-3 text-left transition-all hover:border-blue-300 hover:shadow-sm"
												>
													<div className="font-semibold text-sm text-slate-900">{row.name}</div>
													<div className="mt-1 truncate font-mono text-xs text-slate-500">{row.assetKey || row.id}</div>
													<div className="mt-2 flex flex-wrap gap-1">
														<Tag style={{ fontSize: 10 }}>{row.type || "ASSET"}</Tag>
														<Tag color={row.classification ? "orange" : "default"} style={{ fontSize: 10 }}>
															{row.classification || "未定密"}
														</Tag>
														<Tag color={row.warehouseLayer ? "blue" : "default"} style={{ fontSize: 10 }}>
															{row.warehouseLayer || "未分层"}
														</Tag>
														<Tag color="blue" style={{ fontSize: 10 }}>
															{row.source || "assets-v2"}
														</Tag>
													</div>
													<AssetTagChips tags={row.assetTags || []} variant="inline" />
												</Link>
											),
										)}
									</div>
								) : (
									<div className="py-4 text-sm text-slate-400">无匹配资产</div>
								),
							},
							{
								key: "DATASET",
								label: `数据集（${grouped.DATASET.length}）`,
								children: grouped.DATASET.length ? (
									<div className="grid grid-cols-1 gap-2 md:grid-cols-2">
										{grouped.DATASET.map((row) => (
											<Link
												key={row.id}
												to={`/catalog/datasets/${row.id}`}
												className="cursor-pointer rounded-[18px] border border-slate-200 bg-white px-4 py-3 text-left transition-all hover:border-blue-300 hover:shadow-sm"
											>
												<div className="font-semibold text-sm text-slate-900">{row.name}</div>
												<div className="mt-1 text-xs text-slate-500">{row.domain ?? "未归域"}</div>
												<Tag style={{ fontSize: 10 }} className="mt-1">
													DATASET
												</Tag>
												<AssetTagChips tags={row.assetTags || []} variant="inline" />
											</Link>
										))}
									</div>
								) : (
									<div className="py-4 text-sm text-slate-400">无匹配数据集</div>
								),
							},
							{
								key: "TABLE",
								label: `表（${grouped.TABLE.length}）`,
								children: grouped.TABLE.length ? (
									<div className="space-y-2">
										{grouped.TABLE.map((row) => (
											<div key={row.id} className="rounded-[14px] border border-slate-200 px-3 py-2 text-sm">
												<span className="font-medium">{row.name}</span>
												{row.datasetName && <span className="ml-2 text-slate-400 text-xs">in {row.datasetName}</span>}
												{row.datasetAssetTags?.length ? (
													<div className="mt-1">
														<div className="text-xs text-slate-500">所属数据集标签</div>
														<AssetTagChips tags={row.datasetAssetTags} variant="inline" />
													</div>
												) : null}
											</div>
										))}
									</div>
								) : (
									<div className="py-4 text-sm text-slate-400">无匹配表</div>
								),
							},
							{
								key: "COLUMN",
								label: `字段（${grouped.COLUMN.length}）`,
								children: grouped.COLUMN.length ? (
									<div className="space-y-2">
										{grouped.COLUMN.map((row) => (
											<div key={row.id} className="rounded-[14px] border border-slate-200 px-3 py-2 text-sm">
												<span className="font-mono text-xs">{row.name}</span>
												{row.type && (
													<Tag className="ml-2" style={{ fontSize: 10 }}>
														{row.type}
													</Tag>
												)}
												{row.datasetName && <span className="ml-2 text-slate-400 text-xs">in {row.datasetName}</span>}
												{row.datasetAssetTags?.length ? (
													<div className="mt-1">
														<div className="text-xs text-slate-500">所属数据集标签</div>
														<AssetTagChips tags={row.datasetAssetTags} variant="inline" />
													</div>
												) : null}
											</div>
										))}
									</div>
								) : (
									<div className="py-4 text-sm text-slate-400">无匹配字段</div>
								),
							},
						]}
					/>
				) : (
					<EmptyState title="开始检索" description="输入关键词并点击搜索。" />
				)}
			</Card>
		</div>
	);
}
