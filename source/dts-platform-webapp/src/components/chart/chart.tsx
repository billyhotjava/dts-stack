import ReactECharts from "echarts-for-react";
import type { EChartsOption } from "echarts";

export type ChartProps = {
	option: EChartsOption;
	height?: number | string;
	className?: string;
	loading?: boolean;
};

export function Chart({ option, height = 320, className, loading }: ChartProps) {
	return (
		<ReactECharts
			option={option}
			style={{ height, width: "100%" }}
			className={className}
			showLoading={loading}
			opts={{ renderer: "canvas" }}
			notMerge
		/>
	);
}
