import type { CSSProperties } from 'react';
import type { ScreenComponent } from './types';

const INTERACTIVE_RUNTIME_FILTER_TYPES = new Set<ScreenComponent['type']>([
    'filter-input',
    'filter-select',
    'filter-date-range',
]);

export function hasInteractiveRuntimeFilters(components: ScreenComponent[] | undefined): boolean {
    if (!Array.isArray(components) || components.length === 0) {
        return false;
    }
    return components.some((component) => INTERACTIVE_RUNTIME_FILTER_TYPES.has(component.type));
}

export function resolveRuntimeCanvasScaleStyle(
    scale: number,
    components: ScreenComponent[] | undefined,
): CSSProperties {
    const safeScale = Number.isFinite(scale) && scale > 0 ? scale : 1;
    if (hasInteractiveRuntimeFilters(components)) {
        // Native select/date controls inside CSS transform layers can become effectively
        // non-interactive on legacy Chromium. Use zoom so runtime filters stay editable.
        return { zoom: safeScale };
    }
    return {
        transform: `scale(${safeScale})`,
        transformOrigin: 'top left',
    };
}
