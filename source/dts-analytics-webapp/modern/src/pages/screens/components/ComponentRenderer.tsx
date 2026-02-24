import { memo, useMemo, useEffect, useRef, useState, useCallback, type ComponentType, type MouseEvent as ReactMouseEvent } from 'react';
import type { CardData, ScreenComponent } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { useDrillDown } from '../hooks/useDrillDown';
import { useScreenRuntime } from '../ScreenRuntimeContext';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';
import { applyFieldMapping } from '../hooks/fieldMappingTransform';
import type { ChartMarkArea, ChartMarkLine, FieldMapping, SeriesConditionalColor } from '../types';
import { getThemeTokens } from '../screenThemes';
import { isSafeSrcUrl } from '../sanitize';
import { PluginRenderBoundary } from '../plugins/PluginRenderBoundary';
import { getRendererPlugin } from '../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../plugins/runtime';
import { useScreenPluginRuntime } from '../plugins/useScreenPluginRuntime';
import type { RendererPlugin } from '../plugins/types';
import type { ReactEChartsComponent, DataViewModule, ComponentRendererProps } from '../renderers/types';
import { resolvePresetMapUrl, fetchGeoJsonWithCache } from '../renderers/shared/geoJsonCache';
import { renderMarkdownToHtml } from '../renderers/shared/markdownUtils';
import {
    resolveTextColor, estimateVisualTextWidth, truncateTextByVisualWidth,
    normalizeParameterBindings, resolveDataSourceType,
    resolveInteractionValue, resolveInteractionMappedValue,
    resolveInteractionUrlTemplate, resolveFilterOptions, resolveTabOptions,
    resolveComponentVariableVisibility, resolveFilterOptionsFromData,
    normalizeFilterDebounceMs, normalizeCarouselItems, resolveCarouselItemsFromData,
} from '../renderers/shared/chartUtils';
import type { ComponentInteractionMapping } from '../types';
import {
    compareTableValues, resolveTableConditionalStyle,
    normalizeColumnAlign, formatTableCell, clampColumnWidth, normalizeColumnFormatter,
    ThemedScrollTable, resolveBoundTableData,
} from '../renderers/shared/tableUtils';

const ECHART_COMPONENT_TYPES = new Set([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'gauge-chart',
    'radar-chart',
    'funnel-chart',
    'scatter-chart',
    'map-chart',
    'combo-chart',
    'wordcloud-chart',
    'treemap-chart',
    'sunburst-chart',
    'waterfall-chart',
    'globe-chart',
    'bar3d-chart',
    'scatter3d-chart',
]);

const ECHART_3D_TYPES = new Set(['globe-chart', 'bar3d-chart', 'scatter3d-chart']);

function isWebGLSupported(): boolean {
    try {
        const canvas = document.createElement('canvas');
        return !!(canvas.getContext('webgl') || canvas.getContext('webgl2'));
    } catch {
        return false;
    }
}

// ── Chart annotation injection ──
const ANNOTATABLE_TYPES = new Set(['line-chart', 'bar-chart', 'scatter-chart', 'combo-chart', 'waterfall-chart']);

function injectChartAnnotations(
    option: Record<string, unknown>,
    config: Record<string, unknown>,
): Record<string, unknown> {
    const markLines = config.markLines as ChartMarkLine[] | undefined;
    const markAreas = config.markAreas as ChartMarkArea[] | undefined;
    const conditionalColors = config.conditionalColors as SeriesConditionalColor[] | undefined;

    if ((!markLines || markLines.length === 0) && (!markAreas || markAreas.length === 0) && (!conditionalColors || conditionalColors.length === 0)) {
        return option;
    }

    const series = option.series as Array<Record<string, unknown>> | undefined;
    if (!Array.isArray(series) || series.length === 0) return option;

    // Build ECharts markLine data
    const markLineData: Array<Record<string, unknown>> = [];
    if (markLines) {
        for (const ml of markLines) {
            if (ml.type === 'value' && ml.value != null) {
                const item: Record<string, unknown> = {
                    name: ml.name ?? `${ml.value}`,
                    label: { formatter: ml.name ?? `${ml.value}`, position: 'insideEndTop' },
                    lineStyle: { color: ml.color ?? '#ff6b6b', type: ml.lineStyle ?? 'dashed' },
                };
                if (ml.axis === 'x') {
                    item.xAxis = ml.value;
                } else {
                    item.yAxis = ml.value;
                }
                markLineData.push(item);
            } else if (ml.type === 'average' || ml.type === 'min' || ml.type === 'max') {
                markLineData.push({
                    type: ml.type,
                    name: ml.name ?? ml.type,
                    label: { formatter: ml.name ?? ml.type, position: 'insideEndTop' },
                    lineStyle: { color: ml.color ?? '#facc15', type: ml.lineStyle ?? 'dashed' },
                });
            }
        }
    }

    // Build ECharts markArea data
    const markAreaData: Array<Array<Record<string, unknown>>> = [];
    if (markAreas) {
        for (const ma of markAreas) {
            const start: Record<string, unknown> = { name: ma.name ?? '' };
            const end: Record<string, unknown> = {};
            if (ma.axis === 'x') {
                start.xAxis = ma.from;
                end.xAxis = ma.to;
            } else {
                start.yAxis = ma.from;
                end.yAxis = ma.to;
            }
            start.itemStyle = { color: ma.color ?? 'rgba(255, 107, 107, 0.15)' };
            markAreaData.push([start, end]);
        }
    }

    // Build conditional color function
    let colorFn: ((params: { value: unknown }) => string) | undefined;
    if (conditionalColors && conditionalColors.length > 0) {
        colorFn = (params: { value: unknown }) => {
            const val = typeof params.value === 'number' ? params.value : (Array.isArray(params.value) ? Number(params.value[1]) : Number(params.value));
            for (const rule of conditionalColors) {
                let match = false;
                switch (rule.operator) {
                    case '>': match = val > rule.value; break;
                    case '>=': match = val >= rule.value; break;
                    case '<': match = val < rule.value; break;
                    case '<=': match = val <= rule.value; break;
                    case '==': match = val === rule.value; break;
                    case 'between': match = val >= rule.value && val <= (rule.valueTo ?? rule.value); break;
                }
                if (match) return rule.color;
            }
            return '';
        };
    }

    // Inject into first series (markLine/markArea) and all series (conditionalColors)
    const patched = series.map((s, idx) => {
        const result = { ...s };
        if (idx === 0) {
            if (markLineData.length > 0) {
                result.markLine = { symbol: ['none', 'arrow'], data: markLineData, silent: true };
            }
            if (markAreaData.length > 0) {
                result.markArea = { data: markAreaData, silent: true };
            }
        }
        if (colorFn) {
            result.itemStyle = { ...(result.itemStyle as Record<string, unknown> || {}), color: colorFn };
        }
        return result;
    });

    return { ...option, series: patched };
}

const DATAV_COMPONENT_TYPES = new Set([
    'border-box',
    'decoration',
    'scroll-board',
    'scroll-ranking',
    'water-level',
    'digital-flop',
]);

// Utility functions, table components, and types extracted to renderers/shared/:
// - chartUtils.ts, tableUtils.tsx, markdownUtils.ts, geoJsonCache.ts

export const ComponentRenderer = memo(function ComponentRenderer({ component, mode = 'preview', theme, onConfigMeta }: ComponentRendererProps) {
    const { type, config, width, height, dataSource, drillDown } = component;

    const runtime = useScreenRuntime();
    const pluginRuntimeVersion = useScreenPluginRuntime();
    const t = useMemo(() => getThemeTokens(theme), [theme]);
    const pluginMeta = useMemo(() => readComponentPluginMeta(config), [config]);
    const runtimePlugin = useMemo<RendererPlugin | null>(() => {
        const runtimeId = resolveRuntimePluginId(pluginMeta);
        if (!runtimeId) return null;
        return getRendererPlugin(runtimeId) ?? null;
    }, [pluginMeta, pluginRuntimeVersion]);

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

    const needsECharts = ECHART_COMPONENT_TYPES.has(type);
    const needsDataV = DATAV_COMPONENT_TYPES.has(type);
    const [EChartsComponent, setEChartsComponent] = useState<ReactEChartsComponent | null>(null);
    const [registerMapFn, setRegisterMapFn] = useState<((mapName: string, geoJson: unknown) => boolean) | null>(null);
    const [hasMapFn, setHasMapFn] = useState<((mapName: string) => boolean) | null>(null);
    const [dataViewModule, setDataViewModule] = useState<DataViewModule | null>(null);
    const [mapDrillRegion, setMapDrillRegion] = useState<string | null>(null);
    const [mapReadyVersion, setMapReadyVersion] = useState(0);
    const [tableSort, setTableSort] = useState<{ colIndex: number; order: 'asc' | 'desc' } | null>(null);
    const [tablePage, setTablePage] = useState(1);

    useEffect(() => {
        if (!needsECharts || EChartsComponent) return;
        let cancelled = false;
        const imports: Promise<unknown>[] = [
            import('../../../components/charts/EChartsRuntime'),
        ];
        if (type === 'wordcloud-chart') {
            imports.push(import('echarts-wordcloud'));
        }
        if (ECHART_3D_TYPES.has(type)) {
            // echarts-gl is an optional peer dep — use variable to bypass Vite static analysis
            const glPkg = 'echarts-gl';
            imports.push(import(/* @vite-ignore */ glPkg).catch(() => null));
        }
        Promise.all(imports).then(([echartsModule]) => {
            const mod = echartsModule as typeof import('../../../components/charts/EChartsRuntime');
            if (!cancelled) {
                setEChartsComponent(() => mod.default as ReactEChartsComponent);
                if (typeof mod.registerEChartsMap === 'function') {
                    setRegisterMapFn(() => mod.registerEChartsMap);
                }
                if (typeof mod.hasEChartsMap === 'function') {
                    setHasMapFn(() => mod.hasEChartsMap);
                }
            }
        });
        return () => {
            cancelled = true;
        };
    }, [needsECharts, EChartsComponent, type]);

    useEffect(() => {
        if (type !== 'map-chart' || !registerMapFn) return;
        const cfg = config as Record<string, unknown>;
        const mapName = String(cfg.mapName || cfg.mapScope || 'dts-map').trim();
        if (!mapName) return;
        const geoJson = cfg.geoJson;
        const geoJsonUrlRaw = typeof cfg.geoJsonUrl === 'string' ? cfg.geoJsonUrl.trim() : '';
        const presetAllowed = cfg.usePresetGeoJson !== false;
        const presetUrl = presetAllowed ? resolvePresetMapUrl(String(cfg.mapScope || 'china')) : undefined;
        const geoJsonUrl = geoJsonUrlRaw || presetUrl || '';
        let cancelled = false;

        if (geoJson && typeof geoJson === 'object') {
            if (registerMapFn(mapName, geoJson)) {
                setMapReadyVersion((v) => v + 1);
            }
            return;
        }

        if (!geoJsonUrl) {
            return;
        }

        fetchGeoJsonWithCache(geoJsonUrl).then((loaded) => {
            if (cancelled || !loaded || typeof loaded !== 'object') return;
            if (registerMapFn(mapName, loaded)) {
                setMapReadyVersion((v) => v + 1);
            }
        });

        return () => {
            cancelled = true;
        };
    }, [config, registerMapFn, type]);

    useEffect(() => {
        if (!needsDataV || dataViewModule) return;
        let cancelled = false;
        import('@jiaminghi/data-view-react').then((mod) => {
            if (!cancelled) {
                setDataViewModule(mod);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [needsDataV, dataViewModule]);

    useEffect(() => {
        setMapDrillRegion(null);
        setTableSort(null);
        setTablePage(1);
    }, [component.id]);

    const borderBoxComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.BorderBox1,
            2: dataViewModule.BorderBox2,
            3: dataViewModule.BorderBox3,
            4: dataViewModule.BorderBox4,
            5: dataViewModule.BorderBox5,
            6: dataViewModule.BorderBox6,
            7: dataViewModule.BorderBox7,
            8: dataViewModule.BorderBox8,
            9: dataViewModule.BorderBox9,
            10: dataViewModule.BorderBox10,
            11: dataViewModule.BorderBox11,
            12: dataViewModule.BorderBox12,
            13: dataViewModule.BorderBox13,
        } as Record<number, ComponentType<{ children?: React.ReactNode; color?: string[] }>>;
    }, [dataViewModule]);

    const decorationComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.Decoration1,
            2: dataViewModule.Decoration2,
            3: dataViewModule.Decoration3,
            4: dataViewModule.Decoration4,
            5: dataViewModule.Decoration5,
            6: dataViewModule.Decoration6,
            7: dataViewModule.Decoration7,
            8: dataViewModule.Decoration8,
            9: dataViewModule.Decoration9,
            10: dataViewModule.Decoration10,
            11: dataViewModule.Decoration11,
            12: dataViewModule.Decoration12,
        } as Record<number, ComponentType<{ color?: string[]; style?: React.CSSProperties }>>;
    }, [dataViewModule]);

    const dataSourceType = useMemo(() => resolveDataSourceType(dataSource), [dataSource]);
    const sourceBindings = useMemo(() => {
        if (dataSourceType === "card") {
            return normalizeParameterBindings(dataSource?.cardConfig?.parameterBindings);
        }
        if (dataSourceType === "metric") {
            return normalizeParameterBindings(dataSource?.metricConfig?.parameterBindings);
        }
        if (dataSourceType === "sql") {
            const sqlConfig = dataSource?.sqlConfig ?? dataSource?.databaseConfig;
            return normalizeParameterBindings(sqlConfig?.parameterBindings);
        }
        return [];
    }, [dataSource, dataSourceType]);

    const bindingParameters = useMemo(() => {
        if (!sourceBindings.length) return [] as Array<{ name: string; value: string }>;
        const out: Array<{ name: string; value: string }> = [];
        for (const item of sourceBindings) {
            let value = item.value ?? "";
            if (item.variableKey) {
                value = runtime.values[item.variableKey] ?? "";
            }
            if ((item.name || "").trim().length === 0) continue;
            out.push({ name: item.name, value: String(value ?? "") });
        }
        return out;
    }, [sourceBindings, runtime.values]);

    // Drill-down state (only active in preview mode for drillable chart types)
    const drillActive = mode === "preview" && DRILLABLE_TYPES.has(type) && drillDown?.enabled === true;
    const rootCardId = dataSourceType === "card" ? dataSource?.cardConfig?.cardId : undefined;
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
    const visibleByVariableRule = mode !== 'preview'
        || resolveComponentVariableVisibility(config, runtime.values);

    // Card data source hook — pass drill overrides when active
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(
        visibleByVariableRule ? dataSource : undefined,
        drillActive ? drillState.effectiveCardId : undefined,
        mergedQueryParameters.length > 0 ? mergedQueryParameters : undefined,
        queryContext,
    );

    // Merge card data into config: card data overrides data fields only, not display fields
    const effectiveConfig = useMemo(() => {
        if (!cardData) return config;
        const fieldMapping = config._fieldMapping as FieldMapping | undefined;
        const useFieldMapping = config._useFieldMapping !== false && fieldMapping
            && (fieldMapping.dimension || (fieldMapping.measures && fieldMapping.measures.length > 0));
        if (useFieldMapping) {
            const mapped = applyFieldMapping(type, fieldMapping, cardData);
            return { ...config, ...mapped };
        }
        const mapped = mapCardDataToConfig(type, cardData);
        return { ...config, ...mapped };
    }, [config, cardData, type]);

    // Persist _sourceColumns to saved config so PropertyPanel can read them
    const onConfigMetaRef = useRef(onConfigMeta);
    onConfigMetaRef.current = onConfigMeta;
    const [legendDragPreview, setLegendDragPreview] = useState<{ x: number; y: number } | null>(null);
    const legendDragHandlersRef = useRef<{ move: (event: MouseEvent) => void; up: (event: MouseEvent) => void } | null>(null);
    const [chartDragPreview, setChartDragPreview] = useState<{ x: number; y: number } | null>(null);
    const chartDragHandlersRef = useRef<{ move: (event: MouseEvent) => void; up: (event: MouseEvent) => void } | null>(null);

    const clearLegendDragHandlers = useCallback(() => {
        const handlers = legendDragHandlersRef.current;
        if (!handlers) return;
        window.removeEventListener('mousemove', handlers.move);
        window.removeEventListener('mouseup', handlers.up);
        legendDragHandlersRef.current = null;
    }, []);

    const clearChartDragHandlers = useCallback(() => {
        const handlers = chartDragHandlersRef.current;
        if (!handlers) return;
        window.removeEventListener('mousemove', handlers.move);
        window.removeEventListener('mouseup', handlers.up);
        chartDragHandlersRef.current = null;
    }, []);

    useEffect(() => () => {
        clearLegendDragHandlers();
        clearChartDragHandlers();
    }, [clearChartDragHandlers, clearLegendDragHandlers]);

    useEffect(() => {
        setLegendDragPreview(null);
        setChartDragPreview(null);
        clearLegendDragHandlers();
        clearChartDragHandlers();
    }, [clearChartDragHandlers, clearLegendDragHandlers, component.id]);

    const sourceColsKey = (config._sourceColumns as Array<{ name: string }> | undefined)
        ?.map(c => c.name).join(',');

    useEffect(() => {
        if (!onConfigMetaRef.current || !cardData?.cols?.length) return;
        const newCols = cardData.cols.map((c) => ({
            name: c.name,
            displayName: c.display_name || c.name,
            baseType: c.base_type,
        }));
        const newKey = newCols.map(c => c.name).join(',');
        // Only update if columns actually changed (avoid infinite loop)
        if (sourceColsKey !== newKey) {
            onConfigMetaRef.current({ _sourceColumns: newCols });
        }
    }, [cardData, sourceColsKey]);

    // For datetime component, update every second
    const [currentTime, setCurrentTime] = useState(new Date());
    useEffect(() => {
        if (type === 'datetime' || type === 'countdown') {
            const timer = setInterval(() => setCurrentTime(new Date()), 1000);
            return () => clearInterval(timer);
        }
    }, [type]);

    const [carouselIndex, setCarouselIndex] = useState(0);
    const [carouselPaused, setCarouselPaused] = useState(false);
    const carouselItems = useMemo(() => {
        if (type !== 'carousel') return [];
        const sourceMode = String(effectiveConfig.itemSourceMode ?? 'auto').trim().toLowerCase();
        const dataItems = resolveCarouselItemsFromData(cardData, effectiveConfig);
        const manualItems = normalizeCarouselItems(effectiveConfig.items);
        if (sourceMode === 'data') {
            return dataItems;
        }
        if (sourceMode === 'manual') {
            return manualItems;
        }
        if (dataItems.length > 0) {
            return dataItems;
        }
        return manualItems;
    }, [cardData, effectiveConfig.dataItemField, effectiveConfig.dataItemMax, effectiveConfig.itemSourceMode, effectiveConfig.items, type]);

    useEffect(() => {
        if (type !== 'carousel') return;
        if (carouselItems.length <= 1) {
            setCarouselIndex(0);
            return;
        }
        const autoPlay = effectiveConfig.autoPlay !== false;
        if (!autoPlay) {
            return;
        }
        const pauseOnHover = effectiveConfig.pauseOnHover !== false;
        if (pauseOnHover && carouselPaused) {
            return;
        }
        const rawSeconds = Number(effectiveConfig.intervalSeconds ?? 4);
        const safeSeconds = Number.isFinite(rawSeconds)
            ? Math.max(1, Math.min(120, Math.floor(rawSeconds)))
            : 4;
        const timer = setInterval(() => {
            setCarouselIndex((prev) => (prev + 1) % carouselItems.length);
        }, safeSeconds * 1000);
        return () => clearInterval(timer);
    }, [carouselItems.length, carouselPaused, effectiveConfig.autoPlay, effectiveConfig.intervalSeconds, effectiveConfig.pauseOnHover, type]);

    const filterInputVariableKey = useMemo(() => {
        if (type !== 'filter-input') return '';
        return String((effectiveConfig.variableKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const filterInputRuntimeValue = filterInputVariableKey ? (runtime.values[filterInputVariableKey] ?? '') : '';
    const [filterInputDraft, setFilterInputDraft] = useState(filterInputRuntimeValue);
    const filterVariableTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

    useEffect(() => {
        setFilterInputDraft(filterInputRuntimeValue);
    }, [filterInputRuntimeValue, filterInputVariableKey]);

    useEffect(() => () => {
        for (const timer of filterVariableTimersRef.current.values()) {
            clearTimeout(timer);
        }
        filterVariableTimersRef.current.clear();
    }, []);

    const tabVariableKey = useMemo(() => {
        if (type !== 'tab-switcher') return '';
        return String((effectiveConfig.variableKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const tabOptions = useMemo(() => {
        if (type !== 'tab-switcher') return [];
        const sourceMode = String(effectiveConfig.optionSourceMode ?? 'manual').trim().toLowerCase();
        if (sourceMode === 'data') {
            const dynamicOptions = resolveFilterOptionsFromData(cardData, effectiveConfig);
            if (dynamicOptions.length > 0) {
                return dynamicOptions;
            }
        }
        return resolveTabOptions(effectiveConfig.options);
    }, [cardData, effectiveConfig, type]);
    const tabDefaultValue = String(effectiveConfig.defaultValue ?? '').trim();
    const tabRuntimeValue = tabVariableKey ? String(runtime.values[tabVariableKey] ?? '') : '';

    useEffect(() => {
        if (type !== 'tab-switcher' || !tabVariableKey || tabOptions.length <= 0) return;
        if (tabRuntimeValue) return;
        const fallbackValue = tabDefaultValue && tabOptions.some((item) => item.value === tabDefaultValue)
            ? tabDefaultValue
            : tabOptions[0]?.value;
        if (fallbackValue) {
            runtime.setVariable(tabVariableKey, fallbackValue, `tab-switcher:init:${component.id}`);
        }
    }, [component.id, runtime, tabDefaultValue, tabOptions, tabRuntimeValue, tabVariableKey, type]);

    const scheduleFilterVariableUpdate = (
        key: string,
        value: string,
        source: string,
        debounceMsRaw: unknown,
        immediate = false,
    ) => {
        const safeKey = String(key || '').trim();
        if (!safeKey) return;
        const debounceMs = normalizeFilterDebounceMs(debounceMsRaw);
        const currentTimer = filterVariableTimersRef.current.get(safeKey);
        if (currentTimer) {
            clearTimeout(currentTimer);
            filterVariableTimersRef.current.delete(safeKey);
        }
        if (immediate || debounceMs <= 0) {
            runtime.setVariable(safeKey, value, source);
            return;
        }
        const timer = setTimeout(() => {
            filterVariableTimersRef.current.delete(safeKey);
            runtime.setVariable(safeKey, value, `${source}:debounced`);
        }, debounceMs);
        filterVariableTimersRef.current.set(safeKey, timer);
    };

    const interactionMappings = useMemo(() => (
        mode === "preview" && component.interaction?.enabled
            ? (component.interaction.mappings ?? []).filter((m): m is ComponentInteractionMapping => !!m && !!m.variableKey && !!m.sourcePath)
            : []
    ), [component.interaction, mode]);
    const interactionJump = useMemo(() => {
        if (mode !== 'preview' || component.interaction?.enabled !== true || component.interaction?.jumpEnabled !== true) {
            return null;
        }
        const template = String(component.interaction.jumpUrlTemplate || '').trim();
        if (!template) return null;
        return {
            template,
            openMode: component.interaction.jumpOpenMode === 'self' ? 'self' : 'new-tab',
        };
    }, [component.interaction, mode]);

    // ECharts click handler for drill-down + variable interaction
    const echartsClickHandler = useMemo(() => {
        const canDrill = drillActive && drillState.canDrillDown;
        const canInteract = interactionMappings.length > 0;
        const canJump = !!interactionJump;
        if (!canDrill && !canInteract && !canJump) return undefined;

        return {
            click: (params: Record<string, unknown>) => {
                if (canDrill) {
                    const value = (params.name as string | undefined)
                        ?? ((params.data as Record<string, unknown> | undefined)?.name as string | undefined);
                    if (value) {
                        const clicked = String(value);
                        runtime.trackEvent({
                            kind: 'drill-down',
                            key: 'drillValue',
                            value: clicked,
                            source: `drill:${component.id}`,
                            meta: `depth=${drillState.breadcrumbs.length}`,
                        });
                        drillState.handleDrill(clicked);
                    }
                }

                if (canInteract) {
                    for (const mapping of interactionMappings) {
                        const rawNextValue = resolveInteractionValue(params, mapping.sourcePath);
                        const nextValue = resolveInteractionMappedValue(rawNextValue, mapping);
                        if (nextValue != null) {
                            runtime.setVariable(mapping.variableKey, nextValue, `interaction:${component.id}`);
                        }
                    }
                }

                if (canJump && interactionJump) {
                    const targetUrl = resolveInteractionUrlTemplate(interactionJump.template, params);
                    if (!targetUrl) return;
                    runtime.trackEvent({
                        kind: 'jump',
                        key: 'jumpUrl',
                        value: targetUrl,
                        source: `interaction:${component.id}`,
                        meta: `openMode=${interactionJump.openMode}`,
                    });
                    if (interactionJump.openMode === 'self') {
                        // Only allow same-origin navigation for 'self' to prevent open redirect
                        if (targetUrl.startsWith('/') || (() => { try { return new URL(targetUrl).origin === window.location.origin; } catch { return false; } })()) {
                            window.location.assign(targetUrl);
                        }
                    } else {
                        window.open(targetUrl, '_blank', 'noopener,noreferrer');
                    }
                }
            },
        };
    }, [
        component.id,
        drillActive,
        drillState.breadcrumbs.length,
        drillState.canDrillDown,
        drillState.handleDrill,
        interactionJump,
        interactionMappings,
        runtime,
    ]);

    const content = useMemo(() => {
        const c = effectiveConfig;
        if (runtimePlugin) {
            return (
                <PluginRenderBoundary title={`插件渲染失败: ${runtimePlugin.name}`}>
                    {runtimePlugin.render({
                        component,
                        mode,
                        theme,
                        width,
                        height,
                        config: c,
                        data: cardData,
                        runtimeValues: runtime.values,
                        setVariable: (key, value) => runtime.setVariable(key, value, `plugin:${runtimePlugin.id}`),
                    })}
                </PluginRenderBoundary>
            );
        }
        const axisFontSize = (c.axisFontSize as number) || 12;
        const legendFontSize = (c.legendFontSize as number) || 12;
        const seriesColors = Array.isArray(c.seriesColors)
            ? (c.seriesColors as string[]).filter((color) => typeof color === 'string' && color.trim().length > 0)
            : [];
        const toNumber = (raw: unknown, fallback: number, min: number, max: number) => {
            const parsed = Number(raw);
            if (!Number.isFinite(parsed)) return fallback;
            return Math.min(max, Math.max(min, parsed));
        };
        const readPaddingOverride = (key: 'chartPaddingTop' | 'chartPaddingRight' | 'chartPaddingBottom' | 'chartPaddingLeft') => {
            const parsed = Number(c[key]);
            if (!Number.isFinite(parsed) || parsed <= 0) return undefined;
            return Math.round(Math.max(0, parsed));
        };
        const compactPresetRaw = String(c.compactLayoutPreset ?? 'auto').trim().toLowerCase();
        const compactPresetEnabled = compactPresetRaw !== 'off';
        const isCompactCanvas = compactPresetEnabled && (width < 560 || height < 320);
        const isTinyCanvas = compactPresetEnabled && (width < 420 || height < 260);
        const titleText = String(c.title ?? '').trim();
        const hasTitle = titleText.length > 0;
        const xAxisData = Array.isArray(c.xAxisData) ? (c.xAxisData as unknown[]) : [];
        const xAxisCategoryCount = xAxisData.length;
        const longestXAxisLabelLength = xAxisData.reduce<number>(
            (max, item) => Math.max(max, String(item ?? '').trim().length),
            0,
        );
        const xAxisLabelRotateRaw = Number(c.xAxisLabelRotate);
        const autoXAxisLabelRotate = isCompactCanvas && (xAxisCategoryCount >= 7 || longestXAxisLabelLength >= 8)
            ? (isTinyCanvas ? -45 : -30)
            : 0;
        const xAxisLabelRotate = Number.isFinite(xAxisLabelRotateRaw)
            ? toNumber(c.xAxisLabelRotate, 0, -90, 90)
            : autoXAxisLabelRotate;
        const xAxisLabelMaxLengthRaw = Number(c.xAxisLabelMaxLength);
        const autoXAxisLabelMaxLength = isCompactCanvas ? (isTinyCanvas ? 8 : 12) : 0;
        const xAxisLabelMaxLength = Number.isFinite(xAxisLabelMaxLengthRaw)
            ? Math.round(Math.min(40, Math.max(0, xAxisLabelMaxLengthRaw)))
            : (longestXAxisLabelLength > autoXAxisLabelMaxLength ? autoXAxisLabelMaxLength : 0);
        const formatXAxisLabel = (value: unknown) => {
            const text = String(value ?? '');
            if (xAxisLabelMaxLength <= 0 || text.length <= xAxisLabelMaxLength) {
                return text;
            }
            const keep = Math.max(1, xAxisLabelMaxLength);
            return `${text.slice(0, keep)}...`;
        };
        const legendNames = (() => {
            const out: string[] = [];
            const seen = new Set<string>();
            const push = (raw: unknown) => {
                const text = String(raw ?? '').trim();
                if (!text || seen.has(text)) return;
                seen.add(text);
                out.push(text);
            };
            if (Array.isArray(c.series)) {
                for (const item of c.series as Array<Record<string, unknown>>) {
                    push(item?.name);
                }
            }
            if (Array.isArray(c.data)) {
                for (const item of c.data as Array<Record<string, unknown>>) {
                    push(item?.name);
                }
            }
            return out;
        })();
        const legendCount = legendNames.length;
        const legendDisplayRaw = String(c.legendDisplay ?? 'auto').trim().toLowerCase();
        const legendDisplayMode = legendDisplayRaw === 'show' || legendDisplayRaw === 'hide' ? legendDisplayRaw : 'auto';
        const longestLegendTextWidth = legendNames.reduce(
            (max, name) => Math.max(max, estimateVisualTextWidth(name, legendFontSize)),
            0,
        );
        const autoHideLegendForDensity = isTinyCanvas
            && legendCount >= 16
            && longestLegendTextWidth >= 90;
        const legendVisibleByAuto = !autoHideLegendForDensity && (legendCount > 1 || !isCompactCanvas);
        const legendVisible = legendDisplayMode === 'show'
            ? true
            : (legendDisplayMode === 'hide' ? false : legendVisibleByAuto);
        const autoLegendAvoid = c.autoLegendAvoid !== false;
        const legendPosRaw = String(c.legendPosition ?? 'auto').trim().toLowerCase();
        const legendPosMode = legendPosRaw === 'top'
            || legendPosRaw === 'bottom'
            || legendPosRaw === 'left'
            || legendPosRaw === 'right'
            || legendPosRaw === 'auto'
            ? legendPosRaw
            : 'auto';
        let legendPosition: 'top' | 'bottom' | 'left' | 'right' = (() => {
            if (legendPosMode !== 'auto') {
                return legendPosMode;
            }
            if (isTinyCanvas) {
                return width >= height ? 'bottom' : 'right';
            }
            if (isCompactCanvas) {
                return legendCount >= 8
                    ? (width >= height ? 'bottom' : 'right')
                    : (width >= height ? 'top' : 'right');
            }
            return width >= height ? 'top' : 'right';
        })();
        if (legendVisible && autoLegendAvoid && isCompactCanvas && (legendPosition === 'left' || legendPosition === 'right')) {
            legendPosition = width >= height ? 'top' : 'bottom';
        }
        if (legendVisible && autoLegendAvoid && isTinyCanvas && legendPosition === 'top' && legendCount >= 8) {
            legendPosition = 'bottom';
        }
        const legendOrientRaw = String(c.legendOrient ?? 'auto').trim().toLowerCase();
        const legendOrient = legendOrientRaw === 'horizontal' || legendOrientRaw === 'vertical'
            ? legendOrientRaw
            : ((legendPosition === 'left' || legendPosition === 'right') ? 'vertical' : 'horizontal');
        const legendAlignRaw = String(c.legendAlign ?? 'auto').trim().toLowerCase();
        const legendAlign = legendAlignRaw === 'start' || legendAlignRaw === 'center' || legendAlignRaw === 'end'
            ? legendAlignRaw
            : 'auto';
        const legendItemGap = toNumber(c.legendItemGap, 12, 0, 80);
        const legendReserveOverrideRaw = Number(c.legendReserveSize);
        const legendReserveOverride = Number.isFinite(legendReserveOverrideRaw) && legendReserveOverrideRaw > 0
            ? Math.round(Math.min(360, Math.max(20, legendReserveOverrideRaw)))
            : undefined;
        const legendOffsetBoundX = Math.max(120, Math.round(width * 0.5));
        const legendOffsetBoundY = Math.max(120, Math.round(height * 0.5));
        const legendOffsetXBase = toNumber(c.legendOffsetX, 0, -legendOffsetBoundX, legendOffsetBoundX);
        const legendOffsetYBase = toNumber(c.legendOffsetY, 0, -legendOffsetBoundY, legendOffsetBoundY);
        const legendOffsetX = legendDragPreview ? legendDragPreview.x : legendOffsetXBase;
        const legendOffsetY = legendDragPreview ? legendDragPreview.y : legendOffsetYBase;
        const legendNameMaxWidthRaw = Number(c.legendNameMaxWidth);
        const legendNameMaxWidthOverride = Number.isFinite(legendNameMaxWidthRaw) && legendNameMaxWidthRaw > 0
            ? Math.round(Math.min(320, Math.max(40, legendNameMaxWidthRaw)))
            : undefined;
        const chartOffsetBoundX = Math.max(40, Math.round(width * 0.45));
        const chartOffsetBoundY = Math.max(40, Math.round(height * 0.45));
        const axisChartOffsetXBase = toNumber(c.chartOffsetX, 0, -chartOffsetBoundX, chartOffsetBoundX);
        const axisChartOffsetYBase = toNumber(c.chartOffsetY, 0, -chartOffsetBoundY, chartOffsetBoundY);
        const chartOffsetX = chartDragPreview ? chartDragPreview.x : axisChartOffsetXBase;
        const chartOffsetY = chartDragPreview ? chartDragPreview.y : axisChartOffsetYBase;
        const legendTextMaxWidth = (() => {
            if (!legendVisible || legendCount <= 0) return 0;
            if (legendNameMaxWidthOverride) {
                return legendNameMaxWidthOverride;
            }
            if (legendPosition === 'left' || legendPosition === 'right') {
                return Math.max(56, Math.min(220, Math.floor(width * 0.32)));
            }
            const slots = Math.max(1, Math.min(legendCount, isTinyCanvas ? 2 : (isCompactCanvas ? 3 : 4)));
            return Math.max(56, Math.min(240, Math.floor((width - 24) / slots) - 28));
        })();
        const shouldTruncateLegend = legendVisible
            && autoLegendAvoid
            && legendTextMaxWidth > 0
            && (isCompactCanvas || longestLegendTextWidth > legendTextMaxWidth + 8);
        const formatLegendText = (name: string) => {
            if (!shouldTruncateLegend) {
                return name;
            }
            return truncateTextByVisualWidth(String(name ?? ''), legendTextMaxWidth, legendFontSize);
        };
        const estimateHorizontalLegendReserve = () => {
            if (!legendVisible || legendCount <= 0) return 0;
            const safeWidth = Math.max(180, width - 24);
            const perItemWidth = Math.max(64, Math.min(280, Math.max(Math.round(legendFontSize * 5.8), longestLegendTextWidth + 26)));
            const itemsPerRow = Math.max(1, Math.floor(safeWidth / perItemWidth));
            const rowCount = Math.max(1, Math.ceil(Math.max(legendCount, 1) / itemsPerRow));
            const rowHeight = Math.max(18, legendFontSize + 8);
            const reserve = rowCount * rowHeight + 8;
            return Math.min(Math.max(40, Math.floor(height * 0.45)), Math.max(32, reserve));
        };
        const estimateVerticalLegendReserve = () => {
            if (!legendVisible || legendCount <= 0) return 0;
            const baseWidth = Math.max(72, Math.min(260, Math.max(Math.round(64 + legendFontSize * 3.5), longestLegendTextWidth + 26)));
            const overflowExtra = legendCount > 8 ? Math.min(60, (legendCount - 8) * 4) : 0;
            const reserve = baseWidth + overflowExtra;
            return Math.min(Math.max(76, Math.floor(width * 0.42)), Math.max(70, reserve));
        };
        const axisLegendReserveDefault = legendPosition === 'left' || legendPosition === 'right'
            ? estimateVerticalLegendReserve()
            : estimateHorizontalLegendReserve();
        const visualLegendReserveDefault = legendPosition === 'left' || legendPosition === 'right'
            ? Math.max(50, axisLegendReserveDefault - 14)
            : Math.max(28, axisLegendReserveDefault - 10);
        const axisLegendReserve = legendReserveOverride ?? axisLegendReserveDefault;
        const visualLegendReserve = legendReserveOverride ?? visualLegendReserveDefault;
        const legendBaseLayout: Record<string, unknown> = (() => {
            if (!legendVisible) {
                return {};
            }
            const resolvedAlign = legendAlign === 'auto'
                ? ((autoLegendAvoid && isCompactCanvas && legendCount > 8) ? 'start' : 'center')
                : legendAlign;
            if (legendPosition === 'bottom') {
                if (resolvedAlign === 'start') return { top: 'auto', bottom: 4, left: 8 };
                if (resolvedAlign === 'end') return { top: 'auto', bottom: 4, right: 8 };
                return { top: 'auto', bottom: 4, left: 'center' };
            }
            if (legendPosition === 'left') {
                if (resolvedAlign === 'start') return { left: 4, top: 8 };
                if (resolvedAlign === 'end') return { left: 4, bottom: 8 };
                return { left: 4, top: 'middle' };
            }
            if (legendPosition === 'right') {
                if (resolvedAlign === 'start') return { right: 4, top: 8 };
                if (resolvedAlign === 'end') return { right: 4, bottom: 8 };
                return { right: 4, top: 'middle' };
            }
            if (resolvedAlign === 'start') return { top: 4, left: 8 };
            if (resolvedAlign === 'end') return { top: 4, right: 8 };
            return { top: 4, left: 'center' };
        })();
        const estimateLegendRenderSize = () => {
            if (!legendVisible || legendCount <= 0) {
                return { width: 0, height: 0 };
            }
            if (legendOrient === 'vertical') {
                const lineHeight = Math.max(18, legendFontSize + 8);
                const estimatedHeight = Math.min(height - 16, Math.max(lineHeight + 8, legendCount * lineHeight));
                const estimatedWidth = Math.min(
                    width - 16,
                    Math.max(72, (legendNameMaxWidthOverride ?? Math.min(260, longestLegendTextWidth)) + 26),
                );
                return { width: estimatedWidth, height: estimatedHeight };
            }
            const perItemWidth = Math.max(64, Math.min(280, Math.max((legendNameMaxWidthOverride ?? longestLegendTextWidth) + 26, Math.round(legendFontSize * 5.8))));
            const itemsPerRow = Math.max(1, Math.floor(Math.max(180, width - 24) / perItemWidth));
            const rows = Math.max(1, Math.ceil(legendCount / itemsPerRow));
            const estimatedWidth = Math.min(width - 16, Math.max(perItemWidth, itemsPerRow * perItemWidth));
            const estimatedHeight = Math.min(height - 16, Math.max(24, rows * (legendFontSize + 8) + 8));
            return { width: estimatedWidth, height: estimatedHeight };
        };
        const legendLayout: Record<string, unknown> = (() => {
            if (!legendVisible || (legendOffsetX === 0 && legendOffsetY === 0)) {
                return legendBaseLayout;
            }
            const next = { ...legendBaseLayout } as Record<string, unknown>;
            const est = estimateLegendRenderSize();
            const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
            if (legendOffsetX !== 0) {
                if (typeof next.left === 'number') {
                    next.left = Math.round(clamp(next.left + legendOffsetX, 0, Math.max(0, width - est.width)));
                } else if (typeof next.right === 'number') {
                    next.right = Math.round(clamp(next.right - legendOffsetX, 0, Math.max(0, width - est.width)));
                } else if (next.left === 'center') {
                    next.left = Math.round(clamp(((width - est.width) / 2) + legendOffsetX, 0, Math.max(0, width - est.width)));
                }
            }
            if (legendOffsetY !== 0) {
                if (typeof next.top === 'number') {
                    next.top = Math.round(clamp(next.top + legendOffsetY, 0, Math.max(0, height - est.height)));
                } else if (typeof next.bottom === 'number') {
                    next.bottom = Math.round(clamp(next.bottom - legendOffsetY, 0, Math.max(0, height - est.height)));
                } else if (next.top === 'middle') {
                    next.top = Math.round(clamp(((height - est.height) / 2) + legendOffsetY, 0, Math.max(0, height - est.height)));
                }
            }
            return next;
        })();
        const legendDragEnabled = mode === 'designer'
            && c.legendDragEnabled === true
            && legendVisible
            && legendCount > 0;
        const chartDragEnabled = mode === 'designer'
            && c.chartDragEnabled === true;
        const legendBoxRect = (() => {
            if (!legendVisible) {
                return { left: 0, top: 0, width: 0, height: 0 };
            }
            const est = estimateLegendRenderSize();
            const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
            let left = 8;
            if (typeof legendLayout.left === 'number') {
                left = legendLayout.left;
            } else if (typeof legendLayout.right === 'number') {
                left = width - legendLayout.right - est.width;
            } else if (legendLayout.left === 'center') {
                left = (width - est.width) / 2;
            }
            let top = 8;
            if (typeof legendLayout.top === 'number') {
                top = legendLayout.top;
            } else if (typeof legendLayout.bottom === 'number') {
                top = height - legendLayout.bottom - est.height;
            } else if (legendLayout.top === 'middle') {
                top = (height - est.height) / 2;
            }
            return {
                left: Math.round(clamp(left, 0, Math.max(0, width - est.width))),
                top: Math.round(clamp(top, 0, Math.max(0, height - est.height))),
                width: Math.max(0, est.width),
                height: Math.max(0, est.height),
            };
        })();
        const legendDragHandleStyle = legendDragEnabled ? {
            position: 'absolute' as const,
            left: Math.max(2, Math.min(width - 14, Math.round(legendBoxRect.left + Math.max(8, legendBoxRect.width / 2) - 6))),
            top: Math.max(2, Math.min(height - 14, Math.round(legendBoxRect.top + 2))),
            width: 12,
            height: 12,
            borderRadius: 999,
            border: `1px solid ${t.textPrimary}`,
            background: t.echarts.colorPalette?.[0] || t.accentColor,
            boxShadow: '0 1px 4px rgba(0,0,0,0.35)',
            cursor: 'grab',
            zIndex: 20,
            opacity: 0.9,
            padding: 0,
        } : null;
        const legendConfig: Record<string, unknown> = {
            show: legendVisible,
            type: c.legendScrollable === false ? 'plain' : 'scroll',
            orient: legendOrient,
            itemGap: legendItemGap,
            ...(shouldTruncateLegend ? { formatter: (name: string) => formatLegendText(name) } : {}),
            textStyle: { color: t.textPrimary, fontSize: legendFontSize },
            pageTextStyle: { color: t.textSecondary, fontSize: Math.max(10, legendFontSize - 1) },
            pageIconColor: t.textSecondary,
            pageIconInactiveColor: t.textMuted,
            ...legendLayout,
        };
        const axisLabelBottomBoost = Math.abs(xAxisLabelRotate) >= 30 ? 16 : 0;
        const axisLabelEllipsisBoost = xAxisLabelMaxLength > 0 ? 6 : 0;
        const axisAutoPadding = {
            left: 56 + (legendPosition === 'left' ? axisLegendReserve : 0),
            right: 30 + (legendPosition === 'right' ? axisLegendReserve : 0),
            top: 18 + (hasTitle ? 28 : 0) + (legendPosition === 'top' ? axisLegendReserve : 0),
            bottom: 42 + (legendPosition === 'bottom' ? axisLegendReserve : 0) + axisLabelBottomBoost + axisLabelEllipsisBoost,
        };
        const axisBaseLeft = readPaddingOverride('chartPaddingLeft') ?? axisAutoPadding.left;
        const axisBaseRight = readPaddingOverride('chartPaddingRight') ?? axisAutoPadding.right;
        const axisBaseTop = readPaddingOverride('chartPaddingTop') ?? axisAutoPadding.top;
        const axisBaseBottom = readPaddingOverride('chartPaddingBottom') ?? axisAutoPadding.bottom;
        const axisGrid = {
            left: Math.max(0, axisBaseLeft + Math.max(0, chartOffsetX)),
            right: Math.max(0, axisBaseRight + Math.max(0, -chartOffsetX)),
            top: Math.max(0, axisBaseTop + Math.max(0, chartOffsetY)),
            bottom: Math.max(0, axisBaseBottom + Math.max(0, -chartOffsetY)),
            containLabel: true,
        };
        const xAxisLabelIntervalRaw = Number(c.xAxisLabelInterval);
        const autoXAxisLabelInterval = (() => {
            if (!xAxisCategoryCount || xAxisCategoryCount <= 1) return 0;
            if (!isCompactCanvas && xAxisCategoryCount <= 12) return 0;
            const projectedWidth = Math.max(
                axisFontSize + 6,
                estimateVisualTextWidth('W'.repeat(Math.max(1, Math.min(16, xAxisLabelMaxLength || longestXAxisLabelLength))), axisFontSize),
            );
            const plotSpan = Math.max(120, width - axisGrid.left - axisGrid.right);
            const perCategorySpan = Math.max(8, plotSpan / xAxisCategoryCount);
            const rotationFactor = Math.max(0.3, Math.cos(Math.abs(xAxisLabelRotate) * Math.PI / 180));
            const neededStep = Math.ceil((projectedWidth * rotationFactor) / perCategorySpan);
            return Math.max(0, Math.min(xAxisCategoryCount - 1, neededStep - 1));
        })();
        const xAxisLabelInterval = Number.isFinite(xAxisLabelIntervalRaw) && xAxisLabelIntervalRaw > 0
            ? Math.max(0, Math.round(xAxisLabelIntervalRaw))
            : autoXAxisLabelInterval;
        const visualAutoPadding = {
            left: 12 + (legendPosition === 'left' ? visualLegendReserve : 0),
            right: 12 + (legendPosition === 'right' ? visualLegendReserve : 0),
            top: 12 + (hasTitle ? 28 : 0) + (legendPosition === 'top' ? visualLegendReserve : 0),
            bottom: 12 + (legendPosition === 'bottom' ? visualLegendReserve : 0),
        };
        const visualPadding = {
            left: readPaddingOverride('chartPaddingLeft') ?? visualAutoPadding.left,
            right: readPaddingOverride('chartPaddingRight') ?? visualAutoPadding.right,
            top: readPaddingOverride('chartPaddingTop') ?? visualAutoPadding.top,
            bottom: readPaddingOverride('chartPaddingBottom') ?? visualAutoPadding.bottom,
        };
        const chartScalePercentRaw = Number(c.chartScalePercent);
        const chartScalePercent = Number.isFinite(chartScalePercentRaw)
            ? toNumber(c.chartScalePercent, 100, 40, 180)
            : (isCompactCanvas ? (isTinyCanvas ? 82 : 90) : 100);
        const chartScale = chartScalePercent / 100;
        const seriesLabelPositionRaw = String(c.seriesLabelPosition ?? 'auto').trim().toLowerCase();
        const seriesLabelPosition = seriesLabelPositionRaw === 'inside'
            || seriesLabelPositionRaw === 'outside'
            || seriesLabelPositionRaw === 'none'
            ? seriesLabelPositionRaw
            : 'auto';
        const seriesLabelFontSize = toNumber(c.seriesLabelFontSize, 12, 10, 28);
        const seriesLabelMinAngleRaw = Number(c.seriesLabelMinAngle);
        const seriesLabelMinAngle = Number.isFinite(seriesLabelMinAngleRaw) && seriesLabelMinAngleRaw > 0
            ? toNumber(c.seriesLabelMinAngle, 2, 1, 45)
            : (isTinyCanvas ? 8 : 2);
        const seriesLabelLineLengthRaw = Number(c.seriesLabelLineLength);
        const seriesLabelLineLength2Raw = Number(c.seriesLabelLineLength2);
        const seriesLabelLineLength = Number.isFinite(seriesLabelLineLengthRaw) && seriesLabelLineLengthRaw > 0
            ? toNumber(c.seriesLabelLineLength, 12, 4, 60)
            : (isTinyCanvas ? 8 : 15);
        const seriesLabelLineLength2 = Number.isFinite(seriesLabelLineLength2Raw) && seriesLabelLineLength2Raw > 0
            ? toNumber(c.seriesLabelLineLength2, 8, 3, 60)
            : (isTinyCanvas ? 5 : 10);
        const axisSeries = Array.isArray(c.series)
            ? (c.series as Array<Record<string, unknown>>)
            : [];
        const axisSeriesCount = axisSeries.length;
        const axisSeriesPointCount = axisSeries.reduce((sum, item) => {
            const data = item?.data;
            return sum + (Array.isArray(data) ? data.length : 0);
        }, 0);
        const axisSeriesDensity = xAxisCategoryCount * Math.max(axisSeriesCount, 1);
        const axisSeriesLabelAutoHide = isCompactCanvas
            ? axisSeriesDensity > (isTinyCanvas ? 18 : 28)
            : axisSeriesDensity > 40;
        const axisSeriesLabelStrategyRaw = String(c.axisSeriesLabelStrategy ?? 'auto').trim().toLowerCase();
        const axisSeriesLabelStrategy = axisSeriesLabelStrategyRaw === 'all'
            || axisSeriesLabelStrategyRaw === 'first'
            || axisSeriesLabelStrategyRaw === 'none'
            ? axisSeriesLabelStrategyRaw
            : 'auto';
        const resolvedAxisSeriesLabelStrategy = (() => {
            if (axisSeriesLabelStrategy !== 'auto') {
                return axisSeriesLabelStrategy;
            }
            if (seriesLabelPosition === 'none' || axisSeriesLabelAutoHide) {
                return 'none';
            }
            if (isTinyCanvas) {
                return axisSeriesCount > 1 ? 'first' : 'all';
            }
            if (axisSeriesCount >= 3 || axisSeriesDensity > 30) {
                return 'first';
            }
            return 'all';
        })();
        const axisSeriesLabelShow = resolvedAxisSeriesLabelStrategy !== 'none';
        const axisSeriesLabelStepRaw = Number(c.axisSeriesLabelStep);
        const autoAxisSeriesLabelStep = (() => {
            if (xAxisCategoryCount <= 0) return 1;
            const baseTarget = isTinyCanvas ? 5 : (isCompactCanvas ? 8 : 12);
            const target = resolvedAxisSeriesLabelStrategy === 'all'
                ? baseTarget
                : Math.max(4, Math.round(baseTarget * 0.8));
            const step = Math.ceil(xAxisCategoryCount / Math.max(1, target));
            return Math.max(1, step);
        })();
        const axisSeriesLabelStep = Number.isFinite(axisSeriesLabelStepRaw) && axisSeriesLabelStepRaw > 0
            ? Math.max(1, Math.round(axisSeriesLabelStepRaw))
            : autoAxisSeriesLabelStep;
        const axisLineLabelPosition = seriesLabelPosition === 'inside' ? 'inside' : 'top';
        const axisBarLabelPosition = seriesLabelPosition === 'inside' ? 'insideTop' : 'top';
        const axisBarLabelColor = axisBarLabelPosition === 'insideTop' ? '#ffffff' : t.textPrimary;
        const formatMeasureValue = (raw: unknown): string => {
            const parsed = Number(raw);
            if (Number.isFinite(parsed)) {
                if (Number.isInteger(parsed)) {
                    return parsed.toLocaleString('zh-CN');
                }
                return parsed.toLocaleString('zh-CN', { maximumFractionDigits: 2 });
            }
            return String(raw ?? '');
        };
        const resolveAxisPointValue = (raw: unknown): unknown => {
            if (Array.isArray(raw)) {
                for (let i = raw.length - 1; i >= 0; i -= 1) {
                    const item = raw[i];
                    if (item !== null && item !== undefined && item !== '') {
                        return item;
                    }
                }
                return '';
            }
            if (raw && typeof raw === 'object') {
                const row = raw as Record<string, unknown>;
                if ('value' in row) {
                    return resolveAxisPointValue(row.value);
                }
            }
            return raw;
        };
        const axisSeriesLabelFormatter = (raw: unknown) => {
            const row = raw && typeof raw === 'object'
                ? (raw as Record<string, unknown>)
                : null;
            const dataIndexRaw = Number(row?.dataIndex);
            if (axisSeriesLabelStep > 1 && Number.isFinite(dataIndexRaw) && dataIndexRaw >= 0) {
                const dataIndex = Math.round(dataIndexRaw);
                if (dataIndex % axisSeriesLabelStep !== 0) {
                    return '';
                }
            }
            const value = resolveAxisPointValue(row?.value ?? raw);
            return formatMeasureValue(value);
        };
        const axisTooltipMaxRowsRaw = Number(c.axisTooltipMaxRows);
        const axisTooltipMaxRows = Number.isFinite(axisTooltipMaxRowsRaw) && axisTooltipMaxRowsRaw > 0
            ? Math.min(50, Math.max(1, Math.round(axisTooltipMaxRowsRaw)))
            : (isTinyCanvas ? 4 : (isCompactCanvas ? 6 : 10));
        const axisTooltipFormatter = (raw: unknown) => {
            const rows = Array.isArray(raw)
                ? raw as Array<Record<string, unknown>>
                : [raw as Record<string, unknown>];
            if (rows.length === 0) {
                return '';
            }
            const first = rows[0] ?? {};
            const axisTitle = String(first.axisValueLabel ?? first.axisValue ?? first.name ?? '').trim();
            const lines = [axisTitle];
            const withPriority = rows.map((row, index) => {
                const value = resolveAxisPointValue(row.value ?? row.data);
                const numeric = Number(value);
                return {
                    row,
                    index,
                    value,
                    weight: Number.isFinite(numeric) ? Math.abs(numeric) : -1,
                };
            });
            const limitedRows = withPriority.length > axisTooltipMaxRows
                ? [...withPriority]
                    .sort((a, b) => (b.weight - a.weight) || (a.index - b.index))
                    .slice(0, axisTooltipMaxRows)
                    .sort((a, b) => a.index - b.index)
                : withPriority;
            for (const item of limitedRows) {
                const row = item.row;
                const marker = typeof row.marker === 'string' ? row.marker : '';
                const seriesName = String(row.seriesName ?? '').trim() || '系列';
                lines.push(`${marker}${seriesName}: ${formatMeasureValue(item.value)}`);
            }
            const hidden = rows.length - limitedRows.length;
            if (hidden > 0) {
                lines.push(`... 其余 ${hidden} 项`);
            }
            return lines.join('<br/>');
        };
        const seriesDataCount = Array.isArray(c.data) ? c.data.length : 0;
        const forceInsideForTiny = isTinyCanvas && seriesDataCount >= 6 && seriesLabelPosition === 'auto';
        const pieLabelPosition = forceInsideForTiny
            ? 'inside'
            : (seriesLabelPosition === 'auto'
                ? 'outside'
                : (seriesLabelPosition === 'outside' ? 'outside' : 'inside'));
        const funnelLabelPosition = forceInsideForTiny
            ? 'inside'
            : (seriesLabelPosition === 'outside' ? 'right' : 'inside');
        const pieLabelShow = seriesLabelPosition !== 'none' && !(isTinyCanvas && seriesDataCount >= 10);
        const funnelLabelShow = seriesLabelPosition !== 'none' && !(isTinyCanvas && seriesDataCount >= 9);
        const chartDataPointCount = (() => {
            if (axisSeriesCount > 0) {
                return axisSeriesPointCount;
            }
            if (Array.isArray(c.data)) {
                return c.data.length;
            }
            return 0;
        })();
        const disableChartAnimation = isTinyCanvas || chartDataPointCount > 2000;
        const chartMotionOption = disableChartAnimation
            ? { animation: false, animationDuration: 0, animationDurationUpdate: 0 }
            : {};
        const plotWidth = Math.max(40, width - visualPadding.left - visualPadding.right);
        const plotHeight = Math.max(40, height - visualPadding.top - visualPadding.bottom);
        const plotCenterX = visualPadding.left + (plotWidth / 2) + chartOffsetX;
        const plotCenterY = visualPadding.top + (plotHeight / 2) + chartOffsetY;
        const pieOuterRadius = Math.max(20, Math.min(plotWidth, plotHeight) * 0.36 * chartScale);
        const pieInnerRadius = Math.max(10, pieOuterRadius * 0.58);
        const radarRadius = Math.max(20, Math.min(plotWidth, plotHeight) * 0.42 * chartScale);
        const funnelLeft = Math.max(0, visualPadding.left + chartOffsetX);
        const funnelRight = Math.max(0, visualPadding.right - chartOffsetX);
        const funnelTop = Math.max(0, visualPadding.top + chartOffsetY);
        const funnelBottom = Math.max(0, visualPadding.bottom - chartOffsetY);
        const chartDragHandleStyle = chartDragEnabled ? {
            position: 'absolute' as const,
            left: Math.max(2, Math.min(width - 14, Math.round(plotCenterX) - 6)),
            top: Math.max(2, Math.min(height - 14, Math.round(plotCenterY) - 6)),
            width: 12,
            height: 12,
            borderRadius: 3,
            border: `1px solid ${t.textPrimary}`,
            background: t.echarts.colorPalette?.[1] || t.accentColor,
            boxShadow: '0 1px 4px rgba(0,0,0,0.35)',
            cursor: 'move',
            zIndex: 20,
            opacity: 0.92,
            padding: 0,
        } : null;
        const dependencyPlaceholder = (label: string) => (
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
                fontSize: 12,
            }}>
                {label}加载中...
            </div>
        );

        if (ECHART_COMPONENT_TYPES.has(type) && !EChartsComponent) {
            return dependencyPlaceholder('图表引擎');
        }
        if (DATAV_COMPONENT_TYPES.has(type) && !dataViewModule) {
            return dependencyPlaceholder('DataV');
        }

        const EChart = EChartsComponent as ReactEChartsComponent;
        const ScrollBoard = dataViewModule?.ScrollBoard;
        const ScrollRankingBoard = dataViewModule?.ScrollRankingBoard;
        const WaterLevelPond = dataViewModule?.WaterLevelPond;
        const DigitalFlop = dataViewModule?.DigitalFlop;
        const renderEChartWithHandles = (
            option: Record<string, unknown>,
            onEvents?: Record<string, (params: Record<string, unknown>) => void>,
        ) => {
            // Inject markLine / markArea / conditionalColors from config
            const annotatedOption = injectChartAnnotations(option, c);
            const chartNode = (
                <EChart
                    style={{ width: '100%', height: '100%' }}
                    option={annotatedOption}
                    onEvents={onEvents}
                />
            );
            const showLegendHandle = legendDragEnabled && !!legendDragHandleStyle;
            const showChartHandle = chartDragEnabled && !!chartDragHandleStyle;
            if (!showLegendHandle && !showChartHandle) {
                return chartNode;
            }
            const handleLegendHandleMouseDown = (event: ReactMouseEvent<HTMLButtonElement>) => {
                event.preventDefault();
                event.stopPropagation();
                clearLegendDragHandlers();
                clearChartDragHandlers();
                const startClientX = event.clientX;
                const startClientY = event.clientY;
                const startOffsetX = legendOffsetX;
                const startOffsetY = legendOffsetY;
                let lastOffsetX = startOffsetX;
                let lastOffsetY = startOffsetY;
                const clampX = (value: number) => Math.round(Math.min(legendOffsetBoundX, Math.max(-legendOffsetBoundX, value)));
                const clampY = (value: number) => Math.round(Math.min(legendOffsetBoundY, Math.max(-legendOffsetBoundY, value)));
                const move = (moveEvent: MouseEvent) => {
                    const deltaX = moveEvent.clientX - startClientX;
                    const deltaY = moveEvent.clientY - startClientY;
                    lastOffsetX = clampX(startOffsetX + deltaX);
                    lastOffsetY = clampY(startOffsetY + deltaY);
                    setLegendDragPreview({ x: lastOffsetX, y: lastOffsetY });
                };
                const up = () => {
                    clearLegendDragHandlers();
                    setLegendDragPreview(null);
                    if ((lastOffsetX !== startOffsetX || lastOffsetY !== startOffsetY) && onConfigMetaRef.current) {
                        onConfigMetaRef.current({
                            legendOffsetX: lastOffsetX,
                            legendOffsetY: lastOffsetY,
                            legendDragEnabled: true,
                        });
                    }
                };
                legendDragHandlersRef.current = { move, up };
                window.addEventListener('mousemove', move);
                window.addEventListener('mouseup', up);
            };
            const handleChartHandleMouseDown = (event: ReactMouseEvent<HTMLButtonElement>) => {
                event.preventDefault();
                event.stopPropagation();
                clearLegendDragHandlers();
                clearChartDragHandlers();
                const startClientX = event.clientX;
                const startClientY = event.clientY;
                const startOffsetX = chartOffsetX;
                const startOffsetY = chartOffsetY;
                let lastOffsetX = startOffsetX;
                let lastOffsetY = startOffsetY;
                const clampX = (value: number) => Math.round(Math.min(chartOffsetBoundX, Math.max(-chartOffsetBoundX, value)));
                const clampY = (value: number) => Math.round(Math.min(chartOffsetBoundY, Math.max(-chartOffsetBoundY, value)));
                const move = (moveEvent: MouseEvent) => {
                    const deltaX = moveEvent.clientX - startClientX;
                    const deltaY = moveEvent.clientY - startClientY;
                    lastOffsetX = clampX(startOffsetX + deltaX);
                    lastOffsetY = clampY(startOffsetY + deltaY);
                    setChartDragPreview({ x: lastOffsetX, y: lastOffsetY });
                };
                const up = () => {
                    clearChartDragHandlers();
                    setChartDragPreview(null);
                    if ((lastOffsetX !== startOffsetX || lastOffsetY !== startOffsetY) && onConfigMetaRef.current) {
                        onConfigMetaRef.current({
                            chartOffsetX: lastOffsetX,
                            chartOffsetY: lastOffsetY,
                            chartDragEnabled: true,
                        });
                    }
                };
                chartDragHandlersRef.current = { move, up };
                window.addEventListener('mousemove', move);
                window.addEventListener('mouseup', up);
            };
            return (
                <div style={{ width: '100%', height: '100%', position: 'relative' }}>
                    {chartNode}
                    {showLegendHandle ? (
                        <button
                            type="button"
                            style={legendDragHandleStyle!}
                            onMouseDown={handleLegendHandleMouseDown}
                            title="拖拽微调图例位置"
                        />
                    ) : null}
                    {showChartHandle ? (
                        <button
                            type="button"
                            style={chartDragHandleStyle!}
                            onMouseDown={handleChartHandleMouseDown}
                            title="拖拽微调图形位置"
                        />
                    ) : null}
                </div>
            );
        };

        switch (type) {
            // ==================== ECharts 图表 ====================
            case 'line-chart':
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                            color: t.echarts.axisLabelColor,
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
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                        splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                    },
                    series: (c.series as Array<{ name: string; data: number[] }>).map((s, idx) => {
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
                        color: t.echarts.axisLabelColor,
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
                    axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                };
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                    series: (c.series as Array<{ name: string; data: number[] }>).map((s, idx) => ({
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
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
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
                        data: [{ value: c.value as number, name: c.title as string }],
                    }],
                });

            case 'radar-chart':
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
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
                        data: [{ value: c.data as number[], areaStyle: { opacity: 0.3 } }],
                    }],
                }, echartsClickHandler);

            case 'funnel-chart':
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
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

            case 'scatter-chart':
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                    legend: legendConfig,
                    xAxis: {
                        axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                        splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                    },
                    yAxis: {
                        axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                        splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                    },
                    series: [{
                        type: 'scatter',
                        data: c.data as number[][],
                        symbolSize: 10,
                        itemStyle: { color: seriesColors[0] || t.scatterColor },
                    }],
                    grid: axisGrid,
                }, echartsClickHandler);

            case 'combo-chart': {
                const comboSeries = (c.series as Array<{ name: string; type: 'bar' | 'line'; yAxisIndex?: number; data: number[] }>) || [];
                const comboYAxis = (c.yAxis as Array<{ name?: string; min?: number; max?: number }>) || [{}];
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                            color: t.echarts.axisLabelColor,
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
                        nameTextStyle: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                        min: y.min,
                        max: y.max,
                        position: i === 0 ? 'left' : 'right',
                        axisLine: { show: true, lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
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
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
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
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                            fontFamily: 'sans-serif',
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
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
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
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate },
                    },
                    yAxis: {
                        type: 'value',
                        axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
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
                    const baseTitle = { text: title, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } };
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
                        <div style={{
                            fontSize: (c.titleFontSize as number) || 12,
                            color: resolveTextColor(c.titleColor as string | undefined, t.numberCard.titleColor),
                            marginBottom: 8,
                        }}>
                            {c.title as string}
                        </div>
                        <div style={{
                            fontSize: (c.valueFontSize as number) || 32,
                            fontWeight: 'bold',
                            color: resolveTextColor(c.valueColor as string | undefined, t.numberCard.valueColor),
                        }}>
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
                        color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                    }}>
                        {c.text as string}
                    </div>
                );

            case 'markdown-text': {
                const markdown = String(c.markdown ?? '');
                const html = renderMarkdownToHtml(markdown);
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            overflow: 'auto',
                            color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                            fontSize: (c.fontSize as number) || 14,
                            lineHeight: Number(c.lineHeight || 1.6),
                            padding: 8,
                        }}
                        dangerouslySetInnerHTML={{ __html: html }}
                    />
                );
            }

            case 'richtext': {
                const rtContent = String(c.content ?? '');
                // Sanitize: strip script/iframe/style/on* attributes
                const sanitizedRtHtml = rtContent
                    .replace(/<script[\s\S]*?<\/script>/gi, '')
                    .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
                    .replace(/<style[\s\S]*?<\/style>/gi, '')
                    .replace(/\bon\w+\s*=\s*["'][^"']*["']/gi, '')
                    .replace(/\bon\w+\s*=\s*\S+/gi, '')
                    .replace(/<a\s/gi, '<a rel="noreferrer" target="_blank" ');
                const rtPadding = Number(c.padding ?? 12);
                const rtOverflow = String(c.overflow ?? 'hidden');
                const rtVAlign = String(c.verticalAlign ?? 'top');
                const alignMap: Record<string, string> = { top: 'flex-start', middle: 'center', bottom: 'flex-end' };
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            padding: rtPadding,
                            overflow: rtOverflow as 'hidden' | 'visible' | 'scroll',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: alignMap[rtVAlign] ?? 'flex-start',
                            boxSizing: 'border-box',
                        }}
                        dangerouslySetInnerHTML={{ __html: sanitizedRtHtml }}
                    />
                );
            }

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
                        color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                        fontFamily: 'monospace',
                    }}>
                        {formatted}
                    </div>
                );
            }

            case 'countdown': {
                const targetVariableKey = String(c.targetVariableKey || '').trim();
                const runtimeTarget = targetVariableKey ? String(runtime.values[targetVariableKey] || '').trim() : '';
                const configuredTarget = String(c.targetTime || '').trim();
                const targetRaw = runtimeTarget || configuredTarget;
                const targetMillis = Date.parse(targetRaw);
                const hasTarget = Number.isFinite(targetMillis);
                const remaining = hasTarget ? Math.max(0, targetMillis - currentTime.getTime()) : 0;
                const dayMs = 24 * 3600 * 1000;
                const hourMs = 3600 * 1000;
                const minuteMs = 60 * 1000;
                const days = Math.floor(remaining / dayMs);
                const hours = Math.floor((remaining % dayMs) / hourMs);
                const minutes = Math.floor((remaining % hourMs) / minuteMs);
                const seconds = Math.floor((remaining % minuteMs) / 1000);
                const showDays = c.showDays !== false;
                const accentColor = (c.accentColor as string) || t.accentColor;
                const labelColor = resolveTextColor(c.color as string | undefined, t.textSecondary);
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 8 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>
                            {String(c.title || '倒计时')}
                        </div>
                        {!hasTarget ? (
                            <div style={{ fontSize: 13, color: labelColor, opacity: 0.8 }}>
                                请配置目标时间或绑定目标时间变量
                            </div>
                        ) : null}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: accentColor, fontWeight: 700 }}>
                            {showDays ? <span style={{ fontSize: 26 }}>{String(days).padStart(2, '0')}天</span> : null}
                            <span style={{ fontSize: 26 }}>{String(hours).padStart(2, '0')}:</span>
                            <span style={{ fontSize: 26 }}>{String(minutes).padStart(2, '0')}:</span>
                            <span style={{ fontSize: 26 }}>{String(seconds).padStart(2, '0')}</span>
                        </div>
                    </div>
                );
            }

            case 'marquee': {
                const text = String(c.text || '');
                const speed = Math.max(10, Number(c.speed || 40));
                const keyframesName = `dts_marquee_${component.id.replace(/[^a-zA-Z0-9_]/g, '_')}`;
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            overflow: 'hidden',
                            display: 'flex',
                            alignItems: 'center',
                            background: (c.backgroundColor as string) || 'transparent',
                            color: resolveTextColor(c.color as string | undefined, t.textPrimary),
                            fontSize: (c.fontSize as number) || 14,
                            whiteSpace: 'nowrap',
                            position: 'relative',
                        }}
                    >
                        <style>{`@keyframes ${keyframesName} { from { transform: translateX(100%); } to { transform: translateX(-100%); } }`}</style>
                        <div style={{ display: 'inline-block', paddingLeft: '100%', animation: `${keyframesName} ${speed}s linear infinite` }}>
                            {text}
                        </div>
                    </div>
                );
            }

            case 'carousel': {
                const items = carouselItems;
                const hasItems = items.length > 0;
                const index = hasItems ? (carouselIndex % items.length) : 0;
                const currentItem = hasItems ? items[index] : '暂无轮播内容';
                const cardTitle = String(c.title || '轮播卡片');
                const cardColor = resolveTextColor(c.color as string | undefined, t.textPrimary);
                const titleColor = resolveTextColor(c.titleColor as string | undefined, t.textSecondary);
                const backgroundColor = String(c.backgroundColor || t.cardBackground);
                const fontSize = Math.max(12, Number(c.fontSize || 24));
                const showDots = c.showDots !== false;
                const showControls = c.showControls !== false;
                const pauseOnHover = c.pauseOnHover !== false;
                const canFlip = hasItems && items.length > 1;
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            border: '1px solid rgba(148,163,184,0.3)',
                            borderRadius: 10,
                            background: backgroundColor,
                            padding: 12,
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'space-between',
                            boxSizing: 'border-box',
                            overflow: 'hidden',
                        }}
                        onMouseEnter={() => {
                            if (pauseOnHover) {
                                setCarouselPaused(true);
                            }
                        }}
                        onMouseLeave={() => {
                            if (pauseOnHover) {
                                setCarouselPaused(false);
                            }
                        }}
                    >
                        <div style={{ fontSize: 12, color: titleColor, letterSpacing: 0.4 }}>
                            {cardTitle}
                        </div>
                        <div style={{ flex: 1, display: 'grid', gridTemplateColumns: showControls ? '28px 1fr 28px' : '1fr', alignItems: 'center', gap: 8 }}>
                            {showControls && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (!canFlip) return;
                                        setCarouselIndex((prev) => (prev - 1 + items.length) % items.length);
                                    }}
                                    style={{
                                        width: 28,
                                        height: 28,
                                        borderRadius: '50%',
                                        border: '1px solid rgba(148,163,184,0.4)',
                                        background: 'rgba(15,23,42,0.45)',
                                        color: cardColor,
                                        cursor: canFlip ? 'pointer' : 'default',
                                        opacity: canFlip ? 1 : 0.45,
                                    }}
                                    title="上一条"
                                >
                                    {'<'}
                                </button>
                            )}
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    color: cardColor,
                                    fontSize,
                                    fontWeight: 600,
                                    lineHeight: 1.35,
                                    transition: 'opacity 0.2s ease',
                                    wordBreak: 'break-word',
                                    opacity: hasItems ? 1 : 0.7,
                                }}
                            >
                                {currentItem}
                            </div>
                            {showControls && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (!canFlip) return;
                                        setCarouselIndex((prev) => (prev + 1) % items.length);
                                    }}
                                    style={{
                                        width: 28,
                                        height: 28,
                                        borderRadius: '50%',
                                        border: '1px solid rgba(148,163,184,0.4)',
                                        background: 'rgba(15,23,42,0.45)',
                                        color: cardColor,
                                        cursor: canFlip ? 'pointer' : 'default',
                                        opacity: canFlip ? 1 : 0.45,
                                    }}
                                    title="下一条"
                                >
                                    {'>'}
                                </button>
                            )}
                        </div>
                        {showDots && hasItems && (
                            <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
                                {items.map((_, dotIdx) => (
                                    <span
                                        key={`dot-${dotIdx}`}
                                        style={{
                                            width: dotIdx === index ? 16 : 6,
                                            height: 6,
                                            borderRadius: 999,
                                            background: dotIdx === index ? '#38bdf8' : 'rgba(148,163,184,0.45)',
                                            transition: 'all 0.2s ease',
                                        }}
                                    />
                                ))}
                            </div>
                        )}
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

            case 'tab-switcher': {
                const options = tabOptions;
                const label = String(c.label ?? '切换');
                const activeValue = tabRuntimeValue || tabDefaultValue || options[0]?.value || '';
                const activeTextColor = String(c.activeTextColor || '#0f172a');
                const activeBackgroundColor = String(c.activeBackgroundColor || '#38bdf8');
                const inactiveTextColor = String(c.inactiveTextColor || t.textSecondary);
                const inactiveBackgroundColor = String(c.inactiveBackgroundColor || 'rgba(15,23,42,0.45)');
                const compact = c.compact === true;
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: t.textSecondary }}>{label}</div>
                        <div style={{ display: 'flex', gap: compact ? 4 : 8, flexWrap: 'wrap', alignItems: 'center' }}>
                            {options.map((option) => {
                                const active = option.value === activeValue;
                                return (
                                    <button
                                        key={option.value}
                                        type="button"
                                        onClick={() => {
                                            if (!tabVariableKey) return;
                                            runtime.setVariable(tabVariableKey, option.value, `tab-switcher:${component.id}`);
                                        }}
                                        style={{
                                            border: '1px solid rgba(148,163,184,0.3)',
                                            background: active ? activeBackgroundColor : inactiveBackgroundColor,
                                            color: active ? activeTextColor : inactiveTextColor,
                                            borderRadius: 999,
                                            padding: compact ? '3px 10px' : '6px 14px',
                                            fontSize: compact ? 11 : 12,
                                            cursor: tabVariableKey ? 'pointer' : 'default',
                                            whiteSpace: 'nowrap',
                                            transition: 'all 0.2s ease',
                                        }}
                                    >
                                        {option.label}
                                    </button>
                                );
                            })}
                            {options.length === 0 && (
                                <span style={{ fontSize: 12, color: t.textSecondary }}>请在属性中配置 Tab 选项</span>
                            )}
                        </div>
                    </div>
                );
            }

            case 'filter-input': {
                const label = String(c.label ?? '筛选');
                const variableKey = String(c.variableKey ?? '').trim();
                const placeholder = String(c.placeholder ?? '请输入');
                const value = variableKey ? filterInputDraft : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                const debounceMs = normalizeFilterDebounceMs(c.debounceMs);
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <input
                            type="text"
                            value={value}
                            onChange={(e) => {
                                const nextValue = e.target.value;
                                setFilterInputDraft(nextValue);
                                if (!variableKey) return;
                                scheduleFilterVariableUpdate(variableKey, nextValue, `filter-input:${component.id}`, debounceMs);
                            }}
                            onBlur={() => {
                                if (!variableKey) return;
                                scheduleFilterVariableUpdate(variableKey, filterInputDraft, `filter-input:${component.id}`, debounceMs, true);
                            }}
                            placeholder={placeholder}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 10px',
                                outline: 'none',
                            }}
                        />
                    </div>
                );
            }

            case 'filter-select': {
                const label = String(c.label ?? '筛选');
                const variableKey = String(c.variableKey ?? '').trim();
                const placeholder = String(c.placeholder ?? '请选择');
                const optionSourceMode = String(c.optionSourceMode ?? 'manual').trim().toLowerCase();
                const staticOptions = resolveFilterOptions(c.options);
                const dataOptions = optionSourceMode === 'data'
                    ? resolveFilterOptionsFromData(cardData, c)
                    : [];
                const options = dataOptions.length > 0 ? dataOptions : staticOptions;
                const value = variableKey ? (runtime.values[variableKey] ?? '') : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <select
                            value={value}
                            onChange={(e) => variableKey && runtime.setVariable(variableKey, e.target.value, `filter-select:${component.id}`)}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 10px',
                                outline: 'none',
                            }}
                        >
                            <option value="">{placeholder}</option>
                            {options.map((option) => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                        </select>
                    </div>
                );
            }

            case 'filter-date-range': {
                const label = String(c.label ?? '日期区间');
                const startKey = String(c.startKey ?? '').trim();
                const endKey = String(c.endKey ?? '').trim();
                const startValue = startKey ? (runtime.values[startKey] ?? '') : '';
                const endValue = endKey ? (runtime.values[endKey] ?? '') : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 16px 1fr', alignItems: 'center', gap: 4 }}>
                            <input
                                type="date"
                                value={startValue}
                                onChange={(e) => startKey && runtime.setVariable(startKey, e.target.value, `filter-date-range:start:${component.id}`)}
                                style={{
                                    width: '100%',
                                    height: 34,
                                    borderRadius: 6,
                                    border: `1px solid ${inputBorderColor}`,
                                    background: inputBackground,
                                    color: inputTextColor,
                                    padding: '0 8px',
                                    outline: 'none',
                                }}
                            />
                            <span style={{ textAlign: 'center', color: t.textSecondary }}>~</span>
                            <input
                                type="date"
                                value={endValue}
                                onChange={(e) => endKey && runtime.setVariable(endKey, e.target.value, `filter-date-range:end:${component.id}`)}
                                style={{
                                    width: '100%',
                                    height: 34,
                                    borderRadius: 6,
                                    border: `1px solid ${inputBorderColor}`,
                                    background: inputBackground,
                                    color: inputTextColor,
                                    padding: '0 8px',
                                    outline: 'none',
                                }}
                            />
                        </div>
                    </div>
                );
            }

            case 'shape': {
                const shapeType = String(c.shapeType || 'rect');
                const fillColor = String(c.fillColor || 'rgba(59,130,246,0.2)');
                const borderColor = String(c.borderColor || '#60a5fa');
                const borderWidth = Math.max(0, Number(c.borderWidth || 2));
                const radius = Math.max(0, Number(c.radius || 8));
                if (shapeType === 'line' || shapeType === 'arrow') {
                    return (
                        <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
                            {shapeType === 'arrow' ? (
                                <defs>
                                    <marker id={`arrow_${component.id}`} markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                                        <path d="M0,0 L0,6 L6,3 z" fill={borderColor} />
                                    </marker>
                                </defs>
                            ) : null}
                            <line
                                x1="8"
                                y1="50"
                                x2="92"
                                y2="50"
                                stroke={borderColor}
                                strokeWidth={borderWidth || 2}
                                markerEnd={shapeType === 'arrow' ? `url(#arrow_${component.id})` : undefined}
                            />
                        </svg>
                    );
                }
                if (shapeType === 'circle') {
                    return (
                        <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: fillColor, border: `${borderWidth}px solid ${borderColor}` }} />
                    );
                }
                return (
                    <div style={{ width: '100%', height: '100%', borderRadius: radius, background: fillColor, border: `${borderWidth}px solid ${borderColor}` }} />
                );
            }

            case 'container':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        border: `${Math.max(0, Number(c.borderWidth || 1))}px solid ${String(c.borderColor || 'rgba(148,163,184,0.35)')}`,
                        borderRadius: Math.max(0, Number(c.radius || 10)),
                        background: String(c.backgroundColor || t.cardBackground),
                        padding: Math.max(0, Number(c.padding || 12)),
                        boxSizing: 'border-box',
                        color: resolveTextColor(c.titleColor as string | undefined, t.textPrimary),
                    }}>
                        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                            {String(c.title || '容器')}
                        </div>
                        <div style={{ fontSize: 12, color: t.textSecondary }}>
                            容器组件：可用于分组布局与内容分区
                        </div>
                    </div>
                );

            case 'image':
                return isSafeSrcUrl(c.src) ? (
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
                return isSafeSrcUrl(c.src) ? (
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
                return isSafeSrcUrl(c.src) ? (
                    <iframe
                        src={c.src as string}
                        sandbox="allow-scripts allow-same-origin"
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
                const BorderBoxComponent = borderBoxComponents?.[boxType] || borderBoxComponents?.[1];
                if (!BorderBoxComponent) return dependencyPlaceholder('DataV');
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
                const DecorationComponent = decorationComponents?.[decorationType] || decorationComponents?.[1];
                if (!DecorationComponent) return dependencyPlaceholder('DataV');
                const colors = c.color as string[] | undefined;
                return (
                    <DecorationComponent color={colors} style={{ width: '100%', height: '100%' }} />
                );
            }

            // ==================== DataV 数据展示组件 ====================
            case 'scroll-board': {
                const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'center' });
                const filteredConfig = { ...c, header: displayHeader, data: displayData, _columnMeta: columnMeta };

                // DataV ScrollBoard 硬编码 color:#fff 且无法通过 CSS/style 覆盖
                // 非 legacy-dark 主题使用自定义表格组件
                if (theme && theme !== 'legacy-dark') {
                    return <ThemedScrollTable config={filteredConfig} tokens={t} />;
                }
                if (!ScrollBoard) return dependencyPlaceholder('DataV');
                const allHaveWidth = columnMeta.length > 0 && columnMeta.every((col) => typeof col.width === 'number');
                const columnWidth = allHaveWidth
                    ? columnMeta.map((col) => Math.max(40, Math.round((width * Number(col.width)) / 100)))
                    : undefined;
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
                            align: columnMeta.map((col) => col.align || 'center'),
                            ...(columnWidth ? { columnWidth } : {}),
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );
            }

            case 'table': {
                const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'left' });
                const fontSize = (c.fontSize as number) || 13;
                const headerColor = resolveTextColor(c.headerColor as string | undefined, t.textPrimary);
                const headerBackground = (c.headerBackground as string) || 'rgba(148, 163, 184, 0.16)';
                const bodyColor = resolveTextColor(c.bodyColor as string | undefined, t.textSecondary);
                const bodyBackground = (c.bodyBackground as string) || 'transparent';
                const borderColor = (c.borderColor as string) || 'rgba(148, 163, 184, 0.24)';
                const oddRowBackground = (c.oddRowBackground as string) || bodyBackground;
                const evenRowBackground = (c.evenRowBackground as string) || 'rgba(148, 163, 184, 0.06)';
                const enableSort = c.enableSort !== false;
                const enablePagination = c.enablePagination === true;
                const freezeHeader = c.freezeHeader !== false;
                const freezeFirstColumn = c.freezeFirstColumn === true;
                const pageSize = Math.max(1, Number(c.pageSize || 10));
                const conditionalRules = c.conditionalRules;

                const sortedRows = tableSort && enableSort
                    ? [...displayData].sort((a, b) => {
                        const v = compareTableValues(a[tableSort.colIndex], b[tableSort.colIndex]);
                        return tableSort.order === 'asc' ? v : -v;
                    })
                    : displayData;
                const totalPages = enablePagination ? Math.max(1, Math.ceil(sortedRows.length / pageSize)) : 1;
                const safePage = Math.max(1, Math.min(tablePage, totalPages));
                const pageRows = enablePagination
                    ? sortedRows.slice((safePage - 1) * pageSize, safePage * pageSize)
                    : sortedRows;

                return (
                    <div style={{ width: '100%', height: '100%', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                        <div style={{ flex: 1, overflow: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed', fontSize }}>
                            {displayHeader.length > 0 && (
                                <thead>
                                    <tr style={{ background: headerBackground }}>
                                        {displayHeader.map((title, i) => (
                                            <th key={i} style={{
                                                color: headerColor,
                                                width: columnMeta[i]?.width ? `${columnMeta[i].width}%` : undefined,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                textAlign: columnMeta[i]?.align || 'left',
                                                fontWeight: 600,
                                                whiteSpace: columnMeta[i]?.wrap ? 'normal' : 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: columnMeta[i]?.wrap ? undefined : 'ellipsis',
                                                overflowWrap: columnMeta[i]?.wrap ? 'anywhere' : undefined,
                                                wordBreak: columnMeta[i]?.wrap ? 'break-word' : undefined,
                                                lineHeight: columnMeta[i]?.wrap ? 1.35 : undefined,
                                                ...(freezeHeader ? { position: 'sticky', top: 0, zIndex: 3 } : {}),
                                                ...(freezeFirstColumn && i === 0
                                                    ? {
                                                        position: 'sticky',
                                                        left: 0,
                                                        zIndex: freezeHeader ? 5 : 2,
                                                        background: headerBackground,
                                                        boxShadow: `1px 0 0 ${borderColor}`,
                                                    }
                                                    : {}),
                                            }}>
                                                <button
                                                    type="button"
                                                    disabled={!enableSort}
                                                    onClick={() => {
                                                        if (!enableSort) return;
                                                        setTablePage(1);
                                                        setTableSort((prev) => {
                                                            if (!prev || prev.colIndex !== i) {
                                                                return { colIndex: i, order: 'asc' };
                                                            }
                                                            if (prev.order === 'asc') {
                                                                return { colIndex: i, order: 'desc' };
                                                            }
                                                            return null;
                                                        });
                                                    }}
                                                    style={{
                                                        border: 'none',
                                                        background: 'transparent',
                                                        color: headerColor,
                                                        fontWeight: 600,
                                                        cursor: enableSort ? 'pointer' : 'default',
                                                        display: 'inline-flex',
                                                        alignItems: 'center',
                                                        gap: 4,
                                                        padding: 0,
                                                    }}
                                                >
                                                    <span>{title}</span>
                                                    {columnMeta[i]?.masked && (
                                                        <span style={{ fontSize: 9, opacity: 0.5, marginLeft: 2 }} title="此列数据已脱敏">*</span>
                                                    )}
                                                    {tableSort?.colIndex === i ? (
                                                        <span style={{ fontSize: 10 }}>{tableSort.order === 'asc' ? '▲' : '▼'}</span>
                                                    ) : null}
                                                </button>
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                            )}
                            <tbody>
                                {pageRows.map((row, rowIndex) => (
                                    <tr key={rowIndex} style={{ background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground }}>
                                        {displayHeader.map((_, colIndex) => {
                                            const conditional = resolveTableConditionalStyle(
                                                conditionalRules,
                                                colIndex,
                                                row[colIndex],
                                                columnMeta[colIndex],
                                            );
                                            const rowBackground = rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground;
                                            const cellBackground = conditional.background || rowBackground;
                                            return (
                                                <td key={colIndex} style={{
                                                    color: conditional.color || bodyColor,
                                                    background: cellBackground,
                                                    borderBottom: '1px solid ' + borderColor,
                                                    borderRight: colIndex < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                    padding: '8px 10px',
                                                    textAlign: columnMeta[colIndex]?.align || 'left',
                                                    whiteSpace: columnMeta[colIndex]?.wrap ? 'normal' : 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: columnMeta[colIndex]?.wrap ? undefined : 'ellipsis',
                                                    overflowWrap: columnMeta[colIndex]?.wrap ? 'anywhere' : undefined,
                                                    wordBreak: columnMeta[colIndex]?.wrap ? 'break-word' : undefined,
                                                    lineHeight: columnMeta[colIndex]?.wrap ? 1.35 : undefined,
                                                    ...(freezeFirstColumn && colIndex === 0
                                                        ? {
                                                            position: 'sticky',
                                                            left: 0,
                                                            zIndex: 1,
                                                            boxShadow: `1px 0 0 ${borderColor}`,
                                                            background: cellBackground,
                                                        }
                                                        : {}),
                                                }}>
                                                    {columnMeta[colIndex]?.masked ? (
                                                        <span
                                                            style={{ color: 'rgba(148,163,184,0.6)', fontStyle: 'italic' }}
                                                            title="数据已脱敏"
                                                        >
                                                            {row[colIndex] ?? '***'}
                                                        </span>
                                                    ) : (
                                                        row[colIndex] ?? ''
                                                    )}
                                                </td>
                                            );
                                        })}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        </div>
                        {enablePagination && totalPages > 1 ? (
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'flex-end',
                                gap: 6,
                                paddingTop: 8,
                                color: t.textSecondary,
                                fontSize: 12,
                            }}>
                                <button
                                    type="button"
                                    disabled={safePage <= 1}
                                    onClick={() => setTablePage((p) => Math.max(1, p - 1))}
                                    style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                                >
                                    上一页
                                </button>
                                <span>{safePage}/{totalPages}</span>
                                <button
                                    type="button"
                                    disabled={safePage >= totalPages}
                                    onClick={() => setTablePage((p) => Math.min(totalPages, p + 1))}
                                    style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                                >
                                    下一页
                                </button>
                            </div>
                        ) : null}
                    </div>
                );
            }
            case 'scroll-ranking':
                if (!ScrollRankingBoard) return dependencyPlaceholder('DataV');
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
                if (!WaterLevelPond) return dependencyPlaceholder('DataV');
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
                if (!DigitalFlop) return dependencyPlaceholder('DataV');
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

            // ==================== 3D 可视化 (echarts-gl) ====================
            case 'globe-chart': {
                if (!isWebGLSupported()) {
                    return (
                        <div style={{
                            width: '100%', height: '100%', display: 'flex',
                            alignItems: 'center', justifyContent: 'center',
                            background: t.placeholder.background, border: t.placeholder.border,
                            borderRadius: 4, color: '#ef4444', fontSize: 13,
                        }}>
                            当前浏览器不支持 WebGL，无法渲染 3D 组件
                        </div>
                    );
                }
                const globeTitle = String(c.title ?? '3D 地球');
                const autoRotate = c.autoRotate !== false;
                const rotateSpeed = Number(c.rotateSpeed ?? 10);
                const baseTexture = String(c.baseTexture ?? '');
                const heightTexture = String(c.heightTexture ?? '');
                const showAtmosphere = c.showAtmosphere !== false;
                const globeBgColor = String(c.globeBackground ?? '#000');
                const scatterData = Array.isArray(c.scatterData)
                    ? (c.scatterData as Array<{ name: string; value: [number, number, number] }>)
                    : [];
                const flowData = Array.isArray(c.flowData)
                    ? (c.flowData as Array<{ coords: [number, number][] }>)
                    : [];

                const globeSeries: Array<Record<string, unknown>> = [];
                if (scatterData.length > 0) {
                    globeSeries.push({
                        type: 'scatter3D',
                        coordinateSystem: 'globe',
                        data: scatterData.map(d => ({
                            name: d.name,
                            value: d.value,
                        })),
                        symbolSize: Number(c.pointSize ?? 12),
                        itemStyle: { color: t.echarts.colorPalette[0] },
                        label: { show: true, formatter: '{b}', textStyle: { color: '#fff', fontSize: 10 } },
                    });
                }
                if (flowData.length > 0) {
                    globeSeries.push({
                        type: 'lines3D',
                        coordinateSystem: 'globe',
                        effect: { show: true, trailLength: 0.2, trailWidth: 2, trailOpacity: 0.6 },
                        lineStyle: { width: 1, color: t.echarts.colorPalette[1], opacity: 0.6 },
                        data: flowData.map(f => ({ coords: f.coords })),
                        blendMode: 'lighter',
                    });
                }

                return renderEChartWithHandles({
                    backgroundColor: globeBgColor,
                    globe: {
                        baseTexture: baseTexture || undefined,
                        heightTexture: heightTexture || undefined,
                        shading: 'color',
                        viewControl: {
                            autoRotate,
                            autoRotateSpeed: rotateSpeed,
                            distance: Number(c.viewDistance ?? 200),
                        },
                        light: {
                            ambient: { intensity: 0.6 },
                            main: { intensity: 1.2 },
                        },
                        atmosphere: showAtmosphere ? { show: true, glowPower: 6 } : undefined,
                    },
                    series: globeSeries,
                    title: { text: globeTitle, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center', top: 8 },
                }, echartsClickHandler);
            }

            case 'bar3d-chart': {
                if (!isWebGLSupported()) {
                    return (
                        <div style={{
                            width: '100%', height: '100%', display: 'flex',
                            alignItems: 'center', justifyContent: 'center',
                            background: t.placeholder.background, border: t.placeholder.border,
                            borderRadius: 4, color: '#ef4444', fontSize: 13,
                        }}>
                            当前浏览器不支持 WebGL，无法渲染 3D 组件
                        </div>
                    );
                }
                const bar3dTitle = String(c.title ?? '3D 柱状图');
                const xData = Array.isArray(c.xAxisData) ? c.xAxisData as string[] : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'];
                const yData = Array.isArray(c.yAxisData) ? c.yAxisData as string[] : ['A', 'B', 'C'];
                const bar3dData = Array.isArray(c.data)
                    ? (c.data as Array<[number, number, number]>)
                    : xData.flatMap((_, xi) => yData.map((__, yi) => [xi, yi, Math.round(Math.random() * 100)] as [number, number, number]));
                const bar3dMax = Math.max(1, ...bar3dData.map(d => d[2]));
                const colorRangeRaw = c.colorRange as [string, string] | undefined;
                const colorRange = colorRangeRaw ?? ['#313695', '#a50026'];
                const viewAlpha = Number(c.viewAlpha ?? 40);
                const viewBeta = Number(c.viewBeta ?? 30);

                return renderEChartWithHandles({
                    ...themeOptions,
                    title: { text: bar3dTitle, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center', top: 8 },
                    tooltip: {},
                    visualMap: {
                        max: bar3dMax,
                        inRange: { color: colorRange },
                        textStyle: { color: t.textPrimary },
                    },
                    xAxis3D: { type: 'category', data: xData, axisLabel: { color: t.textPrimary } },
                    yAxis3D: { type: 'category', data: yData, axisLabel: { color: t.textPrimary } },
                    zAxis3D: { type: 'value', axisLabel: { color: t.textPrimary } },
                    grid3D: {
                        boxWidth: Number(c.boxWidth ?? 100),
                        boxDepth: Number(c.boxDepth ?? 80),
                        boxHeight: Number(c.boxHeight ?? 60),
                        viewControl: { alpha: viewAlpha, beta: viewBeta, autoRotate: c.autoRotate === true },
                        light: { main: { intensity: 1.2 }, ambient: { intensity: 0.3 } },
                    },
                    series: [{
                        type: 'bar3D',
                        data: bar3dData.map(d => ({ value: [d[0], d[1], d[2]] })),
                        shading: 'lambert',
                        label: {
                            show: c.showLabel === true,
                            textStyle: { color: '#fff', fontSize: 10 },
                            formatter: (p: Record<string, unknown>) => String((p.value as number[])?.[2] ?? ''),
                        },
                    }],
                }, echartsClickHandler);
            }

            case 'scatter3d-chart': {
                if (!isWebGLSupported()) {
                    return (
                        <div style={{
                            width: '100%', height: '100%', display: 'flex',
                            alignItems: 'center', justifyContent: 'center',
                            background: t.placeholder.background, border: t.placeholder.border,
                            borderRadius: 4, color: '#ef4444', fontSize: 13,
                        }}>
                            当前浏览器不支持 WebGL，无法渲染 3D 组件
                        </div>
                    );
                }
                const scatter3dTitle = String(c.title ?? '3D 散点图');
                const scatter3dData = Array.isArray(c.data)
                    ? (c.data as Array<[number, number, number]>)
                    : Array.from({ length: 30 }, () => [
                        Math.round(Math.random() * 100),
                        Math.round(Math.random() * 100),
                        Math.round(Math.random() * 100),
                    ] as [number, number, number]);
                const scatter3dMax = Math.max(1, ...scatter3dData.map(d => d[2]));
                const sColorRange = (c.colorRange as [string, string]) ?? ['#50a3ba', '#eac736'];
                const sPointSize = Number(c.pointSize ?? 8);

                return renderEChartWithHandles({
                    ...themeOptions,
                    title: { text: scatter3dTitle, textStyle: { color: t.textPrimary, fontSize: 14 }, left: 'center', top: 8 },
                    tooltip: {},
                    visualMap: {
                        max: scatter3dMax,
                        inRange: { color: sColorRange },
                        dimension: 2,
                        textStyle: { color: t.textPrimary },
                    },
                    xAxis3D: { type: 'value', axisLabel: { color: t.textPrimary }, name: String(c.xAxisName ?? 'X') },
                    yAxis3D: { type: 'value', axisLabel: { color: t.textPrimary }, name: String(c.yAxisName ?? 'Y') },
                    zAxis3D: { type: 'value', axisLabel: { color: t.textPrimary }, name: String(c.zAxisName ?? 'Z') },
                    grid3D: {
                        viewControl: {
                            alpha: Number(c.viewAlpha ?? 40),
                            beta: Number(c.viewBeta ?? 30),
                            autoRotate: c.autoRotate === true,
                        },
                        light: { main: { intensity: 1.2 }, ambient: { intensity: 0.3 } },
                    },
                    series: [{
                        type: 'scatter3D',
                        data: scatter3dData,
                        symbolSize: sPointSize,
                        itemStyle: { opacity: 0.8 },
                        label: {
                            show: c.showLabel === true,
                            textStyle: { color: '#fff', fontSize: 10 },
                        },
                    }],
                }, echartsClickHandler);
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
    }, [
        type,
        effectiveConfig,
        width,
        height,
        currentTime,
        echartsClickHandler,
        t,
        theme,
        themeOptions,
        EChartsComponent,
        dataViewModule,
        borderBoxComponents,
        decorationComponents,
        cardData,
        component,
        mode,
        hasMapFn,
        mapReadyVersion,
        mapDrillRegion,
        tableSort,
        tablePage,
        filterInputDraft,
        legendDragPreview,
        clearLegendDragHandlers,
        runtime.values,
        runtime,
        runtimePlugin,
    ]);

    return (
        !visibleByVariableRule ? null : (
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
                                        onClick={() => {
                                            runtime.trackEvent({
                                                kind: 'drill-up',
                                                key: 'drillDepth',
                                                value: String(crumb.depth),
                                                source: `drill:${component.id}`,
                                            });
                                            drillState.handleRollUp(crumb.depth);
                                        }}
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
            {pluginMeta && !runtimePlugin && (
                <div style={{
                    position: 'absolute',
                    bottom: 4,
                    right: 4,
                    fontSize: 10,
                    color: '#fbbf24',
                    background: 'rgba(30,41,59,0.85)',
                    padding: '2px 6px',
                    borderRadius: 3,
                    maxWidth: '60%',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }} title="插件未加载，已使用基础组件渲染">
                    插件未加载，已降级
                </div>
                )}
            </div>
        )
    );
});
