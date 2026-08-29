/** Shared renderer types for screen component renderers. */

import type { ComponentType } from 'react';
import type { CardData, ScreenComponent, ScreenCustomTheme, ScreenTheme } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';
import type { ComponentDataFeedback } from '../ScreenDataFeedbackContext';

export type ReactEChartsComponent = ComponentType<{
    style?: React.CSSProperties;
    option?: unknown;
    onEvents?: Record<string, (params: Record<string, unknown>) => void>;
}>;

export interface ComponentRendererProps {
    component: ScreenComponent;
    mode?: 'designer' | 'preview';
    theme?: ScreenTheme;
    customTheme?: ScreenCustomTheme;
    fontFamily?: string;
    /** Callback to persist card-derived metadata (e.g. _sourceColumns) back to saved config */
    onConfigMeta?: (meta: Record<string, unknown>) => void;
    /** Designer-only bridge for transient query status and sample rows. */
    onDataFeedback?: (componentId: string, feedback: ComponentDataFeedback | null) => void;
}

/**
 * Shared context passed to individual renderer functions.
 * Contains all computed values from the orchestrator component.
 */
export interface RenderContext {
    component: ScreenComponent;
    mode: 'designer' | 'preview';
    theme?: ScreenTheme;
    fontFamily?: string;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    effectiveConfig: Record<string, unknown>;
    cardData: CardData | null;
}
