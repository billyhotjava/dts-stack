// Screen Designer Component Types

export type ScreenTheme = 'legacy-dark' | 'titanium' | 'glacier';

export interface ScreenGlobalVariable {
    key: string;
    label: string;
    type: 'string' | 'number' | 'date';
    defaultValue?: string;
    description?: string;
}

export interface CardParameterBinding {
    name: string;
    variableKey?: string;
    value?: string;
}

export interface ComponentInteractionMapping {
    variableKey: string;
    sourcePath: string; // e.g. name, seriesName, value, data.name
    transform?: 'raw' | 'string' | 'number' | 'lowercase' | 'uppercase';
    fallbackValue?: string;
}

export interface ComponentInteractionConfig {
    enabled: boolean;
    mappings: ComponentInteractionMapping[];
    jumpEnabled?: boolean;
    jumpUrlTemplate?: string;
    jumpOpenMode?: 'self' | 'new-tab';
}

export interface DrillLevel {
    cardId: number;
    paramName: string;
    label: string;
}

export interface DrillDownConfig {
    enabled: boolean;
    levels: DrillLevel[];
}

export type FieldMappingAggregation = 'sum' | 'count' | 'avg' | 'min' | 'max';

export interface FieldMapping {
    dimension?: string;       // 维度列名 (X 轴 / 名称)
    measures?: string[];      // 度量列名 (Y 轴, 多系列)
    groupBy?: string;         // 分组列名
    sizeField?: string;       // 大小映射列名 (scatter)
    aggregation?: FieldMappingAggregation;
    sortField?: string;
    sortOrder?: 'asc' | 'desc';
}

export interface ChartMarkLine {
    type: 'value' | 'average' | 'min' | 'max';
    value?: number;
    name?: string;
    color?: string;
    lineStyle?: 'solid' | 'dashed' | 'dotted';
    axis?: 'x' | 'y';
}

export interface ChartMarkArea {
    name?: string;
    from: number;
    to: number;
    axis?: 'x' | 'y';
    color?: string;
}

export interface SeriesConditionalColor {
    operator: '>' | '>=' | '<' | '<=' | '==' | 'between';
    value: number;
    valueTo?: number;
    color: string;
}

export const DRILLABLE_TYPES: Set<ComponentType> = new Set([
    'line-chart', 'bar-chart', 'pie-chart', 'funnel-chart', 'scatter-chart', 'radar-chart',
    'combo-chart', 'treemap-chart', 'sunburst-chart',
]);

export interface ScreenComponent {
    id: string;
    groupId?: string;
    parentContainerId?: string;
    type: ComponentType;
    name: string;
    x: number;
    y: number;
    width: number;
    height: number;
    zIndex: number;
    locked: boolean;
    visible: boolean;
    config: Record<string, unknown>;
    dataSource?: DataSourceConfig;
    drillDown?: DrillDownConfig;
    interaction?: ComponentInteractionConfig;
}

export type ComponentType =
    // ECharts 图表
    | 'line-chart'
    | 'bar-chart'
    | 'pie-chart'
    | 'gauge-chart'
    | 'scatter-chart'
    | 'radar-chart'
    | 'funnel-chart'
    | 'map-chart'
    | 'combo-chart'
    | 'wordcloud-chart'
    | 'treemap-chart'
    | 'sunburst-chart'
    | 'waterfall-chart'
    // DataV 装饰
    | 'border-box'
    | 'decoration'
    | 'scroll-board'
    | 'scroll-ranking'
    | 'water-level'
    | 'digital-flop'
    | 'flyline-chart'
    | 'percent-pond'
    // 基础组件
    | 'title'
    | 'markdown-text'
    | 'number-card'
    | 'progress-bar'
    | 'tab-switcher'
    | 'carousel'
    | 'countdown'
    | 'marquee'
    | 'shape'
    | 'container'
    | 'datetime'
    | 'image'
    | 'video'
    | 'iframe'
    | 'table'
    | 'filter-input'
    | 'filter-select'
    | 'filter-date-range'
    | 'richtext'
    // 3D 可视化 (echarts-gl)
    | 'globe-chart'
    | 'bar3d-chart'
    | 'scatter3d-chart';

export type DataSourceType = 'static' | 'api' | 'card' | 'sql' | 'dataset' | 'metric' | 'database';
export type QuerySourceType = 'metric' | 'dataset' | 'sql' | 'card' | 'api';

export interface DataSourceConfig {
    /**
     * Canonical source type for Spec v2.
     * Legacy payloads may still carry `type: "database"`.
     */
    sourceType?: QuerySourceType;
    type: DataSourceType;
    refreshInterval?: number; // 刷新间隔(秒)
    staticData?: unknown;
    apiConfig?: {
        url: string;
        method: 'GET' | 'POST';
        headers?: Record<string, string>;
        params?: Record<string, string>;
        body?: string;
    };
    databaseConfig?: {
        // Prefer analytics database id; keep connectionId for backward compatibility.
        databaseId?: number;
        connectionId?: string;
        query: string;
        queryTimeoutSeconds?: number;
        maxRows?: number;
        parameterBindings?: CardParameterBinding[];
    };
    sqlConfig?: {
        // Prefer analytics database id; keep connectionId for backward compatibility.
        databaseId?: number;
        connectionId?: string;
        query: string;
        queryTimeoutSeconds?: number;
        maxRows?: number;
        parameterBindings?: CardParameterBinding[];
    };
    datasetConfig?: {
        queryBody?: Record<string, unknown>;
    };
    metricConfig?: {
        metricId?: number;
        metricVersion?: string;
        cardId?: number;
        parameterBindings?: CardParameterBinding[];
    };
    cardConfig?: {
        cardId: number;
        refreshInterval?: number; // Card 专属刷新间隔(秒)
        metricId?: number; // 语义指标绑定
        metricVersion?: string; // 指标版本/口径版本
        parameterBindings?: CardParameterBinding[]; // 参数绑定（变量/静态值）
    };
}

export interface CardData {
    rows: unknown[][];
    cols: Array<{ name: string; display_name: string; base_type: string }>;
}

export interface CarouselConfig {
    enabled: boolean;
    intervalSeconds: number;       // default 30
    transition: 'fade' | 'slide-left' | 'slide-up' | 'none';
    transitionDuration: number;    // ms, default 800
    loop: boolean;                 // default true
}

export interface ScreenPage {
    id: string;
    name: string;
    components: ScreenComponent[];
    backgroundColor?: string;
    backgroundImage?: string;
}

export interface ScreenConfig {
    schemaVersion?: number;
    id: string;
    name: string;
    description?: string;
    updatedAt?: string;
    width: number;
    height: number;
    backgroundColor: string;
    backgroundImage?: string;
    theme?: ScreenTheme;
    components: ScreenComponent[];
    globalVariables?: ScreenGlobalVariable[];
    /** Multi-page support. When empty/undefined, uses top-level components (single-page mode). */
    pages?: ScreenPage[];
    carouselConfig?: CarouselConfig;
}

export interface ScreenState {
    config: ScreenConfig;
    baselineConfig: ScreenConfig;
    selectedIds: string[];
    zoom: number;
    showGrid: boolean;
    history: ScreenConfig[];
    historyIndex: number;
}

export type ScreenAction =
    | { type: 'SET_CONFIG'; payload: ScreenConfig }
    | { type: 'LOAD_CONFIG'; payload: ScreenConfig }  // Load without adding to history
    | { type: 'MARK_BASELINE'; payload: ScreenConfig }
    | { type: 'MERGE_CONFIG'; payload: Partial<ScreenConfig> }
    | { type: 'ADD_COMPONENT'; payload: ScreenComponent }
    | { type: 'UPDATE_COMPONENT'; payload: { id: string; updates: Partial<ScreenComponent> } }
    | { type: 'DELETE_COMPONENTS'; payload: string[] }
    | { type: 'DUPLICATE_COMPONENTS'; payload: { sourceIds: string[] } }
    | { type: 'COPY_COMPONENTS'; payload: string[] }  // Copy to clipboard
    | { type: 'PASTE_COMPONENTS'; payload: { components: ScreenComponent[]; offsetX?: number; offsetY?: number } }
    | { type: 'SELECT_COMPONENTS'; payload: string[] }
    | { type: 'MOVE_COMPONENT'; payload: { id: string; x: number; y: number } }
    | { type: 'MOVE_COMPONENTS'; payload: Array<{ id: string; x: number; y: number }> }
    | { type: 'TRANSFORM_COMPONENTS'; payload: Array<{ id: string; x: number; y: number; width: number; height: number }> }
    | { type: 'RESIZE_COMPONENT'; payload: { id: string; width: number; height: number } }
    | { type: 'REORDER_LAYER'; payload: { id: string; direction: 'up' | 'down' | 'top' | 'bottom' } }
    | { type: 'SNAPSHOT' }
    | { type: 'SET_ZOOM'; payload: number }
    | { type: 'TOGGLE_GRID' }
    | { type: 'UNDO' }
    | { type: 'REDO' };

// Component category for the component library panel
export interface ComponentCategory {
    name: string;
    icon: string;
    items: ComponentItem[];
}

export interface ComponentItem {
    type: ComponentType;
    name: string;
    icon: string;
    defaultWidth: number;
    defaultHeight: number;
    defaultConfig: Record<string, unknown>;
}
