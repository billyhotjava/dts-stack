import { DatabaseOutlined, DownOutlined } from "@ant-design/icons";
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
import { resolveAssetReadiness } from "./assetPortalUx.helpers";
import { AssetLedgerView } from "./assets/AssetLedgerView";
import { AssetTagsWorkspace } from "./assets/AssetTagsWorkspace";
import { GOVERNANCE_STATUS_DICT, resolveEnumLabel } from "./assets/assetEnumLabels";
import {
	ASSET_PORTAL_V2_ENABLED,
	type AssetRow,
	classificationTagColor,
	classificationText,
	formatTime,
	LAYER_META,
	LEDGER_PAGE_SIZE,
	normalizeLayer,
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
	ownerDept?: string;
	datasetName?: string;
	assetType?: string;
	assetKey?: string;
	assetTags?: CatalogTagDto[];
	datasetAssetKey?: string;
	datasetAssetTags?: CatalogTagDto[];
	classification?: string;
	warehouseLayer?: string;
	description?: string;
	service?: string;
	governanceStatus?: string;
	lifecycleStatus?: string;
	matchStatus?: string;
	columnCount?: number;
	syncStatus?: string;
	source?: string;
	updatedAt?: string;
};

const DATASET_TYPE_OPTIONS = [
	{ label: "全部数据源类型", value: "ALL" },
	{ label: "PostgreSQL", value: "POSTGRESQL" },
	{ label: "MySQL", value: "MYSQL" },
	{ label: "Oracle", value: "ORACLE" },
	{ label: "达梦", value: "DAMENG" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

const ASSET_KIND_LABELS: Record<string, string> = {
	ASSET: "数据资产",
	DATASET: "数据集",
	TABLE: "数据表",
	COLUMN: "字段",
};

const DATA_SOURCE_TYPE_LABELS: Record<string, string> = {
	POSTGRESQL: "PostgreSQL",
	MYSQL: "MySQL",
	ORACLE: "Oracle",
	DAMENG: "达梦",
	HIVE: "Hive",
	JDBC: "JDBC",
	FILE: "文件",
};

const METADATA_SOURCE_LABELS: Record<string, string> = {
	"dts-catalog": "平台登记",
	"openmetadata-cache": "自动采集",
	"assets-v2": "统一目录",
};

const dataSourceTypeText = (value?: string) => {
	const normalized = String(value || "")
		.trim()
		.toUpperCase();
	return DATA_SOURCE_TYPE_LABELS[normalized] || value || "来源待识别";
};

const metadataSourceText = (value?: string) => METADATA_SOURCE_LABELS[String(value || "").toLowerCase()] || "统一目录";

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

// 筛选区统一排版：所有字段=固定高度标签 + 撑满栅格列的控件，杜绝逐个 style 宽度导致的高低/间距漂移
const FILTER_LABEL_CLASS = "mb-1 block h-5 text-xs text-slate-500 leading-5";

const SEARCH_FORM_STORAGE_KEY = "catalog.search.form.v1";
const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v2";

type StoredSearchForm = {
	keyword: string;
	domain: string | undefined;
	datasetType: string | undefined;
	classification: string | undefined;
	warehouseLayer: string | undefined;
};

const EMPTY_SEARCH_FORM: StoredSearchForm = {
	keyword: "",
	domain: undefined,
	datasetType: undefined,
	classification: undefined,
	warehouseLayer: undefined,
};

function readStoredSearchForm(): StoredSearchForm {
	if (typeof window === "undefined") return EMPTY_SEARCH_FORM;
	try {
		const raw = localStorage.getItem(SEARCH_FORM_STORAGE_KEY);
		if (!raw) return EMPTY_SEARCH_FORM;
		const saved = JSON.parse(raw);
		const savedSourceType = saved?.datasetType || saved?.assetType;
		return {
			keyword: typeof saved?.keyword === "string" ? saved.keyword : "",
			domain: typeof saved?.domain === "string" && saved.domain && saved.domain !== "ALL" ? saved.domain : undefined,
			datasetType:
				typeof savedSourceType === "string" && savedSourceType && savedSourceType !== "ALL"
					? savedSourceType
					: undefined,
			classification:
				typeof saved?.classification === "string" && saved.classification && saved.classification !== "ALL"
					? saved.classification
					: undefined,
			warehouseLayer:
				typeof saved?.warehouseLayer === "string" && saved.warehouseLayer && saved.warehouseLayer !== "ALL"
					? saved.warehouseLayer
					: undefined,
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
	const [keyword, setKeyword] = useState(EMPTY_SEARCH_FORM.keyword);
	const [domain, setDomain] = useState<string | undefined>(EMPTY_SEARCH_FORM.domain);
	const [datasetType, setDatasetType] = useState<string | undefined>(EMPTY_SEARCH_FORM.datasetType);
	const [classification, setClassification] = useState<string | undefined>(EMPTY_SEARCH_FORM.classification);
	const [warehouseLayer, setWarehouseLayer] = useState<string | undefined>(EMPTY_SEARCH_FORM.warehouseLayer);
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

	// 回显兜底：URL 深链（?domain=uuid）进入时 domains 可能尚未加载完成或加载失败，
	// 此时若选中值不在选项中，antd 会直接回显 value（uuid 序列号）。兜底保证回显始终是名称。
	const domainOptions = useMemo(() => {
		const options = [
			{ label: "全部主题域", value: "ALL" },
			...domains.map((item) => ({ label: item.name, value: item.id })),
		];
		if (domain && domain !== "ALL" && !options.some((option) => option.value === domain)) {
			const matched = domains.find((item) => item.id === domain);
			options.push({ label: matched ? matched.name : "主题域", value: domain });
		}
		return options;
	}, [domains, domain]);

	const domainMap = useMemo(() => new Map(domains.map((item) => [item.id, item.name])), [domains]);

	const persistCurrentQuery = () => {
		const payload = {
			keyword,
			domain: domain || "",
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
			const saved = readStoredSearchForm();
			setKeyword(saved.keyword);
			setDomain(saved.domain);
			setDatasetType(saved.datasetType);
			setClassification(saved.classification);
			setWarehouseLayer(saved.warehouseLayer);
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
			setDatasetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : undefined);
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
			owner: item.owner || undefined,
			ownerDept: item.ownerDept || undefined,
			assetType: item.assetType || item.grantAssetType || item.type || undefined,
			assetKey: item.assetKey || item.fqn || undefined,
			assetTags: Array.isArray(item.assetTags) ? item.assetTags : [],
			classification: item.classification || undefined,
			warehouseLayer: item.warehouseLayer || undefined,
			description: item.description || undefined,
			service: item.service || undefined,
			governanceStatus: item.governanceStatus || undefined,
			lifecycleStatus: item.lifecycleStatus || undefined,
			matchStatus: item.matchStatus || undefined,
			columnCount: Number.isFinite(Number(item.columnCount)) ? Number(item.columnCount) : undefined,
			syncStatus: item.syncStatus || undefined,
			source: item.metadataSource || "assets-v2",
			updatedAt: item.lastSyncedAt || item.lastModifiedDate || item.createdDate || undefined,
		}));
	};

	// 合并台账后以 assets-v2 为唯一数据源：搜索、浏览、治理作业共用同一份资产列表与分页
	const runSearch = async (page = 1, size = LEDGER_PAGE_SIZE) => {
		const sequence = ++searchRequestSequence.current;
		const trimmed = keyword.trim();
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
		void runSearchRef.current();
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
		const datasetTypeParam = params.get("datasetType") || params.get("assetType");
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
			runSearchRef.current();
		}
	}, [
		deepLinkFilterKey,
		warehouseLayer,
		governanceFilter,
		domain,
		classification,
		datasetType,
		unclassifiedFilter,
		staleFilter,
		searchParams,
	]);

	// 与台账一致的 URL 回写：筛选选择后立即进入 URL（keyword 仍走本地缓存，避免每键重写）
	const urlFilterKey = [domain || "ALL", datasetType, classification, warehouseLayer].join("\u0000");
	// biome-ignore lint/correctness/useExhaustiveDependencies: 派生 key 聚合筛选状态，各状态已作为依赖
	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		if (domain && domain !== "ALL") params.set("domain", domain);
		else params.delete("domain");
		params.delete("assetType");
		if (datasetType && datasetType !== "ALL") params.set("datasetType", datasetType);
		else params.delete("datasetType");
		if (classification && classification !== "ALL") params.set("classification", classification);
		else params.delete("classification");
		if (warehouseLayer && warehouseLayer !== "ALL") params.set("layer", warehouseLayer);
		else params.delete("layer");
		if (view === "table") params.set("view", "table");
		else params.delete("view");
		const next = params.toString();
		if (next !== searchParams.toString()) setSearchParams(params, { replace: true });
	}, [urlFilterKey, view, searchParams, setSearchParams]);

	return (
		<div className="space-y-4">
			<div>
				<PageHeader title="数据资产目录" />
				<p className="mt-1 text-sm text-slate-500">
					识别资产的业务归属、治理状态和技术来源，进入详情完成治理或申请使用。
				</p>
			</div>

			<Card title="资产筛选">
				{!ASSET_PORTAL_V2_ENABLED ? (
					<Alert
						className="mb-3"
						type="warning"
						showIcon
						message="旧版资产门户未启用业务数据标签筛选"
						description="当前仍可按关键词检索数据集；如需按业务数据标签检索全部资产类型，请启用新版资产门户。"
					/>
				) : null}
				{/* 筛选字段栅格：主筛选（关键词/主题域/密级/分层）与次要筛选（类型/系统/标签）共用同一列宽与行距 */}
				<div className="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
					<div className="min-w-0">
						<label className={FILTER_LABEL_CLASS} htmlFor="catalog-search-keyword">
							关键词
						</label>
						<Input.Search
							id="catalog-search-keyword"
							className="w-full"
							placeholder="搜索资产名称、业务说明或技术标识"
							value={keyword}
							onChange={(event) => setKeyword(event.target.value)}
							onSearch={() => void runSearch()}
							allowClear
						/>
					</div>
					<div className="min-w-0">
						<label className={FILTER_LABEL_CLASS} htmlFor="catalog-search-domain">
							主题域
						</label>
						<Select
							id="catalog-search-domain"
							className="w-full"
							allowClear
							placeholder="请选择主题域"
							value={domain}
							onChange={(value) => setDomain(value === "ALL" ? undefined : value)}
							options={domainOptions}
						/>
					</div>
					<div className="min-w-0">
						<label className={FILTER_LABEL_CLASS} htmlFor="catalog-search-classification">
							密级
						</label>
						<Select
							id="catalog-search-classification"
							className="w-full"
							allowClear
							placeholder="请选择密级"
							value={classification}
							onChange={(value) => setClassification(value === "ALL" ? undefined : value)}
							options={CLASSIFICATION_OPTIONS}
						/>
					</div>
					<div className="min-w-0">
						<label className={FILTER_LABEL_CLASS} htmlFor="catalog-search-layer">
							分层
						</label>
						<Select
							id="catalog-search-layer"
							className="w-full"
							allowClear
							placeholder="请选择分层"
							value={warehouseLayer}
							onChange={(value) => setWarehouseLayer(value === "ALL" ? undefined : value)}
							options={LAYER_OPTIONS}
						/>
					</div>
					<div className="min-w-0">
						<label className={FILTER_LABEL_CLASS} htmlFor="catalog-search-source-type">
							数据源类型
						</label>
						<Select
							id="catalog-search-source-type"
							className="w-full"
							allowClear
							placeholder="请选择数据源类型"
							value={datasetType}
							onChange={(value) => setDatasetType(value === "ALL" ? undefined : value)}
							options={DATASET_TYPE_OPTIONS}
						/>
					</div>
					<AssetTagFilter
						className="min-w-0 sm:col-span-2"
						value={selectedTagIds}
						disabled={!ASSET_PORTAL_V2_ENABLED}
						onChange={(nextIds) => {
							setSearchParams(writeTagIds(searchParams, nextIds), { replace: true });
						}}
					/>
				</div>
				{/* 动作区与筛选字段分栏，避免按钮被卷进字段网格造成行高不齐 */}
				<div className="mt-4 flex flex-wrap items-center justify-end gap-2 border-slate-100 border-t pt-3">
					<Dropdown
						menu={{
							items: [
								{ key: "save", label: "保存当前条件", onClick: saveCurrentQuery },
								{ key: "restore", label: "恢复已存条件", onClick: restoreSavedQuery },
								{ type: "divider" },
								{
									key: "apply-ledger",
									label: "应用资产清单筛选",
									onClick: applyAssetFilters,
								},
							],
						}}
					>
						<Button>
							条件 <DownOutlined />
						</Button>
					</Dropdown>
					<Button type="primary" onClick={() => void runSearch()} loading={loading}>
						筛选资产
					</Button>
				</div>
			</Card>

			<Card
				title={
					<span className="flex flex-wrap items-center gap-3">
						资产目录
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
								{ label: "资产名片", value: "card" },
								{ label: "治理视图", value: "table" },
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
								onAssetChanged={() => void runSearch(pageState.page, pageState.size)}
							/>
						) : results.length ? (
							<div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
								{results.map((row) => {
									const readiness = resolveAssetReadiness(row);
									const layer = LAYER_META[normalizeLayer(row.warehouseLayer)];
									const domainName = row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined);
									const responsibility =
										row.owner && row.ownerDept && row.owner !== row.ownerDept
											? `${row.owner}（${row.ownerDept}）`
											: row.owner || row.ownerDept || "待明确";
									return (
										<Link
											key={row.id}
											to={`/catalog/datasets/${row.id}`}
											className="group cursor-pointer rounded-xl border border-slate-200 bg-white p-4 text-left transition-all hover:border-blue-300 hover:shadow-sm"
										>
											<div className="flex items-start justify-between gap-3">
												<div className="min-w-0 flex-1">
													<div className="flex flex-wrap items-center gap-2">
														<DatabaseOutlined className="text-blue-500" />
														<span className="truncate font-semibold text-slate-900 group-hover:text-blue-600">
															{row.name}
														</span>
														<Tag>
															{ASSET_KIND_LABELS[String(row.assetType || row.assetKind).toUpperCase()] || "数据资产"}
														</Tag>
													</div>
													<div className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
														{row.description || "暂无业务说明"}
													</div>
												</div>
												<Tag color={readiness.color}>{readiness.label}</Tag>
											</div>

											<div className="mt-3 grid grid-cols-1 gap-x-4 gap-y-2 rounded-lg bg-slate-50 px-3 py-2 text-xs sm:grid-cols-2">
												<div className="min-w-0">
													<span className="text-slate-400">主题域</span>
													<div className="truncate font-medium text-slate-700">{domainName || "待归域"}</div>
												</div>
												<div className="min-w-0">
													<span className="text-slate-400">责任归属</span>
													<div className="truncate font-medium text-slate-700">{responsibility}</div>
												</div>
												<div className="min-w-0">
													<span className="text-slate-400">数据分层</span>
													<div className="truncate font-medium text-slate-700">
														{layer.code ? `${layer.label}（${layer.code}）` : layer.label}
													</div>
												</div>
												<div className="min-w-0">
													<span className="text-slate-400">来源系统</span>
													<div className="truncate font-medium text-slate-700">
														{row.service || dataSourceTypeText(row.type)}
													</div>
												</div>
											</div>

											<div className="mt-3 flex flex-wrap items-center gap-1.5">
												<Tag color={classificationTagColor(row.classification, "default")}>
													密级：{classificationText(row.classification)}
												</Tag>
												<Tag color={row.governanceStatus === "GOVERNED" ? "green" : "gold"}>
													治理状态：{resolveEnumLabel(GOVERNANCE_STATUS_DICT, row.governanceStatus)}
												</Tag>
												<Tag>{dataSourceTypeText(row.type)}</Tag>
												{row.columnCount !== undefined ? <Tag>{row.columnCount} 个字段</Tag> : null}
											</div>
											<AssetTagChips tags={row.assetTags || []} variant="inline" />

											<div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-slate-100 border-t pt-2 text-[11px] text-slate-400">
												<span className="min-w-0 flex-1 truncate font-mono">{row.assetKey || row.id}</span>
												<span className="shrink-0">
													{metadataSourceText(row.source)} · 更新于 {formatTime(row.updatedAt)}
												</span>
											</div>
										</Link>
									);
								})}
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
								onChange={(page, size) => void runSearch(page, size)}
							/>
						</div>
					</>
				) : (
					<EmptyState title="正在加载数据资产" description="正在获取当前用户可见的数据资产。" />
				)}
			</Card>
		</div>
	);
}
