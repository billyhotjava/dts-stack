import type { CardData, ComponentType } from '../types';

/**
 * Map Card query result {rows, cols} to component config fields.
 * Only returns DATA fields (xAxisData, series, data, value, header).
 * Never overrides display fields (title, prefix, suffix, color, etc.).
 */
export function mapCardDataToConfig(
    type: ComponentType,
    cardData: CardData,
    config?: Record<string, unknown>,
): Record<string, unknown> {
    const { rows, cols } = cardData;
    if (!rows?.length || !cols?.length) return {};

    switch (type) {
        case 'number-card':
        case 'gauge-chart': {
            const vf = config?.valueField as string | undefined;
            if (vf) {
                const colIdx = cols.findIndex((c) => c.name === vf);
                if (colIdx >= 0) {
                    const lastRow = rows[rows.length - 1];
                    return { value: toNumber(lastRow?.[colIdx]) };
                }
            }
            return { value: toNumber(rows[0]?.[0]) };
        }

        case 'line-chart':
        case 'bar-chart':
            return mapAxisChart(rows, cols, config);

        case 'pie-chart': {
            const nameF = config?.nameField as string | undefined;
            const valF = config?.valueField as string | undefined;
            const nameIdx = nameF ? cols.findIndex((c) => c.name === nameF) : 0;
            const valIdx = valF ? cols.findIndex((c) => c.name === valF) : 1;
            return {
                data: rows.map((row) => ({
                    name: String(row[nameIdx >= 0 ? nameIdx : 0] ?? ''),
                    value: toNumber(row[valIdx >= 0 ? valIdx : 1]),
                })),
            };
        }

        case 'map-chart':
            return {
                regions: rows.map((row) => ({
                    name: String(row[0] ?? ''),
                    value: toNumber(row[1]),
                })),
            };

        case 'scroll-board':
        case 'table': {
            const fields = config?.fields as string[] | undefined;
            if (fields?.length) {
                const indices = fields.map((f) => cols.findIndex((c) => c.name === f)).filter((i) => i >= 0);
                if (indices.length) {
                    return {
                        header: indices.map((i) => cols[i].display_name || cols[i].name),
                        data: rows.map((row) => indices.map((i) => String(row[i] ?? ''))),
                        _sourceColumns: indices.map((i) => ({
                            name: cols[i].name,
                            displayName: cols[i].display_name || cols[i].name,
                            baseType: cols[i].base_type,
                        })),
                    };
                }
            }
            return {
                header: cols.map((c) => c.display_name || c.name),
                data: rows.map((row) => row.map((cell) => String(cell ?? ''))),
                _sourceColumns: cols.map((c) => ({
                    name: c.name,
                    displayName: c.display_name || c.name,
                    baseType: c.base_type,
                })),
            };
        }

        case 'scroll-ranking':
            return {
                data: rows.map((row) => ({
                    name: String(row[0] ?? ''),
                    value: toNumber(row[1]),
                })),
            };

        case 'gantt-chart': {
            const colIndex = (name: string) => cols.findIndex((c) => c.name === name);
            return {
                tasks: rows.map((row) => ({
                    name: String(row[colIndex('node_task')] ?? ''),
                    type: String(row[colIndex('node_type')] ?? ''),
                    planDate: String(row[colIndex('plan_date')] ?? ''),
                    actualDate: String(row[colIndex('actual_date')] ?? ''),
                    isCompleted: Boolean(row[colIndex('is_completed')]),
                    isOverdue: Boolean(row[colIndex('is_overdue_completed')]),
                    isIncomplete: Boolean(row[colIndex('is_incomplete')]),
                    delayDays: toNumber(row[colIndex('delay_days')]),
                    riskLevel: String(row[colIndex('risk_level')] ?? ''),
                    owner: String(row[colIndex('owner')] ?? ''),
                })),
            };
        }

        default:
            return {};
    }
}

/** Map axis chart data respecting config.xAxisField and config.series[].field */
function mapAxisChart(
    rows: unknown[][],
    cols: CardData['cols'],
    config?: Record<string, unknown>,
): Record<string, unknown> {
    const xField = config?.xAxisField as string | undefined;
    const cfgSeries = config?.series as Array<{ field?: string; name?: string }> | undefined;

    // Resolve x-axis column index
    const xIdx = xField ? cols.findIndex((c) => c.name === xField) : 0;
    const effectiveXIdx = xIdx >= 0 ? xIdx : 0;
    const xAxisData = rows.map((row) => String(row[effectiveXIdx] ?? ''));

    // If config defines explicit series with field names, use them
    if (cfgSeries?.length && cfgSeries.some((s) => s.field)) {
        const series = cfgSeries
            .filter((s) => s.field)
            .map((s) => {
                const colIdx = cols.findIndex((c) => c.name === s.field);
                return {
                    name: s.name || s.field!,
                    data: colIdx >= 0
                        ? rows.map((row) => toNumber(row[colIdx]))
                        : rows.map(() => 0),
                };
            });
        return { xAxisData, series };
    }

    // Fallback: all non-x columns become series
    const series = cols
        .map((col, idx) => ({ col, idx }))
        .filter(({ idx }) => idx !== effectiveXIdx)
        .map(({ col, idx }) => ({
            name: col.display_name || col.name,
            data: rows.map((row) => toNumber(row[idx])),
        }));
    return { xAxisData, series };
}

function toNumber(val: unknown): number {
    if (typeof val === 'number') return val;
    const n = Number(val);
    return Number.isFinite(n) ? n : 0;
}
