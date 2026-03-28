import type { EChartsOption } from "echarts";
import { useMemo } from "react";
import { useSettings } from "@/store/settingStore";
import type { ThemeMode } from "@/types/enum";

const COLORS = ["#1677ff", "#52c41a", "#ff4d4f", "#faad14", "#722ed1", "#13c2c2", "#eb2f96", "#2f54eb"];

function baseChartOptions(themeMode: ThemeMode): EChartsOption {
	const isDark = themeMode === "dark";
	return {
		color: COLORS,
		backgroundColor: "transparent",
		textStyle: {
			fontFamily: "'Inter Variable', -apple-system, BlinkMacSystemFont, sans-serif",
			color: isDark ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.85)",
		},
		grid: {
			top: 40,
			right: 16,
			bottom: 24,
			left: 16,
			containLabel: true,
		},
		tooltip: {
			trigger: "axis",
			backgroundColor: isDark ? "rgba(30,30,30,0.9)" : "rgba(255,255,255,0.95)",
			borderColor: isDark ? "rgba(255,255,255,0.1)" : "rgba(0,0,0,0.06)",
			textStyle: {
				color: isDark ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.85)",
				fontSize: 13,
			},
		},
		legend: {
			top: 0,
			left: 0,
			textStyle: {
				color: isDark ? "rgba(255,255,255,0.65)" : "rgba(0,0,0,0.65)",
				fontSize: 12,
			},
		},
		xAxis: {
			axisLine: { show: false },
			axisTick: { show: false },
			splitLine: { show: false },
			axisLabel: {
				color: isDark ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.45)",
				fontSize: 11,
			},
		},
		yAxis: {
			axisLine: { show: false },
			axisTick: { show: false },
			splitLine: {
				lineStyle: {
					type: "dashed",
					color: isDark ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.06)",
				},
			},
			axisLabel: {
				color: isDark ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.45)",
				fontSize: 11,
			},
		},
		animationDuration: 360,
		animationEasing: "cubicInOut",
	};
}

export function useChart(overrides: EChartsOption): EChartsOption {
	const { themeMode } = useSettings();
	return useMemo(() => {
		const base = baseChartOptions(themeMode);
		return { ...base, ...overrides } as EChartsOption;
	}, [themeMode, overrides]);
}
