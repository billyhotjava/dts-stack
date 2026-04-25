/**
 * Sprint-12 — v2 ScreenConfig 类型定义
 *
 * v2 从"固定像素绝对定位"改为"网格单元响应式布局"：
 * - 坐标/尺寸用 grid units（cols × rows），不再是 px
 * - layout 模型由 react-grid-layout 驱动
 * - 运行时按 viewport 自动重排，组件内部响应式（F4）
 *
 * 与 v1（schemaVersion=1 或 undefined）通过 `schemaVersion === 2` 区分，
 * 共存期沿用 `ScreenConfig` 的 `schemaVersion` 字段，避免后端 DTO 改字段名。
 */

import type {
	ScreenTheme,
	CarouselConfig,
	ScreenGlobalVariable,
	DataSourceConfig,
	DrillDownConfig,
	ScreenComponentAction,
	ComponentInteractionConfig,
} from '../types';

/** 网格布局全局参数 */
export interface ScreenLayoutV2 {
    /** 列数，默认 12 */
    cols: number;
    /** 行高：'auto' = viewport 高度除以最大行数；number = 固定 px */
    rowHeight: 'auto' | number;
    /** 组件间距（px） */
    gap: number;
}

/** 组件在 grid 上的位置与尺寸，单位为 grid cell */
export interface GridLayoutCell {
    /** 列起点（0-based） */
    x: number;
    /** 行起点（0-based） */
    y: number;
    /** 列跨度（≥1） */
    w: number;
    /** 行跨度（≥1） */
    h: number;
    minW?: number;
    minH?: number;
    maxW?: number;
    maxH?: number;
}

/** 各端可见性（PC/平板/手机） */
export interface VisibilityByDevice {
    pc?: boolean;
    tablet?: boolean;
    mobile?: boolean;
}

/** 组件定义（layout 改 grid units，config 与 v1 兼容） */
export interface ComponentV2 {
    id: string;
    /** 组件 type（与 v1 保持一致，复用现有 ComponentRenderer） */
    type: string;
    /** 可选显示名，仅编辑态显示 */
    name?: string;
    groupId?: string;
    parentContainerId?: string;
    /** 网格位置 */
    layout: GridLayoutCell;
    /** 组件自身配置，保持与 v1 `ScreenComponent['config']` 同 shape */
    config: Record<string, unknown>;
    /** 数据源配置，与 v1 共享 */
    dataSource?: DataSourceConfig;
    /** 下钻配置，与 v1 共享 */
    drillDown?: DrillDownConfig;
    /** 动作配置，与 v1 共享 */
    actions?: ScreenComponentAction[];
    /** 交互配置，与 v1 共享 */
    interaction?: ComponentInteractionConfig;
    /** 是否可见（默认 true） */
    visible?: boolean;
    /** 编辑态禁用拖放/resize */
    static?: boolean;
    /** 各端显隐 */
    visibleByDevice?: VisibilityByDevice;
    /** grid layout 本身不依赖 zIndex；保留此字段用于组件重叠时的渲染顺序 */
    zIndex?: number;
}

/** v2 多页定义，组件也使用 v2 grid layout */
export interface ScreenPageV2 {
    id: string;
    name: string;
    components: ComponentV2[];
    backgroundColor?: string;
    backgroundImage?: string;
}

/** v2 大屏配置 */
export interface ScreenConfigV2 {
    /** 必须为 2 */
    schemaVersion: 2;
    id?: string;
    name?: string;
    description?: string;
    updatedAt?: string;
    theme?: ScreenTheme;
    backgroundColor?: string;
    backgroundImage?: string;
    layout: ScreenLayoutV2;
    components: ComponentV2[];
    globalVariables?: ScreenGlobalVariable[];
    pages?: ScreenPageV2[];
    carouselConfig?: CarouselConfig;
    /**
     * 设计时的参考 viewport（仅作提示，不约束运行时）。
     * 默认填当前浏览器 viewport，便于回到"近似 WYSIWYG"状态。
     */
    referenceViewport?: { width: number; height: number };
}

/** layout 默认值 */
export const DEFAULT_SCREEN_LAYOUT_V2: ScreenLayoutV2 = {
    cols: 12,
    rowHeight: 'auto',
    gap: 12,
};

/** 创建空白 v2 大屏 */
export function createEmptyScreenV2(overrides?: Partial<ScreenConfigV2>): ScreenConfigV2 {
    return {
        schemaVersion: 2,
        name: '新建大屏',
        theme: 'enterprise-dark',
        backgroundColor: '#1e1f26',
        layout: { ...DEFAULT_SCREEN_LAYOUT_V2 },
        components: [],
        pages: [],
        ...overrides,
    };
}

/** v2 判别 type guard */
export function isScreenConfigV2(s: unknown): s is ScreenConfigV2 {
    if (s === null || typeof s !== 'object') return false;
    return (s as { schemaVersion?: unknown }).schemaVersion === 2;
}
