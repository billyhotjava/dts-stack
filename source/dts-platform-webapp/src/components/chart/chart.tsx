import ReactECharts from "echarts-for-react";
import type { EChartsOption } from "echarts";

export type ChartProps = {
	option: EChartsOption;
	height?: number | string;
	className?: string;
	loading?: boolean;
	/** ECharts 实例事件（如图表元素点击），透传给 echarts-for-react */
	onEvents?: Record<string, (params: unknown, instance: unknown) => void>;
};

export function Chart({ option, height = 320, className, loading, onEvents }: ChartProps) {
	return (
		<ReactECharts
			option={option}
			style={{ height, width: "100%" }}
			className={className}
			showLoading={loading}
			opts={{ renderer: "canvas" }}
			notMerge
			onEvents={onEvents}
		/>
	);
}
