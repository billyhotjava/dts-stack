import {
	ApartmentOutlined,
	DatabaseOutlined,
	SafetyCertificateOutlined,
	TableOutlined,
	WarningOutlined,
} from "@ant-design/icons";
import { Alert, Button, Card, Collapse, Layout, Pagination, Space, Spin, Tabs, Tree } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
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
import { readTagIds, writeTagIds } from "@/components/catalog/tags/catalogTagUrlState";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness, STALE_LIFECYCLE_STATUSES } from "./assetPortalUx.helpers";
import { AssetLedgerDialogs } from "./assets/AssetLedgerDialogs";
import { AssetLedgerToolbar } from "./assets/AssetLedgerToolbar";
import { AssetLedgerView } from "./assets/AssetLedgerView";
import { AssetReconciliationPanel } from "./assets/AssetReconciliationPanel";
import type {
	AssetRow,
	DomainNode,
	GovernanceGapRow,
	LineageFailureRow,
	ReconciliationResult,
	ResolutionFailureRow,
} from "./assets/assetPageShared";
import {
	ASSET_PORTAL_V2_ENABLED,
	buildTreeNodes,
	classificationText,
	DATASET_FILTER_STORAGE_KEY,
	formatTime,
	LAYER_META,
	LAYER_ORDER,
	LEDGER_PAGE_SIZE,
	MetricTile,
	normalizeLayer,
	UNASSIGNED_DOMAIN_KEY,
} from "./assets/assetPageShared";

export default function Page() {
	const router = useRouter();
	const [searchParams, setSearchParams] = useSearchParams();
	const selectedTagIds = useMemo(() => readTagIds(searchParams), [searchParams]);
	const effectiveSelectedTagIds = ASSET_PORTAL_V2_ENABLED ? selectedTagIds : [];
	const selectedTagIdsKey = effectiveSelectedTagIds.join("\u0000");
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>(() => {
		try {
			return new URLSearchParams(window.location.search).get("domain") || undefined;
		} catch {
			return undefined;
		}
	});
	const [assetType, setAssetType] = useState<string>("ALL");
	const [classification, setClassification] = useState<string>("ALL");
	const [warehouseLayer, setWarehouseLayer] = useState<string>(() => {
		try {
			return new URLSearchParams(window.location.search).get("layer") || "ALL";
		} catch {
			return "ALL";
		}
	});
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
	const [pageState, setPageState] = useState(() => ({
		page: 1,
		size: LEDGER_PAGE_SIZE,
		total: 0,
	}));
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
	const [reconciliationLoading, setReconciliationLoading] = useState(false);
	const [governanceGapReport, setGovernanceGapReport] = useState<any | null>(null);
	const [lineageFailureReport, setLineageFailureReport] = useState<any | null>(null);
	const [signalsLoading, setSignalsLoading] = useState(false);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(false);
	const requestSeqRef = useRef(0);
	const pageSizeRef = useRef(LEDGER_PAGE_SIZE);
	const tagFilterEffectReadyRef = useRef(false);

	useEffect(() => {
		try {
			// 地图矩阵下钻等深链显式携带 layer/domain 时，URL 优先于本地缓存的筛选
			const hasDeepLinkFilters = Boolean(
				new URLSearchParams(window.location.search).get("layer") ||
					new URLSearchParams(window.location.search).get("domain"),
			);
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) return;
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(
				typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL",
			);
			setGovernanceStatus(
				typeof saved?.governanceStatus === "string" && saved.governanceStatus ? saved.governanceStatus : "ALL",
			);
			setMatchStatus(typeof saved?.matchStatus === "string" && saved.matchStatus ? saved.matchStatus : "ALL");
			if (!hasDeepLinkFilters) {
				setDomain(undefined);
				setWarehouseLayer(
					typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL",
				);
			}
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
			void loadDatasets(1, pageSizeRef.current);
			void loadGovernanceSignals();
		}, 280);
		return () => window.clearTimeout(timer);
	}, [keyword, domain, assetType, classification, warehouseLayer, governanceStatus, matchStatus]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: tag key intentionally triggers this tag-only reload; stable refs provide the current page size and request function inputs.
	useEffect(() => {
		void selectedTagIdsKey;
		if (!tagFilterEffectReadyRef.current) {
			tagFilterEffectReadyRef.current = true;
			return;
		}
		const timer = window.setTimeout(() => {
			void loadDatasets(1, pageSizeRef.current);
		}, 280);
		return () => window.clearTimeout(timer);
	}, [selectedTagIdsKey]);

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

	const buildAssetQuery = (page = 1, size = LEDGER_PAGE_SIZE) => ({
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

	const buildAssetListQuery = (page = 1, size = LEDGER_PAGE_SIZE) => ({
		...buildAssetQuery(page, size),
		tagIds: effectiveSelectedTagIds.length ? effectiveSelectedTagIds : undefined,
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

	const loadDatasets = async (page = 1, size = LEDGER_PAGE_SIZE) => {
		pageSizeRef.current = size;
		const reqId = ++requestSeqRef.current;
		setLoading(true);
		try {
			const resp: any = ASSET_PORTAL_V2_ENABLED
				? await listCatalogAssetsV2(buildAssetListQuery(page, size))
				: await listDatasets({
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
					assetType: item.assetType || item.grantAssetType || item.type || undefined,
					assetKey: item.assetKey || undefined,
					assetTags: Array.isArray(item.assetTags) ? item.assetTags : [],
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

	const selectedDomainName =
		domain === UNASSIGNED_DOMAIN_KEY ? "未归域" : domain ? domainMap.get(domain) || "当前主题域" : "全部主题域";

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
	const staleCount = records.filter((row) => STALE_LIFECYCLE_STATUSES.has(String(row.lifecycleStatus || "").toUpperCase())).length;
	const activeCount = records.filter((row) => row.status === "启用").length;
	const readinessCounts = records.reduce<Record<string, number>>((acc, row) => {
		const state = resolveAssetReadiness(row).state;
		acc[state] = (acc[state] || 0) + 1;
		return acc;
	}, {});
	const governanceCoverage = records.length
		? Math.round(((records.length - unclassifiedCount - missingDomainCount) / Math.max(records.length, 1)) * 100)
		: 0;
	const blockingGapCount = Number(governanceGapReport?.severityCounts?.BLOCKING || 0);
	const lineageFailureCount = Array.isArray(lineageFailureReport?.content) ? lineageFailureReport.content.length : 0;
	const pageTitle = "资产台账";
	const pageSubtitle = "核验登记、权属、密级、治理和消费出口。查看统计概览请返回资产地图。";
	const ledgerIssueCount = unclassifiedCount + missingDomainCount + blockingGapCount + lineageFailureCount;
	const governanceGapRows: GovernanceGapRow[] = Array.isArray(governanceGapReport?.content)
		? governanceGapReport.content
		: [];
	const lineageFailureRows: LineageFailureRow[] = Array.isArray(lineageFailureReport?.content)
		? lineageFailureReport.content
		: [];
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

	const resetFilters = () => {
		setKeyword("");
		setDomain(undefined);
		setAssetType("ALL");
		setClassification("ALL");
		setWarehouseLayer("ALL");
		setGovernanceStatus("ALL");
		setMatchStatus("ALL");
		setSearchParams(writeTagIds(searchParams, []), { replace: true });
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

	const [filtersOpen, setFiltersOpen] = useState(false);
	const activeFilterCount =
		[assetType, classification, governanceStatus, matchStatus].filter((value) => value && value !== "ALL").length +
		(effectiveSelectedTagIds.length > 0 ? 1 : 0);

	const opsItem = (title: string, description: string) => (
		<div className="py-0.5">
			<div>{title}</div>
			<div className="text-xs text-slate-400">{description}</div>
		</div>
	);
	// 低频同步/诊断动作收进菜单，避免工具栏按钮平铺（Sprint-57 F4-T05）
	const opsMenuItems = [
		...(ASSET_PORTAL_V2_ENABLED
			? [
					{
						key: "sync-om",
						disabled: syncing,
						label: opsItem(
							syncing ? "同步 OpenMetadata（进行中…）" : "同步 OpenMetadata",
							"从元数据平台拉取最新资产清单（耗时较长，完成后自动刷新）",
						),
					},
					{
						key: "diagnostics",
						disabled: diagnosticsLoading,
						label: opsItem("映射诊断", "统计已映射/未匹配/待人工确认数量，结果显示在工具栏下方"),
					},
					{
						key: "resolution-failures",
						disabled: resolutionFailuresLoading,
						label: opsItem("解析失败记录", "查看资产身份解析失败明细"),
					},
				]
			: []),
		{
			key: "reconciliation",
			disabled: reconciliationLoading,
			label: opsItem("发布前核对", "刷新发布前回归断言结果（页面底部展开查看）"),
		},
	];
	const handleOpsMenuClick = (key: string) => {
		if (key === "sync-om") void syncOpenMetadataAssets();
		if (key === "diagnostics") void loadDiagnostics();
		if (key === "resolution-failures") void loadResolutionFailures();
		if (key === "reconciliation") void loadReconciliation();
	};

	const exportLedgerCsv = () => {
		const header = ["名称", "物理位置", "类型", "分层", "主题域", "密级", "状态", "治理状态", "负责人", "更新时间"];
		const escapeCell = (value: unknown) => `"${String(value ?? "").replaceAll('"', '""')}"`;
		const lines = [header.map(escapeCell).join(",")];
		for (const row of records) {
			lines.push(
				[
					row.name,
					row.hiveDatabase && row.hiveTable ? `${row.hiveDatabase}.${row.hiveTable}` : row.description || row.id,
					row.type,
					LAYER_META[normalizeLayer(row.warehouseLayer)].label,
					row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域",
					classificationText(row.classification),
					row.status || "",
					resolveAssetReadiness(row).label,
					row.owner || row.ownerDept || "",
					formatTime(row.snapshotTime || row.updatedAt),
				]
					.map(escapeCell)
					.join(","),
			);
		}
		const blob = new Blob([`﻿${lines.join("\n")}`], { type: "text/csv;charset=utf-8" });
		const url = URL.createObjectURL(blob);
		const link = document.createElement("a");
		link.href = url;
		link.download = `asset-ledger-${new Date().toISOString().slice(0, 10)}.csv`;
		document.body.appendChild(link);
		link.click();
		link.remove();
		URL.revokeObjectURL(url);
	};

	const renderAssetTable = () => (
		<AssetLedgerView
			records={records}
			domainMap={domainMap}
			ledgerIssueCount={ledgerIssueCount}
			readyCount={Number(readinessCounts.READY || 0)}
			selectedDomainName={selectedDomainName}
			missingDomainCount={missingDomainCount}
			onOpenGovernanceRemediation={openGovernanceRemediation}
		/>
	);

	return (
			<Layout className="min-h-full" style={{ background: "transparent" }}>
				<Layout.Sider
					width={248}
					breakpoint="md"
					collapsedWidth={0}
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
					<PageHeader title={pageTitle} />
					<AssetLedgerToolbar
						activeFilterCount={activeFilterCount}
						assetType={assetType}
						classification={classification}
						filtersOpen={filtersOpen}
						governanceStatus={governanceStatus}
						keyword={keyword}
						loading={loading}
						matchStatus={matchStatus}
						onAssetTypeChange={setAssetType}
						onClassificationChange={setClassification}
						onGovernanceStatusChange={setGovernanceStatus}
						onKeywordChange={setKeyword}
						onMatchStatusChange={setMatchStatus}
						onOpsMenuClick={handleOpsMenuClick}
						onRefresh={() => void loadDatasets(1, pageState.size)}
						onReset={resetFilters}
						onReturnToMap={() => router.push("/catalog/assets")}
						onTagIdsChange={(nextIds) => {
							setSearchParams(writeTagIds(searchParams, nextIds), { replace: true });
						}}
						onToggleFilters={() => setFiltersOpen((open) => !open)}
						opsMenuItems={opsMenuItems}
						pageSubtitle={pageSubtitle}
						pageTitle={pageTitle}
						selectedTagIds={effectiveSelectedTagIds}
						tagFilterEnabled={ASSET_PORTAL_V2_ENABLED}
					/>

					{diagnostics ? (
						<Alert
							type={Number(diagnostics.unmatchedCount || 0) > 0 ? "warning" : "info"}
							showIcon
							message={`OpenMetadata映射诊断：资产 ${Number(diagnostics.assetCount || 0)}，已映射 ${Number(diagnostics.matchedCount || 0)}，未匹配 ${Number(diagnostics.unmatchedCount || 0)}，人工确认 ${Number(diagnostics.manualReviewCount || 0)}`}
							description={
								Array.isArray(diagnostics.issues) && diagnostics.issues.length > 0
									? diagnostics.issues
											.slice(0, 3)
											.map((item: any) => `${item.fqn || "-"}：${item.matchReason || item.matchStatus || "-"}`)
											.join("；")
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
						<MetricTile
							icon={<DatabaseOutlined />}
							label="台账总量"
							value={pageState.total}
							footnote={selectedDomainName}
						/>
						<MetricTile
							icon={<TableOutlined />}
							label="本页登记"
							value={records.length}
							footnote={`启用 ${activeCount} 个`}
						/>
						<MetricTile
							icon={<WarningOutlined />}
							label="待补字段"
							value={ledgerIssueCount}
							footnote={`未定密 ${unclassifiedCount} 个 / 未归域 ${missingDomainCount} 个`}
							tone={ledgerIssueCount > 0 ? "text-amber-600" : "text-green-600"}
						/>
						<MetricTile
							icon={<SafetyCertificateOutlined />}
							label="可消费资产"
							value={Number(readinessCounts.READY || 0)}
							footnote={`阻断 ${Number(readinessCounts.BLOCKED || 0)} 个 / 待确认 ${Number(readinessCounts.WARNING || 0)} 个`}
							tone={Number(readinessCounts.BLOCKED || 0) > 0 ? "text-amber-600" : "text-green-600"}
						/>
						<MetricTile
							icon={<SafetyCertificateOutlined />}
							label="治理覆盖率"
							value={`${Math.max(0, governanceCoverage)}%`}
							footnote={`血缘缺口 ${lineageFailureCount} 个 / 失效 ${staleCount} 个`}
							tone={governanceCoverage >= 80 ? "text-green-600" : "text-amber-600"}
						/>
					</div>

					<Card
						className="asset-ledger-card"
						title={
							<Space size={8}>
								<TableOutlined />
								<span>资产登记台账</span>
							</Space>
						}
					>
						<div className="asset-ledger-filter-strip mb-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
							<div className="mb-2 flex flex-wrap items-center justify-between gap-3">
								<div>
									<div className="text-sm font-semibold text-slate-900">资产登记台账</div>
									<div className="mt-1 text-xs text-slate-500">按登记字段、治理状态、密级和消费动作核验当前资产。</div>
								</div>
								<Space size={8}>
									<Button size="small" onClick={exportLedgerCsv} data-testid="asset-ledger-export">
										导出 CSV（本页）
									</Button>
									<Button size="small" onClick={() => router.push("/catalog/assets")}>
										返回地图
									</Button>
								</Space>
							</div>
							<Tabs
								activeKey={warehouseLayer}
								onChange={(value) => setWarehouseLayer(value || "ALL")}
								items={layerTabItems}
								tabBarStyle={{ marginBottom: 0 }}
							/>
						</div>
						{records.length ? (
							renderAssetTable()
						) : (
							<EmptyState
								title="未发现当前账号可见资产"
								description="可能还未完成数据源结构采集，也可能当前密级、主题域或资产授权限制了可见范围。"
							/>
						)}
						{pageState.total > 0 ? (
							<div className="asset-ledger-pagination mt-4 flex justify-end">
								<Pagination
									size="small"
									current={pageState.page}
									pageSize={pageState.size}
									total={pageState.total}
									showSizeChanger
									pageSizeOptions={[10, 20, 50, 100]}
									onChange={(page, size) => {
										const nextSize = size || LEDGER_PAGE_SIZE;
										void loadDatasets(nextSize !== pageState.size ? 1 : page, nextSize);
									}}
								/>
							</div>
						) : null}
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
								children: (
									<AssetReconciliationPanel reconciliation={reconciliation} onNavigate={(path) => router.push(path)} />
								),
							},
						]}
					/>
				</div>
			</Layout.Content>
			<AssetLedgerDialogs
				governanceGapRows={governanceGapRows}
				lineageFailureRows={lineageFailureRows}
				onCloseRemediation={() => setRemediationOpen(false)}
				onCloseResolutionFailures={() => setResolutionFailuresOpen(false)}
				onNavigate={(path) => router.push(path)}
				onOpenGovernanceRemediation={openGovernanceRemediation}
				onRefreshGovernanceSignals={() => void loadGovernanceSignals()}
				onSyncLineage={(assetId) => void syncLineageForRow(assetId)}
				remediationLoading={remediationLoading}
				remediationOpen={remediationOpen}
				resolutionFailures={resolutionFailures}
				resolutionFailuresLoading={resolutionFailuresLoading}
				resolutionFailuresOpen={resolutionFailuresOpen}
				signalsLoading={signalsLoading}
			/>
		</Layout>
	);
}
