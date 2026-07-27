import { DatabaseOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Layout, Spin, Tabs, Tag, Tooltip } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { getCatalogAssetsOverview, getDomainTree, listCatalogAssetsV2 } from "@/api/platformApi";
import { DomainScopeNav, type DomainScopeStats } from "@/components/catalog/DomainScopeNav";
import { TagManagementTab } from "@/components/catalog/tags/TagManagementTab";
import { PageHeader } from "@/components/page-header";
import { useCatalogTagGovernanceAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "./assetPortalUx.helpers";
import { GOVERNANCE_STATUS_DICT, resolveEnumLabel } from "./assets/assetEnumLabels";
import { GovernanceGapPanel } from "./assets/GovernanceGapPanel";
import type { AssetRow, DomainNode } from "./assets/assetPageShared";
import {
	buildDomainScopeNodes,
	LAYER_META,
	LAYER_ORDER,
	MetricTile,
	normalizeLayer,
	UNASSIGNED_DOMAIN_KEY,
} from "./assets/assetPageShared";

/** 矩阵最多展示的主题域列数，超出合并为「其他 N 个域」并明示 */
const MATRIX_MAX_COLUMNS = 8;

/** 合并列的伪 key。它不是真实域 id，下钻时必须特殊处理，否则台账按 UUID 绑定会 400。 */
const MERGED_DOMAIN_KEY = "__OTHERS__";

type MatrixCell = { layer: string; domainId: string | null; total: number; attention: number };

type AssetOverview = {
	total?: number;
	unclassified?: number;
	missingDomain?: number;
	stale?: number;
	attention?: number;
	byLayer?: Record<string, number>;
	governanceStatusCounts?: Record<string, number>;
	matrix?: MatrixCell[];
	scanned?: number;
	truncated?: boolean;
};

/**
 * 资产地图 = 纯统计概览：全量聚合数据源，零输入控件；
 * 一切执行动作（搜索/筛选/行级操作/诊断运维）都在台账（/catalog/assets/ledger）。
 */
export default function AssetOverviewPage() {
	const router = useRouter();
	const [searchParams, setSearchParams] = useSearchParams();
	const canManageCatalog = useCatalogTagGovernanceAccess();
	const activeTab = searchParams.get("tab") === "catalog-tags" ? "catalog-tags" : "asset-map";
	const isLegacyLedgerRedirect = searchParams.get("view") === "table";
	const isAssetMapActive = activeTab === "asset-map" && !isLegacyLedgerRedirect;
	const domain = searchParams.get("domain") || undefined;
	const setDomain = useCallback(
		(next: string | undefined) => {
			const params = new URLSearchParams(searchParams);
			if (next) params.set("domain", next);
			else params.delete("domain");
			setSearchParams(params);
		},
		[searchParams, setSearchParams],
	);
	const [overview, setOverview] = useState<AssetOverview | null>(null);
	const [overviewLoading, setOverviewLoading] = useState(false);
	const [attentionRows, setAttentionRows] = useState<AssetRow[]>([]);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [domainStats, setDomainStats] = useState<{
		all?: DomainScopeStats;
		unassigned?: DomainScopeStats;
		byDomain?: Record<string, DomainScopeStats>;
		truncated?: boolean;
	}>({});
	const [treeLoading, setTreeLoading] = useState(false);
	const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

	// ?view=table 旧深链兼容：台账已是独立路由
	useEffect(() => {
		if (!isLegacyLedgerRedirect) return;
		const params = new URLSearchParams(searchParams);
		params.delete("view");
		params.delete("tab");
		const rest = params.toString();
		router.replace(`/catalog/assets/ledger${rest ? `?${rest}` : ""}`);
	}, [isLegacyLedgerRedirect, router, searchParams]);

	useEffect(() => {
		if (!isAssetMapActive) return;
		void (async () => {
			setTreeLoading(true);
			try {
				// 单次请求同时拿树与统计，消除「树取 getDomainTree、矩阵列名取 listDomains」的双数据源漂移
				const resp = (await getDomainTree({ withStats: true })) as any;
				const payload = resp?.data ?? resp;
				const tree = Array.isArray(payload) ? payload : payload?.tree;
				setDomainTree(Array.isArray(tree) ? tree : []);
				const stats = payload?.stats;
				setDomainStats(
					stats
						? {
								all: stats.all,
								unassigned: stats.unassigned,
								byDomain: stats.byDomain || {},
								truncated: Boolean(stats.truncated),
							}
						: {},
				);
			} catch {
				// error toast handled by global interceptor
			} finally {
				setTreeLoading(false);
			}
		})();
	}, [isAssetMapActive]);

	// 矩阵列名与导航同源，避免域超过 listDomains 的取数上限时列名掉成「未知主题域」
	const domainMap = useMemo(() => {
		const map = new Map<string, string>();
		const walk = (nodes: DomainNode[]) => {
			for (const node of nodes) {
				if (node.id) map.set(String(node.id), node.name ?? node.code ?? "未命名");
				if (node.children?.length) walk(node.children);
			}
		};
		walk(domainTree);
		return map;
	}, [domainTree]);

	const loadOverview = useCallback(async () => {
		if (!isAssetMapActive) return;
		setOverviewLoading(true);
		try {
			const scope = {
				domainId: domain && domain !== UNASSIGNED_DOMAIN_KEY ? domain : undefined,
				domainUnassigned: domain === UNASSIGNED_DOMAIN_KEY || undefined,
			};
			const [overviewResp, rowsResp] = await Promise.all([
				getCatalogAssetsOverview(scope) as Promise<AssetOverview>,
				listCatalogAssetsV2({ page: 0, size: 50, ...scope }) as Promise<any>,
			]);
			setOverview(overviewResp || null);
			setLastUpdatedAt(new Date());
			const rows: AssetRow[] = Array.isArray(rowsResp?.content)
				? rowsResp.content.map((item: any) => ({
						id: String(item.id || ""),
						name: item.displayName || item.table || item.fqn || "",
						type: item.type || "",
						domainId: item.domainId ? String(item.domainId) : undefined,
						classification: item.classification,
						warehouseLayer: item.warehouseLayer,
						lifecycleStatus: item.lifecycleStatus,
						governanceStatus: item.governanceStatus,
						matchStatus: item.matchStatus,
					}))
				: [];
			setAttentionRows(
				rows
					.map((row) => ({ row, readiness: resolveAssetReadiness(row) }))
					.filter((item) => item.readiness.state !== "READY")
					.slice(0, 5)
					.map((item) => item.row),
			);
		} catch {
			// error toast handled by global interceptor
		} finally {
			setOverviewLoading(false);
		}
	}, [domain, isAssetMapActive]);

	useEffect(() => {
		if (!isAssetMapActive) return;
		void loadOverview();
	}, [isAssetMapActive, loadOverview]);

	const drillToLedger = (layer?: string, domainKey?: string | null) => {
		const params = new URLSearchParams();
		if (layer && layer !== "ALL") params.set("layer", layer);
		if (domainKey === MERGED_DOMAIN_KEY) {
			// 「其他 N 个域」是展示用的聚合列，不是真实域 id；台账把 domain 当 UUID 绑定，
			// 传过去会 400。这里只带分层，域维度不加筛选。
			if (domain) params.set("domain", domain);
		} else if (domainKey === null) {
			params.set("domain", UNASSIGNED_DOMAIN_KEY);
		} else if (domainKey) {
			params.set("domain", domainKey);
		} else if (domain) {
			params.set("domain", domain);
		}
		const rest = params.toString();
		router.push(`/catalog/assets/ledger${rest ? `?${rest}` : ""}`);
	};

	const matrixColumns = useMemo(() => {
		const cells = overview?.matrix || [];
		const totals = new Map<string, { key: string | null; total: number }>();
		for (const cell of cells) {
			const key = cell.domainId === null ? "__NULL__" : cell.domainId;
			const entry = totals.get(key) || { key: cell.domainId, total: 0 };
			entry.total += cell.total;
			totals.set(key, entry);
		}
		const sorted = [...totals.values()].sort((a, b) => b.total - a.total);
		const named = sorted.map((entry) => ({
			key: entry.key,
			name: entry.key === null ? "未归域" : domainMap.get(entry.key) || "未知主题域",
			total: entry.total,
		}));
		if (named.length <= MATRIX_MAX_COLUMNS) return named;
		// 截断必须明示：此前静默 slice(0,8)，域多了用户不知道自己看的是局部
		const head = named.slice(0, MATRIX_MAX_COLUMNS);
		const rest = named.slice(MATRIX_MAX_COLUMNS);
		return [
			...head,
			{
				key: MERGED_DOMAIN_KEY,
				name: `其他 ${rest.length} 个域`,
				total: rest.reduce((sum, item) => sum + item.total, 0),
				mergedNames: rest.map((item) => item.name),
				mergedKeys: rest.map((item) => (item.key === null ? "__NULL__" : item.key)),
			},
		];
	}, [overview, domainMap]);

	const lastUpdatedText = useMemo(
		() => (lastUpdatedAt ? lastUpdatedAt.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" }) : "—"),
		[lastUpdatedAt],
	);

	// 可见域 <=1 时矩阵退化为单行分层分布：否则会画出 8 行 x 1 列、其中多行是「-」的空表
	const isSingleDomainScope = matrixColumns.length === 1;

	const scopeLabel = domain
		? domain === UNASSIGNED_DOMAIN_KEY
			? "未归域"
			: domainMap.get(domain) || "当前主题域"
		: "全部主题域";

	const matrixCellMap = useMemo(() => {
		const map = new Map<string, MatrixCell>();
		const mergedKeys = new Set(
			(matrixColumns.find((col) => col.key === MERGED_DOMAIN_KEY) as any)?.mergedKeys ?? [],
		);
		for (const cell of overview?.matrix || []) {
			const domainKey = cell.domainId === null ? "__NULL__" : cell.domainId;
			map.set(`${cell.layer}|${domainKey}`, cell);
			// 被合并进「其他 N 个域」的列必须预聚合，否则该列每格都查不到而恒显示 "-"
			if (mergedKeys.has(domainKey)) {
					const key = `${cell.layer}|${MERGED_DOMAIN_KEY}`;
				const prev = map.get(key);
				map.set(
					key,
					prev
						? { ...prev, total: prev.total + cell.total, attention: prev.attention + cell.attention }
						: { layer: cell.layer, domainId: "__OTHERS__", total: cell.total, attention: cell.attention },
				);
			}
		}
		return map;
	}, [overview, matrixColumns]);

	// 缺口原因合并三处口径：治理状态分布 + 未定密 + 失效，避免同一件事讲三遍
	const gapReasons = useMemo(() => {
		const counts = overview?.governanceStatusCounts || {};
		const fromGovernance = Object.entries(counts)
			.filter(([status]) => status !== "GOVERNED")
			.map(([status, count]) => ({
				key: status,
				label: resolveEnumLabel(GOVERNANCE_STATUS_DICT, status),
				count: Number(count) || 0,
			}));
		return [
			...fromGovernance,
			{ key: "UNCLASSIFIED", label: "未定密", count: overview?.unclassified ?? 0 },
			{ key: "STALE", label: "失效", count: overview?.stale ?? 0 },
		].sort((a, b) => b.count - a.count);
	}, [overview]);

	const drillToLedgerByReason = useCallback(
		(key: string) => {
			const params = new URLSearchParams();
			if (domain) params.set("domain", domain);
			// 只传台账真正会读取的参数。未定密/失效在台账没有对应筛选位，
			// 传了也不会生效，反而让用户以为筛过了——宁可只带范围。
			// 尤其不能传 lifecycle=STALE：该取值不在 CatalogAssetLifecycleStatus 中，恒不命中。
			if (key !== "UNCLASSIFIED" && key !== "STALE") {
				params.set("governance", key);
			}
			const rest = params.toString();
			router.push(`/catalog/assets/ledger${rest ? `?${rest}` : ""}`);
		},
		[domain, router],
	);

	const scopeNodes = useMemo(
		() => buildDomainScopeNodes(domainTree, domainStats.byDomain),
		[domainTree, domainStats.byDomain],
	);

	const handleTabChange = (key: string) => {
		const params = new URLSearchParams(searchParams);
		if (key === "catalog-tags") {
			params.set("tab", "catalog-tags");
		} else {
			params.delete("tab");
		}
		setSearchParams(params);
	};

	const assetTabs = (
		<Tabs
			activeKey={activeTab}
			onChange={handleTabChange}
			items={[
				{ key: "asset-map", label: "资产地图" },
				{ key: "catalog-tags", label: "数据标签" },
			]}
		/>
	);

	if (isLegacyLedgerRedirect) return null;

	if (activeTab === "catalog-tags") {
		return (
			<div className="space-y-4">
				{assetTabs}
				<Alert
					type="info"
					showIcon
					message="标签字典用于数据资产打标"
					description="统一维护标签分类与标签字典，供资产台账和资产详情选择使用。"
				/>
				<TagManagementTab canManage={canManageCatalog} />
			</div>
		);
	}

	return (
		<Layout className="min-h-full bg-transparent">
			<Layout.Sider
				width={240}
				breakpoint="md"
				collapsedWidth={0}
				theme="light"
				className="rounded-lg border border-slate-200 bg-white p-3"
			>
				<DomainScopeNav
					nodes={scopeNodes}
					allStats={domainStats.all}
					unassignedStats={domainStats.unassigned}
					value={domain}
					onChange={setDomain}
					loading={treeLoading}
					truncated={domainStats.truncated}
				/>
			</Layout.Sider>
			<Layout.Content style={{ padding: "0 16px" }}>
				{assetTabs}
				<div className="space-y-4">
					<PageHeader
						title="资产地图"
						actions={
							<div className="flex items-center gap-2 text-xs text-slate-500">
								<span>统计更新于 {lastUpdatedText}</span>
								<Button
									type="text"
									size="small"
									icon={<ReloadOutlined />}
									aria-label="刷新统计"
									loading={overviewLoading}
									onClick={() => void loadOverview()}
								/>
							</div>
						}
					/>
					<div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
						<span>统计概览：查找、筛选与处置在台账完成。</span>
						<button
							type="button"
							data-testid="scope-echo"
							onClick={() => drillToLedger()}
							className="rounded bg-slate-100 px-2 py-0.5 text-slate-700 transition hover:bg-slate-200"
						>
							当前范围：{scopeLabel}
						</button>
					</div>
					{overview?.truncated ? (
						<Alert
							type="warning"
							showIcon
							message={`统计基于前 ${overview.scanned} 条可见资产（已达统计上限），实际总量可能更多`}
						/>
					) : null}

					<div className="grid gap-3 md:grid-cols-[minmax(180px,240px)_1fr]">
						<MetricTile
							icon={<DatabaseOutlined />}
							label="资产总量"
							value={overview?.truncated ? `≥${overview?.total ?? 0}` : (overview?.total ?? 0)}
							footnote={domain ? domainMap.get(domain) || "未归域" : "全部主题域"}
						/>
						<GovernanceGapPanel
							total={overview?.total ?? 0}
							attention={overview?.attention ?? 0}
							reasons={gapReasons}
							onReasonClick={drillToLedgerByReason}
							loading={overviewLoading}
							truncated={Boolean(overview?.truncated)}
						/>
					</div>

					<div className="rounded-xl border border-slate-200 bg-white p-4" data-testid="asset-overview-matrix">
						<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
							<div>
								<div className="text-sm font-semibold text-slate-900">
									{isSingleDomainScope ? "分层分布" : "分层×主题域矩阵"}
								</div>
								<div className="mt-1 text-xs text-slate-500">
									{isSingleDomainScope
										? "当前范围只有一个主题域，按分层展示；点击进入台账查看明细。"
										: "格子 = 该分层×主题域下的资产数，点击进入台账查看明细。"}
								</div>
							</div>
							<Spin spinning={overviewLoading} size="small" />
						</div>
						{isSingleDomainScope ? (
							<div className="flex flex-wrap gap-2" data-testid="layer-distribution">
								{[...LAYER_ORDER].map((layer) => {
									const cell = matrixCellMap.get(
										`${layer}|${matrixColumns[0].key === null ? "__NULL__" : matrixColumns[0].key}`,
									);
									const meta = LAYER_META[layer];
									return (
										<button
											key={layer}
											type="button"
											disabled={!cell}
											onClick={() => cell && drillToLedger(layer, matrixColumns[0].key)}
											className={`rounded-md border px-3 py-2 text-left text-xs transition ${
												cell ? `${meta.tone} hover:ring-2 hover:ring-blue-200` : "border-slate-100 bg-slate-50 text-slate-300"
											}`}
										>
											<div className="font-medium text-slate-700">
												{meta.label}
												{meta.code ? <span className="ml-1 text-[10px] text-slate-400">{meta.code}</span> : null}
											</div>
											<div className="mt-0.5 tabular-nums font-semibold text-slate-900">{cell ? cell.total : "—"}</div>
											{cell && cell.attention > 0 ? (
												<div className="text-[10px] text-amber-600">待处置 {cell.attention}</div>
											) : null}
										</button>
									);
								})}
							</div>
						) : matrixColumns.length ? (
							<div className="overflow-x-auto">
								<table className="w-full min-w-[720px] border-separate" style={{ borderSpacing: 4 }}>
									<thead>
										<tr>
											<th className="px-2 py-1 text-left text-xs font-medium text-slate-400">分层 \ 主题域</th>
											{matrixColumns.map((col) => (
												<th key={String(col.key)} className="px-2 py-1 text-left text-xs font-medium text-slate-600">
													<span className="line-clamp-1" title={(col as any).mergedNames?.join("、")}>
														{col.name}
													</span>
												</th>
											))}
										</tr>
									</thead>
									<tbody>
										{[...LAYER_ORDER].map((layer) => (
											<tr key={layer}>
												<td className="whitespace-nowrap px-2 py-1 text-xs font-medium text-slate-600">
													{LAYER_META[layer].label}
												</td>
												{matrixColumns.map((col) => {
													const cell = matrixCellMap.get(`${layer}|${col.key === null ? "__NULL__" : col.key}`);
													if (!cell) {
														return (
															<td
																key={String(col.key)}
																className="rounded bg-slate-50 px-2 py-2 text-center text-xs text-slate-300"
															>
																-
															</td>
														);
													}
													return (
														<td key={String(col.key)} className="p-0">
															<button
																type="button"
																className={`w-full rounded px-2 py-2 text-center text-xs font-semibold transition hover:ring-2 hover:ring-blue-200 ${cell.attention > 0 ? "bg-amber-50 text-amber-700" : "bg-green-50 text-green-700"}`}
																onClick={() => drillToLedger(layer, col.key)}
															>
																{cell.total}
																{cell.attention > 0 ? (
																	<span className="ml-1 text-[10px]">待处置 {cell.attention}</span>
																) : null}
															</button>
														</td>
													);
												})}
											</tr>
										))}
									</tbody>
								</table>
							</div>
						) : (
							<div className="py-8 text-center text-xs text-slate-400">
								{overviewLoading ? "统计加载中…" : "当前范围内暂无资产"}
							</div>
						)}
					</div>

					<div className="rounded-xl border border-slate-200 bg-white p-4">
						<div className="mb-3 flex items-center justify-between gap-3">
							<div className="text-sm font-semibold text-slate-900">待处置 Top 5</div>
						</div>
						{attentionRows.length ? (
							<div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5">
								{attentionRows.map((row) => {
									const readiness = resolveAssetReadiness(row);
									return (
										<button
											key={row.id}
											type="button"
											className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-left transition hover:border-blue-200"
											onClick={() => router.push(`/catalog/datasets/${row.id}`)}
										>
											<div className="flex items-start justify-between gap-2">
												<Tooltip title={row.name}>
													<div className="min-w-0 flex-1 truncate text-xs font-semibold text-slate-800">{row.name}</div>
												</Tooltip>
												<Tag color={readiness.color} style={{ fontSize: 11 }}>
													{readiness.label}
												</Tag>
											</div>
											<div className="mt-1 truncate text-[11px] text-slate-500">
												{LAYER_META[normalizeLayer(row.warehouseLayer)].label} · {readiness.reasons[0] || "待确认"}
											</div>
										</button>
									);
								})}
							</div>
						) : (
							<div className="py-6 text-center text-xs text-slate-400">
								{overviewLoading ? "加载中…" : "当前范围内没有待处置资产"}
							</div>
						)}
					</div>
				</div>
			</Layout.Content>
		</Layout>
	);
}
