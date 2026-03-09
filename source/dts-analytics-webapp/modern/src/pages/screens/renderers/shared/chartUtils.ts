/** Shared utility functions for screen component rendering. */

import type { CardData, CardParameterBinding, ComponentInteractionMapping, ScreenComponent } from '../../types';

const LEGACY_LIGHT_TEXT_COLORS = new Set(["#fff", "#ffffff", "#e5e7eb", "#d1d5db", "#cbd5e1", "#94a3b8"]);

export function resolveTextColor(candidate: string | undefined, fallback: string): string {
    if (!candidate || candidate.trim().length === 0) {
        return fallback;
    }
    const normalized = candidate.trim().toLowerCase();
    const fallbackNormalized = (fallback || "").trim().toLowerCase();
    if (LEGACY_LIGHT_TEXT_COLORS.has(normalized) && !LEGACY_LIGHT_TEXT_COLORS.has(fallbackNormalized)) {
        return fallback;
    }
    return candidate;
}

export function estimateVisualTextWidth(text: string, fontSize: number): number {
    const safeText = String(text ?? '');
    const safeFont = Math.max(10, Number.isFinite(fontSize) ? fontSize : 12);
    let units = 0;
    for (const ch of safeText) {
        if (/\s/.test(ch)) {
            units += 0.35;
            continue;
        }
        if (/[\u4e00-\u9fff\u3400-\u4dbf]/.test(ch)) {
            units += 1;
            continue;
        }
        if (/[A-Z0-9]/.test(ch)) {
            units += 0.68;
            continue;
        }
        units += 0.56;
    }
    return Math.ceil(units * safeFont);
}

export function truncateTextByVisualWidth(text: string, maxWidth: number, fontSize: number): string {
    const safeText = String(text ?? '');
    const safeMax = Math.max(24, Math.round(maxWidth || 0));
    if (estimateVisualTextWidth(safeText, fontSize) <= safeMax) {
        return safeText;
    }
    let out = '';
    for (const ch of safeText) {
        if (estimateVisualTextWidth(`${out}${ch}...`, fontSize) > safeMax) {
            break;
        }
        out += ch;
    }
    return out.length > 0 ? `${out}...` : '...';
}

export function normalizeParameterBindings(bindings: CardParameterBinding[] | undefined): CardParameterBinding[] {
    if (!Array.isArray(bindings)) return [];
    return bindings
        .map((item) => ({
            name: (item?.name ?? "").trim(),
            variableKey: (item?.variableKey ?? "").trim() || undefined,
            value: item?.value == null ? undefined : String(item.value),
        }))
        .filter((item) => item.name.length > 0);
}

export function resolveDataSourceType(dataSource: ScreenComponent["dataSource"]): string {
    const sourceType = (dataSource as { sourceType?: string } | undefined)?.sourceType;
    const type = (dataSource as { type?: string } | undefined)?.type;
    const normalized = (sourceType || type || "").toLowerCase();
    if (normalized === "database") return "sql";
    return normalized;
}

export function resolveInteractionValue(params: Record<string, unknown>, sourcePath: string): string | undefined {
    const path = (sourcePath || "").trim();
    if (!path) return undefined;

    const read = (obj: unknown, key: string): unknown => {
        if (!obj || typeof obj !== "object") return undefined;
        return (obj as Record<string, unknown>)[key];
    };

    const segments = path.split(".").filter((s) => s.length > 0);
    let current: unknown = params;
    for (const seg of segments) {
        current = read(current, seg);
    }

    if (current == null) return undefined;
    if (typeof current === "string") return current;
    if (typeof current === "number" || typeof current === "boolean") return String(current);
    return undefined;
}

export function normalizeInteractionTransform(raw: unknown): 'raw' | 'string' | 'number' | 'lowercase' | 'uppercase' {
    const value = String(raw ?? '').trim().toLowerCase();
    if (value === 'string' || value === 'number' || value === 'lowercase' || value === 'uppercase') {
        return value;
    }
    return 'raw';
}

export function resolveInteractionMappedValue(
    rawValue: string | undefined,
    mapping: ComponentInteractionMapping,
): string | undefined {
    const fallback = String(mapping.fallbackValue ?? '').trim();
    const transform = normalizeInteractionTransform(mapping.transform);
    const source = rawValue == null ? '' : String(rawValue);
    let next = source;
    if (transform === 'lowercase') {
        next = source.toLowerCase();
    } else if (transform === 'uppercase') {
        next = source.toUpperCase();
    } else if (transform === 'number') {
        const parsed = Number(source);
        if (!Number.isFinite(parsed)) {
            return fallback || undefined;
        }
        next = String(parsed);
    } else if (transform === 'string') {
        next = String(source);
    }
    if (next.trim().length === 0) {
        return fallback || undefined;
    }
    return next;
}

export function resolveInteractionUrlTemplate(template: string, params: Record<string, unknown>): string | undefined {
    const raw = String(template || '').trim();
    if (!raw) return undefined;
    const withValues = raw.replace(/\{\{\s*([^}]+)\s*\}\}/g, (_, path: string) => {
        const value = resolveInteractionValue(params, path);
        return value == null ? '' : encodeURIComponent(value);
    }).trim();
    if (!withValues) return undefined;
    if (/^https?:\/\//i.test(withValues) || withValues.startsWith('/')) {
        return withValues;
    }
    return undefined;
}

export function resolveFilterOptions(raw: unknown): Array<{ label: string; value: string }> {
    if (!Array.isArray(raw)) return [];
    const out: Array<{ label: string; value: string }> = [];
    for (const item of raw) {
        if (typeof item === 'string') {
            const text = item.trim();
            if (text) out.push({ label: text, value: text });
            continue;
        }
        if (item && typeof item === 'object') {
            const row = item as Record<string, unknown>;
            const value = String(row.value ?? '').trim();
            if (!value) continue;
            const label = String(row.label ?? value).trim() || value;
            out.push({ label, value });
        }
    }
    return out;
}

export function resolveFilterDefaultValue(
    currentValue: string,
    configuredDefault: unknown,
    options: Array<{ label: string; value: string }> = [],
): string {
    const current = String(currentValue ?? '').trim();
    if (current) {
        return current;
    }
    const fallback = String(configuredDefault ?? '').trim();
    if (fallback) {
        return fallback;
    }
    return String(options[0]?.value ?? '').trim();
}

export function resolveDateRangeDefaultValues(
    currentStartValue: string,
    currentEndValue: string,
    configuredStartDefault: unknown,
    configuredEndDefault: unknown,
): { startValue: string; endValue: string } {
    const startValue = String(currentStartValue ?? '').trim() || String(configuredStartDefault ?? '').trim();
    const endValue = String(currentEndValue ?? '').trim() || String(configuredEndDefault ?? '').trim();
    return { startValue, endValue };
}

export function resolveTabOptions(raw: unknown): Array<{ label: string; value: string }> {
    return resolveFilterOptions(raw);
}

export function normalizeVisibilityMatchValues(raw: unknown): string[] {
    if (Array.isArray(raw)) {
        return raw
            .map((item) => String(item ?? '').trim())
            .filter((item) => item.length > 0);
    }
    const text = String(raw ?? '').trim();
    if (!text) return [];
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

export function resolveComponentVariableVisibility(config: Record<string, unknown>, values: Record<string, string>): boolean {
    const enabled = config.visibilityRuleEnabled === true;
    if (!enabled) return true;
    const variableKey = String(config.visibilityVariableKey ?? '').trim();
    if (!variableKey) return true;
    const current = String(values?.[variableKey] ?? '').trim();
    const currentLower = current.toLowerCase();
    const mode = String(config.visibilityMatchMode ?? 'equals').trim().toLowerCase();
    const expectedValues = normalizeVisibilityMatchValues(
        config.visibilityMatchValues ?? config.visibilityMatchValue,
    );
    const expectedLower = expectedValues.map((item) => item.toLowerCase());
    const matched = expectedValues.length > 0 && expectedValues.includes(current);
    if (mode === 'not-equals' || mode === 'not-in') {
        return expectedValues.length === 0 ? true : !matched;
    }
    if (mode === 'contains') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.includes(item));
    }
    if (mode === 'not-contains') {
        if (expectedLower.length === 0) return true;
        return expectedLower.every((item) => item.length === 0 || !currentLower.includes(item));
    }
    if (mode === 'starts-with') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.startsWith(item));
    }
    if (mode === 'ends-with') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.endsWith(item));
    }
    if (mode === 'empty') {
        return current.length === 0;
    }
    if (mode === 'not-empty') {
        return current.length > 0;
    }
    return expectedValues.length === 0 ? true : matched;
}

export function resolveColumnIndex(cols: CardData['cols'], rawField: unknown, fallback = 0): number {
    if (!Array.isArray(cols) || cols.length === 0) {
        return -1;
    }
    const field = String(rawField ?? '').trim();
    if (!field) {
        return Math.max(0, Math.min(cols.length - 1, fallback));
    }
    if (/^\d+$/.test(field)) {
        const byNumber = Number(field) - 1;
        if (Number.isFinite(byNumber) && byNumber >= 0 && byNumber < cols.length) {
            return byNumber;
        }
    }
    const normalized = field.toLowerCase();
    const byName = cols.findIndex((col) => String(col.name || '').toLowerCase() === normalized);
    if (byName >= 0) return byName;
    const byDisplayName = cols.findIndex((col) => String(col.display_name || '').toLowerCase() === normalized);
    if (byDisplayName >= 0) return byDisplayName;
    return Math.max(0, Math.min(cols.length - 1, fallback));
}

export function resolveFilterOptionsFromData(data: CardData | null, config: Record<string, unknown>): Array<{ label: string; value: string }> {
    if (!data || !Array.isArray(data.rows) || !Array.isArray(data.cols) || data.cols.length === 0) {
        return [];
    }
    const maxRaw = Number(config.dataOptionMax ?? 200);
    const maxOptions = Number.isFinite(maxRaw) ? Math.max(1, Math.min(2000, Math.floor(maxRaw))) : 200;
    const valueIndex = resolveColumnIndex(data.cols, config.dataOptionValueField, 0);
    if (valueIndex < 0) {
        return [];
    }
    const labelIndex = resolveColumnIndex(data.cols, config.dataOptionLabelField, valueIndex);
    const dedupe = new Set<string>();
    const out: Array<{ label: string; value: string }> = [];
    for (const row of data.rows) {
        if (!Array.isArray(row)) continue;
        const valueRaw = row[valueIndex];
        if (valueRaw == null) continue;
        const value = String(valueRaw).trim();
        if (!value || dedupe.has(value)) continue;
        const labelRaw = row[labelIndex];
        const label = String(labelRaw ?? value).trim() || value;
        dedupe.add(value);
        out.push({ label, value });
        if (out.length >= maxOptions) break;
    }
    return out;
}

export function normalizeFilterDebounceMs(raw: unknown): number {
    const value = Number(raw ?? 0);
    if (!Number.isFinite(value) || value <= 0) return 0;
    return Math.max(50, Math.min(5000, Math.round(value)));
}

export function normalizeCarouselItems(raw: unknown): string[] {
    if (Array.isArray(raw)) {
        return raw
            .map((item) => String(item ?? '').trim())
            .filter((item) => item.length > 0)
            .slice(0, 200);
    }
    const text = String(raw ?? '').trim();
    if (!text) return [];
    return text
        .split(/\r?\n/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .slice(0, 200);
}

export function resolveCarouselItemsFromData(data: CardData | null, config: Record<string, unknown>): string[] {
    if (!data || !Array.isArray(data.rows) || data.rows.length === 0 || !Array.isArray(data.cols) || data.cols.length === 0) {
        return [];
    }
    const max = Number(config.dataItemMax ?? 50);
    const safeMax = Number.isFinite(max) ? Math.max(1, Math.min(500, Math.floor(max))) : 50;
    const contentIndex = resolveColumnIndex(data.cols, config.dataItemField, 0);
    if (contentIndex < 0) {
        return [];
    }
    const out: string[] = [];
    for (const row of data.rows) {
        if (!Array.isArray(row) || row.length <= contentIndex) continue;
        const text = String(row[contentIndex] ?? '').trim();
        if (!text) continue;
        out.push(text);
        if (out.length >= safeMax) break;
    }
    return out;
}
