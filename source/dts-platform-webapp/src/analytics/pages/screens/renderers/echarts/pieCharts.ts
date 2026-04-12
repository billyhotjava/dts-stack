// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode } from 'react';
import type { EChartsRendererProps } from './types';

export function renderPieChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t,
        renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, seriesColors,
        seriesLabelFontSize,
        plotCenterX, plotCenterY, pieInnerRadius, pieOuterRadius, pieLabelShow, pieLabelPosition,
        funnelLeft, funnelRight, funnelTop, funnelBottom, funnelLabelShow, funnelLabelPosition,
        seriesLabelLineLength, seriesLabelLineLength2, seriesLabelMinAngle,
        echartsClickHandler,
    } = props;

    switch (type) {
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

        default:
            return null;
    }
}
