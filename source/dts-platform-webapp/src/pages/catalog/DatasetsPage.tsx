import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Alert, Button, Card, Collapse, Input, Layout, Pagination, Progress, Select, Space, Spin, Tag, Tooltip, Tree } from "antd";
import {
	ApartmentOutlined,
	BranchesOutlined,
	DatabaseOutlined,
	ProfileOutlined,
	ReloadOutlined,
	SafetyCertificateOutlined,
	SearchOutlined,
	TableOutlined,
	WarningOutlined,
} from "@ant-design/icons";
import { EmptyState } from "@/components/empty-state";
import {
	getCatalogAssetsV2Diagnostics,
	getCatalogReconciliation,
	getDomainTree,
	listCatalogAssetsV2,
	listDatasets,
	listDomains,
	syncCatalogAssetsV2,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

type AssetRow = {
	id: string;
	name: string;
	type: string;
	domainId?: string;
	domain?: string;
	classification?: string;
	warehouseLayer?: string;
	status?: string;
	lifecycleStatus?: string;
	owner?: string;
	ownerDept?: string;
	governanceStatus?: string;
	matchStatus?: string;
	description?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	updatedAt?: string;
	snapshotTime?: string;
};

type DomainNode = { id?: string; name?: string; code?: string; children?: DomainNode[] };

const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
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
	{ label: "SOURCE", value: "SOURCE" },
	{ label: "ODS", value: "ODS" },
	{ label: "STG", value: "STG" },
	{ label: "DWD", value: "DWD" },
	{ label: "DIM", value: "DIM" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const GOVERNANCE_OPTIONS = [
	{ label: "全部治理状态", value: "ALL" },
	{ label: "已治理", value: "GOVERNED" },
	{ label: "待认领", value: "PENDING_CLAIM" },
	{ label: "待定级", value: "PENDING_CLASSIFICATION" },
	{ label: "待归域", value: "PENDING_DOMAIN" },
	{ label: "停用", value: "DISABLED" },
];

const MATCH_OPTIONS = [
	{ label: "全部映射", value: "ALL" },
	{ label: "已映射", value: "MATCHED" },
	{ label: "未匹配", value: "UNMATCHED" },
	{ label: "人工确认", value: "MANUAL_REVIEW" },
];

const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v1";
const ASSET_PORTAL_V2_ENABLED = import.meta.env.VITE_CATALOG_ASSET_PORTAL_V2 !== "false";

const CLASSIFICATION_LABEL: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

const LAYER_META: Record<string, { label: string; color: string; tone: string }> = {
	SOURCE: { label: "来源", color: "magenta", tone: "border-pink-200 bg-pink-50/60" },
	ODS: { label: "ODS", color: "default", tone: "border-slate-200 bg-slate-50/70" },
	STG: { label: "STG", color: "geekblue", tone: "border-indigo-200 bg-indigo-50/60" },
	DWD: { label: "DWD", color: "blue", tone: "border-blue-200 bg-blue-50/60" },
	DIM: { label: "DIM", color: "purple", tone: "border-purple-200 bg-purple-50/60" },
	DWS: { label: "DWS", color: "cyan", tone: "border-cyan-200 bg-cyan-50/60" },
	ADS: { label: "ADS", color: "green", tone: "border-green-200 bg-green-50/60" },
	OTHER: { label: "未分层", color: "default", tone: "border-slate-200 bg-white" },
};

const LAYER_ORDER = ["SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS", "OTHER"];

type ReconciliationAssertion = {
	code?: string;
	name?: string;
	passed?: boolean;
	severity?: string;
	detail?: string;
	suggestion?: string;
};

type ReconciliationResult = {
	generatedAt?: string;
	assertionCount?: number;
	failedCount?: number;
	errorCount?: number;
	warningCount?: number;
	assertions?: ReconciliationAssertion[];
	regressionChecklist?: Array<{ code?: string; name?: string; route?: string; description?: string }>;
};

const normalizeLayer = (value?: string) => {
	const normalized = String(value || "").trim().toUpperCase();
	return normalized && LAYER_META[normalized] ? normalized : "OTHER";
};

const classificationText = (value?: string) => {
	const normalized = String(value || "").trim().toUpperCase();
	return normalized ? CLASSIFICATION_LABEL[normalized] || normalized : "未设定";
};

const formatTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const buildTreeNodes = (nodes: DomainNode[], prefix = "domain"): any[] =>
	nodes.map((node, index) => ({
		key: node.id || `fallback-${prefix}-${index}`,
		title: node.name ?? node.code ?? "未命名",
		children: node.children?.length ? buildTreeNodes(node.children, `${prefix}-${index}`) : undefined,
	}));

const MetricTile = ({
	icon,
	label,
	value,
	footnote,
	tone = "text-slate-700",
}: {
	icon: ReactNode;
	label: string;
	value: ReactNode;
	footnote?: string;
	tone?: string;
}) => (
	<div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
		<div className="flex items-center justify-between gap-3">
			<div className="text-xs text-slate-500">{label}</div>
			<div className={`text-lg ${tone}`}>{icon}</div>
		</div>
		<div className="mt-2 text-2xl font-semibold leading-none text-slate-900">{value}</div>
		{footnote ? <div className="mt-2 truncate text-xs text-slate-500">{footnote}</div> : null}
	</div>
);

export default function Page() {
	const router = useRouter();
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string>("ALL");
	const [classification, setClassification] = useState<string>("ALL");
	const [warehouseLayer, setWarehouseLayer] = useState<string>("ALL");
	const [governanceStatus, setGovernanceStatus] = useState<string>("ALL");
	const [matchStatus, setMatchStatus] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [syncing, setSyncing] = useState(false);
	const [diagnosticsLoading, setDiagnosticsLoading] = useState(false);
	const [diagnostics, setDiagnostics] = useState<any | null>(null);
	const [records, setRecords] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: 18, total: 0 });
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
	const [reconciliationLoading, setReconciliationLoading] = useState(false);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(false);
	const requestSeqRef = useRef(0);

	useEffect(() => {
		try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) return;
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL");
			setWarehouseLayer(typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL");
			setGovernanceStatus(typeof saved?.governanceStatus === "string" && saved.governanceStatus ? saved.governanceStatus : "ALL");
			setMatchStatus(typeof saved?.matchStatus === "string" && saved.matchStatus ? saved.matchStatus : "ALL");
		} catch {
			// ignore malformed cache
		}
	}, []);

	useEffect(() => {
		void loadDomains();
	}, []);

	useEffect(() => {
		void (async () => {
			setTreeLoading(true);
			try {
				const tree = (await getDomainTree()) as any;
				const data = Array.isArray(tree) ? tree : Array.isArray(tree?.data) ? tree.data : [];
				setDomainTree(data);
			} catch {
				// error toast handled by global interceptor
			} finally {
				setTreeLoading(false);
			}
		})();
	}, []);

	useEffect(() => {
		void loadReconciliation();
	}, []);

	useEffect(() => {
		const timer = window.setTimeout(() => {
			void loadDatasets(1, pageState.size);
		}, 280);
		return () => window.clearTimeout(timer);
	}, [keyword, domain, assetType, classification, warehouseLayer, governanceStatus, matchStatus, pageState.size]);

	useEffect(() => {
		const payload = {
			keyword,
			domain: domain || "",
			assetType,
			classification,
			warehouseLayer,
			governanceStatus,
			matchStatus,
		};
		localStorage.setItem(DATASET_FILTER_STORAGE_KEY, JSON.stringify(payload));
	}, [keyword, domain, assetType, classification, warehouseLayer, governanceStatus, matchStatus]);

	const domainMap = useMemo(() => new Map(domains.map((item) => [item.id, item.name])), [domains]);

	const loadDomains = async () => {
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
	};

	const loadReconciliation = async () => {
		setReconciliationLoading(true);
		try {
			const result: any = await getCatalogReconciliation(20);
			setReconciliation((result || null) as ReconciliationResult | null);
		} catch {
			// error toast handled by global interceptor
			setReconciliation(null);
		} finally {
			setReconciliationLoading(false);
		}
	};

	const loadDatasets = async (page = 1, size = 18) => {
		const reqId = ++requestSeqRef.current;
		setLoading(true);
		try {
			const resp: any = ASSET_PORTAL_V2_ENABLED ? await listCatalogAssetsV2({
				page: page - 1,
				size,
				keyword: keyword.trim() || undefined,
				domainId: domain && domain !== "ALL" ? domain : undefined,
				type: assetType === "ALL" ? undefined : assetType,
				classification: classification === "ALL" ? undefined : classification,
				warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
				governanceStatus: governanceStatus === "ALL" ? undefined : governanceStatus,
				matchStatus: matchStatus === "ALL" ? undefined : matchStatus,
			}) : await listDatasets({
				page: page - 1,
				size,
				keyword: keyword.trim() || undefined,
				domainId: domain && domain !== "ALL" ? domain : undefined,
				type: assetType === "ALL" ? undefined : assetType,
				classification: classification === "ALL" ? undefined : classification,
				warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
				sortBy: "lastModifiedDate",
				sortDir: "desc",
			});
			if (reqId !== requestSeqRef.current) {
				return;
			}
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setRecords(
				content.map((item: any) => ({
					id: String(item.id || ""),
					name: item.displayName || item.table || item.name || item.fqn || "-",
					type: item.type || "-",
					domainId: item.domainId ? String(item.domainId) : undefined,
					domain: item.domainName || (item.domainId ? domainMap.get(String(item.domainId)) : undefined),
					classification: item.classification || undefined,
					warehouseLayer: item.warehouseLayer || undefined,
					status: item.syncStatus === "ERROR" || item.enabled === false ? "异常" : "启用",
					lifecycleStatus: item.lifecycleStatus || undefined,
					governanceStatus: item.governanceStatus || undefined,
					matchStatus: item.matchStatus || undefined,
					owner: item.owner || undefined,
					ownerDept: item.ownerDept || undefined,
					description: item.description || undefined,
					hiveDatabase: item.database || item.schema || item.hiveDatabase || undefined,
					hiveTable: item.table || item.hiveTable || undefined,
					updatedAt: item.lastSyncedAt || item.lastModifiedDate || item.createdDate || undefined,
					snapshotTime: item.snapshotTime || undefined,
				})),
			);
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch {
			if (reqId !== requestSeqRef.current) {
				return;
			}
			// error toast handled by global interceptor
			setRecords([]);
		} finally {
			if (reqId === requestSeqRef.current) {
				setLoading(false);
			}
		}
	};

	const selectedDomainName = domain ? domainMap.get(domain) || "当前主题域" : "全部主题域";

	const layerLanes = useMemo(() => {
		const grouped = new Map<string, AssetRow[]>();
		for (const key of LAYER_ORDER) {
			grouped.set(key, []);
		}
		for (const row of records) {
			const key = normalizeLayer(row.warehouseLayer);
			grouped.set(key, [...(grouped.get(key) || []), row]);
		}
		return LAYER_ORDER.map((key) => ({ key, meta: LAYER_META[key], items: grouped.get(key) || [] }));
	}, [records]);

	const typeStats = useMemo(() => {
		const map = new Map<string, number>();
		records.forEach((row) => {
			const key = row.type || "未知";
			map.set(key, (map.get(key) || 0) + 1);
		});
		return [...map.entries()].sort((a, b) => b[1] - a[1]);
	}, [records]);

	const domainStats = useMemo(() => {
		const map = new Map<string, number>();
		records.forEach((row) => {
			const key = row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域";
			map.set(key, (map.get(key) || 0) + 1);
		});
		return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
	}, [records, domainMap]);

	const unclassifiedCount = records.filter((row) => !row.classification).length;
	const missingDomainCount = records.filter((row) => !row.domain && !row.domainId).length;
	const staleCount = records.filter((row) => String(row.lifecycleStatus || "").toUpperCase() === "STALE" || row.status === "停用").length;
	const activeCount = records.filter((row) => row.status === "启用").length;
	const governanceCoverage = records.length ? Math.round(((records.length - unclassifiedCount - missingDomainCount) / Math.max(records.length, 1)) * 100) : 0;

	const treeData = useMemo(
		() => [
			{
				key: "ALL",
				title: "全部资产",
				children: buildTreeNodes(domainTree),
			},
		],
		[domainTree],
	);

	const failedAssertions = Array.isArray(reconciliation?.assertions)
		? reconciliation.assertions.filter((item) => item.passed === false)
		: [];

	const reconciliationContent = reconciliation ? (
		<Space direction="vertical" size={12} className="w-full">
			<div className="grid gap-3 md:grid-cols-4">
				<MetricTile icon={<SafetyCertificateOutlined />} label="断言总数" value={Number(reconciliation.assertionCount || 0)} />
				<MetricTile icon={<WarningOutlined />} label="失败项" value={Number(reconciliation.failedCount || 0)} tone="text-red-600" />
				<MetricTile icon={<WarningOutlined />} label="错误级" value={Number(reconciliation.errorCount || 0)} tone="text-red-600" />
				<MetricTile icon={<WarningOutlined />} label="告警级" value={Number(reconciliation.warningCount || 0)} tone="text-amber-600" />
			</div>
			{failedAssertions.length ? (
				<div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
					{failedAssertions.slice(0, 6).map((item) => (
						<div key={item.code || item.name}>
							[{item.code || "-"}] {item.name || "未命名检查"}：{item.detail || "-"}；建议：{item.suggestion || "-"}
						</div>
					))}
				</div>
			) : (
				<Alert type="success" showIcon message="一致性断言通过，未发现阻断项。" />
			)}
			<div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
				<div className="mb-2 text-sm font-medium text-slate-700">核心页面回归清单</div>
				<Space direction="vertical" size={6} className="w-full">
					{Array.isArray(reconciliation.regressionChecklist) && reconciliation.regressionChecklist.length > 0 ? (
						reconciliation.regressionChecklist.map((item) => (
							<div key={item.code || item.name} className="flex items-center justify-between gap-3 text-xs text-slate-700">
								<div className="min-w-0">
									<span className="font-medium">[{item.code || "-"}] {item.name || "-"}</span>
									<div className="truncate text-slate-500">{item.description || "-"}</div>
								</div>
								<Button
									size="small"
									onClick={() => {
										if (item.route) router.push(item.route);
									}}
								>
									打开页面
								</Button>
							</div>
						))
					) : (
						<div className="text-xs text-slate-500">暂无回归清单</div>
					)}
				</Space>
			</div>
		</Space>
	) : (
		<EmptyState title="暂无核对结果" description="当前账号无权限或尚未执行核对。" />
	);

	const resetFilters = () => {
		setKeyword("");
		setDomain(undefined);
		setAssetType("ALL");
		setClassification("ALL");
		setWarehouseLayer("ALL");
		setGovernanceStatus("ALL");
		setMatchStatus("ALL");
	};

	const syncOpenMetadataAssets = async () => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		setSyncing(true);
		try {
			await syncCatalogAssetsV2(500);
			await loadDatasets(1, pageState.size);
		} catch {
			// error toast handled by global interceptor
		} finally {
			setSyncing(false);
		}
	};

	const loadDiagnostics = async () => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		setDiagnosticsLoading(true);
		try {
			const result = await getCatalogAssetsV2Diagnostics();
			setDiagnostics(result || null);
		} catch {
			setDiagnostics(null);
		} finally {
			setDiagnosticsLoading(false);
		}
	};

	const renderAssetCard = (row: AssetRow) => {
		const layer = normalizeLayer(row.warehouseLayer);
		const meta = LAYER_META[layer];
		const isStale = String(row.lifecycleStatus || "").toUpperCase() === "STALE" || row.status === "停用";
		return (
			<button
				key={row.id}
				type="button"
				className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-left shadow-sm transition hover:border-blue-300 hover:shadow-md"
				onClick={() => router.push(`/catalog/datasets/${row.id}`)}
			>
				<div className="flex items-start justify-between gap-2">
					<Tooltip title={row.name}>
						<div className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900">{row.name}</div>
					</Tooltip>
					<Tag color={isStale ? "red" : meta.color}>{isStale ? "失效" : row.status || "启用"}</Tag>
				</div>
				<div className="mt-1 flex items-center gap-1 truncate text-xs text-slate-500">
					<DatabaseOutlined />
					<span className="truncate">{row.hiveDatabase && row.hiveTable ? `${row.hiveDatabase}.${row.hiveTable}` : row.type || "未知类型"}</span>
				</div>
				<div className="mt-2 flex flex-wrap gap-1">
					<Tag style={{ fontSize: 11 }}>{row.type || "未知"}</Tag>
					<Tag color={row.classification ? "orange" : "default"} style={{ fontSize: 11 }}>
						{classificationText(row.classification)}
					</Tag>
					<Tag color={row.governanceStatus === "GOVERNED" ? "green" : "gold"} style={{ fontSize: 11 }}>
						{row.governanceStatus || "待治理"}
					</Tag>
					<Tag color={row.matchStatus === "MATCHED" ? "blue" : "volcano"} style={{ fontSize: 11 }}>
						{row.matchStatus || "UNMATCHED"}
					</Tag>
					{row.domain || row.domainId ? (
						<Tag color="blue" style={{ fontSize: 11 }}>
							{row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "主题域"}
						</Tag>
					) : null}
				</div>
				<div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-slate-500">
					<span className="truncate">{row.owner || row.ownerDept || "未指定负责人"}</span>
					<span className="shrink-0">{formatTime(row.snapshotTime || row.updatedAt)}</span>
				</div>
			</button>
		);
	};

	return (
		<Layout className="min-h-full" style={{ background: "transparent" }}>
			<Layout.Sider
				width={248}
				theme="light"
				style={{
					background: "#fff",
					borderRight: "1px solid #f0f0f0",
					padding: "12px 8px",
					overflowY: "auto",
					height: "calc(100vh - 64px)",
				}}
			>
				<div className="mb-3 flex items-center gap-2 px-2 text-sm font-semibold text-slate-700">
					<ApartmentOutlined />
					主题域
				</div>
				<Spin spinning={treeLoading}>
					<Tree
						showLine
						defaultExpandAll
						treeData={treeData}
						selectedKeys={[domain || "ALL"]}
						onSelect={(keys) => {
							const selected = String(keys?.[0] ?? "ALL");
							setDomain(selected === "ALL" || selected.startsWith("fallback-") ? undefined : selected);
						}}
					/>
				</Spin>
			</Layout.Sider>
			<Layout.Content style={{ padding: "0 16px" }}>
				<div className="space-y-4">
					<Card
						title={
							<Space size={8}>
								<BranchesOutlined />
								<span>资产地图</span>
							</Space>
						}
						extra={
							<Space wrap>
								<Button icon={<ProfileOutlined />} onClick={() => router.push("/catalog/asset-detail")}>
									明细台账
								</Button>
								<Button icon={<ReloadOutlined />} onClick={() => void loadDatasets(1, pageState.size)} loading={loading}>
									刷新资产
								</Button>
								{ASSET_PORTAL_V2_ENABLED ? (
									<Button icon={<DatabaseOutlined />} onClick={() => void syncOpenMetadataAssets()} loading={syncing}>
										同步OpenMetadata
									</Button>
								) : null}
								<Button icon={<SafetyCertificateOutlined />} onClick={() => void loadReconciliation()} loading={reconciliationLoading}>
									刷新核对
								</Button>
								{ASSET_PORTAL_V2_ENABLED ? (
									<Button icon={<WarningOutlined />} onClick={() => void loadDiagnostics()} loading={diagnosticsLoading}>
										映射诊断
									</Button>
								) : null}
							</Space>
						}
					>
						<div className="flex flex-wrap items-center gap-2">
							<Select
								allowClear
								placeholder="资产类型"
								style={{ minWidth: 160 }}
								value={assetType}
								onChange={(value) => setAssetType(value || "ALL")}
								options={TYPE_OPTIONS}
							/>
							<Select
								allowClear
								placeholder="密级"
								style={{ minWidth: 150 }}
								value={classification}
								onChange={(value) => setClassification(value || "ALL")}
								options={CLASSIFICATION_OPTIONS}
							/>
							<Select
								allowClear
								placeholder="分层"
								style={{ minWidth: 150 }}
								value={warehouseLayer}
								onChange={(value) => setWarehouseLayer(value || "ALL")}
								options={LAYER_OPTIONS}
							/>
							<Select
								allowClear
								placeholder="治理状态"
								style={{ minWidth: 150 }}
								value={governanceStatus}
								onChange={(value) => setGovernanceStatus(value || "ALL")}
								options={GOVERNANCE_OPTIONS}
							/>
							<Select
								allowClear
								placeholder="映射状态"
								style={{ minWidth: 150 }}
								value={matchStatus}
								onChange={(value) => setMatchStatus(value || "ALL")}
								options={MATCH_OPTIONS}
							/>
							<Input
								prefix={<SearchOutlined />}
								placeholder="搜索资产名称 / 描述"
								style={{ width: 260 }}
								value={keyword}
								onChange={(event) => setKeyword(event.target.value)}
								allowClear
							/>
							<Button onClick={resetFilters}>重置筛选</Button>
						</div>
					</Card>

					{diagnostics ? (
						<Alert
							type={Number(diagnostics.unmatchedCount || 0) > 0 ? "warning" : "info"}
							showIcon
							message={`OpenMetadata映射诊断：资产 ${Number(diagnostics.assetCount || 0)}，已映射 ${Number(diagnostics.matchedCount || 0)}，未匹配 ${Number(diagnostics.unmatchedCount || 0)}，人工确认 ${Number(diagnostics.manualReviewCount || 0)}`}
							description={
								Array.isArray(diagnostics.issues) && diagnostics.issues.length > 0
									? diagnostics.issues.slice(0, 3).map((item: any) => `${item.fqn || "-"}：${item.matchReason || item.matchStatus || "-"}`).join("；")
									: undefined
							}
						/>
					) : null}

					<div className="grid gap-3 md:grid-cols-4">
						<MetricTile icon={<DatabaseOutlined />} label="资产总量" value={pageState.total} footnote={selectedDomainName} />
						<MetricTile icon={<TableOutlined />} label="当前页资产" value={records.length} footnote={`启用 ${activeCount} 个`} />
						<MetricTile icon={<ApartmentOutlined />} label="主题域覆盖" value={domains.length} footnote={`未归域 ${missingDomainCount} 个`} />
						<MetricTile
							icon={<SafetyCertificateOutlined />}
							label="治理覆盖率"
							value={`${Math.max(0, governanceCoverage)}%`}
							footnote={`未定密 ${unclassifiedCount} 个 / 失效 ${staleCount} 个`}
							tone={governanceCoverage >= 80 ? "text-green-600" : "text-amber-600"}
						/>
					</div>

					<Card
						title={
							<Space size={8}>
								<BranchesOutlined />
								<span>{selectedDomainName}资产分布</span>
							</Space>
						}
						extra={
							<Pagination
								size="small"
								current={pageState.page}
								pageSize={pageState.size}
								total={pageState.total}
								showSizeChanger
								pageSizeOptions={[12, 18, 30, 48]}
								onChange={(page, size) => void loadDatasets(page, size)}
							/>
						}
					>
						{records.length ? (
							<div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
								<div className="overflow-x-auto">
									<div className="grid min-w-[1120px] grid-cols-8 gap-3">
										{layerLanes.map((lane) => (
											<div key={lane.key} className={`rounded-lg border px-2 py-2 ${lane.meta.tone}`}>
												<div className="mb-2 flex items-center justify-between gap-2">
													<Tag color={lane.meta.color}>{lane.meta.label}</Tag>
													<span className="text-xs font-semibold text-slate-600">{lane.items.length}</span>
												</div>
												<div className="min-h-[360px] space-y-2">
													{lane.items.length ? (
														lane.items.map(renderAssetCard)
													) : (
														<div className="rounded-lg border border-dashed border-slate-200 bg-white/70 px-2 py-6 text-center text-xs text-slate-400">
															暂无资产
														</div>
													)}
												</div>
											</div>
										))}
									</div>
								</div>
								<div className="space-y-3">
									<div className="rounded-lg border border-slate-200 bg-white p-3">
										<div className="mb-3 text-sm font-semibold text-slate-800">类型分布</div>
										<Space direction="vertical" size={10} className="w-full">
											{typeStats.length ? (
												typeStats.map(([type, count]) => (
													<div key={type}>
														<div className="mb-1 flex items-center justify-between text-xs text-slate-600">
															<span>{type}</span>
															<span>{count}</span>
														</div>
														<Progress percent={Math.round((count / Math.max(records.length, 1)) * 100)} size="small" showInfo={false} />
													</div>
												))
											) : (
												<div className="text-xs text-slate-500">暂无类型分布</div>
											)}
										</Space>
									</div>
									<div className="rounded-lg border border-slate-200 bg-white p-3">
										<div className="mb-3 text-sm font-semibold text-slate-800">主题域热点</div>
										<Space direction="vertical" size={8} className="w-full">
											{domainStats.map(([name, count]) => (
												<div key={name} className="flex items-center justify-between gap-3 text-xs">
													<span className="min-w-0 truncate text-slate-600">{name}</span>
													<Tag color="blue">{count}</Tag>
												</div>
											))}
										</Space>
									</div>
									<div className="rounded-lg border border-slate-200 bg-white p-3">
										<div className="mb-2 text-sm font-semibold text-slate-800">治理缺口</div>
										<Space direction="vertical" size={8} className="w-full">
											<div className="flex items-center justify-between text-xs text-slate-600">
												<span>未归域</span>
												<Tag color={missingDomainCount ? "orange" : "green"}>{missingDomainCount}</Tag>
											</div>
											<div className="flex items-center justify-between text-xs text-slate-600">
												<span>未定密</span>
												<Tag color={unclassifiedCount ? "orange" : "green"}>{unclassifiedCount}</Tag>
											</div>
											<div className="flex items-center justify-between text-xs text-slate-600">
												<span>同步失效</span>
												<Tag color={staleCount ? "red" : "green"}>{staleCount}</Tag>
											</div>
										</Space>
									</div>
								</div>
							</div>
						) : (
							<EmptyState title="暂无资产地图" description="请先完成元数据采集或同步资产数据。" />
						)}
					</Card>

					<Collapse
						defaultActiveKey={[]}
						items={[
							{
								key: "reconciliation",
								label: "发布前回归与一致性核对",
								extra: (
									<Button
										size="small"
										icon={<ReloadOutlined />}
										loading={reconciliationLoading}
										onClick={(event) => {
											event.stopPropagation();
											void loadReconciliation();
										}}
									>
										重新核对
									</Button>
								),
								children: reconciliationContent,
							},
						]}
					/>
				</div>
			</Layout.Content>
		</Layout>
	);
}
