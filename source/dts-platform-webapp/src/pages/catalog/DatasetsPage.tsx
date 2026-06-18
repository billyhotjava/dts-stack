import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Alert, Button, Card, Collapse, Input, Layout, Modal, Pagination, Segmented, Select, Space, Spin, Table, Tabs, Tag, Tooltip, Tree } from "antd";
import { ApartmentOutlined, ArrowRightOutlined, BranchesOutlined, DatabaseOutlined, SafetyCertificateOutlined, SearchOutlined, TableOutlined, WarningOutlined } from "@ant-design/icons";
import { EmptyState } from "@/components/empty-state";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import {
	getCatalogAssetsV2Diagnostics,
	getCatalogAssetsV2GovernanceGaps,
	getCatalogAssetsV2LineageFailures,
	getCatalogReconciliation,
	getDomainTree,
	listCatalogAssetResolutionFailures,
	listCatalogAssetsV2,
	listDatasets,
	listDomains,
	syncCatalogAssetsV2,
	syncCatalogAssetV2Lineage,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "./assetPortalUx.helpers";

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
	metadataSource?: string;
	legacyDatasetId?: string;
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

const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v2";
const ASSET_PORTAL_V2_ENABLED = import.meta.env.VITE_CATALOG_ASSET_PORTAL_V2 !== "false";
const UNASSIGNED_DOMAIN_KEY = "__UNASSIGNED__";

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
const ASSET_ACTION_COLUMN_WIDTH = 640;
const ASSET_TABLE_SCROLL_X = 1760;

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

type ResolutionFailureRow = {
	id?: string;
	ref?: string;
	requestedAt?: string;
	caller?: string;
	typeHintGuess?: string;
	reason?: string;
};

type GovernanceGapRow = {
	id?: string;
	displayName?: string;
	fqn?: string;
	assetKey?: string;
	grantAssetType?: string;
	grantAssetId?: string;
	lifecycleStatus?: string;
	governanceStatus?: string;
	severity?: string;
	blockingGaps?: string[];
	warningGaps?: string[];
	metadataSource?: string;
};

type LineageFailureRow = GovernanceGapRow & {
	blocking?: boolean;
	reason?: string;
	evidenceSource?: string;
	nextAction?: string;
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
	const [viewMode, setViewMode] = useState<"map" | "table">(() => {
		try {
			if (new URLSearchParams(window.location.search).get("view") === "table") {
				return "table";
			}
			return "map";
		} catch {
			return "map";
		}
	});
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
	const [resolutionFailuresOpen, setResolutionFailuresOpen] = useState(false);
	const [resolutionFailuresLoading, setResolutionFailuresLoading] = useState(false);
	const [resolutionFailures, setResolutionFailures] = useState<ResolutionFailureRow[]>([]);
	const [remediationOpen, setRemediationOpen] = useState(false);
	const [remediationLoading, setRemediationLoading] = useState<string | null>(null);
	const [records, setRecords] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: 18, total: 0 });
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
	const [reconciliationLoading, setReconciliationLoading] = useState(false);
	const [governanceGapReport, setGovernanceGapReport] = useState<any | null>(null);
	const [lineageFailureReport, setLineageFailureReport] = useState<any | null>(null);
	const [signalsLoading, setSignalsLoading] = useState(false);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(false);
	const requestSeqRef = useRef(0);

	useEffect(() => {
		try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) return;
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(undefined);
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
			void loadGovernanceSignals();
		}, 280);
		return () => window.clearTimeout(timer);
	}, [keyword, domain, assetType, classification, warehouseLayer, governanceStatus, matchStatus, pageState.size]);

	useEffect(() => {
		const payload = {
			keyword,
			assetType,
			classification,
			warehouseLayer,
			governanceStatus,
			matchStatus,
		};
		localStorage.setItem(DATASET_FILTER_STORAGE_KEY, JSON.stringify(payload));
	}, [keyword, assetType, classification, warehouseLayer, governanceStatus, matchStatus]);

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

	const buildAssetQuery = (page = 1, size = 18) => ({
		page: page - 1,
		size,
		keyword: keyword.trim() || undefined,
		domainId: domain && domain !== "ALL" && domain !== UNASSIGNED_DOMAIN_KEY ? domain : undefined,
		domainUnassigned: domain === UNASSIGNED_DOMAIN_KEY || undefined,
		type: assetType === "ALL" ? undefined : assetType,
		classification: classification === "ALL" ? undefined : classification,
		warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
		governanceStatus: governanceStatus === "ALL" ? undefined : governanceStatus,
		matchStatus: matchStatus === "ALL" ? undefined : matchStatus,
	});

	const loadGovernanceSignals = async () => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		setSignalsLoading(true);
		try {
			const query = buildAssetQuery(1, 20);
			const [gapResult, lineageResult] = await Promise.allSettled([
				getCatalogAssetsV2GovernanceGaps(query),
				getCatalogAssetsV2LineageFailures(query),
			]);
			setGovernanceGapReport(gapResult.status === "fulfilled" ? gapResult.value || null : null);
			setLineageFailureReport(lineageResult.status === "fulfilled" ? lineageResult.value || null : null);
		} finally {
			setSignalsLoading(false);
		}
	};

	const loadDatasets = async (page = 1, size = 18) => {
		const reqId = ++requestSeqRef.current;
		setLoading(true);
		try {
			const resp: any = ASSET_PORTAL_V2_ENABLED ? await listCatalogAssetsV2(buildAssetQuery(page, size)) : await listDatasets({
				page: page - 1,
				size,
				keyword: keyword.trim() || undefined,
				domainId: domain && domain !== "ALL" && domain !== UNASSIGNED_DOMAIN_KEY ? domain : undefined,
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
					metadataSource: item.metadataSource || undefined,
					legacyDatasetId: item.legacyDatasetId ? String(item.legacyDatasetId) : undefined,
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

	const selectedDomainName = domain === UNASSIGNED_DOMAIN_KEY ? "未归域" : domain ? domainMap.get(domain) || "当前主题域" : "全部主题域";

	const layerTabItems = useMemo(
		() => [
			{ key: "ALL", label: "全部" },
			...LAYER_ORDER.filter((key) => key !== "OTHER").map((key) => ({
				key,
				label: LAYER_META[key].label,
			})),
			{ key: "OTHER", label: "未分层" },
		],
		[],
	);

	const unclassifiedCount = records.filter((row) => !row.classification).length;
	const missingDomainCount = records.filter((row) => !row.domain && !row.domainId).length;
	const staleCount = records.filter((row) => String(row.lifecycleStatus || "").toUpperCase() === "STALE" || row.status === "停用").length;
	const activeCount = records.filter((row) => row.status === "启用").length;
	const readinessCounts = records.reduce<Record<string, number>>((acc, row) => {
		const state = resolveAssetReadiness(row).state;
		acc[state] = (acc[state] || 0) + 1;
		return acc;
	}, {});
	const governanceCoverage = records.length ? Math.round(((records.length - unclassifiedCount - missingDomainCount) / Math.max(records.length, 1)) * 100) : 0;
	const blockingGapCount = Number(governanceGapReport?.severityCounts?.BLOCKING || 0);
	const lineageFailureCount = Array.isArray(lineageFailureReport?.content) ? lineageFailureReport.content.length : 0;
	const governanceGapRows: GovernanceGapRow[] = Array.isArray(governanceGapReport?.content) ? governanceGapReport.content : [];
	const lineageFailureRows: LineageFailureRow[] = Array.isArray(lineageFailureReport?.content) ? lineageFailureReport.content : [];
	const layerGroups = useMemo(
		() =>
			LAYER_ORDER.map((layer) => ({
				layer,
				meta: LAYER_META[layer],
				items: records.filter((row) => normalizeLayer(row.warehouseLayer) === layer),
			})),
		[records],
	);
	const domainDistribution = useMemo(() => {
		const counts = new Map<string, number>();
		records.forEach((row) => {
			const label = row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域";
			counts.set(label, (counts.get(label) || 0) + 1);
		});
		return Array.from(counts.entries())
			.map(([name, count]) => ({ name, count }))
			.sort((a, b) => b.count - a.count)
			.slice(0, 6);
	}, [domainMap, records]);

	const treeData = useMemo(
		() => [
			{
				key: "ALL",
				title: "全部资产",
				children: [
					{
						key: UNASSIGNED_DOMAIN_KEY,
						title: "未归域",
					},
					...buildTreeNodes(domainTree),
				],
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

	const loadResolutionFailures = async () => {
		if (!ASSET_PORTAL_V2_ENABLED) return;
		setResolutionFailuresOpen(true);
		setResolutionFailuresLoading(true);
		try {
			const result = await listCatalogAssetResolutionFailures({ limit: 100 });
			setResolutionFailures(Array.isArray(result) ? (result as ResolutionFailureRow[]) : []);
		} catch {
			setResolutionFailures([]);
		} finally {
			setResolutionFailuresLoading(false);
		}
	};

	const openRemediationWorkbench = async () => {
		setRemediationOpen(true);
		await loadGovernanceSignals();
	};

	const openGovernanceRemediation = (assetId?: string) => {
		if (!assetId) return;
		router.push(`/catalog/datasets/${assetId}?tab=governance`);
	};

	const syncLineageForRow = async (assetId?: string) => {
		if (!assetId) return;
		setRemediationLoading(assetId);
		try {
			await syncCatalogAssetV2Lineage(assetId, { upstreamDepth: 2, downstreamDepth: 2 });
			await Promise.all([loadGovernanceSignals(), loadDatasets(pageState.page, pageState.size)]);
		} finally {
			setRemediationLoading(null);
		}
	};

	const renderAssetMapNode = (row: AssetRow) => {
		const layer = normalizeLayer(row.warehouseLayer);
		const meta = LAYER_META[layer];
		const isStale = String(row.lifecycleStatus || "").toUpperCase() === "STALE" || row.status === "停用";
		const readiness = resolveAssetReadiness(row);
		return (
			<button
				key={row.id}
				type="button"
				className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-blue-300 hover:shadow-md"
				onClick={() => router.push(`/catalog/datasets/${row.id}`)}
			>
				<div className="flex items-start justify-between gap-2">
					<Tooltip title={row.name}>
						<div className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-900">{row.name}</div>
					</Tooltip>
					<Tag color={readiness.color} style={{ fontSize: 11 }}>{readiness.label}</Tag>
				</div>
				<div className="mt-1 flex items-center gap-1 truncate text-xs text-slate-500">
					<DatabaseOutlined />
					<span className="truncate">{row.hiveDatabase && row.hiveTable ? `${row.hiveDatabase}.${row.hiveTable}` : row.type || "未知类型"}</span>
				</div>
				<div className="mt-2 flex items-center justify-between gap-2">
					<Tag color={isStale ? "red" : meta.color} style={{ fontSize: 11 }}>
						{isStale ? "失效" : row.status || "启用"}
					</Tag>
					<Tag color={row.classification ? "orange" : "default"} style={{ fontSize: 11 }}>
						{classificationText(row.classification)}
					</Tag>
				</div>
				{readiness.reasons.length ? (
					<div className="mt-2 rounded border border-amber-100 bg-amber-50 px-2 py-1 text-[11px] text-amber-700">
						{readiness.reasons[0]}
					</div>
				) : null}
				<div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-slate-500">
					<span className="truncate">{row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域"}</span>
					<span className="shrink-0">{row.owner || row.ownerDept || "未认领"}</span>
				</div>
			</button>
		);
	};

	const renderAssetVisualMap = () => (
		<div className="asset-map-stage space-y-4">
			<div className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
				<div>
					<div className="text-sm font-semibold text-slate-900">数据流向</div>
					<div className="mt-1 text-xs text-slate-500">
						按来源、明细、汇总、应用数据集展示当前筛选范围内的资产分布；点击资产节点进入工作台详情。
					</div>
				</div>
				<Button
					onClick={() => {
						setViewMode("table");
						router.push("/catalog/assets?view=table");
					}}
				>
					进入台账
				</Button>
			</div>
			<div className="overflow-x-auto rounded-xl border border-slate-200 bg-white p-3">
				<div className="grid min-w-[1120px] gap-3" style={{ gridTemplateColumns: `repeat(${LAYER_ORDER.length}, minmax(138px, 1fr))` }}>
					{layerGroups.map((group, index) => (
						<div key={group.layer} className={`relative min-h-[260px] rounded-lg border px-3 py-3 ${group.meta.tone}`}>
							<div className="flex items-center justify-between gap-2">
								<div>
									<div className="text-sm font-semibold text-slate-900">{group.meta.label}</div>
									<div className="text-xs text-slate-500">{group.items.length} 个资产</div>
								</div>
								{index < layerGroups.length - 1 ? <ArrowRightOutlined className="text-slate-400" /> : null}
							</div>
							<div className="mt-3 space-y-2">
								{group.items.slice(0, 5).map(renderAssetMapNode)}
								{group.items.length > 5 ? (
									<button
										type="button"
										className="w-full rounded border border-dashed border-slate-300 bg-white/70 px-2 py-2 text-xs text-slate-500 hover:border-blue-300 hover:text-blue-600"
										onClick={() => {
											setWarehouseLayer(group.layer);
											setViewMode("table");
											router.push("/catalog/assets?view=table");
										}}
									>
										查看剩余 {group.items.length - 5} 个
									</button>
								) : null}
								{!group.items.length ? (
									<div className="rounded border border-dashed border-slate-200 bg-white/60 px-2 py-6 text-center text-xs text-slate-400">
										当前筛选无资产
									</div>
								) : null}
							</div>
						</div>
					))}
				</div>
			</div>
			<div className="grid gap-3 lg:grid-cols-3">
				<Card size="small" title="业务视角">
					<Space direction="vertical" size={8} className="w-full">
						{domainDistribution.length ? (
							domainDistribution.map((item) => (
								<div key={item.name} className="flex items-center justify-between gap-3 rounded border border-slate-100 bg-slate-50 px-3 py-2">
									<span className="truncate text-sm text-slate-700">{item.name}</span>
									<Tag color={item.name === "未归域" ? "orange" : "blue"}>{item.count}</Tag>
								</div>
							))
						) : (
							<div className="py-4 text-center text-xs text-slate-400">暂无主题域分布</div>
						)}
					</Space>
				</Card>
				<Card size="small" title="治理阻断">
					<div className="grid grid-cols-3 gap-2 text-center">
						<div className="rounded border border-amber-100 bg-amber-50 px-2 py-3">
							<div className="text-lg font-semibold text-amber-700">{blockingGapCount}</div>
							<div className="text-xs text-amber-700">治理阻断</div>
						</div>
						<div className="rounded border border-rose-100 bg-rose-50 px-2 py-3">
							<div className="text-lg font-semibold text-rose-700">{lineageFailureCount}</div>
							<div className="text-xs text-rose-700">血缘缺口</div>
						</div>
						<div className="rounded border border-slate-100 bg-slate-50 px-2 py-3">
							<div className="text-lg font-semibold text-slate-700">{missingDomainCount}</div>
							<div className="text-xs text-slate-600">未归域</div>
						</div>
					</div>
					<Button className="mt-3 w-full" onClick={() => void openRemediationWorkbench()} loading={signalsLoading}>
						打开治理缺口处置
					</Button>
				</Card>
				<Card size="small" title="运营入口">
					<Space direction="vertical" size={8} className="w-full">
						<Button block onClick={() => router.push("/catalog/search")}>资产搜索</Button>
						<Button block onClick={() => router.push("/catalog/data-products")}>数据产品</Button>
						<Button block onClick={() => router.push("/catalog/lineage/graph")}>血缘图谱</Button>
					</Space>
				</Card>
			</div>
		</div>
	);

	const renderAssetTable = () => (
		<Table<AssetRow>
			size="small"
			rowKey="id"
			dataSource={records}
			pagination={false}
			scroll={{ x: ASSET_TABLE_SCROLL_X }}
			tableLayout="fixed"
			className="catalog-assets-table"
			onRow={(row) => ({
				onClick: () => router.push(`/catalog/datasets/${row.id}`),
			})}
			columns={[
				{
					title: "资产",
					dataIndex: "name",
					width: 280,
					render: (value, row) => (
						<div className="min-w-0">
							<div className="truncate font-medium text-slate-900">{value || "-"}</div>
							<div className="truncate font-mono text-[11px] text-slate-500">
								{row.hiveDatabase && row.hiveTable ? `${row.hiveDatabase}.${row.hiveTable}` : row.description || row.id}
							</div>
						</div>
					),
				},
				{
					title: "类型/分层",
					width: 150,
					render: (_, row) => (
						<Space direction="vertical" size={2}>
							<Tag>{row.type || "未知"}</Tag>
							<Tag color={LAYER_META[normalizeLayer(row.warehouseLayer)].color}>{LAYER_META[normalizeLayer(row.warehouseLayer)].label}</Tag>
						</Space>
					),
				},
				{
					title: "治理状态",
					width: 180,
					render: (_, row) => {
						const readiness = resolveAssetReadiness(row);
						return (
							<Space direction="vertical" size={2}>
								<Tag color={readiness.color}>{readiness.label}</Tag>
								<span className="text-xs text-slate-500">{readiness.reasons.slice(0, 2).join(" / ") || row.governanceStatus || "-"}</span>
							</Space>
						);
					},
				},
				{
					title: "密级/主题域",
					width: 180,
					render: (_, row) => (
						<Space direction="vertical" size={2}>
							<Tag color={row.classification ? "orange" : "default"}>{classificationText(row.classification)}</Tag>
							<span className="text-xs text-slate-500">{row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域"}</span>
						</Space>
					),
				},
				{
					title: "负责人",
					width: 160,
					render: (_, row) => row.owner || row.ownerDept || "-",
				},
				{
					title: "更新时间",
					width: 170,
					render: (_, row) => formatTime(row.snapshotTime || row.updatedAt),
				},
				{
					title: "操作",
					width: ASSET_ACTION_COLUMN_WIDTH,
					fixed: "right",
					render: (_, row) => (
						<Space size={[4, 4]} className="catalog-assets-actions" onClick={(event) => event.stopPropagation()}>
							<Button size="small" onClick={() => router.push(`/security/dataset-access-approval?datasetId=${row.id}`)}>
								申请权限
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}`)}>
								详情
							</Button>
							<Button size="small" onClick={() => openGovernanceRemediation(row.id)}>
								治理
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=lineage-impact`)}>
								查看血缘
							</Button>
							<Button size="small" onClick={() => router.push(`/bi/dashboards?assetId=${row.id}`)}>
								创建报表
							</Button>
							<Button size="small" onClick={() => router.push(`/catalog/data-products?assetId=${row.id}`)}>
								生成数据产品
							</Button>
							<Button size="small" onClick={() => router.push(`/services/apis?assetId=${row.id}`)}>
								发布数据 API
							</Button>
						</Space>
					),
				},
			]}
		/>
	);

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
					<PageHeader title="资产目录" />
					<Card
						title={
							<Space size={8}>
								<BranchesOutlined />
								<span>资产地图</span>
							</Space>
						}
						extra={
							<Space wrap>
								<Button
									onClick={() => {
										setViewMode("table");
										router.push("/catalog/assets?view=table");
									}}
								>
									进入台账
								</Button>
								<Button onClick={() => void loadDatasets(1, pageState.size)} loading={loading}>
									刷新资产
								</Button>
								{ASSET_PORTAL_V2_ENABLED ? (
									<Button onClick={() => void syncOpenMetadataAssets()} loading={syncing}>
										同步OpenMetadata
									</Button>
								) : null}
								<Button onClick={() => void loadReconciliation()} loading={reconciliationLoading}>
									刷新核对
								</Button>
								{ASSET_PORTAL_V2_ENABLED ? (
									<Button onClick={() => void loadDiagnostics()} loading={diagnosticsLoading}>
										映射诊断
									</Button>
								) : null}
								{ASSET_PORTAL_V2_ENABLED ? (
									<Button onClick={() => void loadResolutionFailures()} loading={resolutionFailuresLoading}>
										解析失败
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

					{ASSET_PORTAL_V2_ENABLED ? (
						<Alert
							type={blockingGapCount || lineageFailureCount ? "warning" : "success"}
							showIcon
							message={
								blockingGapCount || lineageFailureCount
									? `当前筛选存在治理阻断 ${blockingGapCount} 项、血缘证据缺口 ${lineageFailureCount} 项`
									: "当前筛选未发现治理阻断和血缘证据缺口"
							}
							description={
								signalsLoading
									? "正在刷新治理信号..."
									: "资产是否可用于指标、宽表和 BI 发布，以密级、主题域、归属部门、生命周期、映射状态和血缘证据共同判断。"
							}
							action={
								<Space>
									<Button size="small" onClick={() => void openRemediationWorkbench()} loading={signalsLoading}>
										处置缺口
									</Button>
									<Button size="small" onClick={() => void loadGovernanceSignals()} loading={signalsLoading}>
										刷新信号
									</Button>
								</Space>
							}
						/>
					) : null}

					<div className="grid gap-3 md:grid-cols-5">
						<MetricTile icon={<DatabaseOutlined />} label="资产总量" value={pageState.total} footnote={selectedDomainName} />
						<MetricTile icon={<TableOutlined />} label="当前页资产" value={records.length} footnote={`启用 ${activeCount} 个`} />
						<MetricTile icon={<ApartmentOutlined />} label="主题域覆盖" value={domains.length} footnote={`未归域 ${missingDomainCount} 个`} />
						<MetricTile
							icon={<SafetyCertificateOutlined />}
							label="可引用资产"
							value={Number(readinessCounts.READY || 0)}
							footnote={`阻断 ${Number(readinessCounts.BLOCKED || 0)} 个 / 待确认 ${Number(readinessCounts.WARNING || 0)} 个`}
							tone={Number(readinessCounts.BLOCKED || 0) > 0 ? "text-amber-600" : "text-green-600"}
						/>
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
						<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
							<Tabs
								activeKey={warehouseLayer}
								onChange={(value) => setWarehouseLayer(value || "ALL")}
								items={layerTabItems}
								tabBarStyle={{ marginBottom: 0 }}
							/>
							<Segmented
								size="small"
								value={viewMode}
								onChange={(value) => {
									const next = value === "table" ? "table" : "map";
									setViewMode(next);
									router.push(next === "table" ? "/catalog/assets?view=table" : "/catalog/assets");
								}}
								options={[
									{ label: "地图", value: "map" },
									{ label: "台账", value: "table" },
								]}
							/>
						</div>
						{records.length ? (
							viewMode === "table" ? (
								renderAssetTable()
							) : (
								renderAssetVisualMap()
							)
						) : (
							<EmptyState title="未发现当前账号可见资产" description="可能还未完成元数据采集，也可能当前密级、主题域或资产授权限制了可见范围。" />
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
			<Modal
				title="治理缺口处置工作台"
				open={remediationOpen}
				onCancel={() => setRemediationOpen(false)}
				footer={
					<Space>
						<Button onClick={() => void loadGovernanceSignals()} loading={signalsLoading}>
							刷新报告
						</Button>
						<Button onClick={() => setRemediationOpen(false)}>关闭</Button>
					</Space>
				}
				width={1120}
			>
				<Alert
					type={governanceGapRows.length || lineageFailureRows.length ? "warning" : "success"}
					showIcon
					className="mb-3"
					message={
						governanceGapRows.length || lineageFailureRows.length
							? `当前筛选发现治理缺口 ${governanceGapRows.length} 条、血缘失败 ${lineageFailureRows.length} 条`
							: "当前筛选没有需要处置的治理缺口"
					}
					description="点击补治理字段会进入资产详情的治理扩展页；点击同步血缘会重新拉取当前资产的上下游证据并刷新报告。"
				/>
				<Tabs
					items={[
						{
							key: "governance-gaps",
							label: `治理缺口（${governanceGapRows.length}）`,
							children: (
								<CompactTable<GovernanceGapRow>
									rowKey={(row) => row.id || row.assetKey || row.fqn || row.displayName || "asset"}
									size="small"
									loading={signalsLoading}
									dataSource={governanceGapRows}
									autoEllipsis={false} pagination={{ defaultPageSize: 10 }}
									scroll={{ x: 980 }}
									columns={[
										{
											title: "资产",
											dataIndex: "displayName",
											width: 220,
											render: (value, row) => (
												<div>
													<div className="font-medium text-slate-900">{value || row.fqn || "-"}</div>
													<div className="truncate font-mono text-[11px] text-slate-500">{row.assetKey || row.fqn || "-"}</div>
												</div>
											),
										},
										{
											title: "严重度",
											dataIndex: "severity",
											width: 100,
											render: (value) => <Tag color={value === "BLOCKING" ? "red" : value === "READY" ? "green" : "orange"}>{value || "-"}</Tag>,
										},
										{
											title: "阻断项",
											dataIndex: "blockingGaps",
											width: 220,
											render: (value: string[]) => value?.length ? value.map((item) => <Tag color="red" key={item}>{item}</Tag>) : "-",
										},
										{
											title: "提示项",
											dataIndex: "warningGaps",
											width: 220,
											render: (value: string[]) => value?.length ? value.map((item) => <Tag color="orange" key={item}>{item}</Tag>) : "-",
										},
										{
											title: "状态",
											width: 160,
											render: (_, row) => (
												<Space direction="vertical" size={2}>
													<Tag>{row.governanceStatus || "-"}</Tag>
													<span className="text-xs text-slate-500">{row.lifecycleStatus || "-"}</span>
												</Space>
											),
										},
										{
											title: "操作",
											width: 180,
											fixed: "right",
											render: (_, row) => (
												<Space>
													<Button size="small" onClick={() => openGovernanceRemediation(row.id)}>
														补治理字段
													</Button>
													<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}`)}>
														详情
													</Button>
												</Space>
											),
										},
									]}
								/>
							),
						},
						{
							key: "lineage-failures",
							label: `血缘失败（${lineageFailureRows.length}）`,
							children: (
								<CompactTable<LineageFailureRow>
									rowKey={(row) => row.id || row.assetKey || row.fqn || row.displayName || "asset"}
									size="small"
									loading={signalsLoading}
									dataSource={lineageFailureRows}
									autoEllipsis={false} pagination={{ defaultPageSize: 10 }}
									scroll={{ x: 1020 }}
									columns={[
										{
											title: "资产",
											dataIndex: "displayName",
											width: 220,
											render: (value, row) => (
												<div>
													<div className="font-medium text-slate-900">{value || row.fqn || "-"}</div>
													<div className="truncate font-mono text-[11px] text-slate-500">{row.assetKey || row.fqn || "-"}</div>
												</div>
											),
										},
										{
											title: "严重度",
											dataIndex: "severity",
											width: 100,
											render: (value, row) => <Tag color={row.blocking ? "red" : "orange"}>{value || "-"}</Tag>,
										},
										{
											title: "原因",
											dataIndex: "reason",
											width: 220,
											render: (value) => value ? <Tag color="orange">{value}</Tag> : "-",
										},
										{
											title: "下一步",
											dataIndex: "nextAction",
											width: 220,
											render: (value) => value || "同步血缘或补齐治理字段",
										},
										{
											title: "证据源",
											dataIndex: "evidenceSource",
											width: 160,
											render: (value) => value || "-",
										},
										{
											title: "操作",
											width: 220,
											fixed: "right",
											render: (_, row) => (
												<Space>
													<Button
														size="small"
														type="primary"
														loading={remediationLoading === row.id}
														onClick={() => void syncLineageForRow(row.id)}
													>
														同步血缘
													</Button>
													<Button size="small" onClick={() => openGovernanceRemediation(row.id)}>
														治理
													</Button>
													<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=lineage`)}>
														详情
													</Button>
												</Space>
											),
										},
									]}
								/>
							),
						},
					]}
				/>
			</Modal>
			<Modal
				title="资产身份解析失败"
				open={resolutionFailuresOpen}
				onCancel={() => setResolutionFailuresOpen(false)}
				footer={<Button onClick={() => setResolutionFailuresOpen(false)}>关闭</Button>}
				width={920}
			>
				<Alert
					type={resolutionFailures.length ? "warning" : "success"}
					showIcon
					className="mb-3"
					message={resolutionFailures.length ? `最近发现 ${resolutionFailures.length} 条解析失败` : "最近没有资产身份解析失败"}
					description="这些记录会影响指标包、治理指标、代码化资产和资产授权的事实源闭环。请优先处理 ref 命名、资产类型映射和历史兼容代理。"
				/>
				<CompactTable<ResolutionFailureRow>
					rowKey={(row) => row.id || `${row.ref || "ref"}-${row.requestedAt || "time"}`}
					size="small"
					loading={resolutionFailuresLoading}
					dataSource={resolutionFailures}
					autoEllipsis={false} pagination={{ defaultPageSize: 10 }}
					scroll={{ x: 900 }}
					columns={[
						{
							title: "引用",
							dataIndex: "ref",
							width: 280,
							render: (value) => <span className="font-mono text-xs">{value || "-"}</span>,
						},
						{
							title: "类型猜测",
							dataIndex: "typeHintGuess",
							width: 120,
							render: (value) => value ? <Tag>{value}</Tag> : "-",
						},
						{
							title: "原因",
							dataIndex: "reason",
							width: 180,
							render: (value) => value ? <Tag color="orange">{value}</Tag> : "-",
						},
						{
							title: "调用方",
							dataIndex: "caller",
							width: 150,
							render: (value) => value || "-",
						},
						{
							title: "发生时间",
							dataIndex: "requestedAt",
							width: 180,
							render: (value) => formatTime(value),
						},
					]}
				/>
			</Modal>
		</Layout>
	);
}
