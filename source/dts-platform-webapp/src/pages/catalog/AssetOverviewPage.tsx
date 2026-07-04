import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Button, Layout, Spin, Tag, Tooltip, Tree } from "antd";
import { ApartmentOutlined, BranchesOutlined, DatabaseOutlined, SafetyCertificateOutlined, TableOutlined, WarningOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { getCatalogAssetsOverview, getDomainTree, listCatalogAssetsV2, listDomains } from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "./assetPortalUx.helpers";
import { LAYER_META, LAYER_ORDER, UNASSIGNED_DOMAIN_KEY, MetricTile, buildTreeNodes, normalizeLayer } from "./assets/assetPageShared";
import type { AssetRow, DomainNode } from "./assets/assetPageShared";

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

const GOVERNANCE_STATUS_LABELS: Record<string, string> = {
	GOVERNED: "已治理",
	PENDING_CLAIM: "待认领",
	PENDING_CLASSIFICATION: "待定级",
	PENDING_DOMAIN: "待归域",
	DISABLED: "停用",
};

/**
 * 资产地图 = 纯统计概览：全量聚合数据源，零输入控件；
 * 一切执行动作（搜索/筛选/行级操作/诊断运维）都在台账（/catalog/assets/ledger）。
 */
export default function AssetOverviewPage() {
	const router = useRouter();
	const [domain, setDomain] = useState<string | undefined>();
	const [overview, setOverview] = useState<AssetOverview | null>(null);
	const [overviewLoading, setOverviewLoading] = useState(false);
	const [attentionRows, setAttentionRows] = useState<AssetRow[]>([]);
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [domainTree, setDomainTree] = useState<DomainNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(false);

	// ?view=table 旧深链兼容：台账已是独立路由
	useEffect(() => {
		try {
			const params = new URLSearchParams(window.location.search);
			if (params.get("view") === "table") {
				params.delete("view");
				const rest = params.toString();
				router.replace(`/catalog/assets/ledger${rest ? `?${rest}` : ""}`);
			}
		} catch {
			// ignore
		}
	}, [router]);

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
		void (async () => {
			setTreeLoading(true);
			try {
				const tree = (await getDomainTree()) as any;
				setDomainTree(Array.isArray(tree) ? tree : Array.isArray(tree?.data) ? tree.data : []);
			} catch {
				// error toast handled by global interceptor
			} finally {
				setTreeLoading(false);
			}
		})();
	}, []);

	const domainMap = useMemo(() => new Map(domains.map((item) => [item.id, item.name])), [domains]);

	const loadOverview = useCallback(async () => {
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
	}, [domain]);

	useEffect(() => {
		void loadOverview();
	}, [loadOverview]);

	const drillToLedger = (layer?: string, domainKey?: string | null) => {
		const params = new URLSearchParams();
		if (layer && layer !== "ALL") params.set("layer", layer);
		if (domainKey === null) {
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
		return [...totals.values()]
			.sort((a, b) => b.total - a.total)
			.slice(0, 8)
			.map((entry) => ({
				key: entry.key,
				name: entry.key === null ? "未归域" : domainMap.get(entry.key) || "未知主题域",
			}));
	}, [overview, domainMap]);

	const matrixCellMap = useMemo(() => {
		const map = new Map<string, MatrixCell>();
		for (const cell of overview?.matrix || []) {
			map.set(`${cell.layer}|${cell.domainId === null ? "__NULL__" : cell.domainId}`, cell);
		}
		return map;
	}, [overview]);

	const governanceChips = useMemo(
		() =>
			Object.entries(overview?.governanceStatusCounts || {})
				.sort((a, b) => b[1] - a[1])
				.slice(0, 6),
		[overview],
	);

	const treeData = useMemo(
		() => [
			{
				key: "ALL",
				title: "全部资产",
				children: [{ key: UNASSIGNED_DOMAIN_KEY, title: "未归域" }, ...buildTreeNodes(domainTree)],
			},
		],
		[domainTree],
	);

	return (
		<Layout className="min-h-full bg-transparent">
			<Layout.Sider width={240} theme="light" className="rounded-lg border border-slate-200 bg-white p-3">
				<div className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
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
					<PageHeader
						title="资产地图"
						actions={
							<div className="flex gap-2">
								<Button type="primary" ghost icon={<TableOutlined />} onClick={() => drillToLedger()}>
									进入台账
								</Button>
								<Button onClick={() => void loadOverview()} loading={overviewLoading}>
									刷新
								</Button>
							</div>
						}
					/>
					<div className="rounded-md border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
						统计概览视图：数字基于当前主题域范围的全量聚合。查找具体资产、筛选、导出与运维诊断请进入台账。
					</div>
					{overview?.truncated ? (
						<Alert type="warning" showIcon message={`资产数量超过扫描上限，以下统计基于前 ${overview.scanned} 条`} />
					) : null}

					<div className="grid gap-3 md:grid-cols-5">
						<MetricTile icon={<DatabaseOutlined />} label="资产总量" value={overview?.total ?? 0} footnote={domain ? domainMap.get(domain) || "未归域" : "全部主题域"} />
						<MetricTile
							icon={<WarningOutlined />}
							label="待处置"
							value={overview?.attention ?? 0}
							footnote="未定密 / 未归域 / 失效 / 待治理"
							tone={(overview?.attention ?? 0) > 0 ? "text-amber-600" : "text-green-600"}
						/>
						<MetricTile icon={<SafetyCertificateOutlined />} label="未定密" value={overview?.unclassified ?? 0} tone={(overview?.unclassified ?? 0) > 0 ? "text-amber-600" : "text-green-600"} />
						<MetricTile icon={<ApartmentOutlined />} label="未归域" value={overview?.missingDomain ?? 0} tone={(overview?.missingDomain ?? 0) > 0 ? "text-amber-600" : "text-green-600"} />
						<MetricTile icon={<BranchesOutlined />} label="失效资产" value={overview?.stale ?? 0} tone={(overview?.stale ?? 0) > 0 ? "text-red-600" : "text-green-600"} />
					</div>

					{governanceChips.length ? (
						<div className="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-3">
							<span className="text-xs text-slate-500">治理状态分布</span>
							{governanceChips.map(([status, count]) => (
								<Tag key={status} color={status === "GOVERNED" ? "green" : "orange"}>
									{GOVERNANCE_STATUS_LABELS[status] || status} {count}
								</Tag>
							))}
						</div>
					) : null}

					<div className="rounded-xl border border-slate-200 bg-white p-4" data-testid="asset-overview-matrix">
						<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
							<div>
								<div className="text-sm font-semibold text-slate-900">分层×主题域矩阵</div>
								<div className="mt-1 text-xs text-slate-500">格子 = 该分层×主题域下的资产数，点击进入台账查看明细。</div>
							</div>
							<Spin spinning={overviewLoading} size="small" />
						</div>
						{matrixColumns.length ? (
							<div className="overflow-x-auto">
								<table className="w-full min-w-[720px] border-separate" style={{ borderSpacing: 4 }}>
									<thead>
										<tr>
											<th className="px-2 py-1 text-left text-xs font-medium text-slate-400">分层 \ 主题域</th>
											{matrixColumns.map((col) => (
												<th key={String(col.key)} className="px-2 py-1 text-left text-xs font-medium text-slate-600">
													<span className="line-clamp-1">{col.name}</span>
												</th>
											))}
										</tr>
									</thead>
									<tbody>
										{[...LAYER_ORDER].map((layer) => (
											<tr key={layer}>
												<td className="whitespace-nowrap px-2 py-1 text-xs font-medium text-slate-600">{LAYER_META[layer].label}</td>
												{matrixColumns.map((col) => {
													const cell = matrixCellMap.get(`${layer}|${col.key === null ? "__NULL__" : col.key}`);
													if (!cell) {
														return (
															<td key={String(col.key)} className="rounded bg-slate-50 px-2 py-2 text-center text-xs text-slate-300">
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
																{cell.attention > 0 ? <span className="ml-1 text-[10px]">待处置 {cell.attention}</span> : null}
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
							<div className="py-8 text-center text-xs text-slate-400">{overviewLoading ? "统计加载中…" : "当前范围内暂无资产"}</div>
						)}
					</div>

					<div className="rounded-xl border border-slate-200 bg-white p-4">
						<div className="mb-3 flex items-center justify-between gap-3">
							<div className="text-sm font-semibold text-slate-900">待处置 Top 5</div>
							<Button size="small" onClick={() => drillToLedger()}>
								去台账处置
							</Button>
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
							<div className="py-6 text-center text-xs text-slate-400">{overviewLoading ? "加载中…" : "当前范围内没有待处置资产"}</div>
						)}
					</div>
				</div>
			</Layout.Content>
		</Layout>
	);
}
