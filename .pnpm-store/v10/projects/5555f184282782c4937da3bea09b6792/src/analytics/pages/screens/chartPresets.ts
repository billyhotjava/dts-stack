import type { ComponentType } from './types';

export type ChartPreset = 'business' | 'compact' | 'clear';

export const CHART_COMPONENT_TYPES: ReadonlySet<ComponentType> = new Set([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'gauge-chart',
    'scatter-chart',
    'radar-chart',
    'funnel-chart',
    'map-chart',
    'combo-chart',
    'wordcloud-chart',
    'treemap-chart',
    'sunburst-chart',
    'waterfall-chart',
]);

const CHART_PRESET_PATCH: Record<ChartPreset, Record<string, unknown>> = {
    business: {
        titleFontSize: 16,
        axisFontSize: 12,
        legendFontSize: 12,
        legendDisplay: 'auto',
        autoLegendAvoid: true,
        compactLayoutPreset: 'auto',
        chartScalePercent: 100,
        xAxisLabelRotate: 0,
        xAxisLabelMaxLength: 0,
        seriesLabelPosition: 'auto',
    },
    compact: {
        titleFontSize: 13,
        axisFontSize: 11,
        legendFontSize: 11,
        legendDisplay: 'auto',
        autoLegendAvoid: true,
        compactLayoutPreset: 'auto',
        chartScalePercent: 90,
        xAxisLabelRotate: -30,
        xAxisLabelMaxLength: 10,
        seriesLabelPosition: 'inside',
        seriesLabelFontSize: 11,
    },
    clear: {
        legendDisplay: 'auto',
        autoLegendAvoid: true,
        compactLayoutPreset: 'auto',
        chartScalePercent: 100,
        xAxisLabelRotate: 0,
        xAxisLabelMaxLength: 0,
        seriesLabelPosition: 'auto',
    },
};

export function isChartComponentType(type: ComponentType | null | undefined): boolean {
    return Boolean(type && CHART_COMPONENT_TYPES.has(type));
}

export function applyChartPresetConfig(
    config: Record<string, unknown> | undefined,
    preset: ChartPreset,
): Record<string, unknown> {
    return {
        ...(config || {}),
        ...CHART_PRESET_PATCH[preset],
    };
}

export function applyChartPresetDefaults(
    config: Record<string, unknown> | undefined,
    preset: Exclude<ChartPreset, 'clear'>,
): Record<string, unknown> {
    const next = { ...(config || {}) } as Record<string, unknown>;
    const patch = CHART_PRESET_PATCH[preset];
    for (const [key, value] of Object.entries(patch)) {
        const current = next[key];
        if (current === undefined || current === null || current === '') {
            next[key] = value;
        }
    }
    return next;
}
