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
            const valueSourceMode = String(config?.valueSourceMode ?? 'data').trim().toLowerCase();
            if (valueSourceMode === 'manual') {
                return {};
            }
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
            const firstIndex = (...names: string[]) => names.map(colIndex).find((idx) => idx >= 0) ?? -1;
            const levelIdx = firstIndex('level');
            const idIdx = firstIndex('id', 'node_id');
            const nameIdx = firstIndex('node_task', 'task_name', 'name');
            const typeIdx = firstIndex('node_type', 'task_type', 'type');
            const planIdx = firstIndex('plan_date', 'plan_start', 'start_date', 'planDate');
            const baselineStartIdx = firstIndex('baseline_start_date', 'baselineStartDate');
            const baselineEndIdx = firstIndex('baseline_end_date', 'baselineEndDate');
            const actualIdx = firstIndex('actual_date', 'actual_end', 'end_date', 'actualDate');
            const completedIdx = firstIndex('is_completed');
            const overdueIdx = firstIndex('is_overdue_completed', 'is_overdue');
            const incompleteIdx = firstIndex('is_incomplete');
            const delayIdx = firstIndex('delay_days', 'delayDays');
            const riskIdx = firstIndex('risk_level', 'riskLevel');
            const ownerIdx = firstIndex('owner', 'owner_dept', 'owner_user');
            const deptIdx = firstIndex('dept', 'ownerDept', 'owner_dept');
            const majorProjectIdIdx = firstIndex('major_project_id', 'majorProjectId');
            const majorProjectIdx = firstIndex('major_project_name', 'majorProjectName');
            const subprojectIdIdx = firstIndex('subproject_id', 'subprojectId');
            const subprojectIdx = firstIndex('subproject_name', 'subprojectName');
            const statusIdx = firstIndex('completion_status', 'status');
            // When a 'level' column exists (hierarchical SQL), only keep leaf 'node' rows
            // to avoid rendering summary rows (project/subsystem) as gantt bars.
            const effectiveRows = levelIdx >= 0
                ? rows.filter((row) => String(row[levelIdx]) === 'node')
                : rows;
            return {
                tasks: effectiveRows.map((row) => ({
                    id: idIdx >= 0 ? String(row[idIdx] ?? '') : '',
                    name: String(row[nameIdx] ?? ''),
                    type: String(row[typeIdx] ?? ''),
                    planDate: String(row[planIdx] ?? ''),
                    baselineStartDate: baselineStartIdx >= 0 ? String(row[baselineStartIdx] ?? '') : '',
                    baselineEndDate: baselineEndIdx >= 0 ? String(row[baselineEndIdx] ?? '') : '',
                    actualDate: String(row[actualIdx] ?? ''),
                    isCompleted: completedIdx >= 0 ? Boolean(row[completedIdx]) : false,
                    isOverdue: overdueIdx >= 0 ? Boolean(row[overdueIdx]) : toNumber(row[delayIdx]) > 0,
                    isIncomplete: incompleteIdx >= 0 ? Boolean(row[incompleteIdx]) : false,
                    delayDays: toNumber(row[delayIdx]),
                    riskLevel: String(row[riskIdx] ?? ''),
                    owner: String(row[ownerIdx] ?? ''),
                    dept: deptIdx >= 0 ? String(row[deptIdx] ?? '') : '',
                    majorProjectId: majorProjectIdIdx >= 0 ? String(row[majorProjectIdIdx] ?? '') : '',
                    majorProjectName: String(row[majorProjectIdx] ?? ''),
                    subprojectId: subprojectIdIdx >= 0 ? String(row[subprojectIdIdx] ?? '') : '',
                    subprojectName: String(row[subprojectIdx] ?? ''),
                    status: String(row[statusIdx] ?? ''),
                })),
            };
        }

        case 'funnel-chart': {
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

        case 'combo-chart':
            return mapAxisChart(rows, cols, config);

        case 'scatter-chart': {
            const xF = config?.xField as string | undefined;
            const yF = config?.yField as string | undefined;
            const sizeF = config?.sizeField as string | undefined;
            const catF = config?.categoryField as string | undefined;
            const xIdx = xF ? cols.findIndex((c) => c.name === xF) : 0;
            const yIdx = yF ? cols.findIndex((c) => c.name === yF) : 1;
            const sizeIdx = sizeF ? cols.findIndex((c) => c.name === sizeF) : -1;
            const catIdx = catF ? cols.findIndex((c) => c.name === catF) : -1;

            if (catIdx >= 0) {
                const groups = new Map<string, number[][]>();
                for (const row of rows) {
                    const cat = String(row[catIdx] ?? '');
                    const point = [toNumber(row[xIdx >= 0 ? xIdx : 0]), toNumber(row[yIdx >= 0 ? yIdx : 1])];
                    if (sizeIdx >= 0) point.push(toNumber(row[sizeIdx]));
                    if (!groups.has(cat)) groups.set(cat, []);
                    groups.get(cat)!.push(point);
                }
                return {
                    series: Array.from(groups.entries()).map(([name, data]) => ({ name, data })),
                };
            }
            return {
                data: rows.map((row) => {
                    const point = [toNumber(row[xIdx >= 0 ? xIdx : 0]), toNumber(row[yIdx >= 0 ? yIdx : 1])];
                    if (sizeIdx >= 0) point.push(toNumber(row[sizeIdx]));
                    return point;
                }),
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
