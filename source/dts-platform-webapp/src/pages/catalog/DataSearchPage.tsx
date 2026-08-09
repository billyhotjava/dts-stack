import { DownOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Dropdown, Input, Pagination, Segmented, Select, Tag } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { toast } from "sonner";
import type { CatalogTagDto } from "@/api/catalogTagsApi";
import { listCatalogAssetsV2, listDomains } from "@/api/platformApi";
import { AssetTagChips } from "@/components/catalog/tags/AssetTagChips";
import { AssetTagFilter } from "@/components/catalog/tags/AssetTagFilter";
import { readTagIds, writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { AssetLedgerView } from "./assets/AssetLedgerView";
import { AssetTagsWorkspace } from "./assets/AssetTagsWorkspace";
import {
	ASSET_PORTAL_V2_ENABLED,
	type AssetRow,
	classificationTagColor,
	LEDGER_PAGE_SIZE,
} from "./assets/assetPageShared";
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
	// 旧标签工作台深链（标签管理 → 查看资产/关联资产）：tab=catalog-tags 时直接进入标签工作台
	const isTagsWorkspace = searchParams.get("tab") === "catalog-tags";
	if (isTagsWorkspace) {
		return <AssetTagsWorkspace />;
	}
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
	const [view, setView] = useState<"card" | "table">(() => (searchParams.get("view") === "table" ? "table" : "card"));
	const [assetRows, setAssetRows] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: LEDGER_PAGE_SIZE, total: 0 });
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

	const domainMap = useMemo(() => new Map(domains.map((item) => [item.id, item.name])), [domains]);

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

	// 合并台账后以 assets-v2 为唯一数据源：搜索、浏览、治理作业共用同一份资产列表与分页
	const runSearch = async (notifyWhenEmpty = true, page = 1, size = LEDGER_PAGE_SIZE) => {
		const sequence = ++searchRequestSequence.current;
		const trimmed = keyword.trim();
		if (!trimmed && effectiveSelectedTagIds.length === 0) {
			setResults([]);
			setAssetRows([]);
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
			const resp = await listCatalogAssetsV2(
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
					page - 1,
					size,
				),
			);
			if (sequence !== searchRequestSequence.current) return;
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setAssetRows(content as AssetRow[]);
			setResults(normalizeAssetRows(resp || {}));
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? content.length),
			});
			persistCurrentQuery();
		} catch {
			if (sequence !== searchRequestSequence.current) return;
			setResults([]);
			setAssetRows([]);
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
	// biome-ignore lint/correctness/useExhaustiveDependencies: 派生 key 聚合 URL 参数，searchParams 已捕获底层变化
	useEffect(() => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		const params = searchParams;
		let merged = false;
		// 仅当 URL 值与当前筛选不同才合并，避免筛选回写 URL 后触发重复检索
		const layer = params.get("layer");
		if (layer && layer !== warehouseLayer) {
			setWarehouseLayer(layer);
			merged = true;
		}
		const governance = params.get("governance");
		if (governance && governance !== governanceFilter) {
			setGovernanceFilter(governance);
			merged = true;
		}
		const domainParam = params.get("domain");
		if (domainParam && domainParam !== domain) {
			setDomain(domainParam);
			merged = true;
		}
		const classificationParam = params.get("classification");
		if (classificationParam && classificationParam !== classification) {
			setClassification(classificationParam);
			merged = true;
		}
		const assetTypeParam = params.get("assetType");
		if (assetTypeParam && assetTypeParam !== assetType) {
			setAssetType(assetTypeParam);
			merged = true;
		}
		const datasetTypeParam = params.get("datasetType");
		if (datasetTypeParam && datasetTypeParam !== datasetType) {
			setDatasetType(datasetTypeParam);
			merged = true;
		}
		if (params.get("unclassified") === "1" && !unclassifiedFilter) {
			setUnclassifiedFilter(true);
			merged = true;
		}
		if (params.get("stale") === "1" && !staleFilter) {
			setStaleFilter(true);
			merged = true;
		}
		if (merged) {
			runSearchRef.current(false);
		}
	}, [
		deepLinkFilterKey,
		warehouseLayer,
		governanceFilter,
		domain,
		classification,
		assetType,
		datasetType,
		unclassifiedFilter,
		staleFilter,
		searchParams,
	]);

	// 与台账一致的 URL 回写：筛选选择后立即进入 URL（keyword 仍走本地缓存，避免每键重写）
	const urlFilterKey = [domain || "ALL", assetType, datasetType, classification, warehouseLayer].join("\u0000");
	// biome-ignore lint/correctness/useExhaustiveDependencies: 派生 key 聚合筛选状态，各状态已作为依赖
	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		if (domain && domain !== "ALL") params.set("domain", domain);
		else params.delete("domain");
		if (assetType !== "ALL") params.set("assetType", assetType);
		else params.delete("assetType");
		if (datasetType !== "ALL") params.set("datasetType", datasetType);
		else params.delete("datasetType");
		if (classification !== "ALL") params.set("classification", classification);
		else params.delete("classification");
		if (warehouseLayer !== "ALL") params.set("layer", warehouseLayer);
		else params.delete("layer");
		if (view === "table") params.set("view", "table");
		else params.delete("view");
		const next = params.toString();
		if (next !== searchParams.toString()) setSearchParams(params, { replace: true });
	}, [urlFilterKey, view, searchParams, setSearchParams]);

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
				<div className="flex flex-col gap-2">
					<div className="flex flex-wrap items-center gap-2">
						<Input.Search
							placeholder="输入关键词"
							style={{ width: 320 }}
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							onSearch={() => void runSearch(true)}
							allowClear
						/>
						<span className="px-1 text-xs text-slate-400">范围</span>
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
							style={{ minWidth: 170 }}
							value={assetType}
							onChange={(value) => setAssetType(value || "ALL")}
							options={TYPE_OPTIONS}
						/>
						<Select
							allowClear
							placeholder="数据源类型"
							style={{ minWidth: 170 }}
							value={datasetType}
							onChange={(value) => setDatasetType(value || "ALL")}
							options={DATASET_TYPE_OPTIONS}
						/>
					</div>
					<div className="flex flex-wrap items-center gap-2">
						<span className="px-1 text-xs text-slate-400">属性</span>
						<Select
							allowClear
							placeholder="密级"
							style={{ minWidth: 170 }}
							value={classification}
							onChange={(value) => setClassification(value || "ALL")}
							options={CLASSIFICATION_OPTIONS}
						/>
						<Select
							allowClear
							placeholder="分层"
							style={{ minWidth: 170 }}
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
						<Dropdown
							menu={{
								items: [
									{ key: "save", label: "保存当前条件", onClick: saveCurrentQuery },
									{ key: "restore", label: "恢复已存条件", onClick: restoreSavedQuery },
									{ type: "divider" },
									{
										key: "apply-ledger",
										label: "应用台账筛选",
										onClick: applyAssetFilters,
									},
								],
							}}
						>
							<Button>
								条件 <DownOutlined />
							</Button>
						</Dropdown>
					</div>
				</div>
			</Card>

			<Card
				title={
					<span className="flex flex-wrap items-center gap-3">
						检索结果
						{searched ? (
							<span className="text-xs font-normal text-slate-400">
								共 {pageState.total} 条 · 第 {pageState.page} 页
							</span>
						) : null}
					</span>
				}
				extra={
					searched ? (
						<Segmented
							value={view}
							onChange={(value) => setView(value as "card" | "table")}
							options={[
								{ label: "卡片", value: "card" },
								{ label: "表格", value: "table" },
							]}
						/>
					) : null
				}
			>
				{unclassifiedFilter || staleFilter ? (
					<Alert
						type="info"
						showIcon
						className="mb-3"
						message={`当前检索已按治理缺口筛选：${[
							unclassifiedFilter ? "未定密" : null,
							staleFilter ? "已失效（DEPRECATED/ARCHIVED/BLOCKED）" : null,
						]
							.filter(Boolean)
							.join("、")}`}
						description="该筛选来自资产概览的治理缺口下钻；可清除 URL 参数 ?unclassified/?stale 回到全量检索。"
					/>
				) : null}
				{searched ? (
					<>
						{view === "table" ? (
							<AssetLedgerView
								records={assetRows}
								domainMap={domainMap}
								ledgerIssueCount={assetRows.filter((row) => !row.classification).length}
								readyCount={0}
								selectedDomainName={domain ? domainMap.get(domain) || domain : "全部主题域"}
								missingDomainCount={assetRows.filter((row) => !row.domain && !row.domainId).length}
								onAssetChanged={() => void runSearch(false, pageState.page, pageState.size)}
							/>
						) : results.length ? (
							<div className="grid grid-cols-1 gap-2 md:grid-cols-2">
								{results.map((row) => (
									<Link
										key={row.id}
										to={`/catalog/datasets/${row.id}`}
										className="cursor-pointer rounded-lg border border-slate-200 bg-white px-4 py-3 text-left transition-all hover:border-blue-300 hover:shadow-sm"
									>
										<div className="font-semibold text-sm text-slate-900">{row.name}</div>
										<div className="mt-1 truncate font-mono text-xs text-slate-500">{row.assetKey || row.id}</div>
										<div className="mt-2 flex flex-wrap gap-1">
											<Tag style={{ fontSize: 10 }}>{row.type || "ASSET"}</Tag>
											<Tag color={classificationTagColor(row.classification, "default")} style={{ fontSize: 10 }}>
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
								))}
							</div>
						) : (
							<EmptyState title="无匹配资产" description="调整关键词或筛选条件后重试。" />
						)}
						<div className="mt-3 flex justify-end">
							<Pagination
								size="small"
								current={pageState.page}
								pageSize={pageState.size}
								total={pageState.total}
								showSizeChanger
								pageSizeOptions={[10, 20, 50]}
								onChange={(page, size) => void runSearch(false, page, size)}
							/>
						</div>
					</>
				) : (
					<EmptyState title="开始检索" description="输入关键词并点击搜索。" />
				)}
			</Card>
		</div>
	);
}
