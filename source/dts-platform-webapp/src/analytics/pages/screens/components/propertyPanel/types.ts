// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ComponentType, ScreenComponent } from '../../types';
import type { ExplainabilityResponse } from '../../../../api/analyticsApi';

export type ExplainState =
    | { state: 'loading' }
    | { state: 'loaded'; value: ExplainabilityResponse }
    | { state: 'error'; error: unknown };

export type StyleClipboardPayload = {
    type: ComponentType;
    width: number;
    height: number;
    config: Record<string, unknown>;
    copiedAt: string;
};

export type LayoutClipboardPayload = {
    x: number;
    y: number;
    width: number;
    height: number;
    copiedAt: string;
};

export type LegendHeuristicLayout = {
    position: 'top' | 'bottom' | 'left' | 'right';
    orient: 'horizontal' | 'vertical';
    align: 'start' | 'center' | 'end';
    reserveSize: number;
    nameMaxWidth: number;
    hint: string;
};

export type PropertyPanelTab = 'style' | 'data' | 'interaction' | 'advanced';

export interface ColumnEntry {
    source: string;
    alias?: string;
    align?: 'left' | 'center' | 'right';
    width?: number;
    wrap?: boolean;
    formatter?: 'auto' | 'string' | 'number' | 'percent' | 'date';
}

export interface SourceColumnOption {
    name: string;
    displayName: string;
}

// Re-export so ScreenComponent remains importable from this module for convenience.
export type { ScreenComponent };
