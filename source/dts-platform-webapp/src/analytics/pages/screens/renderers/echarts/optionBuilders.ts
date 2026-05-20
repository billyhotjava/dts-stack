// Shared helpers used across ECharts family renderers.
import { SCREEN_DEFAULT_FONT_FAMILY } from '../../screenTypography';

export const SCREEN_UI_FONT_FAMILY = SCREEN_DEFAULT_FONT_FAMILY;

export function isLightColor(hex: string): boolean {
    const c = hex.replace('#', '');
    if (c.length < 6) return false;
    const r = parseInt(c.slice(0, 2), 16);
    const g = parseInt(c.slice(2, 4), 16);
    const b = parseInt(c.slice(4, 6), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 160;
}
