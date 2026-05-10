import type { LayoutClipboardPayload, StyleClipboardPayload } from './types';

export type PropertyPanelDensity = 'focus' | 'full';

function safeGetItem(storage: Storage | null, key: string): string | null {
    try {
        return storage?.getItem(key) ?? null;
    } catch {
        return null;
    }
}

function safeSetItem(storage: Storage | null, key: string, value: string) {
    try {
        storage?.setItem(key, value);
    } catch {
        // ignore storage failure
    }
}

function safeRemoveItem(storage: Storage | null, key: string) {
    try {
        storage?.removeItem(key);
    } catch {
        // ignore storage failure
    }
}

export function readPanelDensity(storage: Storage | null, key: string): PropertyPanelDensity {
    return safeGetItem(storage, key) === 'full' ? 'full' : 'focus';
}

export function writePanelDensity(storage: Storage | null, key: string, value: PropertyPanelDensity) {
    safeSetItem(storage, key, value);
}

export function readCollapsedSections(storage: Storage | null, key: string, fallback: readonly string[]): string[] {
    const raw = safeGetItem(storage, key);
    if (!raw) {
        return [...fallback];
    }
    try {
        const parsed: unknown = JSON.parse(raw);
        return Array.isArray(parsed)
            ? parsed.filter((item): item is string => typeof item === 'string')
            : [...fallback];
    } catch {
        return [...fallback];
    }
}

export function writeCollapsedSections(storage: Storage | null, key: string, value: readonly string[]) {
    safeSetItem(storage, key, JSON.stringify(value));
}

export function readStyleClipboard(storage: Storage | null, key: string): StyleClipboardPayload | null {
    const raw = safeGetItem(storage, key);
    if (!raw) return null;
    try {
        const parsed: unknown = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object') return null;
        const payload = parsed as Partial<StyleClipboardPayload>;
        if (!payload.type || !payload.config || typeof payload.config !== 'object') return null;
        return payload as StyleClipboardPayload;
    } catch {
        return null;
    }
}

export function readLayoutClipboard(storage: Storage | null, key: string): LayoutClipboardPayload | null {
    const raw = safeGetItem(storage, key);
    if (!raw) return null;
    try {
        const parsed: unknown = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object') return null;
        const payload = parsed as Partial<LayoutClipboardPayload>;
        if (!Number.isFinite(payload.x) || !Number.isFinite(payload.y)) return null;
        if (!Number.isFinite(payload.width) || !Number.isFinite(payload.height)) return null;
        return payload as LayoutClipboardPayload;
    } catch {
        return null;
    }
}

export function writeNullableJson(storage: Storage | null, key: string, payload: unknown | null) {
    if (!payload) {
        safeRemoveItem(storage, key);
        return;
    }
    safeSetItem(storage, key, JSON.stringify(payload));
}
