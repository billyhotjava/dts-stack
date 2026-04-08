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
