/**
 * Security sanitization utilities for the screen designer.
 */

/**
 * Safely wraps a URL as a CSS `url(...)` value for `backgroundImage`.
 * Rejects values that could allow CSS injection.
 */
export function safeCssBackgroundUrl(url: string | undefined): string | undefined {
    if (!url) return undefined;
    const trimmed = url.trim();
    if (!/^(https?:\/\/|\/|data:image\/)/i.test(trimmed)) return undefined;
    if (/[);{}\\]/.test(trimmed)) return undefined;
    return `url("${trimmed.replace(/"/g, '')}")`;
}

/**
 * HTML-escape a string for safe interpolation into HTML markup.
 */
export function escapeHtml(str: string): string {
    return str
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

/**
 * Validate a URL for safe use in `src` attributes (img, video, iframe).
 * Only allows http://, https://, /, and data:image/ protocols.
 */
export function isSafeSrcUrl(url: unknown): boolean {
    if (!url || typeof url !== 'string') return false;
    const trimmed = url.trim();
    return /^(https?:\/\/|\/|data:image\/)/i.test(trimmed);
}
