import { Button, Card, Input, Select, Tabs } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import type { CatalogTagDto } from "@/api/catalogTagsApi";
import { listCatalogAssetsV2, listDomains } from "@/api/platformApi";
import { AssetTagFilter } from "@/components/catalog/tags/AssetTagFilter";
import { readTagIds, writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { PageHeader } from "@/components/page-header";
import { type AssetDirectoryRow, AssetLedgerView } from "./assets/AssetLedgerView";
import { resolveModelDataManagementFocus, resolveModelingReturnTo } from "@/pages/data-modeling/prototype/modelDataManagementLink";
import { ModelDataOperationsPanel } from "./assets/ModelDataOperationsPanel";
import { AssetTagsWorkspace } from "./assets/AssetTagsWorkspace";
import { ASSET_PORTAL_V2_ENABLED, LEDGER_PAGE_SIZE } from "./assets/assetPageShared";
import { buildAssetV2Query } from "./assets/assetV2Query";

type AssetDirectoryFilters = {
	keyword: string;
	assetFamily: string;
	domainId?: string;
	datasetType?: string;
	classification?: string;
	warehouseLayer?: string;
	governanceStatus?: string;
	eligibility?: string;
	servingStatus?: string;
	qualityStatus?: string;
	unclassified: boolean;
	stale: boolean;
	tagIds: string[];
};

const ASSET_FAMILY_OPTIONS = [
	{ label: "全部资产", value: "ALL" },
	{ label: "数据表", value: "DATASET" },
	{ label: "数据模型", value: "SEMANTIC_MODEL" },
	{ label: "指标", value: "GOV_INDICATOR" },
	{ label: "分析数据集", value: "BI_DATASET" },
	{ label: "看板", value: "SCREEN" },
	{ label: "数据产品", value: "DATA_PRODUCT" },
	{ label: "数据服务", value: "API_SERVICE" },
];

const DATASET_TYPE_OPTIONS = [
	{ label: "PostgreSQL", value: "POSTGRESQL" },
	{ label: "MySQL", value: "MYSQL" },
	{ label: "Oracle", value: "ORACLE" },
	{ label: "达梦", value: "DAMENG" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

const CLASSIFICATION_OPTIONS = [
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const LAYER_OPTIONS = [
	{ label: "ODS", value: "ODS" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const ELIGIBILITY_OPTIONS = [
	{ label: "可消费", value: "ELIGIBLE" },
	{ label: "有条件可消费", value: "CONDITIONAL" },
	{ label: "不可消费", value: "BLOCKED" },
];

const SERVING_STATUS_OPTIONS = [
	{ label: "已同步", value: "SYNCED" },
	{ label: "同步中", value: "SYNC_PENDING" },
	{ label: "同步失败", value: "SYNC_FAILED" },
	{ label: "不适用", value: "NOT_APPLICABLE" },
];

const QUALITY_STATUS_OPTIONS = [
	{ label: "已通过", value: "PASSED" },
	{ label: "未通过", value: "FAILED" },
	{ label: "暂无证据", value: "UNKNOWN" },
];

const FILTER_PARAM_KEYS = [
	"keyword",
	"family",
	"domain",
	"datasetType",
	"assetType",
	"classification",
	"layer",
	"governance",
	"eligibility",
	"servingStatus",
	"qualityStatus",
	"unclassified",
	"stale",
	"view",
] as const;

const concreteParam = (value: string | null) => (value && value !== "ALL" ? value : undefined);

function readUrlFilters(searchParams: URLSearchParams): AssetDirectoryFilters {
	return {
		keyword: searchParams.get("keyword") || "",
		assetFamily: concreteParam(searchParams.get("family")) || "ALL",
		domainId: concreteParam(searchParams.get("domain")),
		datasetType: concreteParam(searchParams.get("datasetType") || searchParams.get("assetType")),
		classification: concreteParam(searchParams.get("classification")),
		warehouseLayer: concreteParam(searchParams.get("layer")),
		governanceStatus: concreteParam(searchParams.get("governance")),
		eligibility: concreteParam(searchParams.get("eligibility")),
		servingStatus: concreteParam(searchParams.get("servingStatus")),
		qualityStatus: concreteParam(searchParams.get("qualityStatus")),
		unclassified: searchParams.get("unclassified") === "1",
		stale: searchParams.get("stale") === "1",
		tagIds: ASSET_PORTAL_V2_ENABLED ? readTagIds(searchParams) : [],
	};
}

function setFilterParam(params: URLSearchParams, key: string, value?: string) {
	if (value) params.set(key, value);
	else params.delete(key);
}

function normalizeAssetRows(payload: any): AssetDirectoryRow[] {
	const content = Array.isArray(payload?.content) ? payload.content : [];
	return content.map((item: any, index: number) => ({
		id: String(item.id || item.assetKey || item.fqn || `asset-${index}`),
		name: String(item.displayName || item.name || item.table || item.fqn || "-"),
		type: String(item.type || item.sourceType || ""),
		assetType: item.assetType || item.grantAssetType || undefined,
		assetKey: item.assetKey || item.fqn || undefined,
		assetFamily: item.assetFamily || item.assetType || item.grantAssetType || "DATASET",
		subtype: item.subtype || undefined,
		catalogIdentity: item.catalogIdentity || undefined,
		detailRoute: item.detailRoute || undefined,
		relationships: Array.isArray(item.relationships) ? item.relationships : [],
		assetTags: Array.isArray(item.assetTags) ? (item.assetTags as CatalogTagDto[]) : [],
		domainId: item.domainId ? String(item.domainId) : undefined,
		domain: item.domainName || item.domain || undefined,
		classification: item.classification || undefined,
		warehouseLayer: item.warehouseLayer || undefined,
		owner: item.owner || item.businessOwner || undefined,
		ownerDept: item.ownerDept || undefined,
		governanceStatus: item.governanceStatus || undefined,
		statusAxes: item.statusAxes || undefined,
		consumptionEligibility: item.consumptionEligibility || "CONDITIONAL",
		eligibilityReasons: Array.isArray(item.eligibilityReasons) ? item.eligibilityReasons : [],
		projectionUpdatedAt: item.projectionUpdatedAt || undefined,
		modelRefs: Array.isArray(item.modelRefs) ? item.modelRefs : [],
		servingSync: item.servingSync || { status: "NOT_APPLICABLE", attempts: 0 },
		qualityStatus: item.qualityStatus || "UNKNOWN",
		lifecycleStatus: item.lifecycleStatus || undefined,
		matchStatus: item.matchStatus || undefined,
		metadataSource: item.metadataSource || "assets-v2",
		description: item.description || undefined,
		hiveDatabase: item.hiveDatabase || item.database || undefined,
		hiveTable: item.hiveTable || item.table || undefined,
		service: item.service || item.sourceSystem || undefined,
		columnCount: Number.isFinite(Number(item.columnCount)) ? Number(item.columnCount) : undefined,
		syncStatus: item.syncStatus || undefined,
		updatedAt: item.lastSyncedAt || item.lastModifiedDate || item.updatedAt || item.createdDate || undefined,
		snapshotTime: item.snapshotTime || undefined,
	}));
}

export default function DataSearchPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const activeTab = searchParams.get("tab") === "catalog-tags" ? "catalog-tags" : "asset-directory";
	const changeTab = (key: string) => {
		const next = new URLSearchParams(searchParams);
		if (key === "catalog-tags") next.set("tab", "catalog-tags");
		else next.delete("tab");
		setSearchParams(next);
	};

	return (
		<div className="min-w-0 space-y-3">
			<PageHeader title="数据资产目录" />
			<Tabs
				activeKey={activeTab}
				onChange={changeTab}
				items={[
					{ key: "asset-directory", label: "资产目录" },
					{ key: "catalog-tags", label: "数据标签" },
				]}
			/>
			{activeTab === "asset-directory" && searchParams.get("modelSpecId") ? <ModelDataOperationsPanel
				key={`${searchParams.get("modelSpecId")}:${searchParams.get("environment") || "dev"}`}
				modelSpecId={searchParams.get("modelSpecId")!} environment={searchParams.get("environment") || "dev"} candidateId={searchParams.get("candidateId") || undefined}
				focus={resolveModelDataManagementFocus(searchParams.get("focus"))} returnTo={resolveModelingReturnTo(searchParams.get("returnTo"))} /> : null}
			{activeTab === "catalog-tags" ? <AssetTagsWorkspace /> : <DataAssetDirectoryPage />}
		</div>
	);
}

function DataAssetDirectoryPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const searchParamsKey = searchParams.toString();
	const urlFilters = useMemo(() => readUrlFilters(new URLSearchParams(searchParamsKey)), [searchParamsKey]);
	const [keyword, setKeyword] = useState("");
	const [assetFamily, setAssetFamily] = useState("ALL");
	const [domain, setDomain] = useState<string | undefined>(undefined);
	const [datasetType, setDatasetType] = useState<string | undefined>(undefined);
	const [classification, setClassification] = useState<string | undefined>(undefined);
	const [warehouseLayer, setWarehouseLayer] = useState<string | undefined>(undefined);
	const [eligibility, setEligibility] = useState<string | undefined>(undefined);
	const [servingStatus, setServingStatus] = useState<string | undefined>(undefined);
	const [qualityStatus, setQualityStatus] = useState<string | undefined>(undefined);
	const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
	const [loading, setLoading] = useState(false);
	const [assetRows, setAssetRows] = useState<AssetDirectoryRow[]>([]);
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: LEDGER_PAGE_SIZE, total: 0 });
	const requestSequence = useRef(0);

	useEffect(() => {
		void (async () => {
			try {
				const response: any = await listDomains(0, 200, "");
				const content = Array.isArray(response?.content) ? response.content : [];
				setDomains(
					content
						.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
						.filter((item: { id: string; name: string }) => item.id && item.name),
				);
			} catch {
				// 全局请求拦截器负责错误提示；目录仍可展示未设置业务归属数据域的资产。
			}
		})();
	}, []);

	const domainOptions = useMemo(() => {
		const options = domains.map((item) => ({ label: item.name, value: item.id }));
		if (domain && !options.some((option) => option.value === domain)) {
			options.push({ label: domains.find((item) => item.id === domain)?.name || "当前业务归属数据域", value: domain });
		}
		return options;
	}, [domains, domain]);

	const domainMap = useMemo(() => new Map(domains.map((item) => [item.id, item.name])), [domains]);

	const loadAssets = useCallback(async (filters: AssetDirectoryFilters, page: number, size: number) => {
		const sequence = ++requestSequence.current;
		setLoading(true);
		try {
			const response = await listCatalogAssetsV2(
				buildAssetV2Query(
					{
						keyword: filters.keyword,
						assetFamily: filters.assetFamily,
						tagIds: filters.tagIds,
						domainId: filters.domainId,
						assetType: filters.datasetType,
						classification: filters.classification,
						warehouseLayer: filters.warehouseLayer,
						governanceStatus: filters.governanceStatus,
						eligibility: filters.eligibility,
						servingStatus: filters.servingStatus,
						qualityStatus: filters.qualityStatus,
						unclassified: filters.unclassified,
						stale: filters.stale,
					},
					page - 1,
					size,
				),
			);
			if (sequence !== requestSequence.current) return;
			const rows = normalizeAssetRows(response);
			setAssetRows(rows);
			setPageState({
				page: Number(response?.page ?? page - 1) + 1,
				size: Number(response?.size ?? size),
				total: Number(response?.total ?? rows.length),
			});
		} catch {
			if (sequence !== requestSequence.current) return;
			setAssetRows([]);
			setPageState((current) => ({ ...current, page, size, total: 0 }));
		} finally {
			if (sequence === requestSequence.current) setLoading(false);
		}
	}, []);

	useEffect(() => {
		setKeyword(urlFilters.keyword);
		setAssetFamily(urlFilters.assetFamily);
		setDomain(urlFilters.domainId);
		setDatasetType(urlFilters.datasetType);
		setClassification(urlFilters.classification);
		setWarehouseLayer(urlFilters.warehouseLayer);
		setEligibility(urlFilters.eligibility);
		setServingStatus(urlFilters.servingStatus);
		setQualityStatus(urlFilters.qualityStatus);
		setSelectedTagIds(urlFilters.tagIds);
		void loadAssets(urlFilters, 1, LEDGER_PAGE_SIZE);
	}, [loadAssets, urlFilters]);

	const handleSearch = () => {
		const next = new URLSearchParams(searchParams);
		setFilterParam(next, "keyword", keyword.trim());
		setFilterParam(next, "family", assetFamily === "ALL" ? undefined : assetFamily);
		setFilterParam(next, "domain", domain);
		setFilterParam(next, "datasetType", datasetType);
		next.delete("assetType");
		setFilterParam(next, "classification", classification);
		setFilterParam(next, "layer", warehouseLayer);
		setFilterParam(next, "eligibility", eligibility);
		setFilterParam(next, "servingStatus", servingStatus);
		setFilterParam(next, "qualityStatus", qualityStatus);
		next.delete("view");
		const tagged = writeTagIds(next, selectedTagIds);
		if (tagged.toString() === searchParams.toString()) {
			void loadAssets(
				{
					...urlFilters,
					keyword: keyword.trim(),
					assetFamily,
					domainId: domain,
					datasetType,
					classification,
					warehouseLayer,
					eligibility,
					servingStatus,
					qualityStatus,
					tagIds: selectedTagIds,
				},
				1,
				pageState.size,
			);
			return;
		}
		setSearchParams(tagged);
	};

	const handleReset = () => {
		setKeyword("");
		setAssetFamily("ALL");
		setDomain(undefined);
		setDatasetType(undefined);
		setClassification(undefined);
		setWarehouseLayer(undefined);
		setEligibility(undefined);
		setServingStatus(undefined);
		setQualityStatus(undefined);
		setSelectedTagIds([]);
		const params = new URLSearchParams(searchParams);
		for (const key of FILTER_PARAM_KEYS) params.delete(key);
		params.delete("unclassified");
		params.delete("stale");
		const next = writeTagIds(params, []);
		if (next.toString() === searchParams.toString()) {
			void loadAssets(
				{
					keyword: "",
					assetFamily: "ALL",
					unclassified: false,
					stale: false,
					tagIds: [],
				},
				1,
				pageState.size,
			);
			return;
		}
		setSearchParams(next);
	};

	return (
		<div className="min-w-0 space-y-3">
			<Card size="small" className="min-w-0" styles={{ body: { padding: 12 } }}>
				<div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 2xl:grid-cols-10">
					<Input.Search
						id="catalog-search-keyword"
						aria-label="关键词"
						placeholder="搜索资产名称、业务说明或技术标识"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						onSearch={handleSearch}
						allowClear
					/>
					<Select
						id="catalog-search-asset-family"
						aria-label="资产家族"
						value={assetFamily}
						onChange={(value) => {
							setAssetFamily(value);
							if (value !== "ALL" && value !== "DATASET") setDatasetType(undefined);
						}}
						options={ASSET_FAMILY_OPTIONS}
					/>
					<Select
						id="catalog-search-domain"
						aria-label="业务归属数据域"
						allowClear
						showSearch
						optionFilterProp="label"
						placeholder="业务归属数据域"
						value={domain}
						onChange={setDomain}
						options={domainOptions}
					/>
					<Select
						id="catalog-search-source-type"
						aria-label="数据源类型"
						allowClear
						placeholder="数据源类型"
						value={datasetType}
						onChange={setDatasetType}
						disabled={assetFamily !== "ALL" && assetFamily !== "DATASET"}
						options={DATASET_TYPE_OPTIONS}
					/>
					<Select
						id="catalog-search-classification"
						aria-label="密级"
						allowClear
						placeholder="密级"
						value={classification}
						onChange={setClassification}
						options={CLASSIFICATION_OPTIONS}
					/>
					<Select
						id="catalog-search-layer"
						aria-label="数据分层"
						allowClear
						placeholder="数据分层"
						value={warehouseLayer}
						onChange={setWarehouseLayer}
						options={LAYER_OPTIONS}
					/>
					<Select
						id="catalog-search-eligibility"
						aria-label="消费资格"
						allowClear
						placeholder="消费资格"
						value={eligibility}
						onChange={setEligibility}
						options={ELIGIBILITY_OPTIONS}
					/>
					<Select
						id="catalog-search-serving-status"
						aria-label="服务状态"
						allowClear
						placeholder="服务状态"
						value={servingStatus}
						onChange={setServingStatus}
						options={SERVING_STATUS_OPTIONS}
					/>
					<Select
						id="catalog-search-quality-status"
						aria-label="质量状态"
						allowClear
						placeholder="质量状态"
						value={qualityStatus}
						onChange={setQualityStatus}
						options={QUALITY_STATUS_OPTIONS}
					/>
					<AssetTagFilter
						compact
						className="min-w-0"
						value={selectedTagIds}
						disabled={!ASSET_PORTAL_V2_ENABLED}
						onChange={setSelectedTagIds}
					/>
				</div>
				<div className="mt-2 flex justify-end gap-2">
					<Button onClick={handleReset}>重置</Button>
					<Button type="primary" loading={loading} onClick={handleSearch}>
						查询
					</Button>
				</div>
			</Card>

			<Card
				size="small"
				className="min-w-0"
				title="数据资产"
				extra={<span className="text-xs font-normal text-slate-500">共 {pageState.total} 条</span>}
				styles={{ body: { padding: 12 } }}
			>
				<AssetLedgerView
					records={assetRows}
					domainMap={domainMap}
					loading={loading}
					page={pageState.page}
					pageSize={pageState.size}
					total={pageState.total}
					onPageChange={(page, size) => void loadAssets(urlFilters, page, size)}
					onAssetChanged={() => void loadAssets(urlFilters, pageState.page, pageState.size)}
				/>
			</Card>
		</div>
	);
}
