import type { ComponentInteractionMapping, ScreenActionType } from '../../types';
import { resolveInteractionMappedValue, resolveInteractionValue } from './chartUtils';

const SCREEN_ACTION_TYPES = new Set<ScreenActionType>([
    'set-variable',
    'drill-down',
    'drill-up',
    'drill-view',
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

export function buildActionRuntimeParams(
    runtimeValues: Record<string, string> | undefined,
    params: Record<string, unknown>,
): Record<string, unknown> {
    const runtime = runtimeValues && typeof runtimeValues === 'object' ? runtimeValues : {};
    return {
        ...runtime,
        ...params,
        runtime,
    };
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
        ?? resolveInteractionValue(params, 'row[0]')
        ?? (rowValue == null ? undefined : String(rowValue));
}

export function normalizeDataPointClickPayload(params: Record<string, unknown>): Record<string, unknown> | null {
    const componentType = String(params.componentType ?? '').trim().toLowerCase();
    if (componentType && componentType !== 'series') {
        return null;
    }
    const hasDataItem = params.dataIndex !== undefined
        || params.name !== undefined
        || params.value !== undefined
        || params.data !== undefined;
    return hasDataItem ? params : null;
}

export function shouldRunDefaultDrill(options: {
    drillActive: boolean;
    canDrillDown: boolean;
    loading: boolean;
    actionCount: number;
}): boolean {
    return options.drillActive
        && options.canDrillDown
        && !options.loading
        && options.actionCount === 0;
}

function collapseAnalyticsBase(path: string): string {
    const raw = String(path || '').trim();
    if (!raw) {
        return '';
    }
    return raw.replace(/^(\/bi)+(\/|$)/i, '/bi$2');
}

type NormalizeRuntimeJumpUrlOptions = {
    currentOrigin?: string;
    resolveAppRoute?: (route: string) => string;
};

export function normalizeRuntimeJumpUrl(
    targetUrl: string,
    options: NormalizeRuntimeJumpUrlOptions = {},
): string {
    const raw = String(targetUrl || '').trim();
    if (!raw) {
        return '';
    }

    const resolveAppRoute = options.resolveAppRoute ?? ((route: string) => route);
    const currentOrigin = String(options.currentOrigin || '').trim();

    if (raw.startsWith('/')) {
        return resolveAppRoute(collapseAnalyticsBase(raw));
    }

    if (/^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(raw)) {
        try {
            const url = new URL(raw);
            if (!currentOrigin || url.origin !== currentOrigin) {
                return raw;
            }
            return resolveAppRoute(collapseAnalyticsBase(`${url.pathname}${url.search}${url.hash}`));
        } catch {
            return raw;
        }
    }

    return raw;
}
