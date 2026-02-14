import { memo, useMemo, useEffect, useRef, useState } from 'react';
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
import { getThemeTokens, ScreenThemeTokens } from '../screenThemes';

const LEGACY_LIGHT_TEXT_COLORS = new Set(["#fff", "#ffffff", "#e5e7eb", "#d1d5db", "#cbd5e1", "#94a3b8"]);

function resolveTextColor(candidate: string | undefined, fallback: string): string {
    if (!candidate || candidate.trim().length === 0) {
        return fallback;
    }
    const normalized = candidate.trim().toLowerCase();
    const fallbackNormalized = (fallback || "").trim().toLowerCase();
    if (LEGACY_LIGHT_TEXT_COLORS.has(normalized) && !LEGACY_LIGHT_TEXT_COLORS.has(fallbackNormalized)) {
        return fallback;
    }
    return candidate;
}

function normalizeParameterBindings(bindings: CardParameterBinding[] | undefined): CardParameterBinding[] {
    if (!Array.isArray(bindings)) return [];
    return bindings
        .map((item) => ({
            name: (item?.name ?? "").trim(),
            variableKey: (item?.variableKey ?? "").trim() || undefined,
            value: item?.value == null ? undefined : String(item.value),
        }))
        .filter((item) => item.name.length > 0);
}

function resolveInteractionValue(params: Record<string, unknown>, sourcePath: string): string | undefined {
    const path = (sourcePath || "").trim();
    if (!path) return undefined;

    const read = (obj: unknown, key: string): unknown => {
        if (!obj || typeof obj !== "object") return undefined;
        return (obj as Record<string, unknown>)[key];
    };

    const segments = path.split(".").filter((s) => s.length > 0);
    let current: unknown = params;
    for (const seg of segments) {
        current = read(current, seg);
    }

    if (current == null) return undefined;
    if (typeof current === "string") return current;
    if (typeof current === "number" || typeof current === "boolean") return String(current);
    return undefined;
}

/**
 * 自定义滚动表格，替代 DataV ScrollBoard（DataV 硬编码 color:#fff 无法覆盖）
 */
function ThemedScrollTable({ config, tokens }: {
    config: Record<string, unknown>;
    tokens: ScreenThemeTokens;
}) {
    const headers = config.header as string[] || [];
    const allData = config.data as string[][] || [];
    const rowNum = config.rowNum as number || 8;
    const headerBGC = config.headerBGC as string || tokens.scrollBoard.headerBg;
    const oddRowBGC = config.oddRowBGC as string || tokens.scrollBoard.oddRowBg;
    const evenRowBGC = config.evenRowBGC as string || tokens.scrollBoard.evenRowBg;
    const textColor = tokens.scrollBoard.textColor;
    const headerColor = resolveTextColor(config.headerColor as string | undefined, textColor);
    const headerHeight = 35;

    // Auto-scroll animation
    const [offset, setOffset] = useState(0);
    const rowHeight = 38;
    const visibleHeight = rowNum * rowHeight;
    const needScroll = allData.length > rowNum;

    useEffect(() => {
        if (!needScroll) return;
        const waitTime = config.waitTime as number || 2000;
        const timer = setInterval(() => {
            setOffset(prev => {
                const next = prev + 1;
                return next >= allData.length ? 0 : next;
            });
        }, waitTime);
        return () => clearInterval(timer);
    }, [needScroll, allData.length, config.waitTime]);

    // Build visible rows (wrap around for seamless scrolling)
    const visibleRows: { cells: string[]; originalIndex: number }[] = [];
    for (let i = 0; i < Math.min(rowNum + 1, allData.length); i++) {
        const idx = (offset + i) % allData.length;
        visibleRows.push({ cells: allData[idx], originalIndex: idx });
    }

    return (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', color: textColor, fontSize: 14 }}>
            {headers.length > 0 && (
                <div style={{
                    display: 'flex', background: headerBGC, height: headerHeight,
                    lineHeight: `${headerHeight}px`, fontWeight: 600, fontSize: 15, flexShrink: 0,
                    color: headerColor,
                }}>
                    {headers.map((h, i) => (
                        <div key={i} style={{
                            flex: 1, padding: '0 10px', textAlign: 'center',
                            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                        }}>{h}</div>
                    ))}
                </div>
            )}
            <div style={{ height: visibleHeight, overflow: 'hidden', position: 'relative' }}>
                <div style={{
                    transition: needScroll ? 'transform 0.5s ease' : 'none',
                    transform: needScroll ? `translateY(-${0}px)` : 'none',
                }}>
                    {visibleRows.map((row, ri) => (
                        <div key={`${offset}-${ri}`} style={{
                            display: 'flex', height: rowHeight, lineHeight: `${rowHeight}px`,
                            background: row.originalIndex % 2 === 0 ? evenRowBGC : oddRowBGC,
                        }}>
                            {row.cells.map((cell, ci) => (
                                <div key={ci} style={{
                                    flex: 1, padding: '0 10px', textAlign: 'center',
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                }}>{cell}</div>
                            ))}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

interface ColumnEntry {
    source: string;
    alias?: string;
}

function resolveBoundTableData(config: Record<string, unknown>): { header: string[]; data: string[][] } {
    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> | undefined;
    const columnsConfig = config.columns as ColumnEntry[] | undefined;
    const allData = (config.data as Array<Array<unknown>> | undefined) || [];

    if (columnsConfig && sourceCols?.length) {
        const header = columnsConfig.map((col) => {
            const sc = sourceCols.find((s) => s.name === col.source);
            return col.alias || sc?.displayName || col.source;
        });
        const data = allData.map((row) =>
            columnsConfig.map((col) => {
                const idx = sourceCols.findIndex((s) => s.name === col.source);
                return idx >= 0 ? String(row[idx] ?? '') : '';
            }),
        );
        return { header, data };
    }

    const rawHeader = (config.header as string[] | undefined) || [];
    const alias = config.columnAlias as Record<string, string> | undefined;
    const header = alias
        ? rawHeader.map((h, i) => alias[String(i)] || h)
        : rawHeader;
    const data = allData.map((row) => row.map((cell) => String(cell ?? '')));

    return { header, data };
}

interface ComponentRendererProps {
    component: ScreenComponent;
    mode?: 'designer' | 'preview';
    theme?: ScreenTheme;
    /** Callback to persist card-derived metadata (e.g. _sourceColumns) back to saved config */
    onConfigMeta?: (meta: Record<string, unknown>) => void;
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

export const ComponentRenderer = memo(function ComponentRenderer({ component, mode = 'preview', theme, onConfigMeta }: ComponentRendererProps) {
    const { type, config, width, height, dataSource, drillDown } = component;

    const runtime = useScreenRuntime();
    const t = useMemo(() => getThemeTokens(theme), [theme]);

    // Build ECharts base options from theme tokens
    const themeOptions = useMemo(() => ({
        backgroundColor: "transparent",
        color: t.echarts.colorPalette,
        textStyle: { color: t.textPrimary },
        legend: { textStyle: { color: t.textPrimary } },
        tooltip: {
            backgroundColor: t.echarts.tooltipBg,
            borderColor: t.echarts.tooltipBorder,
            textStyle: { color: t.textPrimary },
        },
    }), [t]);

    const cardBindings = useMemo(() => (
        dataSource?.type === "card"
            ? normalizeParameterBindings(dataSource.cardConfig?.parameterBindings)
            : []
    ), [dataSource]);

    const bindingParameters = useMemo(() => {
        if (!cardBindings.length) return [] as Array<{ name: string; value: string }>;
        const out: Array<{ name: string; value: string }> = [];
        for (const item of cardBindings) {
            let value = item.value ?? "";
            if (item.variableKey) {
                value = runtime.values[item.variableKey] ?? "";
            }
            if ((item.name || "").trim().length === 0) continue;
            out.push({ name: item.name, value: String(value ?? "") });
        }
        return out;
    }, [cardBindings, runtime.values]);

    // Drill-down state (only active in preview mode for drillable chart types)
    const drillActive = mode === "preview" && DRILLABLE_TYPES.has(type) && drillDown?.enabled === true;
    const rootCardId = dataSource?.type === "card" ? dataSource.cardConfig?.cardId : undefined;
    const drillState = useDrillDown(
        drillActive ? rootCardId : undefined,
        drillActive ? drillDown : undefined,
    );

    const mergedQueryParameters = useMemo(() => {
        const merged = new Map<string, string>();
        for (const item of bindingParameters) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        for (const item of (drillActive ? (drillState.queryParameters ?? []) : [])) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        return Array.from(merged.entries()).map(([name, value]) => ({ name, value }));
    }, [bindingParameters, drillActive, drillState.queryParameters]);

    const queryContext = useMemo(() => ({
        source: "screen-component",
        componentId: component.id,
        componentType: type,
        mode,
        globalVariables: runtime.values,
    }), [component.id, mode, runtime.values, type]);

    // Card data source hook — pass drill overrides when active
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(
        dataSource,
        drillActive ? drillState.effectiveCardId : undefined,
        mergedQueryParameters.length > 0 ? mergedQueryParameters : undefined,
        queryContext,
    );

    // Merge card data into config: card data overrides data fields only, not display fields
    const effectiveConfig = useMemo(() => {
        if (!cardData) return config;
        const mapped = mapCardDataToConfig(type, cardData);
        return { ...config, ...mapped };
    }, [config, cardData, type]);

    // Persist _sourceColumns to saved config so PropertyPanel can read them
    const onConfigMetaRef = useRef(onConfigMeta);
    onConfigMetaRef.current = onConfigMeta;

    const sourceColsKey = (config._sourceColumns as Array<{ name: string }> | undefined)
        ?.map(c => c.name).join(',');

    useEffect(() => {
        if (!onConfigMetaRef.current || !cardData?.cols?.length) return;
        const newCols = cardData.cols.map(c => ({ name: c.name, displayName: c.display_name || c.name }));
        const newKey = newCols.map(c => c.name).join(',');
        // Only update if columns actually changed (avoid infinite loop)
        if (sourceColsKey !== newKey) {
            onConfigMetaRef.current({ _sourceColumns: newCols });
        }
    }, [cardData, sourceColsKey]);

    // For datetime component, update every second
    const [currentTime, setCurrentTime] = useState(new Date());
    useEffect(() => {
        if (type === 'datetime') {
            const timer = setInterval(() => setCurrentTime(new Date()), 1000);
            return () => clearInterval(timer);
        }
    }, [type]);

    const interactionMappings = useMemo(() => (
        mode === "preview" && component.interaction?.enabled
            ? (component.interaction.mappings ?? []).filter((m): m is ComponentInteractionMapping => !!m && !!m.variableKey && !!m.sourcePath)
            : []
    ), [component.interaction, mode]);

    // ECharts click handler for drill-down + variable interaction
    const echartsClickHandler = useMemo(() => {
        const canDrill = drillActive && drillState.canDrillDown;
        const canInteract = interactionMappings.length > 0;
        if (!canDrill && !canInteract) return undefined;

        return {
            click: (params: Record<string, unknown>) => {
                if (canDrill) {
                    const value = (params.name as string | undefined)
                        ?? ((params.data as Record<string, unknown> | undefined)?.name as string | undefined);
                    if (value) drillState.handleDrill(String(value));
                }

                if (canInteract) {
                    for (const mapping of interactionMappings) {
                        const nextValue = resolveInteractionValue(params, mapping.sourcePath);
                        if (nextValue != null) {
                            runtime.setVariable(mapping.variableKey, nextValue);
                        }
                    }
                }
            },
        };
    }, [drillActive, drillState.canDrillDown, drillState.handleDrill, interactionMappings, runtime]);

    const content = useMemo(() => {
        const c = effectiveConfig;

        // Build legend config from legendPosition
        const legendPos = c.legendPosition as string;
        const legendConfig: Record<string, unknown> = {
            textStyle: { color: t.textPrimary },
            ...(legendPos === 'bottom' ? { top: 'auto', bottom: 0, left: 'center' } :
                legendPos === 'left' ? { left: 0, top: 'middle', orient: 'vertical' } :
                legendPos === 'right' ? { right: 0, top: 'middle', orient: 'vertical' } :
                {}),
        };

        switch (type) {
            // ==================== ECharts 图表 ====================
            case 'line-chart':
                return (
                    <ReactECharts
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
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
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
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
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
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
                                title: { show: true, offsetCenter: [0, '70%'], fontSize: (c.titleFontSize as number) || 14, color: t.gauge.titleColor },
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
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
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
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
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
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
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
                        background: (c.backgroundColor as string) || t.numberCard.background,
                        borderRadius: t.cardBorderRadius,
                        border: t.numberCard.border,
                        boxShadow: t.cardShadow,
                    }}>
                        <div style={{ fontSize: (c.titleFontSize as number) || 12, color: (c.titleColor as string) || t.numberCard.titleColor, marginBottom: 8 }}>
                            {c.title as string}
                        </div>
                        <div style={{ fontSize: (c.valueFontSize as number) || 32, fontWeight: 'bold', color: (c.valueColor as string) || t.numberCard.valueColor }}>
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
            case 'scroll-board': {
                const { header: displayHeader, data: displayData } = resolveBoundTableData(c);
                const filteredConfig = { ...c, header: displayHeader, data: displayData };

                // DataV ScrollBoard 硬编码 color:#fff 且无法通过 CSS/style 覆盖
                // 非 legacy-dark 主题使用自定义表格组件
                if (theme && theme !== 'legacy-dark') {
                    return <ThemedScrollTable config={filteredConfig} tokens={t} />;
                }
                return (
                    <ScrollBoard
                        config={{
                            header: displayHeader,
                            data: displayData,
                            rowNum: c.rowNum as number,
                            headerBGC: c.headerBGC as string,
                            oddRowBGC: c.oddRowBGC as string,
                            evenRowBGC: c.evenRowBGC as string,
                            waitTime: c.waitTime as number || 2000,
                            headerHeight: 35,
                            align: displayHeader.map(() => 'center'),
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );
            }

            case 'table': {
                const { header: displayHeader, data: displayData } = resolveBoundTableData(c);
                const fontSize = (c.fontSize as number) || 13;
                const headerColor = resolveTextColor(c.headerColor as string | undefined, t.textPrimary);
                const headerBackground = (c.headerBackground as string) || 'rgba(148, 163, 184, 0.16)';
                const bodyColor = resolveTextColor(c.bodyColor as string | undefined, t.textSecondary);
                const bodyBackground = (c.bodyBackground as string) || 'transparent';
                const borderColor = (c.borderColor as string) || 'rgba(148, 163, 184, 0.24)';
                const oddRowBackground = (c.oddRowBackground as string) || bodyBackground;
                const evenRowBackground = (c.evenRowBackground as string) || 'rgba(148, 163, 184, 0.06)';

                return (
                    <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed', fontSize }}>
                            {displayHeader.length > 0 && (
                                <thead>
                                    <tr style={{ background: headerBackground }}>
                                        {displayHeader.map((title, i) => (
                                            <th key={i} style={{
                                                color: headerColor,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                textAlign: 'left',
                                                fontWeight: 600,
                                                whiteSpace: 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                            }}>
                                                {title}
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                            )}
                            <tbody>
                                {displayData.map((row, rowIndex) => (
                                    <tr key={rowIndex} style={{ background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground }}>
                                        {row.map((cell, colIndex) => (
                                            <td key={colIndex} style={{
                                                color: bodyColor,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: colIndex < row.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                whiteSpace: 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                            }}>
                                                {cell}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
            }
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
    }, [type, effectiveConfig, width, height, currentTime, echartsClickHandler, t, theme, themeOptions]);

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
