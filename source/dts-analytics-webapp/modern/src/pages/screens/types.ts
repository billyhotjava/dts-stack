// Screen Designer Component Types
export interface ScreenComponent {
    id: string;
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
    | 'number-card'
    | 'progress-bar'
    | 'datetime'
    | 'image'
    | 'video'
    | 'iframe'
    | 'table';

export type DataSourceType = 'static' | 'api' | 'database' | 'card';

export interface DataSourceConfig {
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
        connectionId: string;
        query: string;
    };
    cardConfig?: {
        cardId: number;
        refreshInterval?: number; // Card 专属刷新间隔(秒)
    };
}

export interface CardData {
    rows: unknown[][];
    cols: Array<{ name: string; display_name: string; base_type: string }>;
}

export interface ScreenConfig {
    id: string;
    name: string;
    description?: string;
    width: number;
    height: number;
    backgroundColor: string;
    backgroundImage?: string;
    components: ScreenComponent[];
}

export interface ScreenState {
    config: ScreenConfig;
    selectedIds: string[];
    zoom: number;
    showGrid: boolean;
    history: ScreenConfig[];
    historyIndex: number;
}

export type ScreenAction =
    | { type: 'SET_CONFIG'; payload: ScreenConfig }
    | { type: 'LOAD_CONFIG'; payload: ScreenConfig }  // Load without adding to history
    | { type: 'ADD_COMPONENT'; payload: ScreenComponent }
    | { type: 'UPDATE_COMPONENT'; payload: { id: string; updates: Partial<ScreenComponent> } }
    | { type: 'DELETE_COMPONENTS'; payload: string[] }
    | { type: 'COPY_COMPONENTS'; payload: string[] }  // Copy to clipboard
    | { type: 'PASTE_COMPONENTS'; payload: { components: ScreenComponent[]; offsetX?: number; offsetY?: number } }
    | { type: 'SELECT_COMPONENTS'; payload: string[] }
    | { type: 'MOVE_COMPONENT'; payload: { id: string; x: number; y: number } }
    | { type: 'RESIZE_COMPONENT'; payload: { id: string; width: number; height: number } }
    | { type: 'REORDER_LAYER'; payload: { id: string; direction: 'up' | 'down' | 'top' | 'bottom' } }
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
