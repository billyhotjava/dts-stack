import type { CardData, ComponentType, FieldMapping, FieldMappingAggregation } from '../types';

/**
 * Slot definitions per chart type.
 * Each slot has a key matching FieldMapping property, label, and whether it accepts multiple fields.
 */
export interface MappingSlot {
    key: keyof FieldMapping;
    label: string;
    required: boolean;
    multi?: boolean; // measures can accept multiple fields
    accept?: 'string' | 'number' | 'any';
}

const AXIS_CHART_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: 'X 轴 (维度)', required: true, accept: 'any' },
    { key: 'measures', label: 'Y 轴 (度量)', required: true, multi: true, accept: 'number' },
    { key: 'groupBy', label: '分组 (颜色)', required: false, accept: 'string' },
];

const PIE_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: '名称', required: true, accept: 'any' },
    { key: 'measures', label: '值', required: true, accept: 'number' },
];

const SCATTER_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: 'X 轴', required: true, accept: 'number' },
    { key: 'measures', label: 'Y 轴', required: true, accept: 'number' },
    { key: 'groupBy', label: '分组 (颜色)', required: false, accept: 'string' },
    { key: 'sizeField', label: '大小', required: false, accept: 'number' },
];

const GAUGE_SLOTS: MappingSlot[] = [
    { key: 'measures', label: '值', required: true, accept: 'number' },
];

const FUNNEL_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: '阶段', required: true, accept: 'any' },
    { key: 'measures', label: '值', required: true, accept: 'number' },
];

const MAP_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: '区域', required: true, accept: 'string' },
    { key: 'measures', label: '值', required: true, accept: 'number' },
];

const TABLE_SLOTS: MappingSlot[] = [
    { key: 'measures', label: '显示列', required: true, multi: true, accept: 'any' },
];

const RADAR_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: '指标', required: true, accept: 'any' },
    { key: 'measures', label: '值', required: true, multi: true, accept: 'number' },
    { key: 'groupBy', label: '分组', required: false, accept: 'string' },
];

const COMBO_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: 'X 轴 (维度)', required: true, accept: 'any' },
    { key: 'measures', label: 'Y 轴 (度量)', required: true, multi: true, accept: 'number' },
];

const TREEMAP_SLOTS: MappingSlot[] = [
    { key: 'dimension', label: '名称', required: true, accept: 'any' },
    { key: 'measures', label: '值', required: true, accept: 'number' },
    { key: 'groupBy', label: '父级分组', required: false, accept: 'string' },
];

const SLOT_MAP: Partial<Record<ComponentType, MappingSlot[]>> = {
    'line-chart': AXIS_CHART_SLOTS,
    'bar-chart': AXIS_CHART_SLOTS,
    'combo-chart': COMBO_SLOTS,
    'waterfall-chart': AXIS_CHART_SLOTS,
    'pie-chart': PIE_SLOTS,
    'scatter-chart': SCATTER_SLOTS,
    'gauge-chart': GAUGE_SLOTS,
    'number-card': GAUGE_SLOTS,
    'funnel-chart': FUNNEL_SLOTS,
    'map-chart': MAP_SLOTS,
    'radar-chart': RADAR_SLOTS,
    'table': TABLE_SLOTS,
    'scroll-board': TABLE_SLOTS,
    'scroll-ranking': PIE_SLOTS,
    'treemap-chart': TREEMAP_SLOTS,
    'sunburst-chart': TREEMAP_SLOTS,
};

export function getMappingSlots(type: ComponentType): MappingSlot[] {
    return SLOT_MAP[type] ?? [];
}

export function isMappable(type: ComponentType): boolean {
    return SLOT_MAP[type] != null;
}

/** Detect column base_type category */
function isNumericType(baseType: string): boolean {
    const t = (baseType || '').toLowerCase();
    return t.includes('int') || t.includes('float') || t.includes('double')
        || t.includes('decimal') || t.includes('number') || t.includes('numeric')
        || t.includes('bigint') || t.includes('real');
}

export function columnTypeIcon(baseType: string): string {
    if (isNumericType(baseType)) return '#';
    const t = (baseType || '').toLowerCase();
    if (t.includes('date') || t.includes('time') || t.includes('timestamp')) return '📅';
    return 'Aa';
}

// ── aggregation helpers ──

function aggregate(values: number[], agg: FieldMappingAggregation): number {
    if (values.length === 0) return 0;
    switch (agg) {
        case 'sum': return values.reduce((a, b) => a + b, 0);
        case 'count': return values.length;
        case 'avg': return values.reduce((a, b) => a + b, 0) / values.length;
        case 'min': return Math.min(...values);
        case 'max': return Math.max(...values);
        default: return values.reduce((a, b) => a + b, 0);
    }
}

function toNumber(val: unknown): number {
    if (typeof val === 'number') return val;
    const n = Number(val);
    return Number.isFinite(n) ? n : 0;
}

// ── main transform ──

/**
 * Transform CardData using FieldMapping into chart-consumable config fields.
 */
export function applyFieldMapping(
    type: ComponentType,
    mapping: FieldMapping,
    cardData: CardData,
): Record<string, unknown> {
    const { rows, cols } = cardData;
    if (!rows?.length || !cols?.length) return {};

    const colIndex = new Map(cols.map((c, i) => [c.name, i]));
    const getCol = (name: string): number => colIndex.get(name) ?? -1;
    const agg = mapping.aggregation ?? 'sum';

    const dimIdx = mapping.dimension ? getCol(mapping.dimension) : -1;
    const measureIdxs = (mapping.measures ?? []).map(getCol).filter(i => i >= 0);
    const groupIdx = mapping.groupBy ? getCol(mapping.groupBy) : -1;

    // Sort rows if requested
    let sortedRows = rows;
    if (mapping.sortField) {
        const sortIdx = getCol(mapping.sortField);
        if (sortIdx >= 0) {
            const dir = mapping.sortOrder === 'desc' ? -1 : 1;
            sortedRows = [...rows].sort((a, b) => {
                const va = a[sortIdx], vb = b[sortIdx];
                if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
                return String(va ?? '').localeCompare(String(vb ?? '')) * dir;
            });
        }
    }

    switch (type) {
        case 'gauge-chart':
        case 'number-card':
            if (measureIdxs.length > 0) {
                return { value: toNumber(sortedRows[0]?.[measureIdxs[0]]) };
            }
            return {};

        case 'pie-chart':
        case 'funnel-chart':
        case 'scroll-ranking':
            if (dimIdx >= 0 && measureIdxs.length > 0) {
                return {
                    data: sortedRows.map(row => ({
                        name: String(row[dimIdx] ?? ''),
                        value: toNumber(row[measureIdxs[0]]),
                    })),
                };
            }
            return {};

        case 'map-chart':
            if (dimIdx >= 0 && measureIdxs.length > 0) {
                return {
                    regions: sortedRows.map(row => ({
                        name: String(row[dimIdx] ?? ''),
                        value: toNumber(row[measureIdxs[0]]),
                    })),
                };
            }
            return {};

        case 'line-chart':
        case 'bar-chart':
        case 'combo-chart':
        case 'waterfall-chart': {
            if (dimIdx < 0 || measureIdxs.length === 0) return {};

            if (groupIdx >= 0 && measureIdxs.length === 1) {
                // Group-by mode: split rows into series by group value
                return buildGroupedAxisChart(sortedRows, dimIdx, measureIdxs[0], groupIdx, agg);
            }

            // Multi-measure mode
            const xAxisData = sortedRows.map(row => String(row[dimIdx] ?? ''));
            const series = measureIdxs.map(mIdx => ({
                name: cols[mIdx].display_name || cols[mIdx].name,
                data: sortedRows.map(row => toNumber(row[mIdx])),
            }));
            return { xAxisData, series };
        }

        case 'scatter-chart': {
            if (dimIdx < 0 || measureIdxs.length === 0) return {};
            const sizeIdx = mapping.sizeField ? getCol(mapping.sizeField) : -1;
            const scatterData = sortedRows.map(row => {
                const point: number[] = [toNumber(row[dimIdx]), toNumber(row[measureIdxs[0]])];
                if (sizeIdx >= 0) point.push(toNumber(row[sizeIdx]));
                return point;
            });
            return { data: scatterData };
        }

        case 'radar-chart': {
            if (dimIdx < 0 || measureIdxs.length === 0) return {};
            const indicator = sortedRows.map(row => ({
                name: String(row[dimIdx] ?? ''),
                max: undefined,
            }));

            if (groupIdx >= 0 && measureIdxs.length === 1) {
                const groups = new Map<string, number[]>();
                for (const row of sortedRows) {
                    const gv = String(row[groupIdx] ?? '');
                    if (!groups.has(gv)) groups.set(gv, []);
                    groups.get(gv)!.push(toNumber(row[measureIdxs[0]]));
                }
                const series = Array.from(groups.entries()).map(([name, values]) => ({
                    name,
                    data: values,
                }));
                return { indicator, series };
            }

            const series = measureIdxs.map(mIdx => ({
                name: cols[mIdx].display_name || cols[mIdx].name,
                data: sortedRows.map(row => toNumber(row[mIdx])),
            }));
            return { indicator, series };
        }

        case 'table':
        case 'scroll-board': {
            const selectedCols = measureIdxs.length > 0
                ? measureIdxs
                : cols.map((_, i) => i);
            return {
                header: selectedCols.map(i => cols[i].display_name || cols[i].name),
                data: sortedRows.map(row => selectedCols.map(i => String(row[i] ?? ''))),
            };
        }

        case 'treemap-chart':
        case 'sunburst-chart': {
            if (dimIdx < 0 || measureIdxs.length === 0) return {};
            if (groupIdx >= 0) {
                // Group into parent > children
                const groups = new Map<string, Array<{ name: string; value: number }>>();
                for (const row of sortedRows) {
                    const parent = String(row[groupIdx] ?? '');
                    const child = String(row[dimIdx] ?? '');
                    const val = toNumber(row[measureIdxs[0]]);
                    if (!groups.has(parent)) groups.set(parent, []);
                    groups.get(parent)!.push({ name: child, value: val });
                }
                return {
                    data: Array.from(groups.entries()).map(([name, children]) => ({
                        name,
                        children,
                    })),
                };
            }
            return {
                data: sortedRows.map(row => ({
                    name: String(row[dimIdx] ?? ''),
                    value: toNumber(row[measureIdxs[0]]),
                })),
            };
        }

        default:
            return {};
    }
}

/** Build axis chart with groupBy */
function buildGroupedAxisChart(
    rows: unknown[][],
    dimIdx: number,
    measureIdx: number,
    groupIdx: number,
    agg: FieldMappingAggregation,
): Record<string, unknown> {
    // Collect unique dimension values and group values
    const dimSet = new Set<string>();
    const groupSet = new Set<string>();
    const dataMap = new Map<string, Map<string, number[]>>();

    for (const row of rows) {
        const dim = String(row[dimIdx] ?? '');
        const group = String(row[groupIdx] ?? '');
        const val = toNumber(row[measureIdx]);
        dimSet.add(dim);
        groupSet.add(group);
        if (!dataMap.has(group)) dataMap.set(group, new Map());
        const gm = dataMap.get(group)!;
        if (!gm.has(dim)) gm.set(dim, []);
        gm.get(dim)!.push(val);
    }

    const xAxisData = Array.from(dimSet);
    const series = Array.from(groupSet).map(group => ({
        name: group,
        data: xAxisData.map(dim => {
            const vals = dataMap.get(group)?.get(dim);
            return vals ? aggregate(vals, agg) : 0;
        }),
    }));

    return { xAxisData, series };
}

/**
 * Detect stale field mappings: returns field names that no longer exist in _sourceColumns.
 */
export function detectStaleFields(
    mapping: FieldMapping,
    sourceColumns: Array<{ name: string }>,
): string[] {
    const colNames = new Set(sourceColumns.map(c => c.name));
    const stale: string[] = [];
    if (mapping.dimension && !colNames.has(mapping.dimension)) stale.push(mapping.dimension);
    for (const m of mapping.measures ?? []) {
        if (!colNames.has(m)) stale.push(m);
    }
    if (mapping.groupBy && !colNames.has(mapping.groupBy)) stale.push(mapping.groupBy);
    if (mapping.sizeField && !colNames.has(mapping.sizeField)) stale.push(mapping.sizeField);
    if (mapping.sortField && !colNames.has(mapping.sortField)) stale.push(mapping.sortField);
    return stale;
}
