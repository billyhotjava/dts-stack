// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../../../api/analyticsApi', () => ({
    analyticsApi: {
        listScreens: vi.fn(),
    },
}));

vi.mock('../../../helpers/resolveAnalyticsUrl', () => ({
    resolveRouteForOpen: (route: string) => route,
}));

describe('resolveScreenReferenceUrl - sentinel behavior', () => {
    let analyticsApi: { listScreens: ReturnType<typeof vi.fn> };
    let resolveScreenReferenceUrl: (targetUrl: string) => Promise<string>;

    beforeEach(async () => {
        vi.resetModules();
        vi.clearAllMocks();
        analyticsApi = (await import('../../../api/analyticsApi')).analyticsApi as unknown as {
            listScreens: ReturnType<typeof vi.fn>;
        };
        const mod = await import('./InteractionLayer');
        resolveScreenReferenceUrl = mod.resolveScreenReferenceUrl;
    });

    it('returns empty string when screen not found and no fallback', async () => {
        analyticsApi.listScreens.mockResolvedValue([]);
        const result = await resolveScreenReferenceUrl('screen-ref:NonExistent|');
        expect(result).toBe('');
    });

    it('returns explicit fallback when screen not found but fallback set', async () => {
        analyticsApi.listScreens.mockResolvedValue([]);
        const result = await resolveScreenReferenceUrl('screen-ref:NonExistent|/explicit/fallback');
        expect(result).toBe('/explicit/fallback');
    });

    it('returns resolved URL when screen found', async () => {
        analyticsApi.listScreens.mockResolvedValue([
            { id: 42, name: 'Target', updatedAt: '2026-01-01' },
        ]);
        const result = await resolveScreenReferenceUrl('screen-ref:Target|');
        expect(result).toContain('42');
        expect(result).toContain('preview');
    });

    it('returns empty string when listScreens fails and no fallback', async () => {
        analyticsApi.listScreens.mockRejectedValue(new Error('network'));
        const result = await resolveScreenReferenceUrl('screen-ref:Target|');
        expect(result).toBe('');
    });

    it('returns input as-is for non screen-ref URL', async () => {
        const result = await resolveScreenReferenceUrl('https://example.com/path');
        expect(result).toBe('https://example.com/path');
    });
});

describe('resolveScreenReferenceUrl - id-first lookup (P2 protocol)', () => {
    let analyticsApi: { listScreens: ReturnType<typeof vi.fn> };
    let resolveScreenReferenceUrl: (targetUrl: string) => Promise<string>;

    beforeEach(async () => {
        vi.resetModules();
        vi.clearAllMocks();
        analyticsApi = (await import('../../../api/analyticsApi')).analyticsApi as unknown as {
            listScreens: ReturnType<typeof vi.fn>;
        };
        const mod = await import('./InteractionLayer');
        resolveScreenReferenceUrl = mod.resolveScreenReferenceUrl;
    });

    it('prefers id over name when both present', async () => {
        // listScreens has id=99 with a totally different name; ref carries
        // stale name "OldName" and id=99. id wins.
        analyticsApi.listScreens.mockResolvedValue([
            { id: 99, name: 'RenamedScreen', updatedAt: '2026-04-01' },
            { id: 50, name: 'OldName', updatedAt: '2026-01-01' },
        ]);
        const result = await resolveScreenReferenceUrl(
            'screen-ref:OldName|%2Fbi%2Fscreens%2F99%2Fpreview|id%3D99'
        );
        expect(result).toContain('99');
        expect(result).toContain('preview');
        // Must NOT match the legacy "OldName" record (id=50).
        expect(result).not.toContain('50');
    });

    it('does NOT fall through to name match when id is stale', async () => {
        // The ref carries id=999 + name="Target". listScreens has a different
        // screen with the same name (id=42). When the user-supplied id is no
        // longer valid, we deliberately do NOT silently navigate to a screen
        // that "happens to share a name" — that would be the same fragile
        // name-as-identity behavior we are trying to eliminate. Instead, we
        // return the explicit fallback URL (which preserves the original
        // intent), and the resulting "预览不可用" page surfaces the broken link.
        analyticsApi.listScreens.mockResolvedValue([
            { id: 42, name: 'Target', updatedAt: '2026-01-01' },
        ]);
        const result = await resolveScreenReferenceUrl(
            'screen-ref:Target|%2Fbi%2Fscreens%2F999%2Fpreview|id%3D999'
        );
        expect(result).toBe('/bi/screens/999/preview');
        expect(result).not.toContain('42');
    });
});

describe('resolveScreenReferenceUrl - canonical id-based URLs (no resolution)', () => {
    let analyticsApi: { listScreens: ReturnType<typeof vi.fn> };
    let resolveScreenReferenceUrl: (targetUrl: string) => Promise<string>;

    beforeEach(async () => {
        vi.resetModules();
        vi.clearAllMocks();
        analyticsApi = (await import('../../../api/analyticsApi')).analyticsApi as unknown as {
            listScreens: ReturnType<typeof vi.fn>;
        };
        const mod = await import('./InteractionLayer');
        resolveScreenReferenceUrl = mod.resolveScreenReferenceUrl;
    });

    it('passes through /bi/screens/{id}/preview unchanged without hitting listScreens', async () => {
        // The id-based URL is the new canonical format. The resolver MUST NOT
        // re-resolve it via listScreens — that would re-introduce the name
        // lookup we are trying to eliminate.
        const result = await resolveScreenReferenceUrl('/bi/screens/42/preview');
        expect(result).toBe('/bi/screens/42/preview');
        expect(analyticsApi.listScreens).not.toHaveBeenCalled();
    });

    it('does NOT fuzzy-match names that differ from the legacy ref', async () => {
        // Linking by name is fundamentally wrong: only exact name match is
        // honored for legacy refs. Renamed screens require the user to re-pick
        // (which writes a new id-based URL).
        analyticsApi.listScreens.mockResolvedValue([
            { id: 7, name: '项目执行监控', updatedAt: '2026-04-01' },
        ]);
        const ref = 'screen-ref:' + encodeURIComponent('GPMC 项目执行监控') + '|';
        const result = await resolveScreenReferenceUrl(ref);
        // No fuzzy match — name does not equal exactly, so resolver returns
        // the (empty) fallback. UI surface (sentinel) means "do nothing" so
        // user notices and re-picks the target via dropdown.
        expect(result).toBe('');
    });
});

describe('resolveScreenReferenceUrl - legacy screen-ref id-segment lookup', () => {
    let analyticsApi: { listScreens: ReturnType<typeof vi.fn> };
    let resolveScreenReferenceUrl: (targetUrl: string) => Promise<string>;

    beforeEach(async () => {
        vi.resetModules();
        vi.clearAllMocks();
        analyticsApi = (await import('../../../api/analyticsApi')).analyticsApi as unknown as {
            listScreens: ReturnType<typeof vi.fn>;
        };
        const mod = await import('./InteractionLayer');
        resolveScreenReferenceUrl = mod.resolveScreenReferenceUrl;
    });

    it('honors id segment from legacy screen-ref:{name}|{fb}|id={id}', async () => {
        analyticsApi.listScreens.mockResolvedValue([
            { id: 99, name: 'WhateverNewName', updatedAt: '2026-04-01' },
        ]);
        const result = await resolveScreenReferenceUrl(
            'screen-ref:OldName|%2Fbi%2Fscreens%2F99%2Fpreview|id%3D99'
        );
        expect(result).toContain('99');
        expect(result).toContain('preview');
    });
});

describe('parseScreenReferenceUrl - protocol parsing', () => {
    let parseScreenReferenceUrl: (targetUrl: string) => { screenName: string; fallbackUrl: string | null; screenId: string | null } | null;

    beforeEach(async () => {
        vi.resetModules();
        const mod = await import('./InteractionLayer');
        parseScreenReferenceUrl = mod.parseScreenReferenceUrl;
    });

    it('parses 2-segment legacy format', () => {
        const parsed = parseScreenReferenceUrl('screen-ref:Foo|/fb');
        expect(parsed).toEqual({ screenName: 'Foo', fallbackUrl: '/fb', screenId: null });
    });

    it('parses 3-segment id-bearing legacy format', () => {
        const parsed = parseScreenReferenceUrl('screen-ref:Foo|/fb|id=42');
        expect(parsed).toEqual({ screenName: 'Foo', fallbackUrl: '/fb', screenId: '42' });
    });

    it('returns null for non-screen-ref URL', () => {
        expect(parseScreenReferenceUrl('https://example.com')).toBeNull();
        expect(parseScreenReferenceUrl('/bi/screens/42/preview')).toBeNull();
    });
});
