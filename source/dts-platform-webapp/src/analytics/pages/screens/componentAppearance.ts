import type { CSSProperties } from 'react';

/** Build CSS properties for component-level appearance (background, border, radius, padding). */
export function resolveComponentAppearanceStyle(config: Record<string, unknown>): CSSProperties | undefined {
    const bgColor = config.componentBgColor as string | undefined;
    const bgOpacity = config.componentBgOpacity as number | undefined;
    const borderRadius = config.componentBorderRadius as number | undefined;
    const borderWidth = config.componentBorderWidth as number | undefined;
    const borderColor = config.componentBorderColor as string | undefined;
    const borderStyle = config.componentBorderStyle as string | undefined;
    const padding = config.componentPadding as number | undefined;

    const hasAny = bgColor || (bgOpacity != null && bgOpacity < 100) || borderRadius || borderWidth || padding;
    if (!hasAny) return undefined;

    const style: CSSProperties = {};
    if (bgColor) {
        const opacity = bgOpacity != null ? bgOpacity / 100 : 1;
        if (opacity < 1) {
            const hex = bgColor.replace('#', '');
            if (hex.length === 6) {
                const r = parseInt(hex.substring(0, 2), 16);
                const g = parseInt(hex.substring(2, 4), 16);
                const b = parseInt(hex.substring(4, 6), 16);
                style.backgroundColor = `rgba(${r},${g},${b},${opacity})`;
            } else {
                style.backgroundColor = bgColor;
                style.opacity = opacity;
            }
        } else {
            style.backgroundColor = bgColor;
        }
    }
    if (borderRadius && borderRadius > 0) {
        style.borderRadius = borderRadius;
        style.overflow = 'hidden';
    }
    if (borderWidth && borderWidth > 0) {
        style.border = `${borderWidth}px ${borderStyle || 'solid'} ${borderColor || 'rgba(255,255,255,0.2)'}`;
    }
    if (padding && padding > 0) {
        style.padding = padding;
    }
    return style;
}
