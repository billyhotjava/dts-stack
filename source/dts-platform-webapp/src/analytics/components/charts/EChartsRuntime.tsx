import { useEffect, useRef, type CSSProperties } from "react";
import ReactEChartsCore from "echarts-for-react/lib/core";
import * as echarts from "echarts/core";
import {
	BarChart,
	FunnelChart,
	GaugeChart,
	HeatmapChart,
	LineChart,
	LinesChart,
	EffectScatterChart,
	MapChart,
	PieChart,
	RadarChart,
	ScatterChart,
	TreemapChart,
	SunburstChart,
} from "echarts/charts";
import {
	DatasetComponent,
	GeoComponent,
	GridComponent,
	LegendComponent,
	TitleComponent,
	TooltipComponent,
	TransformComponent,
	VisualMapComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
	TitleComponent,
	TooltipComponent,
	LegendComponent,
	GridComponent,
	DatasetComponent,
	GeoComponent,
	VisualMapComponent,
	TransformComponent,
	LineChart,
	BarChart,
	PieChart,
	GaugeChart,
	RadarChart,
	FunnelChart,
	ScatterChart,
	HeatmapChart,
	LinesChart,
	EffectScatterChart,
	MapChart,
	TreemapChart,
	SunburstChart,
	CanvasRenderer,
]);

export function registerEChartsMap(mapName: string, geoJson: unknown): boolean {
	const name = String(mapName || "").trim();
	if (!name || !geoJson || typeof geoJson !== "object") {
		return false;
	}
	const api = echarts as unknown as {
		getMap?: (id: string) => unknown;
		registerMap?: (id: string, data: unknown) => void;
	};
	try {
		if (!api.getMap?.(name)) {
			api.registerMap?.(name, geoJson);
		}
		return true;
	} catch {
		return false;
	}
}

export function hasEChartsMap(mapName: string): boolean {
	const name = String(mapName || "").trim();
	if (!name) {
		return false;
	}
	const api = echarts as unknown as {
		getMap?: (id: string) => unknown;
	};
	try {
		return Boolean(api.getMap?.(name));
	} catch {
		return false;
	}
}

export interface EChartsRuntimeProps {
	style?: CSSProperties;
	option: unknown;
	onEvents?: Record<string, (params: Record<string, unknown>) => void>;
}

export default function EChartsRuntime(props: EChartsRuntimeProps) {
	// Sprint-12 F4/T01: 监听父容器尺寸变化并调 chart.resize()。
	// echarts-for-react 只监听 window resize，不监听父容器；在 v2 网格布局里，
	// 组件容器会被 react-grid-layout / CSS flex 改变大小而 window 不变，
	// 这时需要我们主动触发 resize，否则图表保持旧尺寸被裁剪或留空。
	// Chrome 95 原生支持 ResizeObserver，无需 polyfill。
	const wrapperRef = useRef<HTMLDivElement | null>(null);
	const chartRef = useRef<ReactEChartsCore | null>(null);

	useEffect(() => {
		const el = wrapperRef.current;
		if (!el || typeof ResizeObserver === "undefined") return;
		let rafId = 0;
		const observer = new ResizeObserver(() => {
			// 用 rAF 合并高频回调，避免拖动 resize 期间每帧重复调用
			if (rafId) cancelAnimationFrame(rafId);
			rafId = requestAnimationFrame(() => {
				const instance = chartRef.current?.getEchartsInstance?.();
				if (instance && typeof instance.resize === "function") {
					instance.resize();
				}
			});
		});
		observer.observe(el);
		return () => {
			if (rafId) cancelAnimationFrame(rafId);
			observer.disconnect();
		};
	}, []);

	const style: CSSProperties = { width: "100%", height: "100%", ...props.style };

	return (
		<div ref={wrapperRef} style={style}>
			<ReactEChartsCore
				ref={(inst) => {
					chartRef.current = inst as unknown as ReactEChartsCore | null;
				}}
				echarts={echarts}
				option={props.option}
				style={{ width: "100%", height: "100%" }}
				onEvents={props.onEvents as Record<string, (params: unknown) => void> | undefined}
			/>
		</div>
	);
}
