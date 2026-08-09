import type { EChartsOption } from "echarts";
import { useMemo } from "react";
import { Chart } from "@/components/chart";
import { UNASSIGNED_DOMAIN_KEY } from "./assetPageShared";

// Sprint-88 F2/T02：主题域 Top6 横向堆叠条形图（正常/待处置）。
// 条形可点 → /catalog/search?view=table&domain=<uuid | __UNASSIGNED__>
export type AssetDomainBarsProps = {
	byDomain?: Record<string, { total: number; attention: number }>;
	domainNames: Map<string, string>;
	unassigned?: { total: number; attention: number };
	truncated?: boolean;
	loading?: boolean;
	onBarClick: (domainKey: string) => void;
	/** 卡片外壳标题（组件自带白色圆角卡片壳） */
	title?: string;
	dataTestId?: string;
};

const BAR_HEIGHT = 260;

export function AssetDomainBars({
	byDomain = {},
	domainNames,
	unassigned,
	truncated = false,
	loading = false,
	onBarClick,
	title = "主题域分布 Top 6",
	dataTestId,
}: AssetDomainBarsProps) {
	const rows = useMemo(() => {
		const listed = Object.entries(byDomain)
			.map(([id, stats]) => ({
				key: id,
				name: domainNames.get(id) || "未知主题域",
				total: Number(stats?.total || 0),
				attention: Number(stats?.attention || 0),
			}))
			.filter((row) => row.total > 0)
			.sort((a, b) => b.total - a.total)
			.slice(0, 6);
		if (unassigned && Number(unassigned.total || 0) > 0) {
			listed.push({
				key: UNASSIGNED_DOMAIN_KEY,
				name: "未归域",
				total: Number(unassigned.total),
				attention: Number(unassigned.attention || 0),
			});
		}
		return listed;
	}, [byDomain, domainNames, unassigned]);

	const option = useMemo<EChartsOption>(() => {
		const series = rows.length
			? [
					{
						type: "bar" as const,
						stack: "total",
						name: "正常",
						itemStyle: { color: "#7bc96f" },
						barWidth: 14,
						data: rows.map((row) => row.total - row.attention),
					},
					{
						type: "bar" as const,
						stack: "total",
						name: "待处置",
						itemStyle: { color: "#f59e0b" },
						barWidth: 14,
						data: rows.map((row) => row.attention),
					},
				]
			: [];
		return {
			tooltip: {
				trigger: "axis",
				axisPointer: { type: "shadow" },
				formatter: (params: any) => {
					const index = Number(params?.[0]?.dataIndex ?? 0);
					const row = rows[index];
					if (!row) return "";
					return `${row.name}：总计 ${row.total}${truncated ? " (≥)" : ""} · 待处置 ${row.attention}`;
				},
			},
			grid: { left: 8, right: 16, top: 8, bottom: 8, containLabel: true },
			xAxis: { type: "value", min: 0, splitLine: { lineStyle: { type: "dashed", color: "#e2e8f0" } } },
			yAxis: {
				type: "category",
				inverse: true,
				data: rows.map((row) => row.name),
				axisLine: { show: false },
				axisTick: { show: false },
			},
			series,
		};
	}, [rows, truncated]);

	if (loading) {
		return <Chart option={{}} height={BAR_HEIGHT} loading />;
	}
	if (!rows.length) {
		return <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">当前范围内暂无资产</div>;
	}

	const summaryText = `主题域分布：${rows.map((row) => `${row.name} ${row.total}`).join("，")}`;

	return (
		<div className="rounded-xl border border-slate-200 bg-white p-4" data-testid={dataTestId}>
			<div className="mb-2 text-sm font-semibold text-slate-900">{title}</div>
			<Chart
				option={option}
				height={BAR_HEIGHT}
				onEvents={
					{
						click: (params: any) => {
							const index = Number(params?.dataIndex);
							const row = rows[index];
							if (row) onBarClick(row.key);
						},
					} as Record<string, (params: unknown) => void>
				}
			/>
			<p className="sr-only">{summaryText}</p>
		</div>
	);
}
