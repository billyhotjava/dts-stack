import type { CSSProperties } from "react";
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
	return (
		<ReactEChartsCore
			echarts={echarts}
			option={props.option}
			style={props.style}
			onEvents={props.onEvents as Record<string, (params: unknown) => void> | undefined}
		/>
	);
}
