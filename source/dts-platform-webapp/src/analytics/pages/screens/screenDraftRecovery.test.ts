import { describe, expect, it } from 'vitest';
import type { ScreenConfig } from './types';
import {
    buildScreenDraftRecoveryKey,
    hasScreenDraftChanges,
    readScreenDraftRecovery,
    saveScreenDraftRecovery,
    clearScreenDraftRecovery,
} from './screenDraftRecovery';

const baseConfig: ScreenConfig = {
    schemaVersion: 2,
    id: '42',
    name: 'Demo',
    width: 1920,
    height: 1080,
    backgroundColor: '#111827',
    components: [],
    globalVariables: [],
};

class MemoryStorage implements Storage {
    private data = new Map<string, string>();
    get length() { return this.data.size; }
    clear() { this.data.clear(); }
    getItem(key: string) { return this.data.get(key) ?? null; }
    key(index: number) { return Array.from(this.data.keys())[index] ?? null; }
    removeItem(key: string) { this.data.delete(key); }
    setItem(key: string, value: string) { this.data.set(key, value); }
}

describe('screenDraftRecovery', () => {
    it('detects persisted payload changes while ignoring non-payload metadata', () => {
        expect(hasScreenDraftChanges(baseConfig, { ...baseConfig, updatedAt: 'later' })).toBe(false);
        expect(hasScreenDraftChanges(baseConfig, { ...baseConfig, name: 'Changed' })).toBe(true);
    });

    it('round-trips recovery payloads through storage', () => {
        const storage = new MemoryStorage();
        const key = buildScreenDraftRecoveryKey(42);
        saveScreenDraftRecovery(key, { screenId: '42', savedAt: 1, config: baseConfig }, storage);

        expect(readScreenDraftRecovery(key, storage)?.config.name).toBe('Demo');

        clearScreenDraftRecovery(key, storage);
        expect(readScreenDraftRecovery(key, storage)).toBeNull();
    });
});
