// @ts-nocheck — 扩展图表渲染器（P2/P3）
// 承载：effectScatter / lines / bar-racing / polar-line / polar-bar /
// liquidFill / bar3D / scatter3D / line3D / surface / map3D
import type { ReactNode } from 'react';
import type { EChartsRendererProps } from './types';

const pickNum = (v: unknown): number | undefined => {
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
};

const asArray = <T,>(v: unknown, fallback: T[] = []): T[] =>
    Array.isArray(v) ? (v as T[]) : fallback;

export function renderExtendedChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t,
        renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
        axisFontSize, axisLabelColor: axisLabelColorOverride,
        xAxisLabelRotate, yAxisLabelRotate, formatXAxisLabel,
        plotCenterX, plotCenterY,
        echartsClickHandler,
    } = props;

    const axisLabelColor = axisLabelColorOverride || t.echarts.axisLabelColor;

    switch (type) {
        // =========================================================
        // effectScatter — 涟漪散点（常用于地图标注）
        // =========================================================
        case 'effectScatter-chart': {
            const data = asArray<number[] | { value: [number, number] | [number, number, number]; name?: string }>(c.data);
            const symbolSize = pickNum(c.symbolSize) ?? 12;
            const rippleScale = pickNum(c.rippleScale) ?? 2.5;
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: { ...themeOptions.tooltip, trigger: 'item' },
                xAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                yAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: yAxisLabelRotate },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: [{
                    type: 'effectScatter',
                    coordinateSystem: 'cartesian2d',
                    showEffectOn: 'render',
                    rippleEffect: { brushType: 'stroke', scale: rippleScale },
                    symbolSize,
                    data,
                    itemStyle: { color: seriesColors[0] ?? t.scatterColor, shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.25)' },
                }],
                grid: axisGrid,
            }, echartsClickHandler);
        }

        // =========================================================
        // lines — 迁徙 / 流动轨迹（ECharts 原生 lines 系列）
        // =========================================================
        case 'lines-chart': {
            // data 形如 [{ coords: [[x1,y1],[x2,y2]], ... }, ...]
            const data = asArray<{ coords: [number, number][]; value?: number }>(c.data);
            const effectShow = c.effectShow !== false;
            const lineWidth = pickNum(c.lineWidth) ?? 2;
            const lineColor = (typeof c.lineColor === 'string' && c.lineColor) || seriesColors[0] || t.accentColor;
            const trailLength = pickNum(c.trailLength) ?? 0.6;
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                xAxis: { type: 'value', axisLine: { lineStyle: { color: t.echarts.axisLineColor } }, axisLabel: { color: axisLabelColor } },
                yAxis: { type: 'value', axisLine: { lineStyle: { color: t.echarts.axisLineColor } }, axisLabel: { color: axisLabelColor } },
                series: [{
                    type: 'lines',
                    coordinateSystem: 'cartesian2d',
                    polyline: false,
                    effect: effectShow ? {
                        show: true,
                        period: 4,
                        trailLength,
                        color: '#fff',
                        symbolSize: 4,
                    } : { show: false },
                    lineStyle: {
                        color: lineColor,
                        width: lineWidth,
                        opacity: 0.75,
                        curveness: 0.2,
                    },
                    data,
                }],
                grid: axisGrid,
            }, echartsClickHandler);
        }

        // =========================================================
        // bar-racing — 动态排序柱状图（需 ECharts 5+）
        // =========================================================
        case 'bar-racing-chart': {
            // data 形式: [{ name: '系列A', data: [v1, v2, v3] }, ...]
            // categories 可选，作为 x 轴类别标签。
            const rows = asArray<{ name: string; data: number[] }>(c.series);
            const categories = asArray<string>(c.categories,
                rows[0]?.data?.map((_, i) => `T${i + 1}`) ?? []);
            const seriesColor = seriesColors[0] ?? t.echarts.colorPalette[0];
            const topN = pickNum(c.topN) ?? Math.min(10, rows.length);
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                grid: { ...axisGrid, top: 48, left: 120 },
                xAxis: {
                    max: 'dataMax',
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                yAxis: {
                    type: 'category',
                    data: rows.map((r) => r.name),
                    inverse: true,
                    animationDuration: 300,
                    animationDurationUpdate: 300,
                    max: topN,
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                },
                series: [{
                    type: 'bar',
                    realtimeSort: true,
                    data: rows.map((r, i) => ({
                        name: r.name,
                        value: r.data[r.data.length - 1] ?? 0,
                        itemStyle: { color: seriesColors[i % Math.max(1, seriesColors.length)] ?? seriesColor },
                    })),
                    itemStyle: { borderRadius: [0, 6, 6, 0] },
                    label: {
                        show: true,
                        position: 'right',
                        valueAnimation: true,
                        color: axisLabelColor,
                        fontSize: 12,
                    },
                }],
                animationDuration: 0,
                animationDurationUpdate: 1500,
                animationEasing: 'linear' as const,
                animationEasingUpdate: 'linear' as const,
                // categories 仅用于说明时间点；真实"动态"行为需要调用方 setOption 连续推数据
                graphic: {
                    type: 'text',
                    right: 20,
                    top: 8,
                    style: {
                        text: categories.at(-1) ?? '',
                        font: 'bolder 16px sans-serif',
                        fill: t.textSecondary,
                    },
                },
            }, echartsClickHandler);
        }

        // =========================================================
        // polar-line / polar-bar — 极坐标系折线/柱状
        // =========================================================
        case 'polar-line-chart':
        case 'polar-bar-chart': {
            const isBar = type === 'polar-bar-chart';
            const series = asArray<{ name: string; data: number[] }>(c.series);
            const categories = asArray<string>(c.categories, series[0]?.data?.map((_, i) => String(i + 1)) ?? []);
            const radius = pickNum(c.polarRadius) ?? Math.min(180, (props.width + props.height) / 6);
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                polar: {
                    center: [plotCenterX, plotCenterY],
                    radius,
                },
                angleAxis: {
                    type: 'category',
                    data: categories,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                },
                radiusAxis: {
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                tooltip: { ...themeOptions.tooltip, trigger: 'axis' },
                series: series.map((s, idx) => ({
                    name: s.name,
                    type: isBar ? 'bar' : 'line',
                    coordinateSystem: 'polar',
                    data: s.data,
                    ...(isBar
                        ? { itemStyle: { borderRadius: 4, color: seriesColors[idx] ?? t.echarts.colorPalette[idx % t.echarts.colorPalette.length] } }
                        : {
                            smooth: true,
                            lineStyle: { width: 2.5 },
                            areaStyle: { opacity: 0.25 },
                        }),
                })),
            }, echartsClickHandler);
        }

        // =========================================================
        // liquidFill — 水球图（需 echarts-liquidfill 扩展）
        // =========================================================
        case 'liquidFill-chart': {
            const value = pickNum(c.value) ?? 0.5;
            const values = Array.isArray(c.values) ? (c.values as number[]) : [value, value * 0.85];
            const shape = (typeof c.shape === 'string' ? c.shape : 'circle') as 'circle' | 'rect' | 'roundRect' | 'triangle' | 'diamond' | 'pin' | 'arrow';
            const primary = (typeof c.primaryColor === 'string' && c.primaryColor) || seriesColors[0] || t.accentColor;
            const outlineColor = (typeof c.outlineColor === 'string' && c.outlineColor) || primary;
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                series: [{
                    type: 'liquidFill',
                    shape,
                    radius: '82%',
                    center: [plotCenterX, plotCenterY],
                    data: values,
                    color: [primary],
                    amplitude: 9,
                    waveLength: '76%',
                    outline: {
                        show: true,
                        borderDistance: 6,
                        itemStyle: {
                            borderColor: outlineColor,
                            borderWidth: 3,
                            shadowBlur: 16,
                            shadowColor: 'rgba(0,0,0,0.18)',
                        },
                    },
                    backgroundStyle: {
                        color: 'transparent',
                        borderColor: outlineColor,
                        borderWidth: 1,
                    },
                    label: {
                        color: t.textPrimary,
                        fontSize: pickNum(c.labelFontSize) ?? 28,
                        fontWeight: 700,
                        formatter: () => `${Math.round((values[0] ?? 0) * 100)}%`,
                    },
                }],
            }, echartsClickHandler);
        }

        // =========================================================
        // 3D 族（需 echarts-gl）
        // =========================================================
        case 'bar3D-chart': {
            const rows = asArray<Record<string, unknown>>(c.rows);
            const data3d = rows.map((row) => Array.isArray(row)
                ? row
                : [row.x ?? 0, row.y ?? 0, row.value ?? row.z ?? 0]);
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: {},
                visualMap: {
                    max: pickNum(c.visualMapMax) ?? 100,
                    inRange: { color: t.echarts.colorPalette },
                    show: false,
                },
                xAxis3D: { type: 'value' },
                yAxis3D: { type: 'value' },
                zAxis3D: { type: 'value' },
                grid3D: {
                    boxWidth: 200,
                    boxDepth: 80,
                    viewControl: { projection: 'perspective' },
                    light: { main: { intensity: 1.2, shadow: true }, ambient: { intensity: 0.3 } },
                },
                series: [{
                    type: 'bar3D',
                    data: data3d,
                    shading: 'realistic',
                    label: { show: false },
                    itemStyle: { opacity: 0.95 },
                    emphasis: { label: { show: true } },
                }],
            }, echartsClickHandler);
        }

        case 'scatter3D-chart': {
            const rows = asArray<Record<string, unknown>>(c.rows);
            const data3d = rows.map((row) => Array.isArray(row)
                ? row
                : [row.x ?? 0, row.y ?? 0, row.z ?? 0, row.value ?? 1]);
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: {},
                visualMap: {
                    max: pickNum(c.visualMapMax) ?? 100,
                    inRange: { symbolSize: [4, 18], color: t.echarts.colorPalette },
                    show: false,
                },
                xAxis3D: { type: 'value' },
                yAxis3D: { type: 'value' },
                zAxis3D: { type: 'value' },
                grid3D: { viewControl: { projection: 'perspective' } },
                series: [{
                    type: 'scatter3D',
                    data: data3d,
                    symbolSize: pickNum(c.symbolSize) ?? 10,
                    itemStyle: { opacity: 0.9 },
                }],
            }, echartsClickHandler);
        }

        case 'line3D-chart': {
            const data = asArray<number[]>(c.data);
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                xAxis3D: { type: 'value' },
                yAxis3D: { type: 'value' },
                zAxis3D: { type: 'value' },
                grid3D: { viewControl: { projection: 'perspective' } },
                series: [{
                    type: 'line3D',
                    data,
                    lineStyle: { width: 3, color: seriesColors[0] ?? t.accentColor },
                }],
            }, echartsClickHandler);
        }

        case 'surface-chart': {
            // data: [[x,y,z], ...]；若配置 function 生成更完善，此处取已计算结果。
            const data = asArray<number[]>(c.data);
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: {},
                visualMap: {
                    show: false,
                    dimension: 2,
                    min: pickNum(c.visualMapMin) ?? -1,
                    max: pickNum(c.visualMapMax) ?? 1,
                    inRange: { color: t.echarts.colorPalette },
                },
                xAxis3D: { type: 'value' },
                yAxis3D: { type: 'value' },
                zAxis3D: { type: 'value' },
                grid3D: { viewControl: { projection: 'perspective' } },
                series: [{
                    type: 'surface',
                    data,
                    shading: 'color',
                    wireframe: { show: false },
                }],
            }, echartsClickHandler);
        }

        case 'map3D-chart': {
            const mapName = String(c.mapName || c.mapScope || 'dts-map');
            const data = asArray<{ name: string; value: number }>(c.data);
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                visualMap: {
                    max: pickNum(c.visualMapMax) ?? 100,
                    inRange: { color: t.echarts.colorPalette },
                    calculable: true,
                    textStyle: { color: t.textSecondary },
                },
                series: [{
                    type: 'map3D',
                    map: mapName,
                    data,
                    shading: 'lambert',
                    light: { main: { intensity: 1.2 }, ambient: { intensity: 0.3 } },
                    groundPlane: { show: false },
                    viewControl: { distance: 120, alpha: 40 },
                    itemStyle: { areaColor: t.cardBackground, borderColor: t.echarts.axisLineColor, borderWidth: 1 },
                }],
            }, echartsClickHandler);
        }

        default:
            return null;
    }
}
