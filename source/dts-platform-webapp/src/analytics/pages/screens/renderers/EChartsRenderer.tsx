// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode, ComponentType } from 'react';
import type { ScreenThemeTokens } from '../screenThemes';
import type { ReactEChartsComponent } from './types';
import { ProjectGanttBoard, type ProjectGanttTask } from '../../project-cockpit/components/ProjectGanttBoard';

const SCREEN_UI_FONT_FAMILY = '"Lato", "Open Sans Variable", "Inter Variable", system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", "Noto Sans", sans-serif';

function isLightColor(hex: string): boolean {
    const c = hex.replace('#', '');
    if (c.length < 6) return false;
    const r = parseInt(c.slice(0, 2), 16);
    const g = parseInt(c.slice(2, 4), 16);
    const b = parseInt(c.slice(4, 6), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 160;
}

// ── Types ──

interface ScreenRuntimeLike {
    setVariable: (key: string, value: string, source?: string) => void;
}

export interface EChartsRendererProps {
    type: string;
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    mode: 'designer' | 'preview';
    componentId: string;
    runtime: ScreenRuntimeLike;

    // ECharts component references
    EChart: ReactEChartsComponent;
    renderEChartWithHandles: (
        option: Record<string, unknown>,
        onEvents?: Record<string, (params: Record<string, unknown>) => void>,
    ) => ReactNode;

    // Theme and chart options
    themeOptions: Record<string, unknown> & { tooltip?: Record<string, unknown> };
    chartMotionOption: Record<string, unknown>;
    chartTitleLayout: { titleOption: Record<string, unknown> };
    legendConfig: Record<string, unknown>;
    axisGrid: Record<string, unknown>;
    seriesColors: string[];

    // Axis font / label config
    axisFontSize: number;
    axisLabelColor?: string;
    seriesLabelFontSize: number;
    xAxisLabelRotate: number;
    xAxisLabelInterval: number;
    formatXAxisLabel: (value: unknown) => string;

    // Series label config
    axisSeriesLabelShow: boolean;
    resolvedAxisSeriesLabelStrategy: string;
    axisSeriesLabelFormatter: (raw: unknown) => string;
    axisLineLabelPosition: string;
    axisBarLabelPosition: string;
    axisBarLabelColor: string;
    axisTooltipFormatter: (raw: unknown) => string;

    // Canvas size flags
    isCompactCanvas: boolean;
    isTinyCanvas: boolean;
    xAxisCategoryCount: number;

    // Pie / donut layout
    plotCenterX: number;
    plotCenterY: number;
    pieInnerRadius: number;
    pieOuterRadius: number;
    pieLabelShow: boolean;
    pieLabelPosition: string;

    // Radar
    radarRadius: number;

    // Funnel
    funnelLeft: number;
    funnelRight: number;
    funnelTop: number;
    funnelBottom: number;
    funnelLabelShow: boolean;
    funnelLabelPosition: string;

    // Series label line
    seriesLabelLineLength: number;
    seriesLabelLineLength2: number;
    seriesLabelMinAngle: number;

    // Interaction handlers
    echartsClickHandler: Record<string, (params: Record<string, unknown>) => void> | undefined;
    componentActions: { length: number };
    executeComponentActions: (params: Record<string, unknown>) => void;

    // Map-chart state
    mapDrillRegion: string | null;
    setMapDrillRegion: (region: string | null) => void;
    mapReadyVersion: number;
    hasMapFn: ((mapName: string) => boolean) | null;
}

// ── Renderer ──

export function renderECharts(props: EChartsRendererProps): ReactNode | null {
    const {
        type, c, t, width, height, mode, componentId, runtime,
        EChart, renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
        axisFontSize, axisLabelColor: axisLabelColorOverride, seriesLabelFontSize,
        xAxisLabelRotate, xAxisLabelInterval, formatXAxisLabel,
        axisSeriesLabelShow, resolvedAxisSeriesLabelStrategy, axisSeriesLabelFormatter,
        axisLineLabelPosition, axisBarLabelPosition, axisBarLabelColor, axisTooltipFormatter,
        isCompactCanvas, isTinyCanvas, xAxisCategoryCount,
        plotCenterX, plotCenterY, pieInnerRadius, pieOuterRadius, pieLabelShow, pieLabelPosition,
        radarRadius,
        funnelLeft, funnelRight, funnelTop, funnelBottom, funnelLabelShow, funnelLabelPosition,
        seriesLabelLineLength, seriesLabelLineLength2, seriesLabelMinAngle,
        echartsClickHandler, componentActions, executeComponentActions,
        mapDrillRegion, setMapDrillRegion, mapReadyVersion, hasMapFn,
    } = props;

    const axisLabelColor = axisLabelColorOverride || t.echarts.axisLabelColor;
    const component = { id: componentId };

    switch (type) {
        // ==================== ECharts 图表 ====================
        case 'line-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: {
                    ...themeOptions.tooltip,
                    trigger: 'axis',
                    confine: true,
                    axisPointer: { type: 'line' },
                    formatter: axisTooltipFormatter,
                },
                xAxis: {
                    type: 'category',
                    data: c.xAxisData as string[],
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: {
                        color: axisLabelColor,
                        fontSize: axisFontSize,
                        rotate: xAxisLabelRotate,
                        hideOverlap: true,
                        formatter: formatXAxisLabel,
                        interval: xAxisLabelInterval,
                    },
                },
                yAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: (Array.isArray(c.series) ? c.series as Array<{ name: string; data: number[] }> : []).map((s, idx) => {
                    const lineStackMode = String(c.stackMode ?? 'off');
                    const stackGroup = lineStackMode !== 'off' ? 'stack' : undefined;
                    return {
                        name: s.name,
                        type: 'line' as const,
                        data: s.data,
                        smooth: true,
                        stack: stackGroup,
                        showSymbol: !isCompactCanvas || xAxisCategoryCount <= 24,
                        label: {
                            show: axisSeriesLabelShow && (resolvedAxisSeriesLabelStrategy === 'all' || idx === 0),
                            position: axisLineLabelPosition,
                            color: t.textPrimary,
                            fontSize: seriesLabelFontSize,
                            distance: isTinyCanvas ? 2 : 6,
                            formatter: axisSeriesLabelFormatter,
                        },
                        labelLayout: {
                            hideOverlap: true,
                            moveOverlap: 'shiftY',
                        },
                        areaStyle: {
                            opacity: stackGroup ? 0.6 : 0.3,
                            ...(seriesColors[idx] ? { color: seriesColors[idx] } : {}),
                        },
                        ...(seriesColors[idx]
                            ? { lineStyle: { color: seriesColors[idx] }, itemStyle: { color: seriesColors[idx] } }
                            : {}),
                    };
                }),
                grid: axisGrid,
            }, echartsClickHandler);

        case 'bar-chart': {
            const barHorizontal = Boolean(c.horizontal);
            const barStackMode = String(c.stackMode ?? 'off');
            const barStackGroup = barStackMode !== 'off' ? 'stack' : undefined;
            const categoryAxisConfig = {
                type: 'category' as const,
                data: c.xAxisData as string[],
                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                axisLabel: {
                    color: axisLabelColor,
                    fontSize: axisFontSize,
                    rotate: barHorizontal ? 0 : xAxisLabelRotate,
                    hideOverlap: true,
                    formatter: formatXAxisLabel,
                    interval: xAxisLabelInterval,
                },
            };
            const valueAxisConfig = {
                type: 'value' as const,
                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
            };
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: {
                    ...themeOptions.tooltip,
                    trigger: 'axis',
                    confine: true,
                    axisPointer: { type: 'shadow' },
                    formatter: axisTooltipFormatter,
                },
                xAxis: barHorizontal ? valueAxisConfig : categoryAxisConfig,
                yAxis: barHorizontal ? categoryAxisConfig : valueAxisConfig,
                series: (Array.isArray(c.series) ? c.series as Array<{ name: string; data: number[] }> : []).map((s, idx) => ({
                    name: s.name,
                    type: 'bar',
                    data: s.data,
                    stack: barStackGroup,
                    label: {
                        show: axisSeriesLabelShow && (resolvedAxisSeriesLabelStrategy === 'all' || idx === 0),
                        position: barHorizontal ? 'right' : axisBarLabelPosition,
                        color: axisBarLabelColor,
                        fontSize: seriesLabelFontSize,
                        distance: isTinyCanvas ? 2 : 6,
                        formatter: axisSeriesLabelFormatter,
                    },
                    labelLayout: {
                        hideOverlap: true,
                    },
                    itemStyle: {
                        borderRadius: barHorizontal ? [0, 4, 4, 0] : [4, 4, 0, 0],
                        color: seriesColors[idx]
                            ? seriesColors[idx]
                            : {
                                type: 'linear',
                                x: 0, y: 0, x2: barHorizontal ? 1 : 0, y2: barHorizontal ? 0 : 1,
                                colorStops: [
                                    { offset: 0, color: t.barGradient[0] },
                                    { offset: 1, color: t.barGradient[1] },
                                ],
                            },
                    },
                })),
                grid: axisGrid,
            }, echartsClickHandler);
        }

        case 'pie-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
                series: [{
                    type: 'pie',
                    center: [plotCenterX, plotCenterY],
                    radius: [pieInnerRadius, pieOuterRadius],
                    avoidLabelOverlap: true,
                    label: {
                        show: pieLabelShow,
                        position: pieLabelPosition,
                        color: t.pieLabelColor,
                        fontSize: seriesLabelFontSize,
                        formatter: pieLabelPosition === 'inside' ? '{d}%' : '{b}: {d}%',
                    },
                    labelLine: {
                        show: pieLabelShow && pieLabelPosition !== 'inside',
                        length: seriesLabelLineLength,
                        length2: seriesLabelLineLength2,
                    },
                    minShowLabelAngle: seriesLabelMinAngle,
                    labelLayout: pieLabelPosition === 'inside'
                        ? { hideOverlap: true }
                        : { hideOverlap: true, moveOverlap: 'shiftY' },
                    data: c.data as Array<{ name: string; value: number }>,
                }],
            }, echartsClickHandler);

        case 'gauge-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                series: [{
                    type: 'gauge',
                    min: c.min as number,
                    max: c.max as number,
                    progress: { show: true, width: 18 },
                    axisLine: { lineStyle: { width: 18, color: [[1, t.gauge.axisLineColor]] } },
                    axisTick: { show: false },
                    splitLine: { length: 10, lineStyle: { width: 2, color: t.gauge.splitLineColor } },
                    axisLabel: { distance: 25, color: t.gauge.axisLabelColor, fontSize: 12 },
                    pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '12%', width: 10, itemStyle: { color: 'auto' } },
                    anchor: { show: true, showAbove: true, size: 18, itemStyle: { borderWidth: 6 } },
                    title: { show: true, offsetCenter: [0, '70%'], fontSize: (c.titleFontSize as number) || 14, color: t.gauge.titleColor },
                    detail: { valueAnimation: true, fontSize: 28, offsetCenter: [0, '45%'], color: t.gauge.detailColor, formatter: '{value}%' },
                    data: [{ value: c.value != null ? Number(c.value) : 0, name: c.title as string }],
                }],
            });

        case 'gantt-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const tasks = Array.isArray(c.tasks) ? (c.tasks as Array<Record<string, any>>) : [];
            const ganttRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
            if (ganttRenderMode === 'board') {
                const onTaskClick = mode === 'preview' && componentActions.length > 0
                    ? (task: ProjectGanttTask) => {
                        executeComponentActions({
                            data: task,
                            name: task.name,
                            ...task,
                        });
                    }
                    : undefined;
                return (
                    <ProjectGanttBoard
                        tasks={tasks as ProjectGanttTask[]}
                        maxHeight={height}
                        onTaskClick={onTaskClick}
                        sideTextColor={typeof c.sideTextColor === 'string' ? c.sideTextColor : undefined}
                        dark={isLightColor(t.textPrimary)}
                    />
                );
            }
            if (!tasks.length) {
                return renderEChartWithHandles({ ...themeOptions, title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: t.textSecondary, fontSize: 14 } } });
            }

            const sorted = [...tasks].sort((a, b) => String(a.planDate ?? '').localeCompare(String(b.planDate ?? '')));
            const categories = sorted.map((tk) => String(tk.name ?? ''));

            const allDates = sorted.flatMap((tk) => [tk.planDate, tk.actualDate].filter(Boolean).map(String));
            if (!allDates.length) {
                return renderEChartWithHandles({ ...themeOptions, title: { text: '无有效日期数据', left: 'center', top: 'center', textStyle: { color: t.textSecondary, fontSize: 14 } } });
            }
            const minDate = allDates.reduce((a, b) => (a < b ? a : b));
            const maxDate = allDates.reduce((a, b) => (a > b ? a : b));
            const today = new Date().toISOString().slice(0, 10);

            const getBarColor = (tk: Record<string, any>) => {
                if (tk.isCompleted && !tk.isOverdue) return '#52c41a';
                if (tk.isCompleted && tk.isOverdue) return '#faad14';
                if (tk.isIncomplete) return '#ff4d4f';
                return '#1890ff';
            };

            // Build bar data: each bar is [startTime, endTime, categoryIndex]
            // Using xAxis=time, yAxis=category, bar series type for compatibility
            const barSeries: any[] = [];
            sorted.forEach((tk, idx) => {
                const start = new Date(String(tk.planDate)).getTime();
                const end = tk.actualDate ? new Date(String(tk.actualDate)).getTime() : Date.now();
                barSeries.push({
                    value: [start, idx, end - start, tk.delayDays],
                    itemStyle: { color: getBarColor(tk) },
                    _task: tk,
                });
            });

            const xMax = maxDate > today ? maxDate : today;
            const ganttOption: Record<string, unknown> = {
                ...themeOptions,
                tooltip: {
                    trigger: 'item',
                    formatter: (params: any) => {
                        const tk = params.data?._task;
                        if (!tk) return '';
                        return [
                            `<b>${tk.name}</b>`,
                            tk.majorProjectName ? `重大项目: ${tk.majorProjectName}` : '',
                            tk.subprojectName ? `子项目: ${tk.subprojectName}` : '',
                            `类型: ${tk.type}`,
                            `责任人: ${tk.owner}`,
                            tk.status ? `状态: ${tk.status}` : '',
                            `计划: ${tk.planDate}`,
                            tk.actualDate ? `实际: ${tk.actualDate}` : '实际: 未完成',
                            tk.delayDays ? `超期: ${tk.delayDays}天` : '',
                            `风险: ${tk.riskLevel}`,
                        ].filter(Boolean).join('<br/>');
                    },
                },
                grid: { left: 120, right: 40, top: 30, bottom: 50 },
                xAxis: {
                    type: 'time',
                    min: minDate,
                    max: xMax,
                    axisLabel: { color: t.textSecondary, fontSize: 11 },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor, type: 'dashed' } },
                },
                yAxis: {
                    type: 'category',
                    data: categories,
                    inverse: true,
                    axisLabel: {
                        color: t.textPrimary,
                        fontSize: 11,
                        width: 100,
                        overflow: 'truncate' as const,
                    },
                    splitLine: { show: false },
                },
                dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
                series: [
                    {
                        type: 'custom',
                        renderItem: (params: any, api: any) => {
                            const startVal = api.value(0);
                            const catIdx = api.value(1);
                            const duration = api.value(2);
                            const endVal = startVal + duration;
                            const startPx = api.coord([startVal, catIdx]);
                            const endPx = api.coord([endVal, catIdx]);
                            const categoryHeight = typeof api.size === 'function' ? api.size([0, 1])[1] : 30;
                            const barHeight = categoryHeight * 0.6;
                            const baseStyle = typeof api.style === 'function' ? api.style() : {};
                            const fillColor = (typeof api.visual === 'function' ? api.visual('color') : null)
                                || baseStyle.fill
                                || barSeries[params.dataIndex]?.itemStyle?.color
                                || '#1890ff';
                            const style = { ...baseStyle, fill: fillColor };
                            // 里程碑节点画菱形
                            if (params.data?._task?.type === '里程碑节点') {
                                const sz = Math.min(categoryHeight * 0.7, 16);
                                return {
                                    type: 'diamond',
                                    shape: { cx: startPx[0], cy: startPx[1], width: sz, height: sz },
                                    style,
                                };
                            }
                            return {
                                type: 'rect',
                                shape: {
                                    x: startPx[0],
                                    y: startPx[1] - barHeight / 2,
                                    width: Math.max(endPx[0] - startPx[0], 4),
                                    height: barHeight,
                                    r: [2, 2, 2, 2],
                                },
                                style,
                            };
                        },
                        encode: { x: [0], y: 1 },
                        data: barSeries,
                        markLine: {
                            silent: true,
                            symbol: 'none',
                            lineStyle: { color: '#ff4d4f', type: 'dashed', width: 2 },
                            data: [{ xAxis: new Date(today).getTime() }],
                            label: { formatter: '今日', position: 'start', color: '#ff4d4f', fontSize: 11 },
                        },
                    },
                ],
            };
            /* eslint-enable @typescript-eslint/no-explicit-any */

            return renderEChartWithHandles(ganttOption, echartsClickHandler);
        }

        case 'radar-chart':
            {
                const mappedSeries = Array.isArray(c.series)
                    ? (c.series as Array<{ name?: string; data?: number[] }>)
                    : [];
                const radarSeries = mappedSeries.length > 0
                    ? mappedSeries.map((item, index) => ({
                        name: item.name,
                        value: Array.isArray(item.data) ? item.data : [],
                        areaStyle: { opacity: Math.max(0.14, 0.32 - (index * 0.08)) },
                    }))
                    : [{ value: c.data as number[], areaStyle: { opacity: 0.3 } }];
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                radar: {
                    indicator: c.indicator as Array<{ name: string; max: number }>,
                    center: [plotCenterX, plotCenterY],
                    radius: radarRadius,
                    axisName: { color: t.radar.axisNameColor },
                    splitLine: { lineStyle: { color: t.radar.splitLineColor } },
                    splitArea: { areaStyle: { color: ['transparent'] } },
                },
                series: [{
                    type: 'radar',
                    data: radarSeries,
                }],
            }, echartsClickHandler);
            }

        case 'funnel-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                series: [{
                    type: 'funnel',
                    left: funnelLeft,
                    right: funnelRight,
                    top: funnelTop,
                    bottom: funnelBottom,
                    min: 0,
                    max: 100,
                    sort: 'descending',
                    gap: 2,
                    label: {
                        show: funnelLabelShow,
                        position: funnelLabelPosition,
                        color: t.funnelLabelColor,
                        fontSize: seriesLabelFontSize,
                        formatter: funnelLabelPosition === 'right' ? '{b}: {c}' : '{b}',
                    },
                    labelLine: {
                        show: funnelLabelShow && funnelLabelPosition === 'right',
                        length: seriesLabelLineLength,
                        length2: seriesLabelLineLength2,
                    },
                    data: c.data as Array<{ name: string; value: number }>,
                }],
            }, echartsClickHandler);

        case 'scatter-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const RISK_COLORS: Record<string, string> = { '高': '#ff4d4f', '中': '#faad14', '低': '#52c41a' };
            const scatterSeries = c.series as Array<{ name: string; data: number[][] }> | undefined;
            const scatterData = c.data as number[][] | undefined;
            const builtSeries = scatterSeries?.length
                ? scatterSeries.map((s, idx) => ({
                    name: s.name,
                    type: 'scatter' as const,
                    data: s.data,
                    symbolSize: (val: number[]) => Math.max((val[2] ?? 1) * 8, 8),
                    itemStyle: { color: RISK_COLORS[s.name] || seriesColors[idx] || t.scatterColor },
                }))
                : [{
                    type: 'scatter' as const,
                    data: scatterData || [],
                    symbolSize: 10,
                    itemStyle: { color: seriesColors[0] || t.scatterColor },
                }];
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: {
                    ...themeOptions.tooltip,
                    formatter: (params: any) => {
                        const d = params.data || [];
                        return `${params.seriesName}<br/>超期: ${d[0]}天<br/>节点数: ${d[2] ?? 1}`;
                    },
                },
                xAxis: {
                    name: (c as any).xAxisName || '',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                yAxis: {
                    name: (c as any).yAxisName || '',
                    type: 'value' as const,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: {
                        color: axisLabelColor,
                        fontSize: axisFontSize,
                        formatter: (v: number) => ['', '低', '中', '高'][v] || String(v),
                    },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                    min: 0,
                    max: 4,
                    interval: 1,
                },
                series: builtSeries,
                grid: axisGrid,
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'combo-chart': {
            const comboSeries = Array.isArray(c.series) ? (c.series as Array<{ name: string; type: 'bar' | 'line'; yAxisIndex?: number; data: number[] }>) : [];
            const comboYAxis = Array.isArray(c.yAxis) ? (c.yAxis as Array<{ name?: string; min?: number; max?: number }>) : [{}];
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: {
                    ...themeOptions.tooltip,
                    trigger: 'axis',
                    confine: true,
                    axisPointer: { type: 'cross' },
                    formatter: axisTooltipFormatter,
                },
                xAxis: {
                    type: 'category',
                    data: c.xAxisData as string[],
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: {
                        color: axisLabelColor,
                        fontSize: axisFontSize,
                        rotate: xAxisLabelRotate,
                        hideOverlap: true,
                        formatter: formatXAxisLabel,
                        interval: xAxisLabelInterval,
                    },
                },
                yAxis: comboYAxis.map((y, i) => ({
                    type: 'value',
                    name: y.name,
                    nameTextStyle: { color: axisLabelColor, fontSize: axisFontSize },
                    min: y.min,
                    max: y.max,
                    position: i === 0 ? 'left' : 'right',
                    axisLine: { show: true, lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { show: i === 0, lineStyle: { color: t.echarts.splitLineColor } },
                })),
                series: comboSeries.map((s, idx) => ({
                    name: s.name,
                    type: s.type || 'bar',
                    yAxisIndex: s.yAxisIndex || 0,
                    data: s.data,
                    smooth: s.type === 'line',
                    label: {
                        show: axisSeriesLabelShow && (resolvedAxisSeriesLabelStrategy === 'all' || idx === 0),
                        position: s.type === 'line' ? axisLineLabelPosition : axisBarLabelPosition,
                        color: t.textPrimary,
                        fontSize: seriesLabelFontSize,
                    },
                    labelLayout: { hideOverlap: true },
                    ...(s.type === 'bar' ? {
                        itemStyle: {
                            borderRadius: [4, 4, 0, 0],
                            color: seriesColors[idx] || {
                                type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                                colorStops: [{ offset: 0, color: t.barGradient[0] }, { offset: 1, color: t.barGradient[1] }],
                            },
                        },
                    } : {
                        lineStyle: seriesColors[idx] ? { color: seriesColors[idx] } : {},
                        itemStyle: seriesColors[idx] ? { color: seriesColors[idx] } : {},
                        areaStyle: { opacity: 0.15, ...(seriesColors[idx] ? { color: seriesColors[idx] } : {}) },
                    }),
                })),
                grid: axisGrid,
            }, echartsClickHandler);
        }

        case 'treemap-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { formatter: '{b}: {c}' },
                series: [{
                    type: 'treemap',
                    data: c.data as Array<{ name: string; value?: number; children?: unknown[] }>,
                    leafDepth: 1,
                    roam: false,
                    breadcrumb: { show: true, itemStyle: { textStyle: { color: t.textPrimary } } },
                    label: { show: true, color: '#fff', fontSize: seriesLabelFontSize || 12 },
                    upperLabel: { show: true, height: 20, color: '#fff', fontSize: 11 },
                    levels: [
                        { itemStyle: { borderColor: t.echarts.splitLineColor, borderWidth: 2, gapWidth: 2 } },
                        { itemStyle: { borderColor: t.echarts.splitLineColor, borderWidth: 1, gapWidth: 1 }, upperLabel: { show: true } },
                    ],
                }],
            }, echartsClickHandler);

        case 'sunburst-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                series: [{
                    type: 'sunburst',
                    data: c.data as Array<{ name: string; value?: number; children?: unknown[] }>,
                    radius: ['15%', '90%'],
                    label: { show: true, color: t.textPrimary, fontSize: seriesLabelFontSize || 11, rotate: 'radial' },
                    itemStyle: { borderWidth: 1, borderColor: t.echarts.splitLineColor },
                    emphasis: { focus: 'ancestor' },
                }],
            }, echartsClickHandler);

        case 'wordcloud-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: { show: true, formatter: '{b}: {c}' },
                series: [{
                    type: 'wordCloud',
                    shape: (c.shape as string) || 'circle',
                    sizeRange: (c.fontSizeRange as [number, number]) || [14, 60],
                    rotationRange: (c.rotationRange as [number, number]) || [-45, 45],
                    rotationStep: 15,
                    gridSize: 8,
                    drawOutOfBound: false,
                    textStyle: {
                        fontFamily: SCREEN_UI_FONT_FAMILY,
                        color: () => t.echarts.colorPalette[Math.floor(Math.random() * t.echarts.colorPalette.length)],
                    },
                    data: (c.data as Array<{ name: string; value: number }>)?.map(d => ({
                        name: d.name,
                        value: d.value,
                    })) || [],
                }],
            });

        case 'waterfall-chart': {
            const waterfallData = (c.data as Array<{ name: string; value: number; isTotal?: boolean }>) || [];
            const wfCategories = waterfallData.map(d => d.name);
            let runningTotal = 0;
            const transparentBars: number[] = [];
            const positiveBars: (number | '-')[] = [];
            const negativeBars: (number | '-')[] = [];
            for (const item of waterfallData) {
                if (item.isTotal) {
                    transparentBars.push(0);
                    positiveBars.push(item.value >= 0 ? item.value : '-');
                    negativeBars.push(item.value < 0 ? Math.abs(item.value) : '-');
                    runningTotal = item.value;
                } else {
                    if (item.value >= 0) {
                        transparentBars.push(runningTotal);
                        positiveBars.push(item.value);
                        negativeBars.push('-');
                    } else {
                        transparentBars.push(runningTotal + item.value);
                        positiveBars.push('-');
                        negativeBars.push(Math.abs(item.value));
                    }
                    runningTotal += item.value;
                }
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: { show: false },
                tooltip: {
                    ...themeOptions.tooltip,
                    trigger: 'axis',
                    confine: true,
                    axisPointer: { type: 'shadow' },
                    formatter: (params: unknown) => {
                        const items = params as Array<{ seriesName: string; value: unknown; dataIndex: number }>;
                        const idx = items[0]?.dataIndex ?? 0;
                        const d = waterfallData[idx];
                        return d ? `${d.name}: ${d.value >= 0 ? '+' : ''}${d.value}` : '';
                    },
                },
                xAxis: {
                    type: 'category',
                    data: wfCategories,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate },
                },
                yAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: [
                    {
                        name: '辅助',
                        type: 'bar',
                        stack: 'waterfall',
                        data: transparentBars,
                        itemStyle: { borderColor: 'transparent', color: 'transparent' },
                        emphasis: { itemStyle: { borderColor: 'transparent', color: 'transparent' } },
                    },
                    {
                        name: '增加',
                        type: 'bar',
                        stack: 'waterfall',
                        data: positiveBars,
                        itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
                        label: {
                            show: true,
                            position: 'top',
                            color: t.textPrimary,
                            fontSize: seriesLabelFontSize,
                            formatter: (p: { value: unknown }) => p.value === '-' ? '' : `+${p.value}`,
                        },
                    },
                    {
                        name: '减少',
                        type: 'bar',
                        stack: 'waterfall',
                        data: negativeBars,
                        itemStyle: { color: '#ef4444', borderRadius: [4, 4, 0, 0] },
                        label: {
                            show: true,
                            position: 'bottom',
                            color: t.textPrimary,
                            fontSize: seriesLabelFontSize,
                            formatter: (p: { value: unknown; dataIndex: number }) => {
                                if (p.value === '-') return '';
                                const d = waterfallData[p.dataIndex];
                                return d ? String(d.value) : '';
                            },
                        },
                    },
                ],
                grid: axisGrid,
            }, echartsClickHandler);
        }

        case 'map-chart': {
            const title = String(c.title ?? '区域地图');
            const mapScope = String(c.mapScope ?? 'china');
            const defaultRegions = mapScope === 'world'
                ? [
                    { name: 'China', code: 'CN', value: 260 },
                    { name: 'United States of America', code: 'US', value: 180 },
                    { name: 'Russia', code: 'RU', value: 150 },
                    { name: 'India', code: 'IN', value: 140 },
                    { name: 'Brazil', code: 'BR', value: 110 },
                    { name: 'Australia', code: 'AU', value: 90 },
                ]
                : [
                    { name: '北京市', code: '110000', value: 120 },
                    { name: '上海市', code: '310000', value: 180 },
                    { name: '广东省', code: '440000', value: 140 },
                    { name: '浙江省', code: '330000', value: 95 },
                    { name: '四川省', code: '510000', value: 72 },
                    { name: '湖北省', code: '420000', value: 88 },
                ];
            const regions = Array.isArray(c.regions) && c.regions.length > 0 ? c.regions as Array<Record<string, unknown>> : defaultRegions;
            const getChildren = (item: unknown): Array<Record<string, unknown>> => {
                if (!item || typeof item !== 'object') return [];
                const raw = (item as Record<string, unknown>).children;
                if (!Array.isArray(raw)) return [];
                return raw.filter((node): node is Record<string, unknown> => !!node && typeof node === 'object');
            };
            const activeRegion = mapDrillRegion
                ? regions.find((item) => String(item.name ?? '') === mapDrillRegion)
                : undefined;
            const canRegionDrill = c.enableRegionDrill !== false;
            const drillRows = getChildren(activeRegion);
            const listRows = drillRows.length > 0 ? drillRows : regions;

            const maxValue = Math.max(1, ...listRows.map((item) => Number(item.value ?? 0)));
            const minValue = Math.min(...listRows.map((item) => Number(item.value ?? 0)));
            const mapName = String(c.mapName || mapScope || `dts-${mapScope}`).trim();
            const usingGeoMap = !mapDrillRegion && Boolean(EChart) && Boolean(hasMapFn?.(mapName)) && mapReadyVersion >= 0;
            const regionCodeVariableKey = String(c.regionCodeVariableKey ?? '').trim();
            const resolveRegionCode = (item: Record<string, unknown> | undefined): string => {
                if (!item) return '';
                const candidate = item.code ?? item.adcode ?? item.regionCode ?? item.id;
                return String(candidate ?? '').trim();
            };

            if (usingGeoMap) {
                const mapMode = String(c.mapMode ?? 'region');
                const baseTitle = chartTitleLayout.titleOption;
                const mapClickHandler = (params: Record<string, unknown>) => {
                    const regionName = String(params.name ?? '');
                    const row = params.data && typeof params.data === 'object'
                        ? (params.data as Record<string, unknown>)
                        : undefined;
                    const clickedCode = String(row?.code ?? row?.adcode ?? '').trim();
                    const target = clickedCode
                        ? regions.find((item) => resolveRegionCode(item) === clickedCode)
                            || regions.find((item) => String(item.name ?? '') === regionName)
                        : regions.find((item) => String(item.name ?? '') === regionName);
                    if (canRegionDrill && target && getChildren(target).length > 0) {
                        setMapDrillRegion(regionName);
                    }
                    const variableKey = String(c.regionVariableKey ?? '').trim();
                    if (variableKey && regionName) {
                        runtime.setVariable(variableKey, regionName, `map-chart:${component.id}`);
                    }
                    const code = resolveRegionCode(target);
                    if (regionCodeVariableKey && code) {
                        runtime.setVariable(regionCodeVariableKey, code, `map-chart:${component.id}`);
                    }
                };

                // Build mapMode-specific ECharts options
                let mapOption: Record<string, unknown>;
                if (mapMode === 'bubble' || mapMode === 'scatter') {
                    const scatterData = (c.scatterData as Array<{ name: string; value: [number, number, number] }>) || [];
                    const sizeRange = (c.bubbleSizeRange as [number, number]) || (mapMode === 'scatter' ? [6, 6] : [8, 40]);
                    const maxMag = Math.max(1, ...scatterData.map(d => Math.abs(d.value?.[2] ?? 0)));
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { trigger: 'item', formatter: (p: Record<string, unknown>) => {
                            const d = p.data as Record<string, unknown> | undefined;
                            return d ? `${d.name}: ${(d.value as number[])?.[2] ?? ''}` : '';
                        }},
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        series: [{
                            type: 'scatter', coordinateSystem: 'geo',
                            data: scatterData.map(d => ({ name: d.name, value: d.value })),
                            symbolSize: (val: number[]) => { const mag = val?.[2] ?? 0; return sizeRange[0] + (sizeRange[1] - sizeRange[0]) * (Math.abs(mag) / maxMag); },
                            itemStyle: { color: (c.bubbleColor as string) || t.echarts.colorPalette[0] },
                            label: { show: mapMode === 'scatter', formatter: '{b}', color: t.textPrimary, fontSize: 10 },
                        }],
                    };
                } else if (mapMode === 'heatmap') {
                    const heatmapData = (c.heatmapData as Array<[number, number, number]>) || [];
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { show: true },
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        visualMap: { show: true, min: 0, max: Math.max(1, ...heatmapData.map(d => d[2] || 0)), left: 6, bottom: 8, itemWidth: 10, itemHeight: 60, textStyle: { color: t.textSecondary, fontSize: 10 }, inRange: { color: ['#3b82f6', '#f59e0b', '#ef4444'] } },
                        series: [{
                            type: 'heatmap', coordinateSystem: 'geo',
                            data: heatmapData,
                            pointSize: (c.heatmapRadius as number) || 20,
                            blurSize: ((c.heatmapRadius as number) || 20) * 1.5,
                        }],
                    };
                } else if (mapMode === 'flow') {
                    const flowData = (c.flowData as Array<{ from: { name: string; coord: [number, number] }; to: { name: string; coord: [number, number] }; value?: number }>) || [];
                    const curveness = (c.flowLineStyle as Record<string, unknown>)?.curveness as number ?? 0.2;
                    const flowColor = (c.flowLineStyle as Record<string, unknown>)?.color as string ?? t.echarts.colorPalette[0];
                    const showEffect = c.showFlowEffect !== false;
                    const endpoints = new Map<string, [number, number]>();
                    for (const f of flowData) {
                        if (f.from?.name && f.from?.coord) endpoints.set(f.from.name, f.from.coord);
                        if (f.to?.name && f.to?.coord) endpoints.set(f.to.name, f.to.coord);
                    }
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { trigger: 'item' },
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        series: [
                            {
                                type: 'lines', coordinateSystem: 'geo',
                                data: flowData.map(f => ({ coords: [f.from.coord, f.to.coord], value: f.value })),
                                lineStyle: { color: flowColor, width: 1.5, curveness, opacity: 0.6 },
                                effect: showEffect ? { show: true, period: 4, trailLength: 0.2, symbol: 'arrow', symbolSize: 6, color: flowColor } : undefined,
                            },
                            {
                                type: 'effectScatter', coordinateSystem: 'geo',
                                data: Array.from(endpoints.entries()).map(([name, coord]) => ({ name, value: coord })),
                                symbolSize: 6,
                                rippleEffect: { brushType: 'stroke', scale: 3 },
                                itemStyle: { color: flowColor },
                                label: { show: true, formatter: '{b}', position: 'right', color: t.textPrimary, fontSize: 10 },
                            },
                        ],
                    };
                } else {
                    // Default: region fill map
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        visualMap: {
                            min: Number.isFinite(minValue) ? minValue : 0,
                            max: Number.isFinite(maxValue) ? maxValue : 100,
                            text: ['高', '低'], left: 6, bottom: 8, itemWidth: 10, itemHeight: 60,
                            textStyle: { color: t.textSecondary, fontSize: 10 },
                            inRange: { color: ['#93c5fd', '#3b82f6', '#1d4ed8'] },
                        },
                        tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                        series: [{
                            type: 'map', map: mapName, roam: true,
                            label: { show: true, color: t.textPrimary, fontSize: 10 },
                            emphasis: { label: { color: t.textPrimary } },
                            data: regions.map((item) => ({
                                name: String(item.name ?? ''),
                                value: Number(item.value ?? 0),
                                code: resolveRegionCode(item),
                            })),
                        }],
                    };
                }

                return (
                    <div style={{ width: '100%', height: '100%', position: 'relative' }}>
                        <EChart
                            style={{ width: '100%', height: '100%' }}
                            option={mapOption}
                            onEvents={{ click: mapClickHandler }}
                        />
                    </div>
                );
            }

            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div style={{ color: t.textPrimary, fontSize: 14, fontWeight: 600 }}>{title}</div>
                        {mapDrillRegion ? (
                            <button
                                type="button"
                                onClick={() => setMapDrillRegion(null)}
                                style={{
                                    border: '1px solid rgba(148,163,184,0.4)',
                                    background: 'rgba(15,23,42,0.45)',
                                    color: t.textPrimary,
                                    borderRadius: 4,
                                    fontSize: 11,
                                    cursor: 'pointer',
                                    padding: '2px 8px',
                                }}
                            >
                                返回上级
                            </button>
                        ) : null}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 8 }}>
                        {listRows.map((item, index) => {
                            const value = Number(item.value ?? 0);
                            const ratio = maxValue <= 0 ? 0 : Math.max(0, Math.min(1, value / maxValue));
                            const colorAlpha = 0.18 + ratio * 0.46;
                            const name = String(item.name ?? `区域${index + 1}`);
                            const hasChild = getChildren(item).length > 0;
                            return (
                                <button
                                    key={`${name}_${index}`}
                                    type="button"
                                    onClick={() => {
                                        if (canRegionDrill && hasChild) {
                                            setMapDrillRegion(name);
                                        }
                                        const variableKey = String(c.regionVariableKey ?? '').trim();
                                        if (variableKey) {
                                            runtime.setVariable(variableKey, name, `map-grid:${component.id}`);
                                        }
                                        const code = resolveRegionCode(item);
                                        if (regionCodeVariableKey && code) {
                                            runtime.setVariable(regionCodeVariableKey, code, `map-grid:${component.id}`);
                                        }
                                    }}
                                    style={{
                                        border: '1px solid rgba(148,163,184,0.25)',
                                        borderRadius: 8,
                                        background: `rgba(59,130,246,${colorAlpha.toFixed(3)})`,
                                        color: t.textPrimary,
                                        textAlign: 'left',
                                        padding: '8px 10px',
                                        minHeight: 56,
                                        cursor: 'pointer',
                                    }}
                                >
                                    <div style={{ fontSize: 12, fontWeight: 600 }}>{name}</div>
                                    <div style={{ marginTop: 4, fontSize: 12, color: t.textSecondary }}>
                                        {Number.isFinite(value) ? value.toLocaleString('zh-CN') : '-'}
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </div>
            );
        }

        default:
            return null;
    }
}
