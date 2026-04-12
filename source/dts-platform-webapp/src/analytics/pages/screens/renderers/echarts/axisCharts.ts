// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode } from 'react';
import type { EChartsRendererProps } from './types';

export function renderAxisChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t,
        renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
        axisFontSize, axisLabelColor: axisLabelColorOverride, seriesLabelFontSize,
        xAxisLabelRotate, xAxisLabelInterval, formatXAxisLabel,
        axisSeriesLabelShow, resolvedAxisSeriesLabelStrategy, axisSeriesLabelFormatter,
        axisLineLabelPosition, axisBarLabelPosition, axisBarLabelColor, axisTooltipFormatter,
        isCompactCanvas, isTinyCanvas, xAxisCategoryCount,
        echartsClickHandler,
    } = props;

    const axisLabelColor = axisLabelColorOverride || t.echarts.axisLabelColor;

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
