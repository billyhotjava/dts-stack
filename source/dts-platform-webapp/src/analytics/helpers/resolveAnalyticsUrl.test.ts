// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('@/global-config', () => ({
    GLOBAL_CONFIG: {
        routerHistory: 'hash',
        publicPath: '/',
    },
}));

const { resolveRouteHref, resolveRouteForOpen } = await import('./resolveAnalyticsUrl');

describe('resolveAnalyticsUrl', () => {
    const originalLocation = window.location;

    beforeEach(() => {
        Object.defineProperty(window, 'location', {
            value: { ...originalLocation, origin: 'https://example.com' },
            writable: true,
        });
    });

    afterEach(() => {
        Object.defineProperty(window, 'location', {
            value: originalLocation,
            writable: true,
        });
    });

    describe('resolveRouteHref (full URL for clipboard/sharing)', () => {
        it('should produce hash-prefixed full URL', () => {
            const result = resolveRouteHref('/bi/screens/42/preview');
            expect(result).toBe('https://example.com/#/bi/screens/42/preview');
        });

        it('should handle query parameters', () => {
            const result = resolveRouteHref('/bi/screens/42/preview?device=mobile');
            expect(result).toBe('https://example.com/#/bi/screens/42/preview?device=mobile');
        });

        it('should handle paths without leading slash', () => {
            const result = resolveRouteHref('bi/public/screen/abc');
            expect(result).toBe('https://example.com/#/bi/public/screen/abc');
        });
    });

    describe('resolveRouteForOpen (for window.open / location.href)', () => {
        it('should produce hash-prefixed URL for window.open', () => {
            const result = resolveRouteForOpen('/bi/screens/42/preview');
            expect(result).toBe('/#/bi/screens/42/preview');
        });

        it('should handle query parameters', () => {
            const result = resolveRouteForOpen('/bi/screens/42/preview?device=mobile');
            expect(result).toBe('/#/bi/screens/42/preview?device=mobile');
        });
    });
});
