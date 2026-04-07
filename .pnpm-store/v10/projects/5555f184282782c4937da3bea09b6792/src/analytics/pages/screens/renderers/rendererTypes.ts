/**
 * Shared types for family renderers.
 * Each renderer function receives RendererProps and returns ReactNode.
 */
import type { ComponentType as ReactComponentType, ReactNode } from 'react';
import type { CardData } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';

export type ReactEChartsComponent = ReactComponentType<Record<string, unknown>>;
export type DataViewModule = Record<string, ReactComponentType<Record<string, unknown>>>;

export interface RendererProps {
    c: Record<string, unknown>;
    width: number;
    height: number;
    t: ScreenThemeTokens;
    theme?: string;
    themeOptions: Record<string, unknown>;
    cardData: CardData | null;
    echartsClickHandler?: Record<string, (params: Record<string, unknown>) => void>;
    EChartsComponent: ReactEChartsComponent | null;
    dataViewModule: DataViewModule | null;
    mode: string;
    componentId: string;
    componentType: string;
}

export type RendererFunction = (props: RendererProps) => ReactNode;
