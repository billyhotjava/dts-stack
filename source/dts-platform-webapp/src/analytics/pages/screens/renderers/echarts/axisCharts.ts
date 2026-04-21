// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode } from 'react';
import type { EChartsRendererProps } from './types';

// ---- 小工具 ----
const pickNum = (v: unknown): number | undefined => {
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
};

// 把 hex / rgb / rgba / hsl / var(--x) 颜色附加 alpha，生成 ECharts 可接受的颜色串。
// 当传入颜色本身已含 alpha（如 rgba），使用 global-composite 简化：直接返回原色（ECharts 会按原色计算）。
function toAlphaHex(color: string, alpha: number): string {
    if (typeof color !== 'string' || !color.trim()) return `rgba(99,102,241,${alpha})`;
    const c = color.trim();
    const a = Math.max(0, Math.min(1, alpha));
    if (c.startsWith('#')) {
        // 扩展 #abc → #aabbcc
        const hex = c.length === 4
            ? '#' + c.slice(1).split('').map((ch) => ch + ch).join('')
            : c;
        if (hex.length === 7) {
            const r = parseInt(hex.slice(1, 3), 16);
            const g = parseInt(hex.slice(3, 5), 16);
            const b = parseInt(hex.slice(5, 7), 16);
            if ([r, g, b].every(Number.isFinite)) return `rgba(${r},${g},${b},${a})`;
        }
    }
    if (c.startsWith('rgb(') || c.startsWith('rgba(')) {
        return c.replace(/rgba?\(([^)]+)\)/, (_, body) => {
            const parts = body.split(',').map((s: string) => s.trim());
            return `rgba(${parts[0]},${parts[1]},${parts[2]},${a})`;
        });
    }
    // 其他（hsl/var/命名色）直接返回原色——ECharts 仍可用，但无法注入 alpha。
    return c;
}

export function renderAxisChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t,
        renderEChartWithHandles: renderEChartRaw,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
        axisFontSize, axisLabelColor: axisLabelColorOverride, seriesLabelFontSize,
        xAxisLabelRotate, yAxisLabelRotate, xAxisLabelInterval, formatXAxisLabel,
        axisSeriesLabelShow, resolvedAxisSeriesLabelStrategy, axisSeriesLabelFormatter,
        axisLineLabelPosition, axisBarLabelPosition, axisBarLabelColor, axisTooltipFormatter,
        isCompactCanvas, isTinyCanvas, xAxisCategoryCount,
        echartsClickHandler,
        axisOverrides,
    } = props;

    const axisLabelColor = axisLabelColorOverride || t.echarts.axisLabelColor;

    // AxisConfigEditor / 嵌套 c.xAxis / c.yAxis 里的 show / splitLineShow / min / max / type
    // 需要在这里统一 merge 到 ECharts option 上。之前每个 case 里自己写 xAxis/yAxis，
    // 这些字段完全被忽略导致"UI 设置不生效"。
    const mergeAxis = (axis: any, ov: any): any => {
        if (!axis || !ov) return axis;
        const next = { ...axis };
        if (ov.show === false || ov.show === true) next.show = ov.show;
        if (ov.type !== undefined) next.type = ov.type;
        if (ov.min !== undefined) next.min = ov.min;
        if (ov.max !== undefined) next.max = ov.max;
        if (ov.splitLineShow === true || ov.splitLineShow === false || ov.splitLineColor !== undefined) {
            const prev = axis.splitLine || {};
            const prevStyle = prev.lineStyle || {};
            next.splitLine = {
                ...prev,
                ...(ov.splitLineShow === true || ov.splitLineShow === false ? { show: ov.splitLineShow } : {}),
                ...(ov.splitLineColor !== undefined
                    ? { lineStyle: { ...prevStyle, color: ov.splitLineColor } }
                    : {}),
            };
        }
        return next;
    };
    const applyToAxis = (val: any, ov: any) => {
        if (!ov) return val;
        if (Array.isArray(val)) return val.map((v) => mergeAxis(v, ov));
        return mergeAxis(val, ov);
    };
    const applyAxisOverrides = (option: Record<string, unknown>): Record<string, unknown> => {
        if (!axisOverrides) return option;
        const out = { ...option };
        if (out.xAxis && axisOverrides.x) out.xAxis = applyToAxis(out.xAxis, axisOverrides.x);
        if (out.yAxis && axisOverrides.y) out.yAxis = applyToAxis(out.yAxis, axisOverrides.y);
        return out;
    };
    const renderEChartWithHandles = (
        option: Record<string, unknown>,
        onEvents?: Record<string, (params: Record<string, unknown>) => void>,
    ) => renderEChartRaw(applyAxisOverrides(option), onEvents);

    // ---- 通用视觉增强开关（由 schema 暴露，默认商业化观感） ----
    const showArea = c.showArea !== false;                 // line 是否显示区域填充渐变
    const smoothLine = c.smooth !== false;                 // line 是否平滑
    const lineWidth = pickNum(c.lineWidth) ?? 2.5;         // line 描边粗细
    const barBorderRadius = pickNum(c.barBorderRadius) ?? 6; // bar 顶部圆角
    // 把用户选 / 主题给出的色 + 透明版本组合为 LinearGradient，用于面积填充。
    // 透明度由 opacityTop -> opacityBottom，ECharts 支持 hex+alpha 或 rgba。
    const makeAreaGradient = (baseColor: string) => ({
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
            { offset: 0, color: toAlphaHex(baseColor, 0.42) },
            { offset: 1, color: toAlphaHex(baseColor, 0.02) },
        ],
    });
    const enableDataZoom = c.enableDataZoom === true;      // 是否启用缩放滑块（大数据场景）

    switch (type) {
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
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: yAxisLabelRotate },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: (Array.isArray(c.series) ? c.series as Array<{ name: string; data: number[] }> : []).map((s, idx) => {
                    const lineStackMode = String(c.stackMode ?? 'off');
                    const stackGroup = lineStackMode !== 'off' ? 'stack' : undefined;
                    const baseColor = seriesColors[idx] ?? t.echarts.colorPalette[idx % t.echarts.colorPalette.length];
                    return {
                        name: s.name,
                        type: 'line' as const,
                        data: s.data,
                        smooth: smoothLine,
                        stack: stackGroup,
                        showSymbol: !isCompactCanvas || xAxisCategoryCount <= 24,
                        symbolSize: 6,
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
                        // 面积渐变：顶部不透明度 0.42，底部 0.02，和主线同色；用户 c.showArea=false 可关闭。
                        ...(showArea ? {
                            areaStyle: {
                                opacity: 1,
                                color: stackGroup
                                    ? toAlphaHex(baseColor, 0.6)
                                    : makeAreaGradient(baseColor),
                            },
                        } : {}),
                        lineStyle: { color: baseColor, width: lineWidth },
                        itemStyle: { color: baseColor, borderWidth: 2, borderColor: t.echarts.tooltipBg || '#fff' },
                        emphasis: { focus: 'series', lineStyle: { width: lineWidth + 1 } },
                    };
                }),
                grid: axisGrid,
                ...(enableDataZoom ? {
                    dataZoom: [
                        { type: 'inside', start: 0, end: 100 },
                        { type: 'slider', height: 18, bottom: 4 },
                    ],
                } : {}),
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
                // In vertical bar, valueAxis=yAxis → use yAxisLabelRotate;
                // In horizontal bar, valueAxis=xAxis → use xAxisLabelRotate.
                axisLabel: {
                    color: axisLabelColor,
                    fontSize: axisFontSize,
                    rotate: barHorizontal ? xAxisLabelRotate : yAxisLabelRotate,
                },
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
                series: (Array.isArray(c.series) ? c.series as Array<{ name: string; data: number[] }> : []).map((s, idx) => {
                    const baseColor = seriesColors[idx] ?? t.echarts.colorPalette[idx % t.echarts.colorPalette.length];
                    return {
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
                        labelLayout: { hideOverlap: true },
                        itemStyle: {
                            // c.barBorderRadius 控制圆角，横向柱子顶部在右，纵向柱子顶部在上
                            borderRadius: barHorizontal
                                ? [0, barBorderRadius, barBorderRadius, 0]
                                : [barBorderRadius, barBorderRadius, 0, 0],
                            color: seriesColors[idx]
                                ? {
                                    // 用户指定单色 → 做一个同色系渐变（浅到深）避免死板
                                    type: 'linear',
                                    x: 0, y: 0, x2: barHorizontal ? 1 : 0, y2: barHorizontal ? 0 : 1,
                                    colorStops: [
                                        { offset: 0, color: toAlphaHex(baseColor, 0.95) },
                                        { offset: 1, color: toAlphaHex(baseColor, 0.55) },
                                    ],
                                }
                                : {
                                    type: 'linear',
                                    x: 0, y: 0, x2: barHorizontal ? 1 : 0, y2: barHorizontal ? 0 : 1,
                                    colorStops: [
                                        { offset: 0, color: t.barGradient[0] },
                                        { offset: 1, color: t.barGradient[1] },
                                    ],
                                },
                        },
                        emphasis: {
                            focus: 'series',
                            itemStyle: {
                                shadowBlur: 10,
                                shadowColor: 'rgba(0,0,0,0.18)',
                            },
                        },
                    };
                }),
                grid: axisGrid,
                ...(enableDataZoom ? {
                    dataZoom: [
                        { type: 'inside', start: 0, end: 100 },
                        { type: 'slider', height: 18, bottom: 4 },
                    ],
                } : {}),
            }, echartsClickHandler);
        }

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
                // 启用 visualMap 时依据 value[2] 将数据按范围映射到调色板
                ...(c.enableVisualMap === true ? {
                    visualMap: {
                        min: pickNum(c.visualMapMin) ?? 0,
                        max: pickNum(c.visualMapMax) ?? 100,
                        dimension: 2,
                        calculable: true,
                        orient: 'horizontal',
                        left: 'center',
                        bottom: 8,
                        inRange: { color: t.echarts.colorPalette },
                        textStyle: { color: t.textSecondary },
                    },
                } : {}),
                ...(enableDataZoom ? {
                    dataZoom: [
                        { type: 'inside', xAxisIndex: 0, start: 0, end: 100 },
                        { type: 'slider', xAxisIndex: 0, height: 18, bottom: 4 },
                    ],
                } : {}),
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

        case 'candlestick-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const candleRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const candleDates: string[] = [];
            const ohlcData: number[][] = [];
            for (const row of candleRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                candleDates.push(String(vals[0] ?? ''));
                ohlcData.push([Number(vals[1] ?? 0), Number(vals[2] ?? 0), Number(vals[3] ?? 0), Number(vals[4] ?? 0)]);
            }
            const candleSeries: Array<Record<string, any>> = [
                {
                    type: 'candlestick',
                    data: ohlcData,
                    itemStyle: {
                        color: (c.upColor as string) || '#ec0000',
                        color0: (c.downColor as string) || '#00da3c',
                        borderColor: (c.upColor as string) || '#ec0000',
                        borderColor0: (c.downColor as string) || '#00da3c',
                    },
                },
            ];
            if (c.showMA) {
                const maPeriods = String(c.maPeriods ?? '5,10,20').split(',').map(Number).filter(n => n > 0);
                const maColors = ['#f5a623', '#f56c6c', '#409eff', '#67c23a'];
                for (let pi = 0; pi < maPeriods.length; pi++) {
                    const period = maPeriods[pi];
                    const maData: (number | null)[] = [];
                    for (let i = 0; i < ohlcData.length; i++) {
                        if (i < period - 1) {
                            maData.push(null);
                        } else {
                            let sum = 0;
                            for (let j = 0; j < period; j++) sum += ohlcData[i - j][1]; // close
                            maData.push(sum / period);
                        }
                    }
                    candleSeries.push({
                        name: `MA${period}`,
                        type: 'line',
                        data: maData,
                        smooth: true,
                        showSymbol: false,
                        lineStyle: { width: 1, color: maColors[pi % maColors.length] },
                        itemStyle: { color: maColors[pi % maColors.length] },
                    });
                }
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: {
                    ...themeOptions.tooltip,
                    trigger: 'axis',
                    axisPointer: { type: 'cross' },
                },
                xAxis: {
                    type: 'category',
                    data: candleDates,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate, hideOverlap: true },
                },
                yAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: candleSeries,
                grid: axisGrid,
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'boxplot-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const boxRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const boxCategories: string[] = [];
            const boxData: number[][] = [];
            const outlierData: Array<[number, number]> = [];
            for (let i = 0; i < boxRows.length; i++) {
                const row = boxRows[i];
                const vals = Array.isArray(row) ? row : Object.values(row);
                if (vals.length >= 6) {
                    boxCategories.push(String(vals[0] ?? ''));
                    boxData.push([Number(vals[1]), Number(vals[2]), Number(vals[3]), Number(vals[4]), Number(vals[5])]);
                    // outliers from index 6+
                    if (c.showOutliers) {
                        for (let j = 6; j < vals.length; j++) {
                            if (vals[j] != null) outlierData.push([i, Number(vals[j])]);
                        }
                    }
                } else if (vals.length >= 5) {
                    boxData.push([Number(vals[0]), Number(vals[1]), Number(vals[2]), Number(vals[3]), Number(vals[4])]);
                }
            }
            const boxHorizontal = c.orient === 'horizontal';
            const boxCategoryAxis = {
                type: 'category' as const,
                data: boxCategories,
                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
            };
            const boxValueAxis = {
                type: 'value' as const,
                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
            };
            const boxSeries: Array<Record<string, any>> = [
                { type: 'boxplot', data: boxData },
            ];
            if (c.showOutliers && outlierData.length > 0) {
                boxSeries.push({
                    type: 'scatter',
                    data: outlierData,
                    symbolSize: 6,
                    itemStyle: { color: seriesColors[0] || t.scatterColor },
                });
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, trigger: 'item' },
                xAxis: boxHorizontal ? boxValueAxis : boxCategoryAxis,
                yAxis: boxHorizontal ? boxCategoryAxis : boxValueAxis,
                series: boxSeries,
                grid: axisGrid,
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'pictorialBar-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const picRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const picCategories: string[] = [];
            const picValues: number[] = [];
            for (const row of picRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                picCategories.push(String(vals[0] ?? ''));
                picValues.push(Number(vals[1] ?? 0));
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                tooltip: { ...themeOptions.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } },
                xAxis: {
                    type: 'category',
                    data: picCategories,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate, hideOverlap: true },
                },
                yAxis: {
                    type: 'value',
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                },
                series: [{
                    type: 'pictorialBar',
                    data: picValues,
                    symbol: (c.symbol as string) || 'circle',
                    symbolRepeat: c.symbolRepeat ? 'fixed' : false,
                    barWidth: (c.barWidth as number) || 30,
                    symbolSize: ['100%', '100%'],
                    itemStyle: { color: seriesColors[0] || t.echarts.colorPalette[0] },
                }],
                grid: axisGrid,
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        default:
            return null;
    }
}
