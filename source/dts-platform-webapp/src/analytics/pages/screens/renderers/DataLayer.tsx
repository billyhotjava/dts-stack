/**
 * DataLayer — Extracted from ComponentRenderer.
 * Encapsulates data source resolution, parameter binding, drill state,
 * query context, card data fetching, and effectiveConfig computation.
 */

import { useEffect, useMemo } from 'react';
import type { CardData, ScreenComponent, FieldMapping } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { useDrillDown } from '../hooks/useDrillDown';
import { useSharedStore } from '../hooks/useSharedStore';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';
import { applyFieldMapping } from '../hooks/fieldMappingTransform';
import {
    normalizeParameterBindings, resolveDataSourceType,
    resolveComponentVariableVisibility,
} from './shared/chartUtils';
import type { ScreenRuntimeMeta } from '../ScreenRuntimeContext';

type ScreenRuntime = {
    values: Record<string, string>;
    runtimeMeta?: ScreenRuntimeMeta;
};

export interface UseComponentDataResult {
    cardData: CardData | null;
    cardLoading: boolean;
    cardError: string | null;
    effectiveConfig: Record<string, unknown>;
    drillState: ReturnType<typeof useDrillDown>;
    drillRuntimeEnabled: boolean;
    drillActive: boolean;
    mergedQueryParameters: Array<{ name: string; value: string }>;
    visibleByVariableRule: boolean;
    bindingParameters: Array<{ name: string; value: string }>;
}

export function useComponentData(
    component: ScreenComponent,
    mode: 'designer' | 'preview',
    runtime: ScreenRuntime,
): UseComponentDataResult {
    const { type, config, dataSource, drillDown } = component;
    const sharedStore = useSharedStore();

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
                // Support {{ shared.KEY }} references
                const sharedMatch = /^shared\.(.+)$/.exec(item.variableKey);
                if (sharedMatch) {
                    const sv = sharedStore.getValue(sharedMatch[1]);
                    value = sv != null ? String(sv) : "";
                } else {
                    value = runtime.values[item.variableKey] ?? "";
                }
            }
            if ((item.name || "").trim().length === 0) continue;
            out.push({ name: item.name, value: String(value ?? "") });
        }
        return out;
    }, [sourceBindings, runtime.values, sharedStore]);

    // Drill runtime state should remain available for template-defined drill paths
    // even when the current component is static and only uses breadcrumb/context state.
    const drillRuntimeEnabled = mode === "preview" && drillDown?.enabled === true;
    const drillActive = drillRuntimeEnabled && DRILLABLE_TYPES.has(type);
    const rootCardId = dataSourceType === "card" ? dataSource?.cardConfig?.cardId : undefined;
    const drillState = useDrillDown(
        drillRuntimeEnabled ? rootCardId : undefined,
        drillRuntimeEnabled ? drillDown : undefined,
    );

    const mergedQueryParameters = useMemo(() => {
        const merged = new Map<string, string>();
        for (const item of bindingParameters) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        for (const item of (drillRuntimeEnabled ? (drillState.queryParameters ?? []) : [])) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        return Array.from(merged.entries()).map(([name, value]) => ({ name, value }));
    }, [bindingParameters, drillRuntimeEnabled, drillState.queryParameters]);

    const queryContext = useMemo(() => ({
        source: "screen-component",
        componentId: component.id,
        componentType: type,
        mode,
        globalVariables: runtime.values,
        ...(runtime.runtimeMeta ? { runtimeMeta: runtime.runtimeMeta } : {}),
    }), [component.id, mode, runtime.runtimeMeta, runtime.values, type]);
    const visibleByVariableRule = mode !== 'preview'
        || resolveComponentVariableVisibility(config, runtime.values);

    // Card data source hook — pass drill overrides when active
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(
        visibleByVariableRule ? dataSource : undefined,
        drillRuntimeEnabled ? drillState.effectiveCardId : undefined,
        mergedQueryParameters.length > 0 ? mergedQueryParameters : undefined,
        queryContext,
    );

    // Write component exports to shared store after data loads
    useEffect(() => {
        if (!cardData || mode !== 'preview') return;
        const exports = config.exports as { key?: string; path?: string } | undefined;
        if (!exports?.key) return;
        const key = exports.key.trim();
        if (!key) return;
        let value: unknown = cardData;
        if (exports.path) {
            const parts = exports.path.split('.');
            let cursor: unknown = cardData;
            for (const part of parts) {
                if (cursor == null || typeof cursor !== 'object') { cursor = undefined; break; }
                cursor = (cursor as Record<string, unknown>)[part];
            }
            value = cursor;
        }
        sharedStore.setValue(key, value);
    }, [cardData, config.exports, mode, sharedStore]);

    // Merge card data into config: card data overrides data fields only, not display fields
    const effectiveConfig = useMemo(() => {
        // For static sources with inline header+data, construct CardData and map it
        let resolvedCardData = cardData;
        if (!resolvedCardData && Array.isArray(config.header) && Array.isArray(config.data)) {
            const header = config.header as string[];
            const data = config.data as string[][];
            if (header.length > 0) {
                resolvedCardData = {
                    cols: header.map((h) => ({ name: String(h), display_name: String(h), base_type: 'type/Text' })),
                    rows: data,
                };
            }
        }
        if (!resolvedCardData) return config;
        const fieldMapping = config._fieldMapping as FieldMapping | undefined;
        const useFieldMapping = config._useFieldMapping !== false && fieldMapping
            && (fieldMapping.dimension || (fieldMapping.measures && fieldMapping.measures.length > 0));
        if (useFieldMapping) {
            const mapped = applyFieldMapping(type, fieldMapping, resolvedCardData);
            return { ...config, ...mapped };
        }
        const mapped = mapCardDataToConfig(type, resolvedCardData, config);
        return { ...config, ...mapped };
    }, [config, cardData, type]);

    return {
        cardData,
        cardLoading,
        cardError,
        effectiveConfig,
        drillState,
        drillRuntimeEnabled,
        drillActive,
        mergedQueryParameters,
        visibleByVariableRule,
        bindingParameters,
    };
}
