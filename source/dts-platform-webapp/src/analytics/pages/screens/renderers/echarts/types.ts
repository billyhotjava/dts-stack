// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode } from 'react';
import type { ScreenThemeTokens } from '../../screenThemes';
import type { CardData } from '../../types';
import type { ReactEChartsComponent } from '../types';

export interface ScreenRuntimeLike {
    setVariable: (key: string, value: string, source?: string) => void;
}

export interface EChartsRendererProps {
    type: string;
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    mode: 'designer' | 'preview';
    componentId: string;
    runtime: ScreenRuntimeLike;

    // ECharts component references
    EChart: ReactEChartsComponent;
    renderEChartWithHandles: (
        option: Record<string, unknown>,
        onEvents?: Record<string, (params: Record<string, unknown>) => void>,
    ) => ReactNode;

    // Theme and chart options
    themeOptions: Record<string, unknown> & { tooltip?: Record<string, unknown> };
    chartMotionOption: Record<string, unknown>;
    chartTitleLayout: { titleOption: Record<string, unknown> };
    legendConfig: Record<string, unknown>;
    axisGrid: Record<string, unknown>;
    seriesColors: string[];

    // Axis font / label config
    axisFontSize: number;
    axisLabelColor?: string;
    seriesLabelFontSize: number;
    xAxisLabelRotate: number;
    xAxisLabelInterval: number;
    formatXAxisLabel: (value: unknown) => string;

    // Series label config
    axisSeriesLabelShow: boolean;
    resolvedAxisSeriesLabelStrategy: string;
    axisSeriesLabelFormatter: (raw: unknown) => string;
    axisLineLabelPosition: string;
    axisBarLabelPosition: string;
    axisBarLabelColor: string;
    axisTooltipFormatter: (raw: unknown) => string;

    // Canvas size flags
    isCompactCanvas: boolean;
    isTinyCanvas: boolean;
    xAxisCategoryCount: number;

    // Pie / donut layout
    plotCenterX: number;
    plotCenterY: number;
    pieInnerRadius: number;
    pieOuterRadius: number;
    pieLabelShow: boolean;
    pieLabelPosition: string;

    // Radar
    radarRadius: number;

    // Funnel
    funnelLeft: number;
    funnelRight: number;
    funnelTop: number;
    funnelBottom: number;
    funnelLabelShow: boolean;
    funnelLabelPosition: string;

    // Series label line
    seriesLabelLineLength: number;
    seriesLabelLineLength2: number;
    seriesLabelMinAngle: number;

    // Interaction handlers
    echartsClickHandler: Record<string, (params: Record<string, unknown>) => void> | undefined;
    componentActions: { length: number };
    executeComponentActions: (params: Record<string, unknown>) => void;

    // Map-chart state
    mapDrillRegion: string | null;
    setMapDrillRegion: (region: string | null) => void;
    mapReadyVersion: number;
    hasMapFn: ((mapName: string) => boolean) | null;

    // Raw SQL data (used by gantt board-hierarchical mode)
    cardData?: CardData | null;
}
