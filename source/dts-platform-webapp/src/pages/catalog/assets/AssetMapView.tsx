import { useMemo } from "react";
import { Button, Space, Tag, Tooltip } from "antd";
import { ArrowRightOutlined } from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "../assetPortalUx.helpers";
import { LAYER_META, LAYER_ORDER, UNASSIGNED_DOMAIN_KEY, normalizeLayer } from "./assetPageShared";
import type { AssetRow } from "./assetPageShared";

export interface AssetMapViewProps {
	records: AssetRow[];
	domainMap: Map<string, string>;
	warehouseLayer: string;
	signalsLoading: boolean;
	blockingGapCount: number;
	lineageFailureCount: number;
	missingDomainCount: number;
	onSelectLayer: (layer: string) => void;
	onOpenRemediationWorkbench: () => void;
	onOpenGovernanceRemediation: (assetId?: string) => void;
	onEnterLedger: () => void;
	onDrillToLedger: (layer: string, domainKey?: string) => void;
}

/** 资产地图视图：链路总览 + 分层×主题域导航矩阵 + 治理信号侧栏（明细职责归台账）。 */
export function AssetMapView({
	records,
	domainMap,
	warehouseLayer,
	signalsLoading,
	blockingGapCount,
	lineageFailureCount,
	missingDomainCount,
	onSelectLayer,
	onOpenRemediationWorkbench,
	onOpenGovernanceRemediation,
	onEnterLedger,
	onDrillToLedger,
}: AssetMapViewProps) {
	const router = useRouter();

	const layerGroups = useMemo(
		() =>
			LAYER_ORDER.map((layer) => {
				const items = records.filter((row) => normalizeLayer(row.warehouseLayer) === layer);
				const summary = items.reduce(
					(acc, row) => {
						const readiness = resolveAssetReadiness(row).state;
						if (readiness === "READY") acc.ready += 1;
						if (readiness === "BLOCKED") acc.blocked += 1;
						if (readiness === "WARNING") acc.warning += 1;
						if (readiness === "FALLBACK") acc.fallback += 1;
						return acc;
					},
					{ ready: 0, blocked: 0, warning: 0, fallback: 0 },
				);
				return {
					layer,
					meta: LAYER_META[layer],
					items,
					...summary,
					attention: summary.blocked + summary.warning,
				};
			}),
		[records],
	);
	const attentionAssets = useMemo(
		() =>
			records
				.map((row) => ({ row, readiness: resolveAssetReadiness(row) }))
				.filter((item) => item.readiness.state !== "READY" || (!item.row.domain && !item.row.domainId))
				.slice(0, 6),
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


	const domainMatrix = useMemo(() => {
		const columns = new Map<string, { key: string; name: string }>();
		const cells = new Map<string, { total: number; attention: number }>();
		for (const row of records) {
			const domainKey = row.domainId ? String(row.domainId) : UNASSIGNED_DOMAIN_KEY;
			const domainName = row.domain || (row.domainId ? domainMap.get(row.domainId) : undefined) || "未归域";
			if (!columns.has(domainKey)) {
				columns.set(domainKey, { key: domainKey, name: domainName });
			}
			const layer = normalizeLayer(row.warehouseLayer);
			const cellKey = `${layer}|${domainKey}`;
			const cell = cells.get(cellKey) || { total: 0, attention: 0 };
			cell.total += 1;
			if (resolveAssetReadiness(row).state !== "READY") {
				cell.attention += 1;
			}
			cells.set(cellKey, cell);
		}
		return { columns: [...columns.values()], cells };
	}, [records, domainMap]);


	const renderAssetMatrix = () => (
		<div className="rounded-xl border border-slate-200 bg-white p-4" data-testid="asset-map-matrix">
			<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
				<div>
					<div className="text-sm font-semibold text-slate-900">分层×主题域矩阵</div>
					<div className="mt-1 text-xs text-slate-500">格子 = 该分层×主题域下的资产数，点击进入台账查看明细。</div>
				</div>
				<Tag color="blue">{records.length} 个资产</Tag>
			</div>
			{domainMatrix.columns.length ? (
				<div className="overflow-x-auto">
					<table className="w-full min-w-[720px] border-separate" style={{ borderSpacing: 4 }}>
						<thead>
							<tr>
								<th className="px-2 py-1 text-left text-xs font-medium text-slate-400">分层 \ 主题域</th>
								{domainMatrix.columns.map((col) => (
									<th key={col.key} className="px-2 py-1 text-left text-xs font-medium text-slate-600">
										<span className="line-clamp-1">{col.name}</span>
									</th>
								))}
							</tr>
						</thead>
						<tbody>
							{LAYER_ORDER.map((layer) => (
								<tr key={layer}>
									<td className="whitespace-nowrap px-2 py-1 text-xs font-medium text-slate-600">{LAYER_META[layer].label}</td>
									{domainMatrix.columns.map((col) => {
										const cell = domainMatrix.cells.get(`${layer}|${col.key}`);
										if (!cell) {
											return (
												<td key={col.key} className="rounded bg-slate-50 px-2 py-2 text-center text-xs text-slate-300">
													-
												</td>
											);
										}
										return (
											<td key={col.key} className="p-0">
												<button
													type="button"
													className={`w-full rounded px-2 py-2 text-center text-xs font-semibold transition hover:ring-2 hover:ring-blue-200 ${cell.attention > 0 ? "bg-amber-50 text-amber-700" : "bg-green-50 text-green-700"}`}
													onClick={() => onDrillToLedger(layer, col.key)}
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
				<div className="py-8 text-center text-xs text-slate-400">当前筛选范围内暂无资产</div>
			)}
		</div>
	);

	return (
		<div className="asset-map-stage space-y-4">
			<div className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
				<div>
					<div className="text-sm font-semibold text-slate-900">资产链路总览</div>
					<div className="mt-1 text-xs text-slate-500">
						当前筛选范围内的数据接入、入湖开发、治理可用和服务发布状态。
					</div>
				</div>
					<Space>
						<Button onClick={() => onOpenRemediationWorkbench()} loading={signalsLoading}>
							治理优先队列
						</Button>
						<Button onClick={() => onEnterLedger()}>
							进入台账
						</Button>
					</Space>
				</div>
			<div className="overflow-x-auto rounded-xl border border-slate-200 bg-white p-3">
				<div className="grid min-w-[1040px] grid-cols-8 gap-3">
					{layerGroups.map((group, index) => (
						<button
							key={group.layer}
							type="button"
							className={`relative min-h-[126px] rounded-lg border px-3 py-3 text-left transition hover:-translate-y-0.5 hover:border-blue-300 hover:shadow-sm ${group.meta.tone} ${warehouseLayer === group.layer ? "ring-2 ring-blue-200" : ""}`}
							onClick={() => onSelectLayer(group.layer)}
						>
							<div className="flex items-start justify-between gap-2">
								<div className="min-w-0">
									<div className="truncate text-sm font-semibold text-slate-900">{group.meta.label}</div>
									<div className="mt-1 text-2xl font-semibold leading-none text-slate-900">{group.items.length}</div>
								</div>
								{index < layerGroups.length - 1 ? <ArrowRightOutlined className="mt-1 text-slate-400" /> : null}
							</div>
							<div className="mt-3 grid grid-cols-2 gap-1 text-[11px]">
								<span className="rounded bg-white/70 px-2 py-1 text-green-700">可用 {group.ready}</span>
								<span className="rounded bg-white/70 px-2 py-1 text-amber-700">待处置 {group.attention}</span>
							</div>
						</button>
					))}
				</div>
			</div>
			<div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
				{renderAssetMatrix()}
				<div className="space-y-4">
					<div className="rounded-xl border border-slate-200 bg-white p-4">
						<div className="mb-3 flex items-center justify-between gap-3">
							<div className="text-sm font-semibold text-slate-900">治理优先队列</div>
							<Button size="small" onClick={() => onOpenRemediationWorkbench()} loading={signalsLoading}>
								处置缺口
							</Button>
						</div>
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
						<div className="mt-3 space-y-2">
							{attentionAssets.length ? (
								attentionAssets.map(({ row, readiness }) => (
									<div key={row.id} className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
										<div className="flex items-start justify-between gap-2">
											<Tooltip title={row.name}>
												<div className="min-w-0 flex-1 truncate text-xs font-semibold text-slate-800">{row.name}</div>
											</Tooltip>
											<Tag color={readiness.color} style={{ fontSize: 11 }}>{readiness.label}</Tag>
										</div>
										<div className="mt-1 truncate text-[11px] text-slate-500">{readiness.reasons.join(" / ") || "映射或生命周期需要确认"}</div>
										<div className="mt-2 flex items-center gap-2">
											<Button size="small" onClick={() => onOpenGovernanceRemediation(row.id)}>
												补治理字段
											</Button>
											<Button size="small" onClick={() => router.push(`/catalog/datasets/${row.id}?tab=lineage`)}>
												血缘详情
											</Button>
										</div>
									</div>
								))
							) : (
								<div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-3 py-6 text-center text-xs text-slate-400">
									当前页暂无优先处置资产
								</div>
							)}
						</div>
					</div>
					<div className="rounded-xl border border-slate-200 bg-white p-4">
						<div className="mb-3 text-sm font-semibold text-slate-900">主题域覆盖</div>
						<div className="space-y-2">
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
						</div>
					</div>
					<div className="rounded-xl border border-slate-200 bg-white p-4">
						<div className="mb-3 text-sm font-semibold text-slate-900">服务化出口</div>
						<Space direction="vertical" size={8} className="w-full">
							<Button block onClick={() => router.push("/catalog/search")}>资产搜索</Button>
							<Button block onClick={() => router.push("/catalog/data-products")}>数据产品</Button>
							<Button block onClick={() => router.push("/catalog/lineage/graph")}>血缘图谱</Button>
						</Space>
					</div>
				</div>
			</div>
		</div>
	);
}
