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
import type { ScreenComponent } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';

interface ComponentRendererProps {
    component: ScreenComponent;
}

// ECharts common options for dark theme
const darkThemeOptions = {
    backgroundColor: 'transparent',
    textStyle: { color: '#fff' },
    legend: { textStyle: { color: '#fff' } },
    tooltip: {
        backgroundColor: 'rgba(0,0,0,0.7)',
        borderColor: '#333',
        textStyle: { color: '#fff' },
    },
};

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

export const ComponentRenderer = memo(function ComponentRenderer({ component }: ComponentRendererProps) {
    const { type, config, width, height, dataSource } = component;

    // Card data source hook
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(dataSource);

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

    const content = useMemo(() => {
        const c = effectiveConfig;
        switch (type) {
            // ==================== ECharts 图表 ====================
            case 'line-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 } },
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                                splitLine: { lineStyle: { color: '#333' } },
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
                    />
                );

            case 'bar-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 } },
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                                splitLine: { lineStyle: { color: '#333' } },
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
                                            { offset: 0, color: '#6366f1' },
                                            { offset: 1, color: '#4f46e5' },
                                        ],
                                    },
                                },
                            })),
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                    />
                );

            case 'pie-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 }, left: 'center' },
                            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
                            series: [{
                                type: 'pie',
                                radius: ['40%', '70%'],
                                avoidLabelOverlap: false,
                                label: { show: true, color: '#fff', fontSize: 12 },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                    />
                );

            case 'gauge-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            series: [{
                                type: 'gauge',
                                min: c.min as number,
                                max: c.max as number,
                                progress: { show: true, width: 18 },
                                axisLine: { lineStyle: { width: 18, color: [[1, '#334155']] } },
                                axisTick: { show: false },
                                splitLine: { length: 10, lineStyle: { width: 2, color: '#999' } },
                                axisLabel: { distance: 25, color: '#999', fontSize: 12 },
                                pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '12%', width: 10, itemStyle: { color: 'auto' } },
                                anchor: { show: true, showAbove: true, size: 18, itemStyle: { borderWidth: 6 } },
                                title: { show: true, offsetCenter: [0, '70%'], fontSize: 14, color: '#fff' },
                                detail: { valueAnimation: true, fontSize: 28, offsetCenter: [0, '45%'], color: '#fff', formatter: '{value}%' },
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
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 }, left: 'center' },
                            radar: {
                                indicator: c.indicator as Array<{ name: string; max: number }>,
                                axisName: { color: '#aaa' },
                                splitLine: { lineStyle: { color: '#444' } },
                                splitArea: { areaStyle: { color: ['transparent'] } },
                            },
                            series: [{
                                type: 'radar',
                                data: [{ value: c.data as number[], areaStyle: { opacity: 0.3 } }],
                            }],
                        }}
                    />
                );

            case 'funnel-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 }, left: 'center' },
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
                                label: { show: true, position: 'inside', color: '#fff' },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                    />
                );

            case 'scatter-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...darkThemeOptions,
                            title: { text: c.title as string, textStyle: { color: '#fff', fontSize: 14 } },
                            xAxis: {
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                                splitLine: { lineStyle: { color: '#333' } },
                            },
                            yAxis: {
                                axisLine: { lineStyle: { color: '#444' } },
                                axisLabel: { color: '#aaa' },
                                splitLine: { lineStyle: { color: '#333' } },
                            },
                            series: [{
                                type: 'scatter',
                                data: c.data as number[][],
                                symbolSize: 10,
                                itemStyle: { color: '#6366f1' },
                            }],
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
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
                        background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(99, 102, 241, 0.05) 100%)',
                        borderRadius: 8,
                        border: '1px solid rgba(99, 102, 241, 0.3)',
                    }}>
                        <div style={{ fontSize: 12, color: '#94a3b8', marginBottom: 8 }}>
                            {c.title as string}
                        </div>
                        <div style={{ fontSize: 32, fontWeight: 'bold', color: '#fff' }}>
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
                            background: 'rgba(255,255,255,0.1)',
                            borderRadius: 6,
                            overflow: 'hidden',
                        }}>
                            <div style={{
                                width: `${value}%`,
                                height: '100%',
                                background: 'linear-gradient(90deg, #6366f1 0%, #8b5cf6 100%)',
                                borderRadius: 6,
                                transition: 'width 0.3s ease',
                            }} />
                        </div>
                        {Boolean(c.showLabel) && (
                            <span style={{ color: '#fff', fontSize: 12, minWidth: 40 }}>{value}%</span>
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
                        background: 'rgba(255,255,255,0.05)',
                        border: '2px dashed rgba(255,255,255,0.2)',
                        borderRadius: 4,
                        color: '#666',
                        fontSize: 14,
                    }}>
                        🖼️ 图片
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
                        background: 'rgba(255,255,255,0.05)',
                        border: '2px dashed rgba(255,255,255,0.2)',
                        borderRadius: 4,
                        color: '#666',
                        fontSize: 14,
                    }}>
                        🎬 视频
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
                        background: 'rgba(255,255,255,0.05)',
                        border: '2px dashed rgba(255,255,255,0.2)',
                        borderRadius: 4,
                        color: '#666',
                        fontSize: 14,
                    }}>
                        🌐 iframe
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
                const colors = c.colors as string[] || ['#3de7c9', '#00baff'];
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
                            background: 'rgba(255,255,255,0.1)',
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
                            color: '#fff',
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
                        background: 'rgba(255,255,255,0.05)',
                        border: '1px dashed rgba(255,255,255,0.2)',
                        borderRadius: 4,
                        color: '#666',
                        fontSize: 12,
                    }}>
                        {type}
                    </div>
                );
        }
    }, [type, effectiveConfig, width, height, currentTime]);

    return (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
            {content}
            {/* Card data source loading indicator */}
            {cardLoading && (
                <div style={{
                    position: 'absolute', top: 4, right: 4,
                    width: 8, height: 8, borderRadius: '50%',
                    background: '#3b82f6',
                    animation: 'pulse 1.5s ease-in-out infinite',
                }} />
            )}
            {/* Card data source error indicator */}
            {cardError && (
                <div style={{
                    position: 'absolute', bottom: 4, left: 4,
                    fontSize: 10, color: '#ef4444',
                    background: 'rgba(0,0,0,0.7)',
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
