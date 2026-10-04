import type { ScreenConfig } from './types';
import { buildScreenPayload } from './screenSpec';

const STORAGE_PREFIX = 'dts.analytics.screenDesigner.recovery.v1';

export interface ScreenDraftRecoveryPayload {
    screenId: string;
    savedAt: number;
    config: ScreenConfig;
}

function sortObject(value: unknown): unknown {
    if (Array.isArray(value)) {
        return value.map(sortObject);
    }
    if (!value || typeof value !== 'object') {
        return value;
    }
    const row = value as Record<string, unknown>;
    return Object.keys(row)
        .sort()
        .reduce<Record<string, unknown>>((acc, key) => {
            const next = row[key];
            if (next !== undefined) {
                acc[key] = sortObject(next);
            }
            return acc;
        }, {});
}

export function buildScreenDraftRecoveryKey(screenId: string | number | undefined | null): string {
    const normalized = String(screenId || 'new').trim() || 'new';
    return `${STORAGE_PREFIX}:${normalized}`;
}

export function hasScreenDraftChanges(
    baseline: ScreenConfig | null | undefined,
    current: ScreenConfig | null | undefined,
): boolean {
    if (!baseline || !current) {
        return false;
    }
    return JSON.stringify(sortObject(buildScreenPayload(baseline)))
        !== JSON.stringify(sortObject(buildScreenPayload(current)));
}

export function saveScreenDraftRecovery(
    key: string,
    payload: ScreenDraftRecoveryPayload,
    storage: Storage | undefined = typeof window !== 'undefined' ? window.localStorage : undefined,
): void {
    if (!storage) return;
    storage.setItem(key, JSON.stringify(payload));
}

export function readScreenDraftRecovery(
    key: string,
    storage: Storage | undefined = typeof window !== 'undefined' ? window.localStorage : undefined,
): ScreenDraftRecoveryPayload | null {
    if (!storage) return null;
    const raw = storage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ScreenDraftRecoveryPayload;
    if (!parsed || typeof parsed !== 'object' || !parsed.config || typeof parsed.savedAt !== 'number') {
        return null;
    }
    return parsed;
}

export function clearScreenDraftRecovery(
    key: string,
    storage: Storage | undefined = typeof window !== 'undefined' ? window.localStorage : undefined,
): void {
    storage?.removeItem(key);
}
