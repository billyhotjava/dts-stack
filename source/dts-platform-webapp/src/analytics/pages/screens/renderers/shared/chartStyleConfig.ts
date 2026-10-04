export type ChartPaddingKey = 'chartPaddingTop' | 'chartPaddingRight' | 'chartPaddingBottom' | 'chartPaddingLeft';

export interface AxisStyleConfig {
    xAxisCfg: Record<string, unknown>;
    yAxisCfg: Record<string, unknown>;
    axisFontSize: number;
    axisLabelColor?: string;
    yAxisLabelRotate: number;
    axisOverrides: {
        x: AxisOverrides;
        y: AxisOverrides;
    };
}

export interface AxisOverrides {
    show?: boolean;
    splitLineShow?: boolean;
    splitLineColor?: string;
    min?: number | string;
    max?: number | string;
    type?: string;
}

export interface LegendStyleConfig {
    legendDisplayOverride?: 'show' | 'hide';
    legendPositionOverride?: 'top' | 'bottom' | 'left' | 'right';
    legendColorOverride?: string;
    legendFontSize: number;
    legendReserveOverrideFromNested?: number;
    legendItemGapOverrideFromNested?: number;
}

export function pickFiniteNumber(value: unknown): number | undefined {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}

export function pickTrimmedString(value: unknown): string | undefined {
    return (typeof value === 'string' && value.trim()) ? value.trim() : undefined;
}

export function pickBoolean(value: unknown): boolean | undefined {
    return typeof value === 'boolean' ? value : undefined;
}

export function pickAxisBound(value: unknown): number | string | undefined {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim()) return value.trim();
    return undefined;
}

export function clampNumber(raw: unknown, fallback: number, min: number, max: number): number {
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.min(max, Math.max(min, parsed));
}

export function readPositivePaddingOverride(config: Record<string, unknown>, key: ChartPaddingKey): number | undefined {
    const parsed = Number(config[key]);
    if (!Number.isFinite(parsed) || parsed <= 0) return undefined;
    return Math.round(Math.max(0, parsed));
}

export function resolveAxisStyleConfig(config: Record<string, unknown>): AxisStyleConfig {
    const xAxisCfg = (config.xAxis && typeof config.xAxis === 'object' ? config.xAxis : {}) as Record<string, unknown>;
    const yAxisCfg = (config.yAxis && typeof config.yAxis === 'object' ? config.yAxis : {}) as Record<string, unknown>;

    return {
        xAxisCfg,
        yAxisCfg,
        axisFontSize: pickFiniteNumber(xAxisCfg.labelFontSize)
            ?? pickFiniteNumber(yAxisCfg.labelFontSize)
            ?? pickFiniteNumber(config.axisFontSize)
            ?? 15,
        axisLabelColor: pickTrimmedString(xAxisCfg.labelColor)
            ?? pickTrimmedString(yAxisCfg.labelColor)
            ?? pickTrimmedString(config.axisLabelColor),
        yAxisLabelRotate: pickFiniteNumber(yAxisCfg.labelRotate) ?? 0,
        axisOverrides: {
            x: {
                show: pickBoolean(xAxisCfg.show),
                splitLineShow: pickBoolean(xAxisCfg.splitLineShow),
                splitLineColor: pickTrimmedString(xAxisCfg.splitLineColor),
                min: pickAxisBound(xAxisCfg.min),
                max: pickAxisBound(xAxisCfg.max),
                type: pickTrimmedString(xAxisCfg.type),
            },
            y: {
                show: pickBoolean(yAxisCfg.show),
                splitLineShow: pickBoolean(yAxisCfg.splitLineShow),
                splitLineColor: pickTrimmedString(yAxisCfg.splitLineColor),
                min: pickAxisBound(yAxisCfg.min),
                max: pickAxisBound(yAxisCfg.max),
                type: pickTrimmedString(yAxisCfg.type),
            },
        },
    };
}

export function resolveLegendStyleConfig(config: Record<string, unknown>): LegendStyleConfig {
    const legendNested = (config.legend && typeof config.legend === 'object' ? config.legend : {}) as Partial<{
        show: boolean;
        position: 'top' | 'bottom' | 'left' | 'right';
        fontSize: number;
        color: string;
        reserveSize: number;
        itemGap: number;
    }>;

    return {
        legendDisplayOverride: legendNested.show === true
            ? 'show'
            : (legendNested.show === false ? 'hide' : undefined),
        legendPositionOverride: legendNested.position,
        legendColorOverride: pickTrimmedString(legendNested.color),
        legendFontSize: (typeof legendNested.fontSize === 'number' && legendNested.fontSize > 0
            ? legendNested.fontSize
            : (config.legendFontSize as number)) || 15,
        legendReserveOverrideFromNested: typeof legendNested.reserveSize === 'number' && legendNested.reserveSize >= 0
            ? legendNested.reserveSize
            : undefined,
        legendItemGapOverrideFromNested: typeof legendNested.itemGap === 'number' && legendNested.itemGap >= 0
            ? legendNested.itemGap
            : undefined,
    };
}

export function resolveSeriesColors(config: Record<string, unknown>): string[] {
    return Array.isArray(config.seriesColors)
        ? (config.seriesColors as string[]).filter((color) => typeof color === 'string' && color.trim().length > 0)
        : [];
}
