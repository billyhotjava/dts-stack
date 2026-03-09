import type { ComponentInteractionMapping, ScreenActionType } from '../../types';
import { resolveInteractionMappedValue, resolveInteractionValue } from './chartUtils';

const SCREEN_ACTION_TYPES = new Set<ScreenActionType>([
    'set-variable',
    'drill-down',
    'drill-up',
    'jump-url',
    'open-panel',
    'emit-intent',
]);

export function normalizeScreenActionType(raw: unknown): ScreenActionType | null {
    const value = String(raw ?? '').trim().toLowerCase();
    if (!SCREEN_ACTION_TYPES.has(value as ScreenActionType)) {
        return null;
    }
    return value as ScreenActionType;
}

export function resolveActionMappingValues(
    params: Record<string, unknown>,
    mappings: ComponentInteractionMapping[] | undefined,
): Record<string, string> {
    if (!Array.isArray(mappings) || mappings.length === 0) {
        return {};
    }
    const next: Record<string, string> = {};
    for (const mapping of mappings) {
        const variableKey = String(mapping?.variableKey ?? '').trim();
        const sourcePath = String(mapping?.sourcePath ?? '').trim();
        if (!variableKey || !sourcePath) {
            continue;
        }
        const rawValue = resolveInteractionValue(params, sourcePath);
        const mappedValue = resolveInteractionMappedValue(rawValue, mapping);
        if (mappedValue == null) {
            continue;
        }
        next[variableKey] = mappedValue;
    }
    return next;
}

export function resolveActionTemplateText(template: string, params: Record<string, unknown>): string {
    const raw = String(template || '');
    if (!raw.trim()) {
        return '';
    }
    return raw.replace(/\{\{\s*([^}]+)\s*\}\}/g, (_, path: string) => {
        const value = resolveInteractionValue(params, path);
        return value == null ? '' : value;
    });
}

export function buildTableRowActionParams(
    header: string[] | undefined,
    row: Array<string | number | boolean | null | undefined> | undefined,
): Record<string, unknown> {
    const safeHeader = Array.isArray(header) ? header : [];
    const safeRow = Array.isArray(row) ? row : [];
    const out: Record<string, unknown> = {
        row: safeRow,
    };
    safeRow.forEach((value, index) => {
        out[`row[${index}]`] = value ?? '';
        const title = String(safeHeader[index] ?? '').trim();
        if (title) {
            out[title] = value ?? '';
        }
    });
    if (safeRow.length > 0 && out.name === undefined) {
        out.name = safeRow[0] ?? '';
    }
    if (safeRow.length > 1 && out.value === undefined) {
        out.value = safeRow[1] ?? '';
    }
    return out;
}

export function resolvePreferredDrillValue(params: Record<string, unknown>): string | undefined {
    const rowValue = Array.isArray(params.row) ? params.row[0] : undefined;
    return resolveInteractionValue(params, 'name')
        ?? resolveInteractionValue(params, 'data.name')
        ?? resolveInteractionValue(params, '项目')
        ?? resolveInteractionValue(params, 'row[0]')
        ?? (rowValue == null ? undefined : String(rowValue));
}
