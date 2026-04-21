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

    // pie 视觉增强开关：用户可配 c.roseType ('radius'|'area'|'') 切换南丁格尔；
    // c.padAngle 控制扇形之间的间隙；c.pieBorderRadius 让扇形出圆角。
    const roseType = (typeof c.roseType === 'string' && c.roseType.trim())
        ? c.roseType.trim() as 'radius' | 'area'
        : undefined;
    const padAngle = typeof c.padAngle === 'number' ? c.padAngle : 2;
    const pieBorderRadius = typeof c.pieBorderRadius === 'number' ? c.pieBorderRadius : 6;

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
                    ...(roseType ? { roseType } : {}),
                    padAngle,
                    itemStyle: {
                        borderRadius: pieBorderRadius,
                        borderColor: t.echarts.tooltipBg || '#fff',
                        borderWidth: 2,
                    },
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
                        smooth: 0.2,
                    },
                    minShowLabelAngle: seriesLabelMinAngle,
                    labelLayout: pieLabelPosition === 'inside'
                        ? { hideOverlap: true }
                        : { hideOverlap: true, moveOverlap: 'shiftY' },
                    emphasis: {
                        focus: 'self',
                        itemStyle: {
                            shadowBlur: 18,
                            shadowColor: 'rgba(0,0,0,0.22)',
                        },
                        scale: true,
                        scaleSize: 6,
                    },
                    data: c.data as Array<{ name: string; value: number }>,
                }],
            }, echartsClickHandler);

        case 'gauge-chart': {
            // 进度条渐变：用主题调色板前两色沿环向渐变，视觉更有层次
            const gaugeColor = (seriesColors && seriesColors.length >= 2)
                ? seriesColors
                : t.echarts.colorPalette.slice(0, 3);
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                series: [{
                    type: 'gauge',
                    min: c.min as number,
                    max: c.max as number,
                    progress: {
                        show: true,
                        width: 18,
                        itemStyle: {
                            // 进度条圆角 + 同色系渐变
                            color: {
                                type: 'linear',
                                x: 0, y: 0, x2: 1, y2: 0,
                                colorStops: [
                                    { offset: 0, color: gaugeColor[0] },
                                    { offset: 1, color: gaugeColor[1] ?? gaugeColor[0] },
                                ],
                            },
                        },
                    },
                    axisLine: { lineStyle: { width: 18, color: [[1, t.gauge.axisLineColor]] } },
                    axisTick: { show: false },
                    splitLine: { length: 10, lineStyle: { width: 2, color: t.gauge.splitLineColor } },
                    axisLabel: { distance: 25, color: t.gauge.axisLabelColor, fontSize: 12 },
                    pointer: {
                        icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
                        length: '12%',
                        width: 10,
                        itemStyle: { color: 'auto', shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.25)' },
                    },
                    anchor: { show: true, showAbove: true, size: 18, itemStyle: { borderWidth: 6 } },
                    title: { show: true, offsetCenter: [0, '70%'], fontSize: (c.titleFontSize as number) || 14, color: t.gauge.titleColor },
                    detail: { valueAnimation: true, fontSize: 28, offsetCenter: [0, '45%'], color: t.gauge.detailColor, formatter: '{value}%' },
                    data: [{ value: c.value != null ? Number(c.value) : 0, name: c.title as string }],
                }],
            });
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

        default:
            return null;
    }
}
