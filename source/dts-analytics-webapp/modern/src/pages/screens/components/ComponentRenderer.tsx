import { memo, useMemo, useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import {
    BorderBox1,
    BorderBox2,
    BorderBox3,
    BorderBox4,
    BorderBox5,
    BorderBox6,
    BorderBox7,
    BorderBox8,
    BorderBox9,
    BorderBox10,
    BorderBox11,
    BorderBox12,
    BorderBox13,
    Decoration1,
    Decoration2,
    Decoration3,
    Decoration4,
    Decoration5,
    Decoration6,
    Decoration7,
    Decoration8,
    Decoration9,
    Decoration10,
    Decoration11,
    Decoration12,
    ScrollBoard,
    ScrollRankingBoard,
    WaterLevelPond,
    DigitalFlop,
} from '@jiaminghi/data-view-react';
import type { ScreenComponent, ScreenTheme } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { useDrillDown } from '../hooks/useDrillDown';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';
import { getThemeTokens } from '../screenThemes';

interface ComponentRendererProps {
    component: ScreenComponent;
    mode?: 'designer' | 'preview';
    theme?: ScreenTheme;
}

// Border box components map
const BorderBoxComponents: Record<number, React.ComponentType<{ children?: React.ReactNode; color?: string[] }>> = {
    1: BorderBox1,
    2: BorderBox2,
    3: BorderBox3,
    4: BorderBox4,
    5: BorderBox5,
    6: BorderBox6,
    7: BorderBox7,
    8: BorderBox8,
    9: BorderBox9,
    10: BorderBox10,
    11: BorderBox11,
    12: BorderBox12,
    13: BorderBox13,
};

// Decoration components map
const DecorationComponents: Record<number, React.ComponentType<{ color?: string[]; style?: React.CSSProperties }>> = {
    1: Decoration1,
    2: Decoration2,
    3: Decoration3,
    4: Decoration4,
    5: Decoration5,
    6: Decoration6,
    7: Decoration7,
    8: Decoration8,
    9: Decoration9,
    10: Decoration10,
    11: Decoration11,
    12: Decoration12,
};

export const ComponentRenderer = memo(function ComponentRenderer({ component, mode = 'preview', theme }: ComponentRendererProps) {
    const { type, config, width, height, dataSource, drillDown } = component;

    const t = useMemo(() => getThemeTokens(theme), [theme]);

    // Build ECharts base options from theme tokens
    const themeOptions = useMemo(() => ({
        backgroundColor: 'transparent',
        textStyle: { color: t.textPrimary },
        legend: { textStyle: { color: t.textPrimary } },
        tooltip: {
            backgroundColor: t.echarts.tooltipBg,
            borderColor: t.echarts.tooltipBorder,
            textStyle: { color: t.textPrimary },
        },
    }), [t]);

    // Drill-down state (only active in preview mode for drillable chart types)
    const drillActive = mode === 'preview' && DRILLABLE_TYPES.has(type) && drillDown?.enabled === true;
    const rootCardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;
    const drillState = useDrillDown(
        drillActive ? rootCardId : undefined,
        drillActive ? drillDown : undefined,
    );

    // Card data source hook — pass drill overrides when active
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(
        dataSource,
        drillActive ? drillState.effectiveCardId : undefined,
        drillActive ? drillState.queryParameters : undefined,
    );

    // Merge card data into config: card data overrides data fields only, not display fields
    const effectiveConfig = useMemo(() => {
        if (!cardData) return config;
        const mapped = mapCardDataToConfig(type, cardData);
        return { ...config, ...mapped };
    }, [config, cardData, type]);

    // For datetime component, update every second
    const [currentTime, setCurrentTime] = useState(new Date());
    useEffect(() => {
        if (type === 'datetime') {
            const timer = setInterval(() => setCurrentTime(new Date()), 1000);
            return () => clearInterval(timer);
        }
    }, [type]);

    // ECharts click handler for drill-down
    const echartsClickHandler = useMemo(() => {
        if (!drillActive || !drillState.canDrillDown) return undefined;
        return {
            click: (params: { name?: string; data?: { name?: string } }) => {
                const value = params.name ?? params.data?.name;
                if (value) drillState.handleDrill(String(value));
            },
        };
    }, [drillActive, drillState.canDrillDown, drillState.handleDrill]);

    const content = useMemo(() => {
        const c = effectiveConfig;
        switch (type) {
            // ==================== ECharts 图表 ====================
            case 'line-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 } },
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: (c.series as Array<{ name: string; data: number[] }>).map((s) => ({
                                name: s.name,
                                type: 'line',
                                data: s.data,
                                smooth: true,
                                areaStyle: { opacity: 0.3 },
                            })),
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'bar-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 } },
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: (c.series as Array<{ name: string; data: number[] }>).map((s) => ({
                                name: s.name,
                                type: 'bar',
                                data: s.data,
                                itemStyle: {
                                    borderRadius: [4, 4, 0, 0],
                                    color: {
                                        type: 'linear',
                                        x: 0, y: 0, x2: 0, y2: 1,
                                        colorStops: [
                                            { offset: 0, color: t.barGradient[0] },
                                            { offset: 1, color: t.barGradient[1] },
                                        ],
                                    },
                                },
                            })),
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'pie-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center' },
                            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
                            series: [{
                                type: 'pie',
                                radius: ['40%', '70%'],
                                avoidLabelOverlap: false,
                                label: {
                                    show: true,
                                    color: t.pieLabelColor,
                                    fontSize: 12,
                                    formatter: '{b}: {d}%',
                                },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'gauge-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
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
                                title: { show: true, offsetCenter: [0, '70%'], fontSize: 14, color: t.gauge.titleColor },
                                detail: { valueAnimation: true, fontSize: 28, offsetCenter: [0, '45%'], color: t.gauge.detailColor, formatter: '{value}%' },
                                data: [{ value: c.value as number, name: c.title as string }],
                            }],
                        }}
                    />
                );

            case 'radar-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center' },
                            radar: {
                                indicator: c.indicator as Array<{ name: string; max: number }>,
                                axisName: { color: t.radar.axisNameColor },
                                splitLine: { lineStyle: { color: t.radar.splitLineColor } },
                                splitArea: { areaStyle: { color: ['transparent'] } },
                            },
                            series: [{
                                type: 'radar',
                                data: [{ value: c.data as number[], areaStyle: { opacity: 0.3 } }],
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'funnel-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center' },
                            series: [{
                                type: 'funnel',
                                left: '10%',
                                top: 60,
                                bottom: 20,
                                width: '80%',
                                min: 0,
                                max: 100,
                                sort: 'descending',
                                gap: 2,
                                label: { show: true, position: 'inside', color: t.funnelLabelColor },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'scatter-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: 14 } },
                            xAxis: {
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            yAxis: {
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: [{
                                type: 'scatter',
                                data: c.data as number[][],
                                symbolSize: 10,
                                itemStyle: { color: t.scatterColor },
                            }],
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            // ==================== 基础组件 ====================
            case 'number-card':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'center',
                        background: t.numberCard.background,
                        borderRadius: t.cardBorderRadius,
                        border: t.numberCard.border,
                        boxShadow: t.cardShadow,
                    }}>
                        <div style={{ fontSize: 12, color: t.numberCard.titleColor, marginBottom: 8 }}>
                            {c.title as string}
                        </div>
                        <div style={{ fontSize: 32, fontWeight: 'bold', color: t.numberCard.valueColor }}>
                            {c.prefix as string}
                            {(c.value as number).toLocaleString()}
                            {c.suffix as string}
                        </div>
                    </div>
                );

            case 'title':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: c.textAlign as string,
                        fontSize: c.fontSize as number,
                        fontWeight: c.fontWeight as string,
                        color: c.color as string,
                    }}>
                        {c.text as string}
                    </div>
                );

            case 'datetime': {
                const formatted = (c.format as string)
                    .replace('YYYY', String(currentTime.getFullYear()))
                    .replace('MM', String(currentTime.getMonth() + 1).padStart(2, '0'))
                    .replace('DD', String(currentTime.getDate()).padStart(2, '0'))
                    .replace('HH', String(currentTime.getHours()).padStart(2, '0'))
                    .replace('mm', String(currentTime.getMinutes()).padStart(2, '0'))
                    .replace('ss', String(currentTime.getSeconds()).padStart(2, '0'));
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: c.fontSize as number,
                        color: c.color as string,
                        fontFamily: 'monospace',
                    }}>
                        {formatted}
                    </div>
                );
            }

            case 'progress-bar': {
                const value = c.value as number;
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                    }}>
                        <div style={{
                            flex: 1,
                            height: 12,
                            background: t.progressBar.trackBg,
                            borderRadius: 6,
                            overflow: 'hidden',
                        }}>
                            <div style={{
                                width: `${value}%`,
                                height: '100%',
                                background: `linear-gradient(90deg, ${t.progressBar.fillGradient[0]} 0%, ${t.progressBar.fillGradient[1]} 100%)`,
                                borderRadius: 6,
                                transition: 'width 0.3s ease',
                            }} />
                        </div>
                        {Boolean(c.showLabel) && (
                            <span style={{ color: t.progressBar.labelColor, fontSize: 12, minWidth: 40 }}>{value}%</span>
                        )}
                    </div>
                );
            }

            case 'image':
                return c.src ? (
                    <img
                        src={c.src as string}
                        alt=""
                        style={{
                            width: '100%',
                            height: '100%',
                            objectFit: c.fit as 'cover' | 'contain' | 'fill',
                        }}
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        图片
                    </div>
                );

            case 'video':
                return c.src ? (
                    <video
                        src={c.src as string}
                        autoPlay={c.autoplay as boolean}
                        loop={c.loop as boolean}
                        muted={c.muted as boolean}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        视频
                    </div>
                );

            case 'iframe':
                return c.src ? (
                    <iframe
                        src={c.src as string}
                        style={{ width: '100%', height: '100%', border: 'none' }}
                        title="Embedded content"
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        iframe
                    </div>
                );

            // ==================== DataV 边框组件 ====================
            case 'border-box': {
                const boxType = (c.boxType as number) || 1;
                const BorderBoxComponent = BorderBoxComponents[boxType] || BorderBox1;
                const colors = c.color as string[] | undefined;
                return (
                    <BorderBoxComponent color={colors}>
                        <div style={{ width: '100%', height: '100%', padding: 16 }}>
                            {c.children as React.ReactNode}
                        </div>
                    </BorderBoxComponent>
                );
            }

            // ==================== DataV 装饰组件 ====================
            case 'decoration': {
                const decorationType = (c.decorationType as number) || 1;
                const DecorationComponent = DecorationComponents[decorationType] || Decoration1;
                const colors = c.color as string[] | undefined;
                return (
                    <DecorationComponent color={colors} style={{ width: '100%', height: '100%' }} />
                );
            }

            // ==================== DataV 数据展示组件 ====================
            case 'scroll-board':
                return (
                    <ScrollBoard
                        config={{
                            header: c.header as string[],
                            data: c.data as string[][],
                            rowNum: c.rowNum as number,
                            headerBGC: c.headerBGC as string,
                            oddRowBGC: c.oddRowBGC as string,
                            evenRowBGC: c.evenRowBGC as string,
                            waitTime: c.waitTime as number || 2000,
                            headerHeight: 35,
                            align: ['center', 'center', 'center'],
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'scroll-ranking':
                return (
                    <ScrollRankingBoard
                        config={{
                            data: c.data as Array<{ name: string; value: number }>,
                            rowNum: c.rowNum as number || 5,
                            waitTime: c.waitTime as number || 2000,
                            carousel: 'single',
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'water-level':
                return (
                    <WaterLevelPond
                        config={{
                            data: [c.value as number],
                            shape: c.shape as 'rect' | 'round' | 'roundRect' || 'round',
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'digital-flop':
                return (
                    <DigitalFlop
                        config={{
                            number: c.number as number[],
                            content: c.content as string,
                            style: c.style as { fontSize?: number; fill?: string },
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'percent-pond': {
                const percentValue = c.value as number;
                const colors = c.colors as string[] || [t.progressBar.fillGradient[0], t.progressBar.fillGradient[1]];
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative',
                    }}>
                        <div style={{
                            width: '100%',
                            height: 20,
                            background: t.progressBar.trackBg,
                            borderRadius: c.borderRadius as number || 5,
                            border: `${c.borderWidth as number || 2}px solid ${colors[0]}`,
                            overflow: 'hidden',
                            position: 'relative',
                        }}>
                            <div style={{
                                width: `${percentValue}%`,
                                height: '100%',
                                background: `linear-gradient(90deg, ${colors[0]} 0%, ${colors[1] || colors[0]} 100%)`,
                                transition: 'width 0.5s ease',
                            }} />
                        </div>
                        <span style={{
                            position: 'absolute',
                            color: t.progressBar.labelColor,
                            fontSize: 14,
                            fontWeight: 'bold',
                            textShadow: '0 0 4px rgba(0,0,0,0.8)',
                        }}>
                            {percentValue}%
                        </span>
                    </div>
                );
            }

            default:
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: `1px dashed ${t.placeholder.color}`,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 12,
                    }}>
                        {type}
                    </div>
                );
        }
    }, [type, effectiveConfig, width, height, currentTime, echartsClickHandler, t, themeOptions]);

    return (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
            {content}
            {/* Drill-down breadcrumb overlay */}
            {drillActive && drillState.breadcrumbs.length > 1 && (
                <div style={{
                    position: 'absolute', top: 4, left: 4,
                    display: 'flex', alignItems: 'center', gap: 2,
                    background: t.breadcrumb.background,
                    padding: '2px 8px', borderRadius: 4,
                    fontSize: 11, color: t.breadcrumb.textColor, zIndex: 10,
                }}>
                    {drillState.breadcrumbs.map((crumb, i) => {
                        const isLast = i === drillState.breadcrumbs.length - 1;
                        return (
                            <span key={crumb.depth} style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                {i > 0 && <span style={{ color: t.textMuted, margin: '0 2px' }}>/</span>}
                                {isLast ? (
                                    <span style={{ color: t.textPrimary }}>{crumb.label}</span>
                                ) : (
                                    <span
                                        style={{ color: t.breadcrumb.linkColor, cursor: 'pointer' }}
                                        onClick={() => drillState.handleRollUp(crumb.depth)}
                                    >
                                        {crumb.label}
                                    </span>
                                )}
                            </span>
                        );
                    })}
                </div>
            )}
            {/* Card data source loading indicator */}
            {cardLoading && (
                <div style={{
                    position: 'absolute', top: 4, right: 4,
                    width: 8, height: 8, borderRadius: '50%',
                    background: t.accentColor,
                    animation: 'pulse 1.5s ease-in-out infinite',
                }} />
            )}
            {/* Card data source error indicator */}
            {cardError && (
                <div style={{
                    position: 'absolute', bottom: 4, left: 4,
                    fontSize: 10, color: '#ef4444',
                    background: t.errorBg,
                    padding: '2px 6px', borderRadius: 3,
                    maxWidth: '80%', overflow: 'hidden',
                    textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }} title={cardError}>
                    {cardError}
                </div>
            )}
        </div>
    );
});
