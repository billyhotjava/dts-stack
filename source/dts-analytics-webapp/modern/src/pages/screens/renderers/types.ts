/** Shared renderer types for screen component renderers. */

import type { ComponentType } from 'react';
import type { CardData, ScreenComponent, ScreenTheme } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';

export type ReactEChartsComponent = ComponentType<{
    style?: React.CSSProperties;
    option?: unknown;
    onEvents?: Record<string, (params: Record<string, unknown>) => void>;
}>;

export type DataViewModule = typeof import('@jiaminghi/data-view-react');

export interface ComponentRendererProps {
    component: ScreenComponent;
    mode?: 'designer' | 'preview';
    theme?: ScreenTheme;
    /** Callback to persist card-derived metadata (e.g. _sourceColumns) back to saved config */
    onConfigMeta?: (meta: Record<string, unknown>) => void;
}

/**
 * Shared context passed to individual renderer functions.
 * Contains all computed values from the orchestrator component.
 */
export interface RenderContext {
    component: ScreenComponent;
    mode: 'designer' | 'preview';
    theme?: ScreenTheme;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    effectiveConfig: Record<string, unknown>;
    cardData: CardData | null;
}
