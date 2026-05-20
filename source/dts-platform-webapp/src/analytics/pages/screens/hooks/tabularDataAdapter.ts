import type { CardData } from '../types';

type CardDataColumn = CardData['cols'][number];

function normalizeColumn(raw: unknown, index: number): CardDataColumn {
    if (typeof raw === 'string') {
        const name = raw.trim() || `col_${index + 1}`;
        return {
            name,
            display_name: name,
            base_type: 'type/Text',
        };
    }

    const col = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {};
    const name = String(col.name ?? col.field_ref ?? `col_${index + 1}`);
    return {
        name,
        display_name: String(col.display_name ?? col.displayName ?? col.label ?? name),
        base_type: String(col.base_type ?? col.baseType ?? col.semantic_type ?? col.semanticType ?? 'type/Text'),
    };
}

function normalizeColumns(
    rawCols: unknown,
    rawMetaCols: unknown,
    fallbackRows?: unknown[],
): CardDataColumn[] {
    if (Array.isArray(rawCols) && rawCols.length > 0) {
        return rawCols.map((item, index) => normalizeColumn(item, index));
    }
    if (Array.isArray(rawMetaCols) && rawMetaCols.length > 0) {
        return rawMetaCols.map((item, index) => normalizeColumn(item, index));
    }
    if (Array.isArray(fallbackRows) && fallbackRows.length > 0 && typeof fallbackRows[0] === 'object' && !Array.isArray(fallbackRows[0])) {
        const keySet = new Set<string>();
        for (const item of fallbackRows as Array<Record<string, unknown>>) {
            Object.keys(item || {}).forEach((key) => keySet.add(key));
        }
        return Array.from(keySet).map((key) => ({
            name: key,
            display_name: key,
            base_type: 'type/Text',
        }));
    }
    return [];
}

function normalizeRows(rawRows: unknown, cols: CardDataColumn[]): unknown[][] {
    if (!Array.isArray(rawRows)) {
        return [];
    }
    if (rawRows.length === 0) {
        return [];
    }
    if (Array.isArray(rawRows[0])) {
        return rawRows as unknown[][];
    }
    if (typeof rawRows[0] === 'object') {
        const objects = rawRows as Array<Record<string, unknown>>;
        const effectiveCols = cols.length > 0
            ? cols
            : normalizeColumns([], [], rawRows);
        return objects.map((row) => effectiveCols.map((col) => row?.[col.name] ?? null));
    }
    return rawRows.map((item) => [item]);
}

function buildFallbackColumns(normalizedRows: unknown[][]): CardDataColumn[] {
    const width = normalizedRows.reduce((max, row) => Math.max(max, row.length), 0);
    return Array.from({ length: width }, (_, index) => ({
        name: `col_${index + 1}`,
        display_name: `列${index + 1}`,
        base_type: 'type/Text',
    }));
}

export function normalizeTabularData(payload: unknown): CardData {
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
        const obj = payload as Record<string, unknown>;
        const rows = obj.rows;
        const cols = obj.cols;
        const resultsMetadata = obj.results_metadata && typeof obj.results_metadata === 'object'
            ? obj.results_metadata as Record<string, unknown>
            : {};
        const metaCols = resultsMetadata.columns;

        if (Array.isArray(rows)) {
            const normalizedCols = normalizeColumns(cols, metaCols, rows);
            const normalizedRows = normalizeRows(rows, normalizedCols);
            return {
                rows: normalizedRows,
                cols: normalizedCols.length > 0 ? normalizedCols : buildFallbackColumns(normalizedRows),
            };
        }

        if (obj.data && typeof obj.data === 'object') {
            return normalizeTabularData(obj.data);
        }

        const keys = Object.keys(obj);
        if (keys.length > 0) {
            return {
                rows: [keys.map((key) => obj[key] ?? null)],
                cols: keys.map((key) => ({ name: key, display_name: key, base_type: 'type/Text' })),
            };
        }
    }

    if (Array.isArray(payload) && payload.length > 0 && typeof payload[0] === 'object' && !Array.isArray(payload[0])) {
        const objects = payload as Array<Record<string, unknown>>;
        const keySet = new Set<string>();
        for (const row of objects) {
            Object.keys(row || {}).forEach((key) => keySet.add(key));
        }
        const keys = Array.from(keySet);
        return {
            rows: objects.map((row) => keys.map((key) => row?.[key] ?? null)),
            cols: keys.map((key) => ({ name: key, display_name: key, base_type: 'type/Text' })),
        };
    }

    if (Array.isArray(payload) && payload.length > 0 && Array.isArray(payload[0])) {
        const rows = payload as unknown[][];
        return {
            rows,
            cols: buildFallbackColumns(rows),
        };
    }

    if (Array.isArray(payload) && payload.length === 0) {
        return { rows: [], cols: [] };
    }

    throw new Error('数据源返回格式不支持行列化，请返回 rows/cols、对象数组或二维数组结构');
}
