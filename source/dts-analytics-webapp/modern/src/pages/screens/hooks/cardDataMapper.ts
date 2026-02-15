import type { CardData, ComponentType } from '../types';

/**
 * Map Card query result {rows, cols} to component config fields.
 * Only returns DATA fields (xAxisData, series, data, value, header).
 * Never overrides display fields (title, prefix, suffix, color, etc.).
 */
export function mapCardDataToConfig(
    type: ComponentType,
    cardData: CardData,
): Record<string, unknown> {
    const { rows, cols } = cardData;
    if (!rows?.length || !cols?.length) return {};

    switch (type) {
        case 'number-card':
        case 'gauge-chart':
            return { value: toNumber(rows[0]?.[0]) };

        case 'line-chart':
        case 'bar-chart':
            return mapAxisChart(rows, cols);

        case 'pie-chart':
            return {
                data: rows.map((row) => ({
                    name: String(row[0] ?? ''),
                    value: toNumber(row[1]),
                })),
            };

        case 'map-chart':
            return {
                regions: rows.map((row) => ({
                    name: String(row[0] ?? ''),
                    value: toNumber(row[1]),
                })),
            };

        case 'scroll-board':
        case 'table':
            return {
                header: cols.map((c) => c.display_name || c.name),
                data: rows.map((row) => row.map((cell) => String(cell ?? ''))),
                _sourceColumns: cols.map((c) => ({
                    name: c.name,
                    displayName: c.display_name || c.name,
                    baseType: c.base_type,
                })),
            };

        case 'scroll-ranking':
            return {
                data: rows.map((row) => ({
                    name: String(row[0] ?? ''),
                    value: toNumber(row[1]),
                })),
            };

        default:
            return {};
    }
}

/** col[0] → xAxisData, remaining cols → series[].data */
function mapAxisChart(
    rows: unknown[][],
    cols: CardData['cols'],
): Record<string, unknown> {
    const xAxisData = rows.map((row) => String(row[0] ?? ''));
    const series = cols.slice(1).map((col, idx) => ({
        name: col.display_name || col.name,
        data: rows.map((row) => toNumber(row[idx + 1])),
    }));
    return { xAxisData, series };
}

function toNumber(val: unknown): number {
    if (typeof val === 'number') return val;
    const n = Number(val);
    return Number.isFinite(n) ? n : 0;
}
