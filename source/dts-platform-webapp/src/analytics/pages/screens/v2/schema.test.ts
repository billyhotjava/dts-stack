import { describe, it, expect } from 'vitest';
import {
    normalizeScreenConfigV2,
    validateScreenConfigV2,
} from './schema';
import {
    createEmptyScreenV2,
    isScreenConfigV2,
    DEFAULT_SCREEN_LAYOUT_V2,
} from './types';

describe('isScreenConfigV2', () => {
    it('returns true for schemaVersion===2', () => {
        expect(isScreenConfigV2({ schemaVersion: 2 })).toBe(true);
    });
    it('returns false for v1 / undefined / primitive', () => {
        expect(isScreenConfigV2({ schemaVersion: 1 })).toBe(false);
        expect(isScreenConfigV2({})).toBe(false);
        expect(isScreenConfigV2(null)).toBe(false);
        expect(isScreenConfigV2('v2')).toBe(false);
    });
});

describe('createEmptyScreenV2', () => {
    it('returns a valid ScreenConfigV2', () => {
        const s = createEmptyScreenV2();
        expect(isScreenConfigV2(s)).toBe(true);
        expect(s.layout).toEqual(DEFAULT_SCREEN_LAYOUT_V2);
        expect(s.components).toEqual([]);
    });
    it('honors overrides', () => {
        const s = createEmptyScreenV2({ name: 'X', id: 'abc' });
        expect(s.name).toBe('X');
        expect(s.id).toBe('abc');
    });
});

describe('normalizeScreenConfigV2', () => {
    it('fills defaults for empty input', () => {
        const { config, warnings } = normalizeScreenConfigV2({});
        expect(config.schemaVersion).toBe(2);
        expect(config.layout.cols).toBe(12);
        expect(config.layout.rowHeight).toBe('auto');
        expect(config.components).toEqual([]);
        expect(warnings.length).toBeGreaterThan(0);
    });

    it('warns when schemaVersion !== 2 but still coerces', () => {
        const { config, warnings } = normalizeScreenConfigV2({ schemaVersion: 1 });
        expect(config.schemaVersion).toBe(2);
        expect(warnings.some((w) => w.includes('schemaVersion'))).toBe(true);
    });

    it('clamps layout cols / trims out-of-range x+w', () => {
        const { config, warnings } = normalizeScreenConfigV2({
            schemaVersion: 2,
            layout: { cols: 12, rowHeight: 'auto', gap: 12 },
            components: [
                { id: 'a', type: 'kpi', layout: { x: 10, y: 0, w: 6, h: 2 }, config: {} },
            ],
        });
        expect(config.components[0].layout.x).toBe(10);
        expect(config.components[0].layout.w).toBe(2); // trimmed 6 → 12 - 10 = 2
        expect(warnings.some((w) => w.includes('裁剪'))).toBe(true);
    });

    it('auto-generates id for components missing it', () => {
        const { config } = normalizeScreenConfigV2({
            components: [{ type: 'text', config: {}, layout: { x: 0, y: 0, w: 2, h: 1 } }],
        });
        expect(config.components[0].id).toMatch(/^comp_/);
    });

    it('de-duplicates component ids', () => {
        const { config, warnings } = normalizeScreenConfigV2({
            components: [
                { id: 'dup', type: 'k', config: {}, layout: { x: 0, y: 0, w: 2, h: 1 } },
                { id: 'dup', type: 'k', config: {}, layout: { x: 2, y: 0, w: 2, h: 1 } },
            ],
        });
        const ids = config.components.map((c) => c.id);
        expect(new Set(ids).size).toBe(2);
        expect(warnings.some((w) => w.includes('重复'))).toBe(true);
    });

    it('skips component without type', () => {
        const { config, warnings } = normalizeScreenConfigV2({
            components: [
                { id: 'has-type', type: 'kpi', config: {}, layout: { x: 0, y: 0, w: 2, h: 1 } },
                { id: 'no-type', config: {}, layout: { x: 2, y: 0, w: 2, h: 1 } },
            ],
        });
        expect(config.components).toHaveLength(1);
        expect(config.components[0].id).toBe('has-type');
        expect(warnings.some((w) => w.includes('缺少 type'))).toBe(true);
    });

    it('coerces negative coordinates to 0', () => {
        const { config } = normalizeScreenConfigV2({
            components: [{ id: 'a', type: 'k', config: {}, layout: { x: -3, y: -1, w: 2, h: 2 } }],
        });
        expect(config.components[0].layout.x).toBe(0);
        expect(config.components[0].layout.y).toBe(0);
    });
});

describe('validateScreenConfigV2', () => {
    it('accepts a well-formed config', () => {
        const c = createEmptyScreenV2({
            components: [
                { id: 'a', type: 'kpi', config: {}, layout: { x: 0, y: 0, w: 3, h: 2 } },
            ],
        });
        expect(validateScreenConfigV2(c)).toEqual([]);
    });

    it('reports wrong schemaVersion', () => {
        const errs = validateScreenConfigV2({ ...createEmptyScreenV2(), schemaVersion: 1 as unknown as 2 });
        expect(errs.some((e) => e.includes('schemaVersion'))).toBe(true);
    });

    it('reports out-of-range x+w', () => {
        const c = createEmptyScreenV2({
            components: [
                { id: 'a', type: 'k', config: {}, layout: { x: 10, y: 0, w: 5, h: 2 } },
            ],
        });
        const errs = validateScreenConfigV2(c);
        expect(errs.some((e) => e.includes('超过 cols'))).toBe(true);
    });

    it('reports duplicate ids', () => {
        const c = createEmptyScreenV2({
            components: [
                { id: 'dup', type: 'k', config: {}, layout: { x: 0, y: 0, w: 2, h: 2 } },
                { id: 'dup', type: 'k', config: {}, layout: { x: 2, y: 0, w: 2, h: 2 } },
            ],
        });
        const errs = validateScreenConfigV2(c);
        expect(errs.some((e) => e.includes('重复'))).toBe(true);
    });

    it('reports zero-size components', () => {
        const c = createEmptyScreenV2({
            components: [
                { id: 'a', type: 'k', config: {}, layout: { x: 0, y: 0, w: 0, h: 2 } },
            ],
        });
        const errs = validateScreenConfigV2(c);
        expect(errs.some((e) => e.includes('layout.w'))).toBe(true);
    });
});
