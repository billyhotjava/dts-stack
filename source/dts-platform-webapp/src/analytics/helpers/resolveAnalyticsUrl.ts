import { resolveAppHref } from '@/routes/constants';

/**
 * Build a full absolute URL (with origin) for a client-side route.
 * Correctly handles hash vs browser routing mode.
 *
 * Use for: shared links, clipboard URLs, embed codes.
 *
 * @example resolveRouteHref('/bi/screens/42/preview')
 *   hash mode  → "https://host/#/bi/screens/42/preview"
 *   browser    → "https://host/bi/screens/42/preview"
 */
export const resolveRouteHref = (routePath: string): string => {
    const href = resolveAppHref(routePath);
    return `${window.location.origin}${href}`;
};

/**
 * Build a URL suitable for window.open() or window.location.href assignment.
 * Same as resolveAppHref but exported under a name that makes intent clear.
 *
 * Use for: window.open(), window.location.href, window.location.assign().
 *
 * @example resolveRouteForOpen('/bi/screens/42/preview')
 *   hash mode  → "/#/bi/screens/42/preview"
 *   browser    → "/bi/screens/42/preview"
 */
export const resolveRouteForOpen = (routePath: string): string => {
    return resolveAppHref(routePath);
};
