import { useMemo } from "react";
import { Empty } from "antd";
import type { EChartsOption } from "echarts";
import { Chart } from "@/components/chart";
import { resolveEnumLabel } from "@/pages/catalog/assets/assetEnumLabels";
import { GOVERNANCE_STATUS_DICT } from "@/pages/catalog/assets/assetEnumLabels";

// Sprint-88 F2/T02：治理状态环形图。扇区可点 → /catalog/search?view=table&governance=<STATUS>
export type AssetGovernanceDonutProps = {
	counts?: Record<string, number>;
	total: number;
	truncated?: boolean;
	loading?: boolean;
	onSliceClick: (status: string) => void;
};

const STATUS_ORDER = ["UNDER_GOVERNANCE", "PENDING_GOVERNANCE", "PENDING_CLASSIFICATION", "DISABLED", "PENDING_REVIEW"];

export function AssetGovernanceDonut({ counts = {}, total, truncated = false, loading = false, onSliceClick }: AssetGovernanceDonutProps) {
	const entries = useMemo(() => {
		const known = STATUS_ORDER.map((key) => ({ key, value: Number(counts[key] || 0) })).filter((item) => item.value > 0);
		const knownKeys = new Set(known.map((item) => item.key));
		const other = Object.entries(counts)
			.filter(([key, value]) => !knownKeys.has(key) && Number(value) > 0)
			.map(([key, value]) => ({ key, value: Number(value) }));
		return [...known, ...other.map((item) => ({ ...item, key: "OTHER" }))];
	}, [counts]);

	const covered = entries.reduce((sum, item) => sum + item.value, 0);
	const uncovered = Math.max(0, total - covered);

	const option = useMemo<EChartsOption>(() => {
		const series = entries.length
			? [
					{
						type: "pie" as const,
						radius: ["62%", "82%"],
						center: ["32%", "50%"],
						avoidLabelOverlap: true,
						label: { show: false },
						labelLine: { show: false },
						itemStyle: { borderRadius: 4, borderColor: "#fff", borderWidth: 2 },
						data: entries.map((item) => ({ name: item.key, value: item.value })),
						emphasis: { scale: true },
					},
				]
			: [];
		return {
			tooltip: {
				trigger: "item",
				formatter: (params: any) => `${params.name}: ${params.value}${truncated ? " (≥)" : ""}`,
			},
			legend: {
				type: "scroll",
				orient: "vertical",
				right: 8,
				top: "middle",
				itemWidth: 10,
				itemHeight: 10,
				formatter: (name: string) => resolveEnumLabel(GOVERNANCE_STATUS_DICT, name, name === "OTHER" ? "其他" : name),
			},
			series,
		};
	}, [entries, truncated]);

	if (loading) {
		return <Chart option={{}} height={260} loading />;
	}
	if (total === 0) {
		return (
			<div className="flex h-[260px] items-center justify-center text-sm text-slate-400">
				当前范围内暂无资产
			</div>
		);
	}
	if (!entries.length) {
		return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理状态数据" />;
	}

	const summaryText = `治理状态：${entries
		.map((item) => `${resolveEnumLabel(GOVERNANCE_STATUS_DICT, item.key, item.key === "OTHER" ? "其他" : item.key)} ${item.value}`)
		.join("，")}${uncovered > 0 ? `，未统计 ${uncovered}` : ""}`;

	const centerText = total > 0 ? `${Math.round((covered / total) * 100)}%` : "0%";

	return (
		<div className="relative">
			<Chart
				option={option}
				height={260}
				onEvents={{
					click: (params: any) => {
						const name = String(params?.name ?? "");
						if (name && name !== "OTHER") onSliceClick(name);
					},
				} as Record<string, (params: unknown) => void>}
			/>
			<div className="pointer-events-none absolute left-[7%] top-1/2 -translate-y-1/2 text-center">
				<div className="text-lg font-semibold text-slate-900">{truncated ? `≥${centerText}` : centerText}</div>
				<div className="text-[10px] text-slate-400">已治理</div>
			</div>
			<p className="sr-only">{summaryText}</p>
		</div>
	);
}
