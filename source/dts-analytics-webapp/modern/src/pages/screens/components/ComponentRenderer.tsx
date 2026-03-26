import { memo, useMemo, useEffect, useRef, useState, useCallback, type ComponentType, type MouseEvent as ReactMouseEvent } from 'react';
import type { CardData, ScreenComponent } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { useDrillDown } from '../hooks/useDrillDown';
import { useScreenRuntime } from '../ScreenRuntimeContext';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';
import { applyFieldMapping } from '../hooks/fieldMappingTransform';
import { useComponentData } from '../renderers/DataLayer';
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
    resolveFilterOptions, resolveTabOptions,
    resolveComponentVariableVisibility, resolveFilterOptionsFromData,
    normalizeFilterDebounceMs, normalizeCarouselItems, resolveCarouselItemsFromData,
    resolveFilterDefaultValue, resolveDateRangeDefaultValues,
} from '../renderers/shared/chartUtils';
import { resolveChartTitleLayout } from '../renderers/shared/chartTitleLayout';
import {
    buildTableRowActionParams,
    resolvePreferredDrillValue,
} from '../renderers/shared/actionUtils';
import { useComponentInteractions } from '../renderers/InteractionLayer';
import {
    compareTableValues, resolveTableConditionalStyle,
    normalizeColumnAlign, formatTableCell, clampColumnWidth, normalizeColumnFormatter,
    ThemedScrollTable, resolveBoundTableData,
} from '../renderers/shared/tableUtils';
import { renderFilter } from '../renderers/FilterRenderer';
import { renderECharts } from '../renderers/EChartsRenderer';
import { renderBasic } from '../renderers/BasicRenderer';
import { renderDataV } from '../renderers/DataVRenderer';
import { renderTable } from '../renderers/TableRenderer';

import { DelayReasonMatrix } from '../../project-cockpit/components/DelayReasonMatrix';

const ECHART_COMPONENT_TYPES = new Set([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'gauge-chart',
    'gantt-chart',
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
// - InteractionLayer.tsx (interaction hook + screen-reference URL resolution)

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

    const {
        cardData, cardLoading, cardError, effectiveConfig,
        drillState, drillRuntimeEnabled, drillActive,
        mergedQueryParameters, visibleByVariableRule, bindingParameters,
    } = useComponentData(component, mode, runtime);

    const {
        scheduleFilterVariableUpdate,
        interactionMappings,
        interactionJump,
        componentActions,
        navigateToResolvedUrl,
        executeComponentActions,
        echartsClickHandler,
        filterVariableTimersRef,
    } = useComponentInteractions(component, mode, runtime, drillState, drillRuntimeEnabled, drillActive);

    // Persist _sourceColumns to saved config so PropertyPanel can read them
    const onConfigMetaRef = useRef(onConfigMeta);
    onConfigMetaRef.current = onConfigMeta;
    const [titleDragPreview, setTitleDragPreview] = useState<{ x: number; y: number } | null>(null);
    const titleDragHandlersRef = useRef<{ move: (event: MouseEvent) => void; up: (event: MouseEvent) => void } | null>(null);
    const [legendDragPreview, setLegendDragPreview] = useState<{ x: number; y: number } | null>(null);
    const legendDragHandlersRef = useRef<{ move: (event: MouseEvent) => void; up: (event: MouseEvent) => void } | null>(null);
    const [chartDragPreview, setChartDragPreview] = useState<{ x: number; y: number } | null>(null);
    const chartDragHandlersRef = useRef<{ move: (event: MouseEvent) => void; up: (event: MouseEvent) => void } | null>(null);

    const clearTitleDragHandlers = useCallback(() => {
        const handlers = titleDragHandlersRef.current;
        if (!handlers) return;
        window.removeEventListener('mousemove', handlers.move);
        window.removeEventListener('mouseup', handlers.up);
        titleDragHandlersRef.current = null;
    }, []);

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
        clearTitleDragHandlers();
        clearLegendDragHandlers();
        clearChartDragHandlers();
    }, [clearChartDragHandlers, clearLegendDragHandlers, clearTitleDragHandlers]);

    useEffect(() => {
        setTitleDragPreview(null);
        setLegendDragPreview(null);
        setChartDragPreview(null);
        clearTitleDragHandlers();
        clearLegendDragHandlers();
        clearChartDragHandlers();
    }, [clearChartDragHandlers, clearLegendDragHandlers, clearTitleDragHandlers, component.id]);

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
    const filterInputDefaultValue = String(effectiveConfig.defaultValue ?? '').trim();
    const [filterInputDraft, setFilterInputDraft] = useState(filterInputRuntimeValue);
    useEffect(() => {
        setFilterInputDraft(filterInputRuntimeValue);
    }, [filterInputRuntimeValue, filterInputVariableKey]);

    useEffect(() => {
        if (type !== 'filter-input' || !filterInputVariableKey || filterInputRuntimeValue) return;
        if (!filterInputDefaultValue) return;
        runtime.setVariable(filterInputVariableKey, filterInputDefaultValue, `filter-input:init:${component.id}`);
    }, [component.id, filterInputDefaultValue, filterInputRuntimeValue, filterInputVariableKey, runtime, type]);

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

    const filterSelectVariableKey = useMemo(() => {
        if (type !== 'filter-select') return '';
        return String((effectiveConfig.variableKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const filterSelectOptions = useMemo(() => {
        if (type !== 'filter-select') return [] as Array<{ label: string; value: string }>;
        const sourceMode = String(effectiveConfig.optionSourceMode ?? 'manual').trim().toLowerCase();
        if (sourceMode === 'data') {
            const dynamicOptions = resolveFilterOptionsFromData(cardData, effectiveConfig);
            if (dynamicOptions.length > 0) {
                return dynamicOptions;
            }
        }
        return resolveFilterOptions(effectiveConfig.options);
    }, [cardData, effectiveConfig, type]);
    const filterSelectRuntimeValue = filterSelectVariableKey ? String(runtime.values[filterSelectVariableKey] ?? '') : '';
    const filterSelectDefaultValue = String(effectiveConfig.defaultValue ?? '').trim();

    useEffect(() => {
        if (type !== 'filter-select' || !filterSelectVariableKey || filterSelectRuntimeValue) return;
        const fallbackValue = resolveFilterDefaultValue('', filterSelectDefaultValue, filterSelectOptions);
        if (!fallbackValue) return;
        runtime.setVariable(filterSelectVariableKey, fallbackValue, `filter-select:init:${component.id}`);
    }, [
        component.id,
        filterSelectDefaultValue,
        filterSelectOptions,
        filterSelectRuntimeValue,
        filterSelectVariableKey,
        runtime,
        type,
    ]);

    const filterDateStartKey = useMemo(() => {
        if (type !== 'filter-date-range') return '';
        return String((effectiveConfig.startKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const filterDateEndKey = useMemo(() => {
        if (type !== 'filter-date-range') return '';
        return String((effectiveConfig.endKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const filterDateStartValue = filterDateStartKey ? String(runtime.values[filterDateStartKey] ?? '') : '';
    const filterDateEndValue = filterDateEndKey ? String(runtime.values[filterDateEndKey] ?? '') : '';

    useEffect(() => {
        if (type !== 'filter-date-range') return;
        const defaults = resolveDateRangeDefaultValues(
            filterDateStartValue,
            filterDateEndValue,
            effectiveConfig.defaultStartValue,
            effectiveConfig.defaultEndValue,
        );
        if (filterDateStartKey && !filterDateStartValue && defaults.startValue) {
            runtime.setVariable(filterDateStartKey, defaults.startValue, `filter-date-range:init:${component.id}:start`);
        }
        if (filterDateEndKey && !filterDateEndValue && defaults.endValue) {
            runtime.setVariable(filterDateEndKey, defaults.endValue, `filter-date-range:init:${component.id}:end`);
        }
    }, [
        component.id,
        effectiveConfig.defaultEndValue,
        effectiveConfig.defaultStartValue,
        filterDateEndKey,
        filterDateEndValue,
        filterDateStartKey,
        filterDateStartValue,
        runtime,
        type,
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
        const axisFontSize = (c.axisFontSize as number) || 15;
        const legendFontSize = (c.legendFontSize as number) || 15;
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
        const titleOffsetBoundX = Math.max(120, Math.round(width * 0.45));
        const titleOffsetBoundY = Math.max(80, Math.round(height * 0.35));
        const titleOffsetXBase = toNumber(c.titleOffsetX, 0, -titleOffsetBoundX, titleOffsetBoundX);
        const titleOffsetYBase = toNumber(c.titleOffsetY, 0, -titleOffsetBoundY, titleOffsetBoundY);
        const titleOffsetX = titleDragPreview ? titleDragPreview.x : titleOffsetXBase;
        const titleOffsetY = titleDragPreview ? titleDragPreview.y : titleOffsetYBase;
        const titleDefaultPosition = (() => {
            switch (type) {
                case 'pie-chart':
                case 'radar-chart':
                case 'funnel-chart':
                case 'wordcloud-chart':
                case 'globe-chart':
                case 'bar3d-chart':
                case 'scatter3d-chart':
                    return 'center' as const;
                default:
                    return 'left' as const;
            }
        })();
        const chartTitleLayout = resolveChartTitleLayout({
            text: titleText,
            width,
            height,
            fontSize: (c.titleFontSize as number) || 18,
            color: t.textPrimary,
            positionRaw: c.titlePosition,
            offsetX: titleOffsetX,
            offsetY: titleOffsetY,
            defaultPosition: titleDefaultPosition,
        });
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
        const titleDragEnabled = mode === 'designer'
            && c.titleDragEnabled === true
            && hasTitle;
        const titleDragHandleStyle = titleDragEnabled ? {
            position: 'absolute' as const,
            left: Math.max(2, Math.min(width - 14, Math.round(chartTitleLayout.handleRect.left + (chartTitleLayout.handleRect.width / 2)) - 6)),
            top: Math.max(2, Math.min(height - 14, Math.round(chartTitleLayout.handleRect.top + (chartTitleLayout.handleRect.height / 2)) - 6)),
            width: 12,
            height: 12,
            borderRadius: 999,
            border: `1px solid ${t.textPrimary}`,
            background: t.echarts.colorPalette?.[2] || t.accentColor,
            boxShadow: '0 1px 4px rgba(0,0,0,0.35)',
            cursor: 'grab',
            zIndex: 20,
            opacity: 0.92,
            padding: 0,
        } : null;
        const renderUnavailableState = (title: string, detail?: string) => (
            <div style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
                background: t.placeholder.background,
                border: t.placeholder.border,
                borderRadius: 8,
                color: t.placeholder.color,
                fontSize: 12,
                textAlign: 'center',
                padding: 12,
            }}>
                <strong style={{ fontSize: 12, fontWeight: 600 }}>{title}</strong>
                {detail ? (
                    <span style={{ fontSize: 11, opacity: 0.82, lineHeight: 1.5 }}>{detail}</span>
                ) : null}
            </div>
        );

        if (ECHART_COMPONENT_TYPES.has(type) && !EChartsComponent) {
            return renderUnavailableState('图表引擎未就绪', '正在加载 ECharts 运行时，请稍候。');
        }
        if (DATAV_COMPONENT_TYPES.has(type) && !dataViewModule) {
            return renderUnavailableState('DataV 运行时未就绪', '正在加载 DataV 组件运行时，请稍候。');
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
            const showTitleHandle = titleDragEnabled && !!titleDragHandleStyle;
            const showLegendHandle = legendDragEnabled && !!legendDragHandleStyle;
            const showChartHandle = chartDragEnabled && !!chartDragHandleStyle;
            if (!showTitleHandle && !showLegendHandle && !showChartHandle) {
                return chartNode;
            }
            const handleTitleHandleMouseDown = (event: ReactMouseEvent<HTMLButtonElement>) => {
                event.preventDefault();
                event.stopPropagation();
                clearTitleDragHandlers();
                clearLegendDragHandlers();
                clearChartDragHandlers();
                const startClientX = event.clientX;
                const startClientY = event.clientY;
                const startOffsetX = titleOffsetX;
                const startOffsetY = titleOffsetY;
                let lastOffsetX = startOffsetX;
                let lastOffsetY = startOffsetY;
                const clampX = (value: number) => Math.round(Math.min(titleOffsetBoundX, Math.max(-titleOffsetBoundX, value)));
                const clampY = (value: number) => Math.round(Math.min(titleOffsetBoundY, Math.max(-titleOffsetBoundY, value)));
                const move = (moveEvent: MouseEvent) => {
                    const deltaX = moveEvent.clientX - startClientX;
                    const deltaY = moveEvent.clientY - startClientY;
                    lastOffsetX = clampX(startOffsetX + deltaX);
                    lastOffsetY = clampY(startOffsetY + deltaY);
                    setTitleDragPreview({ x: lastOffsetX, y: lastOffsetY });
                };
                const up = () => {
                    clearTitleDragHandlers();
                    setTitleDragPreview(null);
                    if ((lastOffsetX !== startOffsetX || lastOffsetY !== startOffsetY) && onConfigMetaRef.current) {
                        onConfigMetaRef.current({
                            titleOffsetX: lastOffsetX,
                            titleOffsetY: lastOffsetY,
                            titleDragEnabled: true,
                        });
                    }
                };
                titleDragHandlersRef.current = { move, up };
                window.addEventListener('mousemove', move);
                window.addEventListener('mouseup', up);
            };
            const handleLegendHandleMouseDown = (event: ReactMouseEvent<HTMLButtonElement>) => {
                event.preventDefault();
                event.stopPropagation();
                clearTitleDragHandlers();
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
                clearTitleDragHandlers();
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
                    {showTitleHandle ? (
                        <button
                            type="button"
                            style={titleDragHandleStyle!}
                            onMouseDown={handleTitleHandleMouseDown}
                            title="拖拽微调标题位置"
                        />
                    ) : null}
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
            // ==================== ECharts 图表 (delegated to EChartsRenderer) ====================
            case 'line-chart':
            case 'bar-chart':
            case 'pie-chart':
            case 'gauge-chart':
            case 'gantt-chart':
            case 'radar-chart':
            case 'funnel-chart':
            case 'scatter-chart':
            case 'combo-chart':
            case 'treemap-chart':
            case 'sunburst-chart':
            case 'wordcloud-chart':
            case 'waterfall-chart':
            case 'map-chart':
                return renderECharts({
                    type, c, t, width, height, mode, componentId: component.id, runtime,
                    EChart, renderEChartWithHandles,
                    themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
                    axisFontSize, seriesLabelFontSize,
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
                });

            // ==================== 基础 + 形状 + 媒体组件 ====================
            case 'number-card':
            case 'title':
            case 'markdown-text':
            case 'richtext':
            case 'datetime':
            case 'countdown':
            case 'marquee':
            case 'carousel':
            case 'progress-bar':
            case 'tab-switcher':
            case 'shape':
            case 'container':
            case 'image':
            case 'video':
            case 'iframe':
                return renderBasic(type, {
                    c, t, component, runtime,
                    currentTime,
                    carouselItems, carouselIndex, setCarouselIndex, setCarouselPaused,
                    tabOptions, tabRuntimeValue, tabDefaultValue, tabVariableKey,
                });

            // ==================== Filter 组件 (delegated to FilterRenderer) ====================
            case 'filter-input':
            case 'filter-select':
            case 'filter-date-range':
                return renderFilter(type, {
                    c, t, theme, component, runtime,
                    filterInputDraft, setFilterInputDraft,
                    filterSelectVariableKey, filterSelectOptions,
                    filterDateStartKey, filterDateEndKey,
                    scheduleFilterVariableUpdate,
                });

            // ==================== DataV 组件 (delegated to DataVRenderer) ====================
            case 'border-box':
            case 'decoration':
                return renderDataV({ type, c, borderBoxComponents, decorationComponents, renderUnavailableState });

            // ==================== Table-family (delegated to TableRenderer) ====================
            case 'scroll-board':
            case 'table':
            case 'scroll-ranking':
                return renderTable({
                    type,
                    c,
                    t,
                    width,
                    height,
                    theme,
                    mode,
                    component,
                    runtime: runtime as any,
                    cardData,
                    tableSort,
                    setTableSort,
                    tablePage,
                    setTablePage,
                    drillState: drillState as any,
                    drillActive,
                    drillRuntimeEnabled,
                    componentActions: componentActions as any,
                    executeComponentActions,
                    dataViewModule: dataViewModule as Record<string, React.ComponentType<Record<string, unknown>>> | null,
                    renderUnavailableState,
                });

            // ==================== DataV 数据展示组件 ====================

            case 'table': {
                const tableRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
                if (tableRenderMode === 'delay-reason-matrix') {
                    const sourceCols = Array.isArray(cardData?.cols) ? cardData.cols : [];
                    const sourceRows = Array.isArray(cardData?.rows) ? cardData.rows : [];
                    const matrixRows = sourceRows.map((row) => Object.fromEntries(
                        sourceCols.map((col, index) => [col.name, row[index]]),
                    ));
                    const canRunMatrixActions = mode === 'preview' && componentActions.length > 0;
                    if (matrixRows.length === 0) {
                        return (
                            <div style={{
                                width: '100%',
                                height: '100%',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                borderRadius: 16,
                                border: '1px dashed rgba(148, 163, 184, 0.3)',
                                background: 'rgba(248, 250, 252, 0.85)',
                                color: t.textSecondary,
                                fontSize: 13,
                            }}>
                                当前筛选范围暂无归因矩阵数据。
                            </div>
                        );
                    }
                    return (
                        <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                            <DelayReasonMatrix
                                rows={matrixRows}
                                onDrillDept={canRunMatrixActions
                                    ? (dept) => {
                                        executeComponentActions({
                                            name: dept,
                                            dept,
                                            data: { dept },
                                        });
                                    }
                                    : undefined}
                                onDrillReason={canRunMatrixActions
                                    ? (dept, reason) => {
                                        executeComponentActions({
                                            name: reason,
                                            dept,
                                            reason,
                                            data: { dept, reason },
                                        });
                                    }
                                    : undefined}
                            />
                        </div>
                    );
                }
                const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'left' });
                const fontSize = (c.fontSize as number) || 16;
                const headerFontSize = (c.headerFontSize as number) || fontSize;
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
                const canRunTableActions = mode === 'preview' && componentActions.length > 0;
                const canRunTableDefaultDrill = mode === 'preview' && !canRunTableActions && drillRuntimeEnabled && drillState.canDrillDown;

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
                                                fontSize: headerFontSize,
                                                width: columnMeta[i]?.width ? `${columnMeta[i].width}%` : undefined,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                textAlign: columnMeta[i]?.headerAlign ?? columnMeta[i]?.align ?? 'left',
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
                                    <tr
                                        key={rowIndex}
                                        onClick={() => {
                                            const params = buildTableRowActionParams(displayHeader, row);
                                            if (canRunTableActions) {
                                                executeComponentActions(params);
                                                return;
                                            }
                                            if (canRunTableDefaultDrill) {
                                                const clickedValue = resolvePreferredDrillValue(params);
                                                if (!clickedValue) return;
                                                runtime.trackEvent({
                                                    kind: 'drill-down',
                                                    key: 'drillValue',
                                                    value: clickedValue,
                                                    source: `drill:${component.id}:table`,
                                                    meta: `depth=${drillState.breadcrumbs.length}`,
                                                });
                                                drillState.handleDrill(clickedValue);
                                            }
                                        }}
                                        style={{
                                            background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground,
                                            cursor: canRunTableActions || canRunTableDefaultDrill ? 'pointer' : 'default',
                                        }}
                                    >
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
                if (!ScrollRankingBoard) return renderUnavailableState('DataV 运行时未就绪');
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
                if (!WaterLevelPond) return renderUnavailableState('DataV 运行时未就绪');
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
                if (!DigitalFlop) return renderUnavailableState('DataV 运行时未就绪');
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
                    title: chartTitleLayout.titleOption,
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
                    title: chartTitleLayout.titleOption,
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
                    title: chartTitleLayout.titleOption,
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
                return renderUnavailableState('组件类型未注册', `当前运行态未找到 ${type} 的渲染器。`);
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

    const supportsRuntimeActionWrapper = mode === 'preview'
        && componentActions.length > 0
        && ['shape', 'title', 'number-card', 'markdown-text'].includes(type);
    const wrappedContent = supportsRuntimeActionWrapper ? (
        <div
            role="button"
            tabIndex={0}
            onClick={() => {
                executeComponentActions({
                    name: component.name,
                    title: typeof effectiveConfig.title === 'string' ? effectiveConfig.title : undefined,
                    text: typeof effectiveConfig.text === 'string' ? effectiveConfig.text : undefined,
                    value: effectiveConfig.value,
                    data: {
                        title: effectiveConfig.title,
                        text: effectiveConfig.text,
                        value: effectiveConfig.value,
                    },
                });
            }}
            onKeyDown={(event) => {
                if (event.key !== 'Enter' && event.key !== ' ') {
                    return;
                }
                event.preventDefault();
                executeComponentActions({
                    name: component.name,
                    title: typeof effectiveConfig.title === 'string' ? effectiveConfig.title : undefined,
                    text: typeof effectiveConfig.text === 'string' ? effectiveConfig.text : undefined,
                    value: effectiveConfig.value,
                    data: {
                        title: effectiveConfig.title,
                        text: effectiveConfig.text,
                        value: effectiveConfig.value,
                    },
                });
            }}
            style={{ width: '100%', height: '100%', cursor: 'pointer' }}
        >
            {content}
        </div>
    ) : content;

    return (
        !visibleByVariableRule ? null : (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
            {wrappedContent}
            {/* Drill-down breadcrumb overlay */}
            {drillRuntimeEnabled && drillState.breadcrumbs.length > 1 && (
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
                    插件未注册，已降级到基础组件
                </div>
                )}
            </div>
        )
    );
});
