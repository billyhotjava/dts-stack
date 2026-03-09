import { useEffect, useState } from 'react';
import { useScreen } from '../ScreenContext';
import type {
    CardParameterBinding,
    ChartMarkArea,
    ChartMarkLine,
    ComponentInteractionMapping,
    ComponentType,
    DataSourceConfig,
    DrillLevel,
    QuerySourceType,
    ScreenComponent,
    ScreenComponentAction,
    ScreenGlobalVariable,
    SeriesConditionalColor,
} from '../types';
import { DRILLABLE_TYPES } from '../types';
import { CardIdPicker } from './CardIdPicker';
import { MetricBindingEditor } from './MetricBindingEditor';
import { CardParamBindingsEditor } from './CardParamBindingsEditor';
import { DatabaseIdPicker } from './DatabaseIdPicker';
import { getRendererPlugin } from '../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../plugins/runtime';
import { useScreenPluginRuntime } from '../plugins/useScreenPluginRuntime';
import type { PropertySchemaField } from '../plugins/types';
import { wouldCreateParentCycle } from '../componentHierarchy';
import { analyticsApi, type ExplainabilityResponse } from '../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../hooks/clipboard';
import {
    CHART_COMPONENT_TYPES,
    applyChartPresetConfig,
    isChartComponentType,
    type ChartPreset,
} from '../chartPresets';
import { PROVINCE_PRESETS } from '../renderers/shared/geoJsonCache';
import { FieldMappingPanel, isMappable } from './FieldMappingPanel';
import type { FieldMapping } from '../types';
import { COLOR_SCHEMES, recommendColorSchemes, type ColorScheme } from '../colorSchemes';

type ExplainState =
    | { state: 'loading' }
    | { state: 'loaded'; value: ExplainabilityResponse }
    | { state: 'error'; error: unknown };

type StyleClipboardPayload = {
    type: ComponentType;
    width: number;
    height: number;
    config: Record<string, unknown>;
    copiedAt: string;
};
type LayoutClipboardPayload = {
    x: number;
    y: number;
    width: number;
    height: number;
    copiedAt: string;
};

const STYLE_CLIPBOARD_KEY = 'dts.analytics.screen.styleClipboard.v1';
const LAYOUT_CLIPBOARD_KEY = 'dts.analytics.screen.layoutClipboard.v1';
const PROPERTY_SECTION_COLLAPSE_KEY = 'dts.analytics.screen.propertySectionCollapse.v1';
const PROPERTY_COMPONENT_MODE_KEY = 'dts.analytics.screen.componentConfigMode.v1';
const PROPERTY_PANEL_DENSITY_KEY = 'dts.analytics.screen.propertyPanelDensity.v1';
const PROPERTY_SECTION_KEYS = [
    'quick-filter',
    'quick-actions',
    'position-size',
    'plugin-config',
    'component-config',
    'data-source',
    'explain',
    'drill-down',
    'interaction',
    'actions',
    'other',
] as const;
const PROPERTY_FOCUS_SECTION_KEYS = new Set<string>([
    'quick-filter',
    'quick-actions',
    'position-size',
    'plugin-config',
    'component-config',
    'data-source',
]);
const PROPERTY_SECTION_ESSENTIAL_COLLAPSED = [
    'plugin-config',
    'explain',
    'drill-down',
    'interaction',
    'actions',
    'other',
] as const;
const STYLE_CONFIG_EXCLUDE_KEYS = new Set<string>([
    '_sourceColumns',
    '_fieldMapping',
    '_useFieldMapping',
    '_colorScheme',
    'markLines',
    'markAreas',
    'conditionalColors',
    'data',
    'xAxisData',
    'series',
    'indicator',
    'options',
    'value',
    'min',
    'max',
    'targetDate',
    'items',
    'content',
    'html',
    'text',
    'imageUrl',
    'videoUrl',
    'src',
    'url',
    'cardId',
    'metricId',
    'metricVersion',
    'query',
    'queryBody',
    'databaseId',
    'connectionId',
    'variableKey',
    'globalVariables',
    'interaction',
    'drillDown',
]);

function isVisualConfigKey(key: string): boolean {
    const normalized = String(key || '').trim();
    if (!normalized) return false;
    if (normalized.startsWith('_')) return false;
    if (STYLE_CONFIG_EXCLUDE_KEYS.has(normalized)) return false;
    const lowered = normalized.toLowerCase();
    if (lowered.includes('data') || lowered.includes('dataset')) return false;
    if (lowered.includes('query') || lowered.includes('metric') || lowered.includes('card')) return false;
    if (lowered.includes('source')) return false;
    return (
        lowered.includes('color')
        || lowered.includes('font')
        || lowered.includes('size')
        || lowered.includes('background')
        || lowered.includes('border')
        || lowered.includes('radius')
        || lowered.includes('padding')
        || lowered.includes('margin')
        || lowered.includes('legend')
        || lowered.includes('axis')
        || lowered.includes('label')
        || lowered.includes('title')
        || lowered.includes('theme')
        || lowered.includes('opacity')
        || lowered.includes('align')
        || lowered.includes('position')
        || lowered.includes('offset')
        || lowered.includes('scale')
        || lowered.includes('rotate')
        || lowered.includes('line')
        || lowered.includes('wrap')
        || lowered.includes('display')
        || lowered.includes('show')
    );
}

function buildStyleClipboardPayload(component: ScreenComponent): StyleClipboardPayload {
    const pickedEntries = Object.entries(component.config || {}).filter(([key]) => isVisualConfigKey(key));
    const visualConfig = Object.fromEntries(pickedEntries);
    return {
        type: component.type,
        width: component.width,
        height: component.height,
        config: visualConfig,
        copiedAt: new Date().toISOString(),
    };
}

const DEFAULT_SERIES_COLORS = [
    '#3b82f6',
    '#22c55e',
    '#f59e0b',
    '#ef4444',
    '#a855f7',
    '#06b6d4',
];

type LegendHeuristicLayout = {
    position: 'top' | 'bottom' | 'left' | 'right';
    orient: 'horizontal' | 'vertical';
    align: 'start' | 'center' | 'end';
    reserveSize: number;
    nameMaxWidth: number;
    hint: string;
};

function resolveLegendHeuristicLayout(component: ScreenComponent): LegendHeuristicLayout {
    const width = Math.max(1, Number(component.width) || 1);
    const height = Math.max(1, Number(component.height) || 1);
    const ratio = width / height;

    if (width < 440 || ratio <= 1.05) {
        return {
            position: 'bottom',
            orient: 'horizontal',
            align: 'start',
            reserveSize: Math.max(48, Math.min(92, Math.round(height * 0.22))),
            nameMaxWidth: 96,
            hint: '当前组件偏窄，建议底部横向图例',
        };
    }
    if (ratio >= 1.75 && width >= 520) {
        return {
            position: 'right',
            orient: 'vertical',
            align: 'center',
            reserveSize: Math.max(96, Math.min(180, Math.round(width * 0.2))),
            nameMaxWidth: 128,
            hint: '当前组件偏宽，建议右侧纵向图例',
        };
    }
    if (height <= 260) {
        return {
            position: 'bottom',
            orient: 'horizontal',
            align: 'center',
            reserveSize: 56,
            nameMaxWidth: 0,
            hint: '当前组件高度有限，建议底部图例降低遮挡',
        };
    }
    return {
        position: 'top',
        orient: 'horizontal',
        align: 'center',
        reserveSize: 52,
        nameMaxWidth: 0,
        hint: '建议顶部横向图例，保持图形区域均衡',
    };
}

function applyLegendHeuristicLayout(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void,
) {
    const next = resolveLegendHeuristicLayout(component);
    onChange('autoLegendAvoid', true);
    onChange('legendDisplay', 'auto');
    onChange('legendPosition', next.position);
    onChange('legendOrient', next.orient);
    onChange('legendAlign', next.align);
    onChange('legendReserveSize', next.reserveSize);
    onChange('legendNameMaxWidth', next.nameMaxWidth);
    onChange('legendOffsetX', 0);
    onChange('legendOffsetY', 0);
}

function renderQuickChartConfig(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void,
    applyPreset: (preset: ChartPreset) => void,
) {
    const { type, config } = component;
    const legendHeuristic = resolveLegendHeuristicLayout(component);
    if (!CHART_COMPONENT_TYPES.has(type)) {
        return null;
    }
    const isAxisChart = type === 'line-chart' || type === 'bar-chart' || type === 'scatter-chart' || type === 'combo-chart' || type === 'waterfall-chart';
    const isLabelChart = type === 'pie-chart' || type === 'funnel-chart' || type === 'radar-chart';
    const isHierarchyChart = type === 'treemap-chart' || type === 'sunburst-chart';
    const showSeriesLabelControls = isLabelChart || isAxisChart || isHierarchyChart;
    const applyLayoutPreset = (preset: 'balanced' | 'compact' | 'spacious') => {
        if (preset === 'compact') {
            onChange('autoLegendAvoid', true);
            onChange('legendDisplay', 'auto');
            onChange('legendPosition', 'bottom');
            onChange('legendOrient', 'horizontal');
            onChange('legendReserveSize', 52);
            onChange('legendAlign', 'start');
            onChange('chartScalePercent', 92);
            onChange('chartOffsetX', 0);
            onChange('chartOffsetY', 0);
            onChange('xAxisLabelRotate', 36);
            onChange('xAxisLabelMaxLength', 8);
            onChange('xAxisLabelInterval', 0);
            onChange('axisSeriesLabelStrategy', 'first');
            onChange('axisSeriesLabelStep', 0);
            onChange('axisTooltipMaxRows', 6);
            return;
        }
        if (preset === 'spacious') {
            onChange('autoLegendAvoid', false);
            onChange('legendDisplay', 'show');
            onChange('legendPosition', 'right');
            onChange('legendOrient', 'vertical');
            onChange('legendReserveSize', 120);
            onChange('legendAlign', 'center');
            onChange('chartScalePercent', 100);
            onChange('chartOffsetX', 0);
            onChange('chartOffsetY', 0);
            onChange('xAxisLabelRotate', 0);
            onChange('xAxisLabelMaxLength', 0);
            onChange('xAxisLabelInterval', 0);
            onChange('axisSeriesLabelStrategy', 'all');
            onChange('axisSeriesLabelStep', 0);
            onChange('axisTooltipMaxRows', 12);
            return;
        }
        onChange('autoLegendAvoid', true);
        onChange('legendDisplay', 'auto');
        onChange('legendPosition', 'auto');
        onChange('legendOrient', 'auto');
        onChange('legendReserveSize', 0);
        onChange('legendAlign', 'auto');
        onChange('chartScalePercent', 100);
        onChange('chartOffsetX', 0);
        onChange('chartOffsetY', 0);
        onChange('xAxisLabelRotate', 0);
        onChange('xAxisLabelMaxLength', 0);
        onChange('xAxisLabelInterval', 0);
        onChange('axisSeriesLabelStrategy', 'auto');
        onChange('axisSeriesLabelStep', 0);
        onChange('axisTooltipMaxRows', 0);
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">标题</label>
                <input
                    type="text"
                    className="property-input"
                    value={String(config.title ?? '')}
                    onChange={(e) => onChange('title', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">标题字号</label>
                <input
                    type="number"
                    className="property-input"
                    min={10}
                    max={40}
                    value={Number(config.titleFontSize) || 14}
                    onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例显示</label>
                <select
                    className="property-input"
                    value={(config.legendDisplay as string) || 'auto'}
                    onChange={(e) => onChange('legendDisplay', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="show">显示</option>
                    <option value="hide">隐藏</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">自动避让</label>
                <input
                    type="checkbox"
                    checked={config.autoLegendAvoid !== false}
                    onChange={(e) => onChange('autoLegendAvoid', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">小屏预设</label>
                <select
                    className="property-input"
                    value={(config.compactLayoutPreset as string) || 'auto'}
                    onChange={(e) => onChange('compactLayoutPreset', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="off">关闭</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">图形缩放(%)</label>
                <input
                    type="number"
                    className="property-input"
                    min={40}
                    max={180}
                    value={(config.chartScalePercent as number) || 100}
                    onChange={(e) => onChange('chartScalePercent', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">布局预设</label>
                <div style={{ display: 'flex', gap: 6, width: '100%' }}>
                    <button
                        type="button"
                        className="property-action-btn"
                        style={{ flex: 1 }}
                        onClick={() => applyLayoutPreset('balanced')}
                        title="自动避让 + 默认留白"
                    >
                        平衡
                    </button>
                    <button
                        type="button"
                        className="property-action-btn"
                        style={{ flex: 1 }}
                        onClick={() => applyLayoutPreset('compact')}
                        title="紧凑布局，优先保证小容器可读"
                    >
                        紧凑
                    </button>
                    <button
                        type="button"
                        className="property-action-btn"
                        style={{ flex: 1 }}
                        onClick={() => applyLayoutPreset('spacious')}
                        title="图例侧边 + 留白更充分"
                    >
                        留白
                    </button>
                </div>
            </div>
            <div className="property-row">
                <label className="property-label">布局回正</label>
                <button
                    type="button"
                    className="property-action-btn"
                    style={{ width: '100%' }}
                    onClick={() => {
                        onChange('legendOffsetX', 0);
                        onChange('legendOffsetY', 0);
                        onChange('chartOffsetX', 0);
                        onChange('chartOffsetY', 0);
                        onChange('chartPaddingTop', 0);
                        onChange('chartPaddingRight', 0);
                        onChange('chartPaddingBottom', 0);
                        onChange('chartPaddingLeft', 0);
                    }}
                    title="清空图例/图形偏移与留白，回到自动布局"
                >
                    一键回到自动布局
                </button>
            </div>
            <div className="property-row">
                <label className="property-label">图例避让</label>
                <div style={{ width: '100%' }}>
                    <button
                        type="button"
                        className="property-action-btn"
                        style={{ width: '100%' }}
                        onClick={() => applyLegendHeuristicLayout(component, onChange)}
                        title={legendHeuristic.hint}
                    >
                        一键自动避让
                    </button>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 4 }}>{legendHeuristic.hint}</div>
                </div>
            </div>
            {(type === 'bar-chart') ? (
                <div className="property-row">
                    <label className="property-label">水平方向</label>
                    <input
                        type="checkbox"
                        checked={Boolean(config.horizontal)}
                        onChange={(e) => onChange('horizontal', e.target.checked)}
                    />
                </div>
            ) : null}
            {(type === 'bar-chart' || type === 'line-chart') ? (
                <div className="property-row">
                    <label className="property-label">堆叠模式</label>
                    <select
                        className="property-input"
                        value={(config.stackMode as string) || 'off'}
                        onChange={(e) => onChange('stackMode', e.target.value)}
                    >
                        <option value="off">关闭</option>
                        <option value="stack">堆叠</option>
                    </select>
                </div>
            ) : null}
            {(type === 'wordcloud-chart') ? (
                <>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={(config.shape as string) || 'circle'}
                            onChange={(e) => onChange('shape', e.target.value)}
                        >
                            <option value="circle">圆形</option>
                            <option value="cardioid">心形</option>
                            <option value="diamond">菱形</option>
                            <option value="square">正方形</option>
                            <option value="triangle-forward">三角形</option>
                            <option value="star">星形</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">最小字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={8}
                            max={40}
                            value={Array.isArray(config.fontSizeRange) ? (config.fontSizeRange as number[])[0] : 14}
                            onChange={(e) => {
                                const range = Array.isArray(config.fontSizeRange) ? [...config.fontSizeRange] as number[] : [14, 60];
                                range[0] = Number(e.target.value);
                                onChange('fontSizeRange', range);
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">最大字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={20}
                            max={120}
                            value={Array.isArray(config.fontSizeRange) ? (config.fontSizeRange as number[])[1] : 60}
                            onChange={(e) => {
                                const range = Array.isArray(config.fontSizeRange) ? [...config.fontSizeRange] as number[] : [14, 60];
                                range[1] = Number(e.target.value);
                                onChange('fontSizeRange', range);
                            }}
                        />
                    </div>
                </>
            ) : null}
            {isAxisChart ? (
                <>
                    <div className="property-row">
                        <label className="property-label">X轴角度</label>
                        <input
                            type="number"
                            className="property-input"
                            min={-90}
                            max={90}
                            value={(config.xAxisLabelRotate as number) || 0}
                            onChange={(e) => onChange('xAxisLabelRotate', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">X轴最大字数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={40}
                            value={(config.xAxisLabelMaxLength as number) || 0}
                            onChange={(e) => onChange('xAxisLabelMaxLength', Number(e.target.value))}
                            placeholder="0=不限"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">X轴抽样间隔</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={200}
                            value={(config.xAxisLabelInterval as number) || 0}
                            onChange={(e) => onChange('xAxisLabelInterval', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标签系列策略</label>
                        <select
                            className="property-input"
                            value={(config.axisSeriesLabelStrategy as string) || 'auto'}
                            onChange={(e) => onChange('axisSeriesLabelStrategy', e.target.value)}
                        >
                            <option value="auto">自动</option>
                            <option value="all">全部系列</option>
                            <option value="first">仅首系列</option>
                            <option value="none">隐藏标签</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">标签步长</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={200}
                            value={(config.axisSeriesLabelStep as number) || 0}
                            onChange={(e) => onChange('axisSeriesLabelStep', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">Tooltip行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={50}
                            value={(config.axisTooltipMaxRows as number) || 0}
                            onChange={(e) => onChange('axisTooltipMaxRows', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                </>
            ) : null}
            {showSeriesLabelControls ? (
                <>
                    <div className="property-row">
                        <label className="property-label">标签位置</label>
                        <select
                            className="property-input"
                            value={(config.seriesLabelPosition as string) || 'auto'}
                            onChange={(e) => onChange('seriesLabelPosition', e.target.value)}
                        >
                            <option value="auto">自动</option>
                            <option value="inside">内部</option>
                            <option value="outside">外部</option>
                            <option value="none">隐藏</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">标签字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.seriesLabelFontSize as number) || 12}
                            onChange={(e) => onChange('seriesLabelFontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            ) : null}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6 }}>
                <button type="button" className="property-btn-small" onClick={() => applyPreset('business')}>商务预设</button>
                <button type="button" className="property-btn-small" onClick={() => applyPreset('compact')}>紧凑预设</button>
                <button type="button" className="property-btn-small" onClick={() => applyPreset('clear')}>恢复预设</button>
            </div>
            <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                当前为简洁模式，仅显示高频参数。切换到“专业模式”可配置全部细节。
            </div>
        </>
    );
}

function serializeVisibilityMatchValues(raw: unknown): string {
    if (Array.isArray(raw)) {
        return raw.map((item) => String(item ?? '').trim()).filter((item) => item.length > 0).join('\n');
    }
    const text = String(raw ?? '').trim();
    if (!text) return '';
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .join('\n');
}

function parseVisibilityMatchValues(text: string): string[] {
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .slice(0, 200);
}

function extractSqlTemplateParameterNames(sql: string): string[] {
    const text = String(sql ?? '');
    if (!text.trim()) {
        return [];
    }
    const names: string[] = [];
    const seen = new Set<string>();
    const patterns = [
        /\{\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}\}/g,
        /\$\{\s*([a-zA-Z][a-zA-Z0-9_-]{0,63})\s*\}/g,
    ];
    for (const pattern of patterns) {
        let match: RegExpExecArray | null = null;
        // eslint-disable-next-line no-cond-assign
        while ((match = pattern.exec(text)) !== null) {
            const name = String(match[1] ?? '').trim();
            if (!name || seen.has(name)) {
                continue;
            }
            seen.add(name);
            names.push(name);
        }
    }
    return names.slice(0, 200);
}

function resolveTabSwitcherOptionValues(raw: unknown): string[] {
    if (!Array.isArray(raw)) {
        return [];
    }
    const out: string[] = [];
    for (const item of raw) {
        if (typeof item === 'string') {
            const text = item.trim();
            if (text) out.push(text);
            continue;
        }
        if (!item || typeof item !== 'object') {
            continue;
        }
        const row = item as Record<string, unknown>;
        const value = String(row.value ?? '').trim();
        if (value) {
            out.push(value);
        }
    }
    return out;
}

export function PropertyPanel() {
    const {
        state,
        updateComponent,
        updateConfig,
        updateSelectedComponents,
        deleteComponents,
        alignSelected,
        distributeSelected,
        groupSelected,
        ungroupSelected,
    } = useScreen();
    const { config, selectedIds } = state;
    useScreenPluginRuntime();
    const [explainState, setExplainState] = useState<ExplainState | null>(null);
    const [panelFilter, setPanelFilter] = useState('');
    const [styleClipboard, setStyleClipboard] = useState<StyleClipboardPayload | null>(null);
    const [layoutClipboard, setLayoutClipboard] = useState<LayoutClipboardPayload | null>(null);
    const [componentConfigMode, setComponentConfigMode] = useState<'quick' | 'advanced'>(() => {
        if (typeof window === 'undefined') {
            return 'quick';
        }
        try {
            const raw = window.localStorage.getItem(PROPERTY_COMPONENT_MODE_KEY);
            return raw === 'advanced' ? 'advanced' : 'quick';
        } catch {
            return 'quick';
        }
    });
    const [quickActionMode, setQuickActionMode] = useState<'core' | 'layout' | 'nudge' | 'clipboard' | 'all'>('core');
    const [panelDensity, setPanelDensity] = useState<'focus' | 'full'>(() => {
        if (typeof window === 'undefined') {
            return 'focus';
        }
        try {
            const raw = window.localStorage.getItem(PROPERTY_PANEL_DENSITY_KEY);
            return raw === 'full' ? 'full' : 'focus';
        } catch {
            return 'focus';
        }
    });
    const [collapsedSections, setCollapsedSections] = useState<string[]>([]);

    const selectedComponents = config.components.filter((c) => selectedIds.includes(c.id));
    const selectedComponent = selectedIds.length === 1
        ? config.components.find((c) => c.id === selectedIds[0])
        : null;

    useEffect(() => {
        setExplainState(null);
    }, [selectedComponent?.id]);
    useEffect(() => {
        if (!selectedComponent) return;
        if (!CHART_COMPONENT_TYPES.has(selectedComponent.type)) {
            if (componentConfigMode !== 'advanced') {
                setComponentConfigMode('advanced');
            }
            return;
        }
    }, [componentConfigMode, selectedComponent?.id, selectedComponent?.type]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        try {
            window.localStorage.setItem(PROPERTY_COMPONENT_MODE_KEY, componentConfigMode);
        } catch {
            // ignore storage failure
        }
    }, [componentConfigMode]);
    useEffect(() => {
        if (typeof window === 'undefined') return;
        try {
            window.localStorage.setItem(PROPERTY_PANEL_DENSITY_KEY, panelDensity);
        } catch {
            // ignore storage failure
        }
    }, [panelDensity]);
    useEffect(() => {
        try {
            const raw = localStorage.getItem(PROPERTY_SECTION_COLLAPSE_KEY);
            if (!raw) {
                setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
                return;
            }
            const parsed = JSON.parse(raw) as unknown;
            if (!Array.isArray(parsed)) {
                setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
                return;
            }
            setCollapsedSections(parsed.filter((item) => typeof item === 'string'));
        } catch {
            setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
        }
    }, []);
    useEffect(() => {
        try {
            localStorage.setItem(PROPERTY_SECTION_COLLAPSE_KEY, JSON.stringify(collapsedSections));
        } catch {
            // ignore storage failure
        }
    }, [collapsedSections]);

    useEffect(() => {
        try {
            const raw = sessionStorage.getItem(STYLE_CLIPBOARD_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as StyleClipboardPayload;
            if (!parsed || typeof parsed !== 'object' || !parsed.type || !parsed.config) return;
            setStyleClipboard(parsed);
        } catch {
            // ignore invalid cache
        }
    }, []);
    useEffect(() => {
        try {
            const raw = sessionStorage.getItem(LAYOUT_CLIPBOARD_KEY);
            if (!raw) return;
            const parsed = JSON.parse(raw) as LayoutClipboardPayload;
            if (!parsed || typeof parsed !== 'object') return;
            if (!Number.isFinite(parsed.x) || !Number.isFinite(parsed.y)) return;
            if (!Number.isFinite(parsed.width) || !Number.isFinite(parsed.height)) return;
            setLayoutClipboard(parsed);
        } catch {
            // ignore invalid cache
        }
    }, []);

    const persistStyleClipboard = (payload: StyleClipboardPayload | null) => {
        setStyleClipboard(payload);
        try {
            if (!payload) {
                sessionStorage.removeItem(STYLE_CLIPBOARD_KEY);
                return;
            }
            sessionStorage.setItem(STYLE_CLIPBOARD_KEY, JSON.stringify(payload));
        } catch {
            // ignore storage failure
        }
    };
    const persistLayoutClipboard = (payload: LayoutClipboardPayload | null) => {
        setLayoutClipboard(payload);
        try {
            if (!payload) {
                sessionStorage.removeItem(LAYOUT_CLIPBOARD_KEY);
                return;
            }
            sessionStorage.setItem(LAYOUT_CLIPBOARD_KEY, JSON.stringify(payload));
        } catch {
            // ignore storage failure
        }
    };

    if (selectedComponents.length === 0) {
        return (
            <div className="property-panel property-panel--empty">
                <div className="property-panel-header">
                    <h3>属性</h3>
                    <p className="property-panel-subtitle">从画布选择一个组件后，这里会展示它的配置、数据和交互能力。</p>
                </div>
                <div className="property-panel-content">
                    <div className="empty-state">
                        <div className="empty-state-icon">🎨</div>
                        <div className="empty-state-text">选择组件以编辑属性</div>
                        <div className="empty-state-hint">点击画布中的组件进行选择</div>
                    </div>
                </div>
            </div>
        );
    }

    if (!selectedComponent) {
        const total = selectedComponents.length;
        const allLocked = selectedComponents.every((item) => item.locked);
        const allVisible = selectedComponents.every((item) => item.visible);
        const grouped = selectedComponents.filter((item) => Boolean(item.groupId)).length;
        const primarySelected = selectedComponents[0];
        return (
            <div className="property-panel property-panel--batch">
                <div className="property-panel-header">
                    <h3>批量属性 ({total})</h3>
                    <p className="property-panel-subtitle">
                        统一处理 {primarySelected?.type || 'selected'} 组件。
                        {grouped > 0 ? ` 当前包含 ${grouped} 个已编组组件。` : ''}
                    </p>
                </div>
                <div className="property-panel-content">
                    <div className="property-section">
                        <div className="property-section-title">批量设置</div>
                        <div className="property-row">
                            <label className="property-label">宽度</label>
                            <input
                                type="number"
                                className="property-input"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ width: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一宽度"
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">高度</label>
                            <input
                                type="number"
                                className="property-input"
                                min={50}
                                onChange={(e) => updateSelectedComponents({ height: Math.max(50, Number(e.target.value) || 50) })}
                                placeholder="统一高度"
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">锁定</label>
                            <select
                                className="property-input"
                                value={allLocked ? 'locked' : 'unlocked'}
                                onChange={(e) => updateSelectedComponents({ locked: e.target.value === 'locked' })}
                            >
                                <option value="locked">全部锁定</option>
                                <option value="unlocked">全部解锁</option>
                            </select>
                        </div>
                        <div className="property-row">
                            <label className="property-label">可见</label>
                            <select
                                className="property-input"
                                value={allVisible ? 'visible' : 'hidden'}
                                onChange={(e) => updateSelectedComponents({ visible: e.target.value === 'visible' })}
                            >
                                <option value="visible">全部可见</option>
                                <option value="hidden">全部隐藏</option>
                            </select>
                        </div>
                    </div>
                    <div className="property-section">
                        <div className="property-section-title">批量动作</div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6 }}>
                            <button type="button" className="property-btn-small" onClick={() => alignSelected('left')} disabled={total < 2}>左对齐</button>
                            <button type="button" className="property-btn-small" onClick={() => alignSelected('right')} disabled={total < 2}>右对齐</button>
                            <button type="button" className="property-btn-small" onClick={() => alignSelected('top')} disabled={total < 2}>顶对齐</button>
                            <button type="button" className="property-btn-small" onClick={() => alignSelected('bottom')} disabled={total < 2}>底对齐</button>
                            <button type="button" className="property-btn-small" onClick={() => distributeSelected('horizontal')} disabled={total < 3}>水平分布</button>
                            <button type="button" className="property-btn-small" onClick={() => distributeSelected('vertical')} disabled={total < 3}>垂直分布</button>
                            <button type="button" className="property-btn-small" onClick={groupSelected} disabled={total < 2}>组合</button>
                            <button type="button" className="property-btn-small" onClick={ungroupSelected} disabled={total < 1}>解组</button>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                            <button
                                type="button"
                                className="property-btn-small"
                                disabled={!primarySelected}
                                onClick={() => {
                                    if (!primarySelected) return;
                                    updateSelectedComponents({ width: primarySelected.width });
                                }}
                                title="将选中组件宽度统一为首个选中组件宽度"
                            >
                                同步首项宽度
                            </button>
                            <button
                                type="button"
                                className="property-btn-small"
                                disabled={!primarySelected}
                                onClick={() => {
                                    if (!primarySelected) return;
                                    updateSelectedComponents({ height: primarySelected.height });
                                }}
                                title="将选中组件高度统一为首个选中组件高度"
                            >
                                同步首项高度
                            </button>
                        </div>
                    </div>
                    <div className="property-section">
                        <div className="property-section-title">选择概览</div>
                        <div style={{ fontSize: 12, opacity: 0.8, lineHeight: 1.7 }}>
                            已选组件: {total}<br />
                            已分组组件: {grouped}<br />
                            类型数: {new Set(selectedComponents.map((item) => item.type)).size}
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    const handleChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, { [key]: value });
    };

    const handleConfigChange = (key: string, value: unknown) => {
        updateComponent(selectedComponent.id, {
            config: { ...selectedComponent.config, [key]: value },
        });
    };

    const canvasWidth = Number(config.width) || 1920;
    const canvasHeight = Number(config.height) || 1080;
    const normalizedPanelFilter = panelFilter.trim().toLowerCase();
    const sectionVisible = (...aliases: string[]) => {
        if (!normalizedPanelFilter) return true;
        return aliases.some((item) => item.toLowerCase().includes(normalizedPanelFilter));
    };
    const isSectionCollapsedStored = (sectionKey: string) => collapsedSections.includes(sectionKey);
    const isSectionCollapsed = (sectionKey: string) => (
        normalizedPanelFilter ? false : isSectionCollapsedStored(sectionKey)
    );
    const toggleSection = (sectionKey: string) => {
        setCollapsedSections((prev) => {
            if (prev.includes(sectionKey)) {
                return prev.filter((item) => item !== sectionKey);
            }
            return [...prev, sectionKey];
        });
    };
    const collapseAllSections = () => {
        setCollapsedSections([...PROPERTY_SECTION_KEYS]);
    };
    const expandAllSections = () => {
        setCollapsedSections([]);
    };
    const collapseToEssential = () => {
        setCollapsedSections([...PROPERTY_SECTION_ESSENTIAL_COLLAPSED]);
    };
    const applyPanelPreset = (
        preset: '' | '位置' | '组件' | '数据' | '联动' | '下钻' | '解释' | '其他' | '常用',
    ) => {
        if (!preset) {
            setPanelFilter('');
            return;
        }
        if (preset === '常用') {
            setPanelFilter('');
            setPanelDensity('focus');
            collapseToEssential();
            return;
        }
        setPanelFilter(preset);
        setPanelDensity('full');
    };
    const shouldRenderSection = (sectionKey: string, ...aliases: string[]) => {
        if (!sectionVisible(...aliases)) {
            return false;
        }
        if (panelDensity === 'full') {
            return true;
        }
        if (normalizedPanelFilter) {
            return true;
        }
        return PROPERTY_FOCUS_SECTION_KEYS.has(sectionKey);
    };
    const showQuickActionGroup = (group: 'core' | 'layout' | 'nudge' | 'clipboard') => (
        quickActionMode === 'all' || quickActionMode === group
    );
    const getQuickActionFilterButtonStyle = (mode: 'core' | 'layout' | 'nudge' | 'clipboard' | 'all') => (
        quickActionMode === mode
            ? { borderColor: 'var(--color-primary)', background: 'var(--color-primary-light)' }
            : undefined
    );

    const alignToCanvas = (mode: 'left' | 'right' | 'top' | 'bottom' | 'h-center' | 'v-center') => {
        if (mode === 'left') {
            handleChange('x', 0);
            return;
        }
        if (mode === 'right') {
            handleChange('x', Math.max(0, canvasWidth - selectedComponent.width));
            return;
        }
        if (mode === 'top') {
            handleChange('y', 0);
            return;
        }
        if (mode === 'bottom') {
            handleChange('y', Math.max(0, canvasHeight - selectedComponent.height));
            return;
        }
        if (mode === 'h-center') {
            handleChange('x', Math.max(0, Math.round((canvasWidth - selectedComponent.width) / 2)));
            return;
        }
        handleChange('y', Math.max(0, Math.round((canvasHeight - selectedComponent.height) / 2)));
    };

    const duplicateCurrentComponent = () => {
        const nextId = `comp_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
        const maxZ = config.components.length > 0
            ? Math.max(...config.components.map((item) => item.zIndex))
            : 0;
        const maxX = Math.max(0, canvasWidth - selectedComponent.width);
        const maxY = Math.max(0, canvasHeight - selectedComponent.height);
        const clone: ScreenComponent = {
            ...selectedComponent,
            id: nextId,
            x: Math.min(maxX, selectedComponent.x + 20),
            y: Math.min(maxY, selectedComponent.y + 20),
            zIndex: maxZ + 1,
            name: `${selectedComponent.name}-副本`,
        };
        updateConfig({ components: [...config.components, clone] });
    };

    const copyCurrentStyle = () => {
        const payload = buildStyleClipboardPayload(selectedComponent);
        persistStyleClipboard(payload);
        alert(`已复制样式（${Object.keys(payload.config).length} 个外观字段）`);
    };

    const applyCopiedStyle = () => {
        if (!styleClipboard) {
            alert('样式剪贴板为空，请先复制一个组件样式');
            return;
        }
        if (styleClipboard.type !== selectedComponent.type) {
            const confirmed = window.confirm(
                `样式来源类型为「${styleClipboard.type}」，当前为「${selectedComponent.type}」。\n继续应用可能只部分生效，是否继续？`
            );
            if (!confirmed) return;
        }
        updateComponent(selectedComponent.id, {
            width: Math.max(50, Number(styleClipboard.width) || selectedComponent.width),
            height: Math.max(50, Number(styleClipboard.height) || selectedComponent.height),
            config: {
                ...selectedComponent.config,
                ...styleClipboard.config,
            },
        });
    };

    const copyLayoutSnapshot = () => {
        persistLayoutClipboard({
            x: selectedComponent.x,
            y: selectedComponent.y,
            width: selectedComponent.width,
            height: selectedComponent.height,
            copiedAt: new Date().toISOString(),
        });
        alert('布局已复制（位置 + 尺寸）');
    };

    const pasteLayoutSnapshot = () => {
        if (!layoutClipboard) {
            alert('布局剪贴板为空，请先复制布局');
            return;
        }
        const nextWidth = Math.max(50, Math.round(layoutClipboard.width));
        const nextHeight = Math.max(50, Math.round(layoutClipboard.height));
        const maxX = Math.max(0, canvasWidth - nextWidth);
        const maxY = Math.max(0, canvasHeight - nextHeight);
        updateComponent(selectedComponent.id, {
            x: Math.min(maxX, Math.max(0, Math.round(layoutClipboard.x))),
            y: Math.min(maxY, Math.max(0, Math.round(layoutClipboard.y))),
            width: nextWidth,
            height: nextHeight,
        });
    };

    const nudgePosition = (dx: number, dy: number) => {
        const maxX = Math.max(0, canvasWidth - selectedComponent.width);
        const maxY = Math.max(0, canvasHeight - selectedComponent.height);
        updateComponent(selectedComponent.id, {
            x: Math.min(maxX, Math.max(0, selectedComponent.x + dx)),
            y: Math.min(maxY, Math.max(0, selectedComponent.y + dy)),
        });
    };

    const nudgeSize = (dw: number, dh: number) => {
        const nextWidth = Math.min(canvasWidth, Math.max(50, selectedComponent.width + dw));
        const nextHeight = Math.min(canvasHeight, Math.max(50, selectedComponent.height + dh));
        const maxX = Math.max(0, canvasWidth - nextWidth);
        const maxY = Math.max(0, canvasHeight - nextHeight);
        updateComponent(selectedComponent.id, {
            width: nextWidth,
            height: nextHeight,
            x: Math.min(maxX, Math.max(0, selectedComponent.x)),
            y: Math.min(maxY, Math.max(0, selectedComponent.y)),
        });
    };
    const applyChartPreset = (preset: ChartPreset) => {
        if (!isChartComponentType(selectedComponent.type)) {
            alert('当前组件不是图表类型，无法应用图表预设');
            return;
        }
        updateComponent(selectedComponent.id, {
            config: applyChartPresetConfig(selectedComponent.config, preset),
        });
    };

    const copyConfigJson = async () => {
        const text = JSON.stringify(selectedComponent.config || {}, null, 2);
        const copied = await writeTextToClipboard(text);
        alert(copied ? '组件配置JSON已复制' : '复制失败，请重试');
    };

    const pasteConfigJson = () => {
        const current = JSON.stringify(selectedComponent.config || {}, null, 2);
        const input = window.prompt('粘贴组件配置 JSON（将覆盖当前组件配置）', current);
        if (input == null) return;
        try {
            const parsed = JSON.parse(input);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
                alert('配置必须是 JSON 对象');
                return;
            }
            updateComponent(selectedComponent.id, { config: parsed as Record<string, unknown> });
            alert('组件配置已更新');
        } catch {
            alert('JSON 格式错误，请检查后重试');
        }
    };

    const applyTabVisibilityRules = () => {
        if (selectedComponent.type !== 'tab-switcher') return;
        const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
        const optionValues = resolveTabSwitcherOptionValues(selectedComponent.config.options);
        if (optionValues.length === 0) {
            alert('请先在 Tab 组件中配置可用选项');
            return;
        }
        const targetTypes = new Set<ComponentType>([
            'line-chart',
            'bar-chart',
            'pie-chart',
            'map-chart',
            'table',
            'scroll-board',
            'scroll-ranking',
            'funnel-chart',
            'scatter-chart',
            'radar-chart',
            'gauge-chart',
        ]);
        let assigned = 0;
        let index = 0;
        const nextComponents = config.components.map((item) => {
            if (item.id === selectedComponent.id || !targetTypes.has(item.type)) {
                return item;
            }
            const match = optionValues[index % optionValues.length];
            index += 1;
            assigned += 1;
            return {
                ...item,
                config: {
                    ...item.config,
                    visibilityRuleEnabled: true,
                    visibilityVariableKey: variableKey,
                    visibilityMatchMode: 'equals',
                    visibilityMatchValues: [match],
                },
            };
        });
        if (assigned <= 0) {
            alert('当前画布没有可绑定 Tab 显隐规则的图表/表格组件');
            return;
        }
        updateConfig({ components: nextComponents });
        alert(`已应用 Tab 显隐规则到 ${assigned} 个组件`);
    };

    const clearTabVisibilityRules = () => {
        if (selectedComponent.type !== 'tab-switcher') return;
        const variableKey = String(selectedComponent.config.variableKey || 'tabKey').trim() || 'tabKey';
        let cleared = 0;
        const nextComponents = config.components.map((item) => {
            if (item.id === selectedComponent.id) {
                return item;
            }
            const currentVarKey = String((item.config as Record<string, unknown>).visibilityVariableKey ?? '').trim();
            if (currentVarKey !== variableKey) {
                return item;
            }
            const raw = item.config as Record<string, unknown>;
            const {
                visibilityRuleEnabled: _visibilityRuleEnabled,
                visibilityVariableKey: _visibilityVariableKey,
                visibilityMatchMode: _visibilityMatchMode,
                visibilityMatchValues: _visibilityMatchValues,
                visibilityMatchValue: _visibilityMatchValue,
                ...rest
            } = raw;
            cleared += 1;
            return { ...item, config: rest };
        });
        if (cleared <= 0) {
            alert('未找到可清理的 Tab 显隐规则');
            return;
        }
        updateConfig({ components: nextComponents });
        alert(`已清理 ${cleared} 个组件的 Tab 显隐规则`);
    };

    const pluginMeta = readComponentPluginMeta(selectedComponent.config);
    const runtimePlugin = pluginMeta ? getRendererPlugin(resolveRuntimePluginId(pluginMeta)) : undefined;
    const explainCardId = resolveExplainCardId(selectedComponent);
    const canExplain = Number.isFinite(explainCardId) && (explainCardId ?? 0) > 0;

    const handleExplain = async () => {
        if (!canExplain || !explainCardId) {
            return;
        }
        setExplainState({ state: 'loading' });
        try {
            const value = await analyticsApi.explainCard(explainCardId, { componentId: selectedComponent.id });
            setExplainState({ state: 'loaded', value });
        } catch (error) {
            setExplainState({ state: 'error', error });
        }
    };
    const drillDownContent = shouldRenderSection('drill-down', '下钻', 'drill')
        ? renderDrillDownConfig(selectedComponent, updateComponent, { embedded: true })
        : null;
    const interactionContent = shouldRenderSection('interaction', '联动', '交互', 'interaction', 'jump')
        ? renderInteractionConfig(selectedComponent, config.globalVariables ?? [], updateComponent, { embedded: true })
        : null;
    const actionContent = shouldRenderSection('actions', '动作', '面板', '意图', '跳转')
        ? renderActionConfig(selectedComponent, updateComponent, { embedded: true })
        : null;

    return (
        <div className={`property-panel property-panel--${panelDensity}`}>
            <div className="property-panel-header">
                <h3>属性 - {selectedComponent.name}</h3>
                <p className="property-panel-subtitle">
                    {selectedComponent.type} · {selectedComponent.width} × {selectedComponent.height} · {panelDensity === 'focus' ? '高频视图' : '完整视图'}
                </p>
            </div>
            <div className="property-panel-content">
                <div className="property-section">
                    <div className="property-section-title property-section-title-collapsible">
                        <button
                            type="button"
                            className="property-section-toggle"
                            onClick={() => toggleSection('quick-filter')}
                        >
                            {isSectionCollapsed('quick-filter') ? '▸' : '▾'} 快速定位
                        </button>
                    </div>
                    {!isSectionCollapsed('quick-filter') ? (
                        <>
                            <div className="property-row">
                                <label className="property-label">筛选</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={panelFilter}
                                    onChange={(e) => setPanelFilter(e.target.value)}
                                    placeholder="输入：位置/样式/数据/联动/可见..."
                                />
                            </div>
                            <div className="property-quick-filter-row">
                                <button type="button" className="property-btn-small" onClick={() => applyPanelPreset('')}>清空</button>
                                <select
                                    className="property-input"
                                    style={{ maxWidth: 160, padding: '4px 8px' }}
                                    defaultValue=""
                                    onChange={(event) => {
                                        const next = event.target.value as '' | '位置' | '组件' | '数据' | '联动' | '下钻' | '解释' | '其他' | '常用';
                                        applyPanelPreset(next);
                                        event.currentTarget.value = '';
                                    }}
                                >
                                    <option value="">快速定位到...</option>
                                    <option value="位置">位置与尺寸</option>
                                    <option value="组件">组件配置</option>
                                    <option value="数据">数据源</option>
                                    <option value="联动">联动配置</option>
                                    <option value="下钻">下钻配置</option>
                                    <option value="解释">解释</option>
                                    <option value="其他">其他</option>
                                    <option value="常用">常用视图</option>
                                </select>
                                <button
                                    type="button"
                                    className={`property-btn-small ${panelDensity === 'focus' ? 'is-active' : ''}`}
                                    onClick={() => setPanelDensity('focus')}
                                >
                                    高频
                                </button>
                                <button
                                    type="button"
                                    className={`property-btn-small ${panelDensity === 'full' ? 'is-active' : ''}`}
                                    onClick={() => setPanelDensity('full')}
                                >
                                    全部
                                </button>
                            </div>
                            <div className="property-quick-filter-row">
                                <button type="button" className="property-btn-small" onClick={collapseToEssential}>常用视图</button>
                                <button type="button" className="property-btn-small" onClick={expandAllSections}>全部展开</button>
                                <button type="button" className="property-btn-small" onClick={collapseAllSections}>全部收起</button>
                            </div>
                        </>
                    ) : null}
                </div>

                {shouldRenderSection('quick-actions', '快捷', '操作', '样式', '复制', '对齐') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('quick-actions')}
                            >
                                {isSectionCollapsed('quick-actions') ? '▸' : '▾'} 快捷操作
                            </button>
                        </div>
                        {!isSectionCollapsed('quick-actions') ? (
                            <>
                                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 6 }}>
                                    <button type="button" className="property-btn-small" style={getQuickActionFilterButtonStyle('core')} onClick={() => setQuickActionMode('core')}>常用</button>
                                    <button type="button" className="property-btn-small" style={getQuickActionFilterButtonStyle('layout')} onClick={() => setQuickActionMode('layout')}>布局</button>
                                    <button type="button" className="property-btn-small" style={getQuickActionFilterButtonStyle('nudge')} onClick={() => setQuickActionMode('nudge')}>微调</button>
                                    <button type="button" className="property-btn-small" style={getQuickActionFilterButtonStyle('clipboard')} onClick={() => setQuickActionMode('clipboard')}>剪贴板</button>
                                    <button type="button" className="property-btn-small" style={getQuickActionFilterButtonStyle('all')} onClick={() => setQuickActionMode('all')}>全部</button>
                                </div>
                                {showQuickActionGroup('core') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6 }}>
                                        <button type="button" className="property-btn-small" onClick={duplicateCurrentComponent}>复制组件</button>
                                        <button type="button" className="property-btn-small" onClick={() => deleteComponents([selectedComponent.id])}>删除组件</button>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('h-center')}>水平居中</button>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('v-center')}>垂直居中</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('layout') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('left')}>贴左</button>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('right')}>贴右</button>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('top')}>贴上</button>
                                        <button type="button" className="property-btn-small" onClick={() => alignToCanvas('bottom')}>贴下</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('nudge') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(-1, 0)} title="X -1">←1</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(1, 0)} title="X +1">→1</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(0, -1)} title="Y -1">↑1</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(0, 1)} title="Y +1">↓1</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(-10, 0)} title="X -10">←10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(10, 0)} title="X +10">→10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(0, -10)} title="Y -10">↑10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgePosition(0, 10)} title="Y +10">↓10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgeSize(-10, 0)} title="宽度 -10">宽-10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgeSize(10, 0)} title="宽度 +10">宽+10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgeSize(0, -10)} title="高度 -10">高-10</button>
                                        <button type="button" className="property-btn-small" onClick={() => nudgeSize(0, 10)} title="高度 +10">高+10</button>
                                    </div>
                                ) : null}
                                {showQuickActionGroup('clipboard') ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small" onClick={copyCurrentStyle}>复制样式</button>
                                        <button type="button" className="property-btn-small" onClick={applyCopiedStyle}>粘贴样式</button>
                                        <button type="button" className="property-btn-small" onClick={copyLayoutSnapshot}>复制布局</button>
                                        <button type="button" className="property-btn-small" onClick={pasteLayoutSnapshot}>粘贴布局</button>
                                        <button type="button" className="property-btn-small" onClick={() => { void copyConfigJson(); }}>复制配置JSON</button>
                                        <button type="button" className="property-btn-small" onClick={pasteConfigJson}>粘贴配置JSON</button>
                                        <button type="button" className="property-btn-small" onClick={() => persistStyleClipboard(null)}>清空样式板</button>
                                        <button type="button" className="property-btn-small" onClick={() => persistLayoutClipboard(null)}>清空布局板</button>
                                    </div>
                                ) : null}
                                {CHART_COMPONENT_TYPES.has(selectedComponent.type) ? (
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6, marginTop: 6 }}>
                                        <button type="button" className="property-btn-small" onClick={() => applyChartPreset('business')} title="适合白底商务大屏">商务预设</button>
                                        <button type="button" className="property-btn-small" onClick={() => applyChartPreset('compact')} title="适合小尺寸组件">紧凑预设</button>
                                        <button type="button" className="property-btn-small" onClick={() => applyChartPreset('clear')} title="恢复默认可读策略">恢复预设</button>
                                    </div>
                                ) : null}
                                <div style={{ marginTop: 6, fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                                    {styleClipboard
                                        ? `样式剪贴板：${styleClipboard.type}（${Object.keys(styleClipboard.config || {}).length} 字段）`
                                        : '样式剪贴板为空，可先在任意组件点击“复制样式”。'}
                                    <br />
                                    {layoutClipboard
                                        ? `布局剪贴板：${layoutClipboard.width}×${layoutClipboard.height} @ (${layoutClipboard.x}, ${layoutClipboard.y})`
                                        : '布局剪贴板为空，可复制当前组件布局。'}
                                </div>
                            </>
                        ) : null}
                    </div>
                )}

                {/* Position & Size */}
                {shouldRenderSection('position-size', '位置', '尺寸', 'x', 'y', '宽', '高') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('position-size')}
                            >
                                {isSectionCollapsed('position-size') ? '▸' : '▾'} 位置与尺寸
                            </button>
                        </div>
                        {!isSectionCollapsed('position-size') ? (
                            <>
                                <div className="property-row">
                                    <label className="property-label">X</label>
                                    <input
                                        type="number"
                                        className="property-input"
                                        value={selectedComponent.x}
                                        onChange={(e) => handleChange('x', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row">
                                    <label className="property-label">Y</label>
                                    <input
                                        type="number"
                                        className="property-input"
                                        value={selectedComponent.y}
                                        onChange={(e) => handleChange('y', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row">
                                    <label className="property-label">宽度</label>
                                    <input
                                        type="number"
                                        className="property-input"
                                        value={selectedComponent.width}
                                        onChange={(e) => handleChange('width', Number(e.target.value))}
                                    />
                                </div>

                                <div className="property-row">
                                    <label className="property-label">高度</label>
                                    <input
                                        type="number"
                                        className="property-input"
                                        value={selectedComponent.height}
                                        onChange={(e) => handleChange('height', Number(e.target.value))}
                                    />
                                </div>
                            </>
                        ) : null}
                    </div>
                )}

                {/* Component-specific config */}
                {runtimePlugin?.propertySchema?.fields?.length && shouldRenderSection('plugin-config', '插件', 'plugin', runtimePlugin.name) ? (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('plugin-config')}
                            >
                                {isSectionCollapsed('plugin-config') ? '▸' : '▾'} 插件配置 ({runtimePlugin.name})
                            </button>
                        </div>
                        {!isSectionCollapsed('plugin-config')
                            ? renderPluginSchemaFields(selectedComponent, runtimePlugin.propertySchema.fields, handleConfigChange)
                            : null}
                    </div>
                ) : null}

                {shouldRenderSection('component-config', '组件', '样式', '图表', '外观') && (
                    <div className="property-section">
                        <div
                            className="property-section-title property-section-title-collapsible"
                            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}
                        >
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('component-config')}
                            >
                                {isSectionCollapsed('component-config') ? '▸' : '▾'} 组件配置
                            </button>
                            {CHART_COMPONENT_TYPES.has(selectedComponent.type) ? (
                                <div style={{ display: 'inline-flex', gap: 6 }}>
                                    <button
                                        type="button"
                                        className="property-btn-small"
                                        onClick={() => setComponentConfigMode('quick')}
                                        style={{
                                            minHeight: 24,
                                            padding: '2px 8px',
                                            opacity: componentConfigMode === 'quick' ? 1 : 0.75,
                                        }}
                                    >
                                        简洁
                                    </button>
                                    <button
                                        type="button"
                                        className="property-btn-small"
                                        onClick={() => setComponentConfigMode('advanced')}
                                        style={{
                                            minHeight: 24,
                                            padding: '2px 8px',
                                            opacity: componentConfigMode === 'advanced' ? 1 : 0.75,
                                        }}
                                    >
                                        专业
                                    </button>
                                </div>
                            ) : null}
                        </div>

                        {!isSectionCollapsed('component-config')
                            ? (
                                CHART_COMPONENT_TYPES.has(selectedComponent.type) && componentConfigMode === 'quick'
                                    ? renderQuickChartConfig(selectedComponent, handleConfigChange, applyChartPreset)
                                    : renderComponentConfig(selectedComponent, handleConfigChange)
                            )
                            : null}
                    </div>
                )}

                {/* Data Source */}
                {shouldRenderSection('data-source', '数据', 'sql', 'card', 'api', 'dataset', 'metric') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('data-source')}
                            >
                                {isSectionCollapsed('data-source') ? '▸' : '▾'} 数据源
                            </button>
                        </div>
                        {!isSectionCollapsed('data-source')
                            ? renderDataSourceConfig(selectedComponent, updateComponent, config.globalVariables ?? [])
                            : null}
                    </div>
                )}

                {/* Field Mapping */}
                {shouldRenderSection('data-source', '字段映射', 'field', 'mapping') && isMappable(selectedComponent.type) && (() => {
                    const fmSourceCols = selectedComponent.config._sourceColumns as Array<{ name: string; displayName: string; baseType?: string }> ?? [];
                    const hasFmSource = resolveDataSourceType(selectedComponent.dataSource as DataSourceConfig | undefined) !== 'static';
                    if (!hasFmSource || fmSourceCols.length === 0) return null;
                    const currentMapping = (selectedComponent.config._fieldMapping as FieldMapping) ?? {};
                    const useFieldMapping = selectedComponent.config._useFieldMapping !== false;
                    return (
                        <div className="property-section">
                            <div className="property-section-title property-section-title-collapsible">
                                <button
                                    type="button"
                                    className="property-section-toggle"
                                    onClick={() => toggleSection('field-mapping')}
                                >
                                    {isSectionCollapsed('field-mapping') ? '▸' : '▾'} 字段映射
                                </button>
                                <label style={{ fontSize: 11, color: '#94a3b8', display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto' }}>
                                    <input
                                        type="checkbox"
                                        checked={useFieldMapping}
                                        onChange={(e) => {
                                            handleConfigChange('_useFieldMapping', e.target.checked);
                                        }}
                                    />
                                    启用
                                </label>
                            </div>
                            {!isSectionCollapsed('field-mapping') && useFieldMapping ? (
                                <FieldMappingPanel
                                    componentType={selectedComponent.type}
                                    sourceColumns={fmSourceCols}
                                    mapping={currentMapping}
                                    onChange={(newMapping) => handleConfigChange('_fieldMapping', newMapping)}
                                />
                            ) : !isSectionCollapsed('field-mapping') ? (
                                <div style={{ fontSize: 11, color: '#64748b', padding: '4px 0' }}>
                                    字段映射已关闭，使用高级模式直接编辑 config。
                                </div>
                            ) : null}
                        </div>
                    );
                })()}

                {shouldRenderSection('explain', '解释', 'explain') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('explain')}
                            >
                                {isSectionCollapsed('explain') ? '▸' : '▾'} 解释
                            </button>
                        </div>
                        {!isSectionCollapsed('explain') ? (
                            canExplain ? (
                                <div style={{ display: 'grid', gap: 8 }}>
                                    <button
                                        type="button"
                                        className="property-btn-small"
                                        onClick={() => { void handleExplain(); }}
                                    >
                                        解释当前组件
                                    </button>
                                    <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                                        解释来源 CardId: {explainCardId}
                                    </div>
                                    {explainState?.state === 'loading' ? (
                                        <div style={{ fontSize: 12, color: '#94a3b8' }}>解释生成中...</div>
                                    ) : null}
                                    {explainState?.state === 'error' ? (
                                        <div style={{ fontSize: 12, color: '#ef4444' }}>
                                            解释失败：{explainState.error instanceof Error ? explainState.error.message : 'unknown error'}
                                        </div>
                                    ) : null}
                                    {explainState?.state === 'loaded' ? (
                                        <>
                                            <button
                                                type="button"
                                                className="property-btn-small"
                                                onClick={() => {
                                                    const text = explainState.value.copyJson ?? JSON.stringify(explainState.value.explainCard ?? {}, null, 2);
                                                    void writeTextToClipboard(text);
                                                }}
                                            >
                                                复制解释JSON
                                            </button>
                                            <pre
                                                style={{
                                                    margin: 0,
                                                    padding: 8,
                                                    borderRadius: 8,
                                                    background: 'rgba(15,23,42,0.6)',
                                                    whiteSpace: 'pre-wrap',
                                                    wordBreak: 'break-word',
                                                    fontSize: 11,
                                                    maxHeight: 240,
                                                    overflow: 'auto',
                                                }}
                                            >
                                                {JSON.stringify(explainState.value.explainCard ?? {}, null, 2)}
                                            </pre>
                                        </>
                                    ) : null}
                                </div>
                            ) : (
                                <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.45 }}>
                                    当前组件未绑定可解释的 Card 数据源。
                                </div>
                            )
                        ) : null}
                    </div>
                )}

                {/* Chart annotations (markLine / markArea / conditionalColors) */}
                {shouldRenderSection('component-config', '标注', '辅助线', 'markLine', 'threshold') && (selectedComponent.type === 'line-chart' || selectedComponent.type === 'bar-chart' || selectedComponent.type === 'scatter-chart' || selectedComponent.type === 'combo-chart' || selectedComponent.type === 'waterfall-chart') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('annotations')}
                            >
                                {isSectionCollapsed('annotations') ? '▸' : '▾'} 标注 / 阈值线
                            </button>
                        </div>
                        {!isSectionCollapsed('annotations') && (
                            <ChartAnnotationConfig
                                component={selectedComponent}
                                onChange={handleConfigChange}
                            />
                        )}
                    </div>
                )}

                {/* Drill-down config */}
                {drillDownContent ? (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('drill-down')}
                            >
                                {isSectionCollapsed('drill-down') ? '▸' : '▾'} 下钻配置
                            </button>
                        </div>
                        {!isSectionCollapsed('drill-down') ? drillDownContent : null}
                    </div>
                ) : null}

                {interactionContent ? (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('interaction')}
                            >
                                {isSectionCollapsed('interaction') ? '▸' : '▾'} 联动配置
                            </button>
                        </div>
                        {!isSectionCollapsed('interaction') ? interactionContent : null}
                    </div>
                ) : null}

                {actionContent ? (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('actions')}
                            >
                                {isSectionCollapsed('actions') ? '▸' : '▾'} 动作入口
                            </button>
                        </div>
                        {!isSectionCollapsed('actions') ? actionContent : null}
                    </div>
                ) : null}

                {/* Visibility & Lock */}
                {shouldRenderSection('other', '其他', '名称', '容器', '锁定', '可见') && (
                    <div className="property-section">
                        <div className="property-section-title property-section-title-collapsible">
                            <button
                                type="button"
                                className="property-section-toggle"
                                onClick={() => toggleSection('other')}
                            >
                                {isSectionCollapsed('other') ? '▸' : '▾'} 其他
                            </button>
                        </div>
                        {!isSectionCollapsed('other') ? (
                            <>
                                <div className="property-row">
                                    <label className="property-label">名称</label>
                                    <input
                                        type="text"
                                        className="property-input"
                                        value={selectedComponent.name}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                    />
                                </div>

                                <div className="property-row">
                                    <label className="property-label">所属容器</label>
                                    <select
                                        className="property-input"
                                        value={selectedComponent.parentContainerId || ''}
                                        onChange={(e) => {
                                            const parentId = e.target.value || undefined;
                                            if (!parentId) {
                                                updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                                return;
                                            }
                                            const parent = config.components.find((item) => item.id === parentId && item.type === 'container');
                                            if (!parent) {
                                                updateComponent(selectedComponent.id, { parentContainerId: undefined });
                                                return;
                                            }
                                            if (wouldCreateParentCycle(config.components, selectedComponent.id, parentId)) {
                                                alert('该容器绑定会形成循环引用，请选择其他容器');
                                                return;
                                            }
                                            const maxX = parent.x + Math.max(0, parent.width - selectedComponent.width);
                                            const maxY = parent.y + Math.max(0, parent.height - selectedComponent.height);
                                            const nextX = Math.max(parent.x, Math.min(selectedComponent.x, maxX));
                                            const nextY = Math.max(parent.y, Math.min(selectedComponent.y, maxY));
                                            updateComponent(selectedComponent.id, {
                                                parentContainerId: parentId,
                                                x: nextX,
                                                y: nextY,
                                            });
                                        }}
                                    >
                                        <option value="">-- 无 --</option>
                                        {config.components
                                            .filter((item) => (
                                                item.type === 'container'
                                                && item.id !== selectedComponent.id
                                                && !wouldCreateParentCycle(config.components, selectedComponent.id, item.id)
                                            ))
                                            .map((item) => (
                                                <option key={item.id} value={item.id}>
                                                    {item.name} ({item.id})
                                                </option>
                                            ))}
                                    </select>
                                </div>

                                {selectedComponent.type === 'container' && (
                                    <div className="property-row">
                                        <label className="property-label">子组件数</label>
                                        <div className="property-input" style={{ display: 'flex', alignItems: 'center' }}>
                                            {config.components.filter((item) => item.parentContainerId === selectedComponent.id).length}
                                        </div>
                                    </div>
                                )}
                                {selectedComponent.type === 'tab-switcher' && (
                                    <div className="property-row" style={{ alignItems: 'flex-start' }}>
                                        <label className="property-label">Tab联动</label>
                                        <div style={{ display: 'grid', gap: 6, width: '100%' }}>
                                            <button type="button" className="property-btn-small" onClick={applyTabVisibilityRules}>
                                                一键应用显隐规则
                                            </button>
                                            <button type="button" className="property-btn-small" onClick={clearTabVisibilityRules}>
                                                清理显隐规则
                                            </button>
                                            <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.5 }}>
                                                规则会按 Tab 选项顺序分配到图表/表格组件。
                                            </div>
                                        </div>
                                    </div>
                                )}

                                <div className="property-row">
                                    <label className="property-label">锁定</label>
                                    <input
                                        type="checkbox"
                                        checked={selectedComponent.locked}
                                        onChange={(e) => handleChange('locked', e.target.checked)}
                                    />
                                </div>

                                <div className="property-row">
                                    <label className="property-label">可见</label>
                                    <input
                                        type="checkbox"
                                        checked={selectedComponent.visible}
                                        onChange={(e) => handleChange('visible', e.target.checked)}
                                    />
                                </div>
                                <div className="property-row">
                                    <label className="property-label">多端可见</label>
                                    <div style={{ display: 'flex', gap: 8 }}>
                                        {(['pc', 'tablet', 'mobile'] as const).map((device) => {
                                            const current = Array.isArray(selectedComponent.config.visibleOn)
                                                ? selectedComponent.config.visibleOn as string[]
                                                : ['pc', 'tablet', 'mobile'];
                                            const checked = current.includes(device);
                                            const label = device === 'pc' ? 'PC' : device === 'tablet' ? '平板' : '手机';
                                            return (
                                                <label key={device} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12 }}>
                                                    <input
                                                        type="checkbox"
                                                        checked={checked}
                                                        onChange={(e) => {
                                                            const base = Array.isArray(selectedComponent.config.visibleOn)
                                                                ? selectedComponent.config.visibleOn as string[]
                                                                : ['pc', 'tablet', 'mobile'];
                                                            const next = e.target.checked
                                                                ? Array.from(new Set([...base, device]))
                                                                : base.filter((item) => item !== device);
                                                            handleConfigChange('visibleOn', next);
                                                        }}
                                                    />
                                                    {label}
                                                </label>
                                            );
                                        })}
                                    </div>
                                </div>
                                <div className="property-row">
                                    <label className="property-label">变量可见条件</label>
                                    <input
                                        type="checkbox"
                                        checked={selectedComponent.config.visibilityRuleEnabled === true}
                                        onChange={(e) => handleConfigChange('visibilityRuleEnabled', e.target.checked)}
                                    />
                                </div>
                                {selectedComponent.config.visibilityRuleEnabled === true && (
                                    <>
                                        <div className="property-row">
                                            <label className="property-label">变量Key</label>
                                            <input
                                                type="text"
                                                className="property-input"
                                                value={String(selectedComponent.config.visibilityVariableKey ?? '')}
                                                onChange={(e) => handleConfigChange('visibilityVariableKey', e.target.value)}
                                                placeholder="tabKey"
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">匹配模式</label>
                                            <select
                                                className="property-input"
                                                value={String(selectedComponent.config.visibilityMatchMode ?? 'equals')}
                                                onChange={(e) => handleConfigChange('visibilityMatchMode', e.target.value)}
                                            >
                                                <option value="equals">等于任一值</option>
                                                <option value="not-equals">不等于任一值</option>
                                                <option value="contains">包含任一值</option>
                                                <option value="not-contains">不包含任一值</option>
                                                <option value="starts-with">前缀匹配任一值</option>
                                                <option value="ends-with">后缀匹配任一值</option>
                                                <option value="empty">为空</option>
                                                <option value="not-empty">非空</option>
                                            </select>
                                        </div>
                                        {(() => {
                                            const mode = String(selectedComponent.config.visibilityMatchMode ?? 'equals');
                                            if (mode === 'empty' || mode === 'not-empty') {
                                                return null;
                                            }
                                            return (
                                                <div className="property-row">
                                                    <label className="property-label">匹配值</label>
                                                    <textarea
                                                        className="property-input"
                                                        rows={4}
                                                        value={serializeVisibilityMatchValues(selectedComponent.config.visibilityMatchValues)}
                                                        onChange={(e) => handleConfigChange('visibilityMatchValues', parseVisibilityMatchValues(e.target.value))}
                                                        placeholder={'每行一个值，例如：\noverview\nline'}
                                                    />
                                                </div>
                                            );
                                        })()}
                                        <div style={{ fontSize: 11, color: '#94a3b8', marginTop: -2 }}>
                                            仅在预览/公开/导出模式生效，设计器中始终可见便于编辑。
                                        </div>
                                    </>
                                )}
                            </>
                        ) : null}
                    </div>
                )}
            </div>
        </div>
    );
}

function renderPluginSchemaFields(
    component: ScreenComponent,
    fields: PropertySchemaField[],
    onChange: (key: string, value: unknown) => void,
) {
    if (!Array.isArray(fields) || fields.length === 0) {
        return null;
    }
    return (
        <>
            {fields.map((field) => {
                const key = String(field?.key || '').trim();
                if (!key) return null;
                const label = field?.label || key;
                const value = component.config[key] ?? field?.defaultValue;
                const description = String(field?.description || '').trim();
                const descriptionNode = description ? (
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4, lineHeight: 1.45 }}>
                        {description}
                    </div>
                ) : null;
                if (field.type === 'boolean') {
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="checkbox"
                                    checked={Boolean(value)}
                                    onChange={(e) => onChange(key, e.target.checked)}
                                />
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'number') {
                    const min = Number.isFinite(field.min) ? Number(field.min) : undefined;
                    const max = Number.isFinite(field.max) ? Number(field.max) : undefined;
                    const step = Number.isFinite(field.step) ? Number(field.step) : undefined;
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={min}
                                    max={max}
                                    step={step}
                                    value={Number(value ?? 0)}
                                    onChange={(e) => onChange(key, Number(e.target.value))}
                                />
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'select') {
                    const options = Array.isArray(field.options)
                        ? field.options
                            .map((item) => {
                                if (!item || typeof item !== 'object') return null;
                                const labelText = String(item.label ?? '').trim();
                                const rawValue = (item as { value?: unknown }).value;
                                if (!labelText) return null;
                                if (
                                    typeof rawValue !== 'string'
                                    && typeof rawValue !== 'number'
                                    && typeof rawValue !== 'boolean'
                                ) {
                                    return null;
                                }
                                return {
                                    label: labelText,
                                    value: rawValue,
                                };
                            })
                            .filter((item): item is { label: string; value: string | number | boolean } => !!item)
                        : [];
                    const selectedIndex = options.findIndex((item) => String(item.value) === String(value));
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <select
                                    className="property-input"
                                    value={selectedIndex >= 0 ? String(selectedIndex) : ''}
                                    onChange={(e) => {
                                        const nextIdx = Number(e.target.value);
                                        if (!Number.isFinite(nextIdx) || nextIdx < 0 || nextIdx >= options.length) {
                                            return;
                                        }
                                        onChange(key, options[nextIdx].value);
                                    }}
                                >
                                    {selectedIndex < 0 && (
                                        <option value="">-- 请选择 --</option>
                                    )}
                                    {options.map((item, idx) => (
                                        <option key={`${item.label}-${idx}`} value={String(idx)}>
                                            {item.label}
                                        </option>
                                    ))}
                                </select>
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'color') {
                    const fallback = typeof value === 'string' && value ? value : '#3b82f6';
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <input
                                    type="color"
                                    className="property-color-input"
                                    value={fallback}
                                    onChange={(e) => onChange(key, e.target.value)}
                                />
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                if (field.type === 'array' || field.type === 'json') {
                    const isArray = field.type === 'array';
                    const snapshot = JSON.stringify(
                        value ?? (isArray ? [] : {}),
                        null,
                        2,
                    );
                    return (
                        <div className="property-row" key={key}>
                            <label className="property-label">{label}</label>
                            <div style={{ flex: 1 }}>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => {
                                        const input = window.prompt(`${label} (${isArray ? 'JSON数组' : 'JSON对象'})`, snapshot);
                                        if (input == null) return;
                                        try {
                                            const parsed = JSON.parse(input);
                                            if (isArray && !Array.isArray(parsed)) {
                                                alert(`${label} 需要是 JSON 数组`);
                                                return;
                                            }
                                            if (!isArray && (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed))) {
                                                alert(`${label} 需要是 JSON 对象`);
                                                return;
                                            }
                                            onChange(key, parsed);
                                        } catch {
                                            alert(`${label} JSON 格式错误`);
                                        }
                                    }}
                                >
                                    编辑JSON
                                </button>
                                <pre style={{
                                    margin: '6px 0 0',
                                    maxHeight: 120,
                                    overflow: 'auto',
                                    fontSize: 11,
                                    opacity: 0.8,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                }}
                                >
                                    {snapshot}
                                </pre>
                                {descriptionNode}
                            </div>
                        </div>
                    );
                }
                return (
                    <div className="property-row" key={key}>
                        <label className="property-label">{label}</label>
                        <div style={{ flex: 1 }}>
                            <input
                                type="text"
                                className="property-input"
                                value={String(value ?? '')}
                                placeholder={String(field?.placeholder || '')}
                                onChange={(e) => onChange(key, e.target.value)}
                            />
                            {descriptionNode}
                        </div>
                    </div>
                );
            })}
        </>
    );
}

function renderComponentConfig(
    component: ScreenComponent,
    onChange: (key: string, value: unknown) => void
) {
    const { type, config } = component;
    const legendHeuristic = resolveLegendHeuristicLayout(component);
    const configuredSeriesColors = Array.isArray(config.seriesColors)
        ? (config.seriesColors as string[]).map((item) => String(item))
        : [];

    const setSeriesColor = (index: number, color: string) => {
        const next = [...configuredSeriesColors];
        next[index] = color;
        onChange('seriesColors', next);
    };

    const renderSeriesColorRows = (labels: string[]) => {
        if (labels.length === 0) return null;
        return (
            <>
                {/* Color scheme selector */}
                <div className="property-row" style={{ marginTop: 8 }}>
                    <label className="property-label">配色方案</label>
                    <select
                        className="property-input"
                        value={(config._colorScheme as string) || ''}
                        onChange={(e) => {
                            const schemeId = e.target.value;
                            const scheme = COLOR_SCHEMES.find(s => s.id === schemeId);
                            if (scheme) {
                                onChange('_colorScheme', schemeId);
                                onChange('seriesColors', scheme.colors);
                            } else {
                                onChange('_colorScheme', '');
                            }
                        }}
                    >
                        <option value="">自定义</option>
                        {COLOR_SCHEMES.map(s => (
                            <option key={s.id} value={s.id}>{s.name}</option>
                        ))}
                    </select>
                </div>
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                    系列配色
                </div>
                {labels.map((label, idx) => (
                    <div className="property-row" key={`${label}-${idx}`}>
                        <label className="property-label">{label || `系列${idx + 1}`}</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={configuredSeriesColors[idx] || DEFAULT_SERIES_COLORS[idx % DEFAULT_SERIES_COLORS.length]}
                            onChange={(e) => setSeriesColor(idx, e.target.value)}
                        />
                    </div>
                ))}
            </>
        );
    };

    const renderLegendLayoutRows = () => (
        <>
            <div className="property-row">
                <label className="property-label">图例显示</label>
                <select
                    className="property-input"
                    value={(config.legendDisplay as string) || 'auto'}
                    onChange={(e) => onChange('legendDisplay', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="show">显示</option>
                    <option value="hide">隐藏</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">图例位置</label>
                <select
                    className="property-input"
                    value={(config.legendPosition as string) || 'auto'}
                    onChange={(e) => onChange('legendPosition', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="top">上</option>
                    <option value="bottom">下</option>
                    <option value="left">左</option>
                    <option value="right">右</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">启用拖拽微调</label>
                <input
                    type="checkbox"
                    checked={config.legendDragEnabled === true}
                    onChange={(e) => onChange('legendDragEnabled', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">自动避让</label>
                <input
                    type="checkbox"
                    checked={config.autoLegendAvoid !== false}
                    onChange={(e) => onChange('autoLegendAvoid', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例方向</label>
                <select
                    className="property-input"
                    value={(config.legendOrient as string) || 'auto'}
                    onChange={(e) => onChange('legendOrient', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="horizontal">横向</option>
                    <option value="vertical">纵向</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">图例对齐</label>
                <select
                    className="property-input"
                    value={(config.legendAlign as string) || 'auto'}
                    onChange={(e) => onChange('legendAlign', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="start">靠前</option>
                    <option value="center">居中</option>
                    <option value="end">靠后</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">图例间距</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={60}
                    value={(config.legendItemGap as number) || 12}
                    onChange={(e) => onChange('legendItemGap', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例预留(px)</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={360}
                    value={(config.legendReserveSize as number) || 0}
                    onChange={(e) => onChange('legendReserveSize', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例水平偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.legendOffsetX as number) || 0}
                    onChange={(e) => onChange('legendOffsetX', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例垂直偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.legendOffsetY as number) || 0}
                    onChange={(e) => onChange('legendOffsetY', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例文本宽(px)</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={320}
                    value={(config.legendNameMaxWidth as number) || 0}
                    onChange={(e) => onChange('legendNameMaxWidth', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">图例偏移</label>
                <button
                    type="button"
                    className="property-input"
                    onClick={() => {
                        onChange('legendOffsetX', 0);
                        onChange('legendOffsetY', 0);
                    }}
                >
                    重置为自动
                </button>
            </div>
            <div className="property-row">
                <label className="property-label">图例避让</label>
                <div style={{ width: '100%' }}>
                    <button
                        type="button"
                        className="property-action-btn"
                        style={{ width: '100%' }}
                        onClick={() => applyLegendHeuristicLayout(component, onChange)}
                        title={legendHeuristic.hint}
                    >
                        一键自动避让
                    </button>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 4 }}>{legendHeuristic.hint}</div>
                </div>
            </div>
        </>
    );

    const renderCompactPresetRow = () => (
        <div className="property-row">
            <label className="property-label">小屏预设</label>
            <select
                className="property-input"
                value={(config.compactLayoutPreset as string) || 'auto'}
                onChange={(e) => onChange('compactLayoutPreset', e.target.value)}
            >
                <option value="auto">自动</option>
                <option value="off">关闭</option>
            </select>
        </div>
    );

    const renderChartPaddingRows = () => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                图形留白(像素)
            </div>
            <div className="property-row">
                <label className="property-label">上留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingTop as number) || 0}
                    onChange={(e) => onChange('chartPaddingTop', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">右留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingRight as number) || 0}
                    onChange={(e) => onChange('chartPaddingRight', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">下留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingBottom as number) || 0}
                    onChange={(e) => onChange('chartPaddingBottom', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">左留白</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={300}
                    value={(config.chartPaddingLeft as number) || 0}
                    onChange={(e) => onChange('chartPaddingLeft', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">图形布局</label>
                <button
                    type="button"
                    className="property-input"
                    onClick={() => {
                        onChange('chartPaddingTop', 0);
                        onChange('chartPaddingRight', 0);
                        onChange('chartPaddingBottom', 0);
                        onChange('chartPaddingLeft', 0);
                        onChange('chartOffsetX', 0);
                        onChange('chartOffsetY', 0);
                    }}
                >
                    一键重置为自动
                </button>
            </div>
        </>
    );

    const renderChartOffsetRows = () => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                图形位置微调
            </div>
            <div className="property-row">
                <label className="property-label">启用拖拽微调</label>
                <input
                    type="checkbox"
                    checked={config.chartDragEnabled === true}
                    onChange={(e) => onChange('chartDragEnabled', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">水平偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.chartOffsetX as number) || 0}
                    onChange={(e) => onChange('chartOffsetX', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">垂直偏移</label>
                <input
                    type="number"
                    className="property-input"
                    min={-400}
                    max={400}
                    value={(config.chartOffsetY as number) || 0}
                    onChange={(e) => onChange('chartOffsetY', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">图形偏移</label>
                <button
                    type="button"
                    className="property-input"
                    onClick={() => {
                        onChange('chartOffsetX', 0);
                        onChange('chartOffsetY', 0);
                    }}
                >
                    重置为自动
                </button>
            </div>
            <div className="property-row">
                <label className="property-label">图形缩放(%)</label>
                <input
                    type="number"
                    className="property-input"
                    min={40}
                    max={180}
                    value={(config.chartScalePercent as number) || 100}
                    onChange={(e) => onChange('chartScalePercent', Number(e.target.value))}
                />
            </div>
        </>
    );

    const renderAxisLabelRows = () => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                轴标签防拥挤
            </div>
            <div className="property-row">
                <label className="property-label">X轴标签角度</label>
                <input
                    type="number"
                    className="property-input"
                    min={-90}
                    max={90}
                    value={(config.xAxisLabelRotate as number) || 0}
                    onChange={(e) => onChange('xAxisLabelRotate', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">X轴最大字数</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={40}
                    value={(config.xAxisLabelMaxLength as number) || 0}
                    onChange={(e) => onChange('xAxisLabelMaxLength', Number(e.target.value))}
                    placeholder="0=不限"
                />
            </div>
            <div className="property-row">
                <label className="property-label">X轴抽样间隔</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={200}
                    value={(config.xAxisLabelInterval as number) || 0}
                    onChange={(e) => onChange('xAxisLabelInterval', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">标签系列策略</label>
                <select
                    className="property-input"
                    value={(config.axisSeriesLabelStrategy as string) || 'auto'}
                    onChange={(e) => onChange('axisSeriesLabelStrategy', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="all">全部系列</option>
                    <option value="first">仅首系列</option>
                    <option value="none">隐藏标签</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">标签步长</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={200}
                    value={(config.axisSeriesLabelStep as number) || 0}
                    onChange={(e) => onChange('axisSeriesLabelStep', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
            <div className="property-row">
                <label className="property-label">Tooltip行数</label>
                <input
                    type="number"
                    className="property-input"
                    min={0}
                    max={50}
                    value={(config.axisTooltipMaxRows as number) || 0}
                    onChange={(e) => onChange('axisTooltipMaxRows', Number(e.target.value))}
                    placeholder="0=自动"
                />
            </div>
        </>
    );

    const renderSeriesLabelRows = (options?: { includeLeaderLines?: boolean }) => (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                数据标签
            </div>
            <div className="property-row">
                <label className="property-label">标签位置</label>
                <select
                    className="property-input"
                    value={(config.seriesLabelPosition as string) || 'auto'}
                    onChange={(e) => onChange('seriesLabelPosition', e.target.value)}
                >
                    <option value="auto">自动</option>
                    <option value="inside">内部</option>
                    <option value="outside">外部</option>
                    <option value="none">隐藏</option>
                </select>
            </div>
            <div className="property-row">
                <label className="property-label">标签字号</label>
                <input
                    type="number"
                    className="property-input"
                    min={10}
                    max={28}
                    value={(config.seriesLabelFontSize as number) || 12}
                    onChange={(e) => onChange('seriesLabelFontSize', Number(e.target.value))}
                />
            </div>
            {(options?.includeLeaderLines ?? true) ? (
                <>
                    <div className="property-row">
                        <label className="property-label">标签最小角度</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={45}
                            value={(config.seriesLabelMinAngle as number) || 0}
                            onChange={(e) => onChange('seriesLabelMinAngle', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">引导线长度1</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={60}
                            value={(config.seriesLabelLineLength as number) || 0}
                            onChange={(e) => onChange('seriesLabelLineLength', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">引导线长度2</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={60}
                            value={(config.seriesLabelLineLength2 as number) || 0}
                            onChange={(e) => onChange('seriesLabelLineLength2', Number(e.target.value))}
                            placeholder="0=自动"
                        />
                    </div>
                </>
            ) : null}
        </>
    );

    switch (type) {
        case 'line-chart':
        case 'bar-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">坐标轴字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.axisFontSize as number) || 12}
                            onChange={(e) => onChange('axisFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.legendFontSize as number) || 12}
                            onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'auto'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="auto">自动</option>
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                    {renderLegendLayoutRows()}
                    {renderCompactPresetRow()}
                    {renderChartPaddingRows()}
                    {renderChartOffsetRows()}
                    {renderAxisLabelRows()}
                    {renderSeriesLabelRows({ includeLeaderLines: false })}
                    {renderSeriesColorRows(
                        ((config.series as Array<{ name?: string }> | undefined) || [])
                            .map((item, idx) => (item?.name || '').trim() || `系列${idx + 1}`),
                    )}
                </>
            );

        case 'pie-chart':
        case 'gauge-chart':
        case 'radar-chart':
        case 'funnel-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    {type !== 'gauge-chart' && (
                        <>
                            <div className="property-row">
                                <label className="property-label">图例字号</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={10}
                                    max={28}
                                    value={(config.legendFontSize as number) || 12}
                                    onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">图例位置</label>
                                <select
                                    className="property-input"
                                    value={(config.legendPosition as string) || 'auto'}
                                    onChange={(e) => onChange('legendPosition', e.target.value)}
                                >
                                    <option value="auto">自动</option>
                                    <option value="top">顶部</option>
                                    <option value="bottom">底部</option>
                                    <option value="left">左侧</option>
                                    <option value="right">右侧</option>
                                </select>
                            </div>
                            {renderLegendLayoutRows()}
                            {renderCompactPresetRow()}
                            {renderChartPaddingRows()}
                            {renderChartOffsetRows()}
                            {(type === 'pie-chart' || type === 'funnel-chart') && renderSeriesLabelRows()}
                            {renderSeriesColorRows(
                                ((config.data as Array<{ name?: string }> | undefined) || [])
                                    .map((item, idx) => (item?.name || '').trim() || `系列${idx + 1}`),
                            )}
                        </>
                    )}
                    {type === 'gauge-chart' && (
                        <div className="property-row">
                            <label className="property-label">值</label>
                            <input
                                type="number"
                                className="property-input"
                                value={config.value as number}
                                onChange={(e) => onChange('value', Number(e.target.value))}
                            />
                        </div>
                    )}
                </>
            );

        case 'number-card':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">前缀</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.prefix as string}
                            onChange={(e) => onChange('prefix', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 12}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={16}
                            max={72}
                            value={(config.valueFontSize as number) || 32}
                            onChange={(e) => onChange('valueFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.titleColor as string) || '#ffffff'}
                            onChange={(e) => onChange('titleColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数值颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.valueColor as string) || '#ffffff'}
                            onChange={(e) => onChange('valueColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">背景色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.backgroundColor as string) || '#1a1a2e'}
                            onChange={(e) => onChange('backgroundColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'title':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">文本</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.text as string}
                            onChange={(e) => onChange('text', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">颜色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={config.color as string}
                            onChange={(e) => onChange('color', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'markdown-text':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">Markdown</label>
                        <textarea
                            className="property-input"
                            rows={8}
                            value={(config.markdown as string) || ''}
                            onChange={(e) => onChange('markdown', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.fontSize as number) || 14}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'richtext':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">HTML 内容</label>
                        <textarea
                            className="property-input"
                            rows={8}
                            value={(config.content as string) || ''}
                            onChange={(e) => onChange('content', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">内边距</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={60}
                            value={(config.padding as number) ?? 12}
                            onChange={(e) => onChange('padding', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">垂直对齐</label>
                        <select
                            className="property-input"
                            value={(config.verticalAlign as string) || 'top'}
                            onChange={(e) => onChange('verticalAlign', e.target.value)}
                        >
                            <option value="top">顶部</option>
                            <option value="middle">居中</option>
                            <option value="bottom">底部</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">溢出</label>
                        <select
                            className="property-input"
                            value={(config.overflow as string) || 'hidden'}
                            onChange={(e) => onChange('overflow', e.target.value)}
                        >
                            <option value="hidden">隐藏</option>
                            <option value="visible">可见</option>
                            <option value="scroll">滚动</option>
                        </select>
                    </div>
                </>
            );

        case 'datetime':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">格式</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.format as string}
                            onChange={(e) => onChange('format', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={config.fontSize as number}
                            onChange={(e) => onChange('fontSize', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'countdown':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '倒计时'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">目标时间</label>
                        <input
                            type="datetime-local"
                            className="property-input"
                            value={String(config.targetTime || '').replace('Z', '').slice(0, 16)}
                            onChange={(e) => {
                                const raw = String(e.target.value || '').trim();
                                if (!raw) {
                                    onChange('targetTime', '');
                                    return;
                                }
                                const parsed = Date.parse(raw);
                                if (Number.isFinite(parsed)) {
                                    onChange('targetTime', new Date(parsed).toISOString());
                                }
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">目标时间变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.targetVariableKey as string) || ''}
                            onChange={(e) => onChange('targetVariableKey', e.target.value)}
                            placeholder="releaseDeadline"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示天数</label>
                        <input
                            type="checkbox"
                            checked={config.showDays !== false}
                            onChange={(e) => onChange('showDays', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'marquee':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">文本</label>
                        <textarea
                            className="property-input"
                            rows={4}
                            value={(config.text as string) || ''}
                            onChange={(e) => onChange('text', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">速度(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={120}
                            value={(config.speed as number) || 40}
                            onChange={(e) => onChange('speed', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'carousel':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '轮播卡片'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">内容来源</label>
                        <select
                            className="property-input"
                            value={(config.itemSourceMode as string) || 'auto'}
                            onChange={(e) => onChange('itemSourceMode', e.target.value)}
                        >
                            <option value="auto">自动（优先数据）</option>
                            <option value="manual">手工内容</option>
                            <option value="data">数据内容</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">轮播内容</label>
                        <textarea
                            className="property-input"
                            rows={6}
                            value={Array.isArray(config.items) ? config.items.map((item) => String(item ?? '')).join('\n') : String(config.items ?? '')}
                            onChange={(e) => {
                                const items = e.target.value
                                    .split(/\r?\n/g)
                                    .map((item) => item.trim())
                                    .filter((item) => item.length > 0)
                                    .slice(0, 200);
                                onChange('items', items);
                            }}
                            placeholder="每行一条，例如：\n设备在线率 99.2%\n昨日告警 6 条"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">轮播间隔(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={120}
                            value={(config.intervalSeconds as number) || 4}
                            onChange={(e) => onChange('intervalSeconds', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动轮播</label>
                        <input
                            type="checkbox"
                            checked={config.autoPlay !== false}
                            onChange={(e) => onChange('autoPlay', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据内容列</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.dataItemField as string) || ''}
                            onChange={(e) => onChange('dataItemField', e.target.value)}
                            placeholder="列名/显示名/序号(1开始)"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据行上限</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={500}
                            value={(config.dataItemMax as number) || 50}
                            onChange={(e) => onChange('dataItemMax', Number(e.target.value))}
                            placeholder="数据源接入时生效"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示切换按钮</label>
                        <input
                            type="checkbox"
                            checked={config.showControls !== false}
                            onChange={(e) => onChange('showControls', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">悬停暂停</label>
                        <input
                            type="checkbox"
                            checked={config.pauseOnHover !== false}
                            onChange={(e) => onChange('pauseOnHover', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示指示点</label>
                        <input
                            type="checkbox"
                            checked={config.showDots !== false}
                            onChange={(e) => onChange('showDots', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'tab-switcher':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '维度切换'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.variableKey as string) || ''}
                            onChange={(e) => onChange('variableKey', e.target.value)}
                            placeholder="tabKey"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">选项来源</label>
                        <select
                            className="property-input"
                            value={(config.optionSourceMode as string) || 'manual'}
                            onChange={(e) => onChange('optionSourceMode', e.target.value === 'data' ? 'data' : 'manual')}
                        >
                            <option value="manual">手工配置</option>
                            <option value="data">数据源首列</option>
                        </select>
                    </div>
                    {(String(config.optionSourceMode || 'manual') !== 'data') ? (
                        <div className="property-row">
                            <label className="property-label">选项</label>
                            <textarea
                                className="property-input"
                                rows={6}
                                value={Array.isArray(config.options)
                                    ? config.options.map((item) => {
                                        if (item && typeof item === 'object') {
                                            const row = item as Record<string, unknown>;
                                            const label = String(row.label ?? '').trim();
                                            const value = String(row.value ?? '').trim();
                                            return label && value ? `${label}:${value}` : (label || value);
                                        }
                                        return String(item ?? '');
                                    }).join('\n')
                                    : String(config.options ?? '')
                                }
                                onChange={(e) => {
                                    const lines = e.target.value
                                        .split(/\r?\n/g)
                                        .map((line) => line.trim())
                                        .filter((line) => line.length > 0)
                                        .slice(0, 300);
                                    const next = lines.map((line) => {
                                        const idx = line.indexOf(':');
                                        if (idx < 0) {
                                            return { label: line, value: line };
                                        }
                                        const label = line.slice(0, idx).trim();
                                        const value = line.slice(idx + 1).trim();
                                        const safeValue = value || label;
                                        return { label: label || safeValue, value: safeValue };
                                    });
                                    onChange('options', next);
                                }}
                                placeholder={'每行一个选项，可写 label:value\n例如：\n总览:overview\n产线:line'}
                            />
                        </div>
                    ) : (
                        <>
                            <div className="property-row">
                                <label className="property-label">标签列</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionLabelField as string) || ''}
                                    onChange={(e) => onChange('dataOptionLabelField', e.target.value)}
                                    placeholder="列名/显示名/序号(1开始)"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">值列</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionValueField as string) || ''}
                                    onChange={(e) => onChange('dataOptionValueField', e.target.value)}
                                    placeholder="列名/显示名/序号(1开始)"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">最大选项数</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={1}
                                    max={500}
                                    value={(config.dataOptionMax as number) || 100}
                                    onChange={(e) => onChange('dataOptionMax', Number(e.target.value))}
                                />
                            </div>
                        </>
                    )}
                    <div className="property-row">
                        <label className="property-label">默认值</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.defaultValue as string) || ''}
                            onChange={(e) => onChange('defaultValue', e.target.value)}
                            placeholder="首次加载时写入变量"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">紧凑模式</label>
                        <input
                            type="checkbox"
                            checked={config.compact === true}
                            onChange={(e) => onChange('compact', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">激活背景</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.activeBackgroundColor as string) || '#38bdf8'}
                            onChange={(e) => onChange('activeBackgroundColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">激活文字</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.activeTextColor as string) || '#0f172a'}
                            onChange={(e) => onChange('activeTextColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">未激活背景</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.inactiveBackgroundColor as string) || '#1e293b'}
                            onChange={(e) => onChange('inactiveBackgroundColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">未激活文字</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.inactiveTextColor as string) || '#94a3b8'}
                            onChange={(e) => onChange('inactiveTextColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'progress-bar':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示标签</label>
                        <input
                            type="checkbox"
                            checked={config.showLabel as boolean}
                            onChange={(e) => onChange('showLabel', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'filter-input':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '筛选'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.variableKey as string) || ''}
                            onChange={(e) => onChange('variableKey', e.target.value)}
                            placeholder="keyword"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">占位</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.placeholder as string) || ''}
                            onChange={(e) => onChange('placeholder', e.target.value)}
                            placeholder="请输入关键词"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">默认值</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.defaultValue as string) || ''}
                            onChange={(e) => onChange('defaultValue', e.target.value)}
                            placeholder="初始关键字"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">作用说明</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.scopeHint as string) || ''}
                            onChange={(e) => onChange('scopeHint', e.target.value)}
                            placeholder="作用于项目、风险、交付物"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">防抖(ms)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={5000}
                            step={50}
                            value={Number(config.debounceMs as number) > 0 ? Number(config.debounceMs as number) : 0}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                onChange('debounceMs', Number.isFinite(n) && n > 0 ? Math.max(50, Math.min(5000, Math.round(n))) : 0);
                            }}
                            placeholder="0=不防抖"
                        />
                    </div>
                </>
            );

        case 'filter-select': {
            const options = Array.isArray(config.options)
                ? (config.options as Array<string | { label?: string; value?: string }>)
                : [];
            const optionSourceMode = String(config.optionSourceMode || 'manual') === 'data' ? 'data' : 'manual';
            const optionText = options
                .map((item) => (typeof item === 'string' ? item : `${item.value || ''}|${item.label || ''}`))
                .join('\n');
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '筛选'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.variableKey as string) || ''}
                            onChange={(e) => onChange('variableKey', e.target.value)}
                            placeholder="region"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">选项来源</label>
                        <select
                            className="property-input"
                            value={optionSourceMode}
                            onChange={(e) => onChange('optionSourceMode', e.target.value === 'data' ? 'data' : 'manual')}
                        >
                            <option value="manual">手工配置</option>
                            <option value="data">来自数据源</option>
                        </select>
                    </div>
                    {optionSourceMode === 'manual' ? (
                        <div className="property-row">
                            <label className="property-label">选项(每行1个)</label>
                            <textarea
                                className="property-input"
                                rows={5}
                                value={optionText}
                                onChange={(e) => {
                                    const lines = e.target.value
                                        .split('\n')
                                        .map((line) => line.trim())
                                        .filter((line) => line.length > 0);
                                    onChange('options', lines);
                                }}
                                placeholder={'华北\n华东\n华南'}
                            />
                        </div>
                    ) : (
                        <>
                            <div className="property-row">
                                <label className="property-label">值字段</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionValueField as string) || ''}
                                    onChange={(e) => onChange('dataOptionValueField', e.target.value)}
                                    placeholder="默认第1列"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">标签字段</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={(config.dataOptionLabelField as string) || ''}
                                    onChange={(e) => onChange('dataOptionLabelField', e.target.value)}
                                    placeholder="默认与值字段相同"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">最大选项数</label>
                                <input
                                    type="number"
                                    className="property-input"
                                    min={1}
                                    max={2000}
                                    value={Number(config.dataOptionMax as number) > 0 ? Number(config.dataOptionMax as number) : 200}
                                    onChange={(e) => {
                                        const n = Number(e.target.value);
                                        onChange('dataOptionMax', Number.isFinite(n) ? Math.max(1, Math.min(2000, n)) : 200);
                                    }}
                                />
                            </div>
                        </>
                    )}
                    <div className="property-row">
                        <label className="property-label">占位</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.placeholder as string) || ''}
                            onChange={(e) => onChange('placeholder', e.target.value)}
                            placeholder="请选择"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">默认值</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.defaultValue as string) || ''}
                            onChange={(e) => onChange('defaultValue', e.target.value)}
                            placeholder="默认选项值"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">作用说明</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.scopeHint as string) || ''}
                            onChange={(e) => onChange('scopeHint', e.target.value)}
                            placeholder="作用于项目、风险、交付物"
                        />
                    </div>
                </>
            );
        }

        case 'filter-date-range':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.label as string) || '日期区间'}
                            onChange={(e) => onChange('label', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">开始变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.startKey as string) || ''}
                            onChange={(e) => onChange('startKey', e.target.value)}
                            placeholder="startDate"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">结束变量</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.endKey as string) || ''}
                            onChange={(e) => onChange('endKey', e.target.value)}
                            placeholder="endDate"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">默认开始</label>
                        <input
                            type="date"
                            className="property-input"
                            value={(config.defaultStartValue as string) || ''}
                            onChange={(e) => onChange('defaultStartValue', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">默认结束</label>
                        <input
                            type="date"
                            className="property-input"
                            value={(config.defaultEndValue as string) || ''}
                            onChange={(e) => onChange('defaultEndValue', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">作用说明</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.scopeHint as string) || ''}
                            onChange={(e) => onChange('scopeHint', e.target.value)}
                            placeholder="作用于统计周期过滤"
                        />
                    </div>
                </>
            );

        case 'image':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">图片URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入图片地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">填充方式</label>
                        <select
                            className="property-input"
                            value={config.fit as string}
                            onChange={(e) => onChange('fit', e.target.value)}
                        >
                            <option value="cover">覆盖</option>
                            <option value="contain">包含</option>
                            <option value="fill">拉伸</option>
                        </select>
                    </div>
                </>
            );

        case 'video':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">视频URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.src as string}
                            onChange={(e) => onChange('src', e.target.value)}
                            placeholder="输入视频地址"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动播放</label>
                        <input
                            type="checkbox"
                            checked={config.autoplay as boolean}
                            onChange={(e) => onChange('autoplay', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">循环</label>
                        <input
                            type="checkbox"
                            checked={config.loop as boolean}
                            onChange={(e) => onChange('loop', e.target.checked)}
                        />
                    </div>
                </>
            );

        case 'iframe':
            return (
                <div className="property-row">
                    <label className="property-label">URL</label>
                    <input
                        type="text"
                        className="property-input"
                        value={config.src as string}
                        onChange={(e) => onChange('src', e.target.value)}
                        placeholder="输入网页地址"
                    />
                </div>
            );

        case 'border-box':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">边框类型</label>
                        <select
                            className="property-input"
                            value={config.boxType as number}
                            onChange={(e) => onChange('boxType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13].map((n) => (
                                <option key={n} value={n}>边框 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'decoration':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">装饰类型</label>
                        <select
                            className="property-input"
                            value={config.decorationType as number}
                            onChange={(e) => onChange('decorationType', Number(e.target.value))}
                        >
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => (
                                <option key={n} value={n}>装饰 {n}</option>
                            ))}
                        </select>
                    </div>
                </>
            );

        case 'water-level':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={config.shape as string}
                            onChange={(e) => onChange('shape', e.target.value)}
                        >
                            <option value="round">圆形</option>
                            <option value="rect">矩形</option>
                            <option value="roundRect">圆角矩形</option>
                        </select>
                    </div>
                </>
            );

        case 'digital-flop':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">数值</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.number as number[])?.[0] || 0}
                            onChange={(e) => onChange('number', [Number(e.target.value)])}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">字号</label>
                        <input
                            type="number"
                            className="property-input"
                            value={(config.style as { fontSize?: number })?.fontSize || 30}
                            onChange={(e) => onChange('style', {
                                ...(config.style as object),
                                fontSize: Number(e.target.value),
                            })}
                        />
                    </div>
                </>
            );

        case 'percent-pond':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">值 (%)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={100}
                            value={config.value as number}
                            onChange={(e) => onChange('value', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">边框宽度</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={10}
                            value={config.borderWidth as number}
                            onChange={(e) => onChange('borderWidth', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'scatter-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={config.title as string}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">标题字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={36}
                            value={(config.titleFontSize as number) || 14}
                            onChange={(e) => onChange('titleFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">坐标轴字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.axisFontSize as number) || 12}
                            onChange={(e) => onChange('axisFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例字号</label>
                        <input
                            type="number"
                            className="property-input"
                            min={10}
                            max={28}
                            value={(config.legendFontSize as number) || 12}
                            onChange={(e) => onChange('legendFontSize', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">图例位置</label>
                        <select
                            className="property-input"
                            value={(config.legendPosition as string) || 'auto'}
                            onChange={(e) => onChange('legendPosition', e.target.value)}
                        >
                            <option value="auto">自动</option>
                            <option value="top">顶部</option>
                            <option value="bottom">底部</option>
                            <option value="left">左侧</option>
                            <option value="right">右侧</option>
                        </select>
                    </div>
                    {renderLegendLayoutRows()}
                    {renderCompactPresetRow()}
                    {renderChartPaddingRows()}
                    {renderChartOffsetRows()}
                    {renderSeriesColorRows(['散点系列'])}
                </>
            );

        case 'map-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '区域地图'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">地图模式</label>
                        <select
                            className="property-input"
                            value={(config.mapMode as string) || 'region'}
                            onChange={(e) => onChange('mapMode', e.target.value)}
                        >
                            <option value="region">区域填色</option>
                            <option value="bubble">气泡地图</option>
                            <option value="scatter">散点地图</option>
                            <option value="heatmap">热力地图</option>
                            <option value="flow">流向地图</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">地图范围</label>
                        <select
                            className="property-input"
                            value={(config.mapScope as string) || 'china'}
                            onChange={(e) => onChange('mapScope', e.target.value)}
                        >
                            <option value="china">中国</option>
                            <option value="world">世界</option>
                            <optgroup label="省级">
                                {PROVINCE_PRESETS.map(p => (
                                    <option key={p.code} value={p.code}>{p.name}</option>
                                ))}
                            </optgroup>
                        </select>
                    </div>
                    {((config.mapMode as string) === 'bubble' || (config.mapMode as string) === 'scatter') ? (
                        <div className="property-row">
                            <label className="property-label">气泡颜色</label>
                            <input
                                type="color"
                                className="property-input"
                                value={(config.bubbleColor as string) || '#3b82f6'}
                                onChange={(e) => onChange('bubbleColor', e.target.value)}
                            />
                        </div>
                    ) : null}
                    {(config.mapMode as string) === 'heatmap' ? (
                        <div className="property-row">
                            <label className="property-label">热力半径</label>
                            <input
                                type="number"
                                className="property-input"
                                min={5}
                                max={80}
                                value={(config.heatmapRadius as number) || 20}
                                onChange={(e) => onChange('heatmapRadius', Number(e.target.value))}
                            />
                        </div>
                    ) : null}
                    {(config.mapMode as string) === 'flow' ? (
                        <div className="property-row">
                            <label className="property-label">流动动画</label>
                            <input
                                type="checkbox"
                                checked={config.showFlowEffect !== false}
                                onChange={(e) => onChange('showFlowEffect', e.target.checked)}
                            />
                        </div>
                    ) : null}
                    <div className="property-row">
                        <label className="property-label">区域变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.regionVariableKey as string) || ''}
                            onChange={(e) => onChange('regionVariableKey', e.target.value)}
                            placeholder="region"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">区域编码变量Key</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.regionCodeVariableKey as string) || ''}
                            onChange={(e) => onChange('regionCodeVariableKey', e.target.value)}
                            placeholder="region_code"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">优先使用内置底图</label>
                        <input
                            type="checkbox"
                            checked={config.usePresetGeoJson !== false}
                            onChange={(e) => onChange('usePresetGeoJson', e.target.checked)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">GeoJSON URL(可选)</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.geoJsonUrl as string) || ''}
                            onChange={(e) => onChange('geoJsonUrl', e.target.value)}
                            placeholder="https://.../map.geojson"
                        />
                    </div>
                    {!(config.mapMode as string) || (config.mapMode as string) === 'region' ? (
                        <div className="property-row">
                            <label className="property-label">启用下钻</label>
                            <input
                                type="checkbox"
                                checked={config.enableRegionDrill !== false}
                                onChange={(e) => onChange('enableRegionDrill', e.target.checked)}
                            />
                        </div>
                    ) : null}
                </>
            );

        case 'scroll-board':
            return <ScrollBoardConfig component={component} onChange={onChange} />;

        case 'table':
            return <TableConfig component={component} onChange={onChange} />;

        case 'scroll-ranking':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            max={20}
                            value={config.rowNum as number}
                            onChange={(e) => onChange('rowNum', Number(e.target.value))}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">等待时间(ms)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={500}
                            max={10000}
                            step={500}
                            value={config.waitTime as number || 2000}
                            onChange={(e) => onChange('waitTime', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        case 'shape':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">形状</label>
                        <select
                            className="property-input"
                            value={(config.shapeType as string) || 'rect'}
                            onChange={(e) => onChange('shapeType', e.target.value)}
                        >
                            <option value="rect">矩形</option>
                            <option value="circle">圆形</option>
                            <option value="line">线条</option>
                            <option value="arrow">箭头</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">填充色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.fillColor as string) || '#3b82f6'}
                            onChange={(e) => onChange('fillColor', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">边框色</label>
                        <input
                            type="color"
                            className="property-color-input"
                            value={(config.borderColor as string) || '#60a5fa'}
                            onChange={(e) => onChange('borderColor', e.target.value)}
                        />
                    </div>
                </>
            );

        case 'container':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '容器'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">内边距</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            max={80}
                            value={(config.padding as number) || 12}
                            onChange={(e) => onChange('padding', Number(e.target.value))}
                        />
                    </div>
                </>
            );

        // ==================== 3D 可视化 ====================
        case 'globe-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input
                            type="text"
                            className="property-input"
                            value={(config.title as string) || '3D 地球'}
                            onChange={(e) => onChange('title', e.target.value)}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动旋转</label>
                        <select className="property-input" value={config.autoRotate !== false ? 'true' : 'false'} onChange={(e) => onChange('autoRotate', e.target.value === 'true')}>
                            <option value="true">开启</option>
                            <option value="false">关闭</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">旋转速度</label>
                        <input type="number" className="property-input" min={1} max={50} value={(config.rotateSpeed as number) || 10} onChange={(e) => onChange('rotateSpeed', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">观测距离</label>
                        <input type="number" className="property-input" min={50} max={500} value={(config.viewDistance as number) || 200} onChange={(e) => onChange('viewDistance', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">底图纹理 URL</label>
                        <input type="text" className="property-input" placeholder="https://..." value={(config.baseTexture as string) || ''} onChange={(e) => onChange('baseTexture', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">高度纹理 URL</label>
                        <input type="text" className="property-input" placeholder="https://..." value={(config.heightTexture as string) || ''} onChange={(e) => onChange('heightTexture', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">大气层效果</label>
                        <select className="property-input" value={config.showAtmosphere !== false ? 'true' : 'false'} onChange={(e) => onChange('showAtmosphere', e.target.value === 'true')}>
                            <option value="true">开启</option>
                            <option value="false">关闭</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">散点大小</label>
                        <input type="number" className="property-input" min={2} max={40} value={(config.pointSize as number) || 12} onChange={(e) => onChange('pointSize', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">背景色</label>
                        <input type="color" className="property-input" value={(config.globeBackground as string) || '#000000'} onChange={(e) => onChange('globeBackground', e.target.value)} />
                    </div>
                </>
            );

        case 'bar3d-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input type="text" className="property-input" value={(config.title as string) || '3D 柱状图'} onChange={(e) => onChange('title', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">视角 Alpha</label>
                        <input type="number" className="property-input" min={0} max={90} value={(config.viewAlpha as number) || 40} onChange={(e) => onChange('viewAlpha', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">视角 Beta</label>
                        <input type="number" className="property-input" min={0} max={360} value={(config.viewBeta as number) || 30} onChange={(e) => onChange('viewBeta', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动旋转</label>
                        <select className="property-input" value={config.autoRotate === true ? 'true' : 'false'} onChange={(e) => onChange('autoRotate', e.target.value === 'true')}>
                            <option value="false">关闭</option>
                            <option value="true">开启</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">盒宽</label>
                        <input type="number" className="property-input" min={20} max={300} value={(config.boxWidth as number) || 100} onChange={(e) => onChange('boxWidth', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">盒深</label>
                        <input type="number" className="property-input" min={20} max={300} value={(config.boxDepth as number) || 80} onChange={(e) => onChange('boxDepth', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">盒高</label>
                        <input type="number" className="property-input" min={20} max={300} value={(config.boxHeight as number) || 60} onChange={(e) => onChange('boxHeight', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示标签</label>
                        <select className="property-input" value={config.showLabel === true ? 'true' : 'false'} onChange={(e) => onChange('showLabel', e.target.value === 'true')}>
                            <option value="false">关闭</option>
                            <option value="true">开启</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">色阶低值</label>
                        <input type="color" className="property-input" value={(config.colorRange as string[])?.[0] || '#313695'} onChange={(e) => onChange('colorRange', [e.target.value, (config.colorRange as string[])?.[1] || '#a50026'])} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">色阶高值</label>
                        <input type="color" className="property-input" value={(config.colorRange as string[])?.[1] || '#a50026'} onChange={(e) => onChange('colorRange', [(config.colorRange as string[])?.[0] || '#313695', e.target.value])} />
                    </div>
                </>
            );

        case 'scatter3d-chart':
            return (
                <>
                    <div className="property-row">
                        <label className="property-label">标题</label>
                        <input type="text" className="property-input" value={(config.title as string) || '3D 散点图'} onChange={(e) => onChange('title', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">散点大小</label>
                        <input type="number" className="property-input" min={2} max={30} value={(config.pointSize as number) || 8} onChange={(e) => onChange('pointSize', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">视角 Alpha</label>
                        <input type="number" className="property-input" min={0} max={90} value={(config.viewAlpha as number) || 40} onChange={(e) => onChange('viewAlpha', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">视角 Beta</label>
                        <input type="number" className="property-input" min={0} max={360} value={(config.viewBeta as number) || 30} onChange={(e) => onChange('viewBeta', Number(e.target.value))} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">自动旋转</label>
                        <select className="property-input" value={config.autoRotate === true ? 'true' : 'false'} onChange={(e) => onChange('autoRotate', e.target.value === 'true')}>
                            <option value="false">关闭</option>
                            <option value="true">开启</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">显示标签</label>
                        <select className="property-input" value={config.showLabel === true ? 'true' : 'false'} onChange={(e) => onChange('showLabel', e.target.value === 'true')}>
                            <option value="false">关闭</option>
                            <option value="true">开启</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">X 轴名称</label>
                        <input type="text" className="property-input" value={(config.xAxisName as string) || 'X'} onChange={(e) => onChange('xAxisName', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">Y 轴名称</label>
                        <input type="text" className="property-input" value={(config.yAxisName as string) || 'Y'} onChange={(e) => onChange('yAxisName', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">Z 轴名称</label>
                        <input type="text" className="property-input" value={(config.zAxisName as string) || 'Z'} onChange={(e) => onChange('zAxisName', e.target.value)} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">色阶低值</label>
                        <input type="color" className="property-input" value={(config.colorRange as string[])?.[0] || '#50a3ba'} onChange={(e) => onChange('colorRange', [e.target.value, (config.colorRange as string[])?.[1] || '#eac736'])} />
                    </div>
                    <div className="property-row">
                        <label className="property-label">色阶高值</label>
                        <input type="color" className="property-input" value={(config.colorRange as string[])?.[1] || '#eac736'} onChange={(e) => onChange('colorRange', [(config.colorRange as string[])?.[0] || '#50a3ba', e.target.value])} />
                    </div>
                </>
            );

        default:
            return (
                <div className="empty-state-hint">
                    暂无可配置项
                </div>
            );
    }
}

function renderDataSourceConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    globalVariables: ScreenGlobalVariable[],
) {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = resolveDataSourceType(ds);
    const sqlConfig = resolveSqlConfig(ds);

    const cardBindings: CardParameterBinding[] = ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [];
    const metricBindings: CardParameterBinding[] = dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [];
    const sqlBindings: CardParameterBinding[] = dsType === 'sql' ? (sqlConfig?.parameterBindings ?? []) : [];
    const variableOptions = (globalVariables ?? []).map((item) => ({ key: item.key, label: item.label || item.key }));

    const updateCardBindings = (bindings: CardParameterBinding[]) => {
        setDataSource({
            type: 'card',
            sourceType: 'card',
            cardConfig: {
                ...(ds?.type === 'card' ? ds.cardConfig : {}),
                cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                parameterBindings: bindings,
            },
        });
    };

    const updateSqlBindings = (bindings: CardParameterBinding[]) => {
        const base = resolveSqlConfig(ds);
        setDataSource({
            type: 'sql',
            sourceType: 'sql',
            refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
            sqlConfig: {
                ...(base ?? { query: '' }),
                query: base?.query ?? '',
                databaseId: base?.databaseId,
                connectionId: base?.connectionId,
                queryTimeoutSeconds: base?.queryTimeoutSeconds,
                maxRows: base?.maxRows,
                parameterBindings: bindings,
            },
        });
    };

    const updateMetricBindings = (bindings: CardParameterBinding[]) => {
        const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
        setDataSource({
            type: 'metric',
            sourceType: 'metric',
            refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
            metricConfig: {
                ...(currentMetricConfig ?? {}),
                cardId: currentMetricConfig?.cardId ?? 0,
                parameterBindings: bindings,
            },
        });
    };

    const setDataSource = (newDs: DataSourceConfig | undefined) => {
        updateComponent(component.id, { dataSource: newDs });
    };

    const setType = (nextType: string) => {
        if (nextType === 'static') {
            setDataSource(undefined);
            return;
        }
        if (nextType === 'card') {
            setDataSource({
                type: 'card',
                sourceType: 'card',
                cardConfig: {
                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                    metricVersion: ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined,
                    parameterBindings: ds?.type === 'card' ? (ds.cardConfig?.parameterBindings ?? []) : [],
                },
            });
            return;
        }
        if (nextType === 'api') {
            setDataSource({
                type: 'api',
                sourceType: 'api',
                refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                apiConfig: {
                    url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                    method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                    body: ds?.type === 'api' ? ds.apiConfig?.body : undefined,
                },
            });
            return;
        }
        if (nextType === 'sql') {
            const base = resolveSqlConfig(ds);
            setDataSource({
                type: 'sql',
                sourceType: 'sql',
                refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                sqlConfig: {
                    databaseId: base?.databaseId,
                    connectionId: base?.connectionId,
                    query: base?.query ?? 'select 1',
                    queryTimeoutSeconds: base?.queryTimeoutSeconds,
                    maxRows: base?.maxRows,
                    parameterBindings: base?.parameterBindings ?? [],
                },
            });
            return;
        }
        if (nextType === 'dataset') {
            setDataSource({
                type: 'dataset',
                sourceType: 'dataset',
                refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                datasetConfig: dsType === 'dataset'
                    ? ds?.datasetConfig
                    : { queryBody: { database: 0, type: 'query', query: {} } },
            });
            return;
        }
        if (nextType === 'metric') {
            setDataSource({
                type: 'metric',
                sourceType: 'metric',
                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                metricConfig: {
                    cardId: dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0,
                    metricId: dsType === 'metric' ? ds?.metricConfig?.metricId : undefined,
                    metricVersion: dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined,
                    parameterBindings: dsType === 'metric' ? (ds?.metricConfig?.parameterBindings ?? []) : [],
                },
            });
        }
    };

    return (
        <>
            <div className="property-row">
                <label className="property-label">类型</label>
                <select
                    className="property-input"
                    value={dsType}
                    onChange={(e) => setType(e.target.value)}
                >
                    <option value="static">静态数据</option>
                    <option value="card">Card 查询</option>
                    <option value="api">HTTP API</option>
                    <option value="sql">SQL 模式</option>
                    <option value="dataset">Dataset 模式</option>
                    <option value="metric">Metric 语义模式</option>
                </select>
            </div>

            {dsType === 'card' && (
                <>
                    <div className="property-row">
                        <label className="property-label">Card</label>
                        <CardIdPicker
                            value={ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={ds?.type === 'card' ? (ds.cardConfig?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'card',
                                    sourceType: 'card',
                                    cardConfig: {
                                        ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                        cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                        refreshInterval: val > 0 ? val : undefined,
                                    },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={ds?.type === 'card' ? ds.cardConfig?.metricId : undefined}
                        metricVersion={ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId,
                                    metricVersion: metricId ? (ds?.type === 'card' ? ds.cardConfig?.metricVersion : undefined) : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            setDataSource({
                                type: 'card',
                                sourceType: 'card',
                                cardConfig: {
                                    ...(ds?.type === 'card' ? ds.cardConfig : {}),
                                    cardId: ds?.type === 'card' ? (ds.cardConfig?.cardId ?? 0) : 0,
                                    refreshInterval: ds?.type === 'card' ? ds.cardConfig?.refreshInterval : undefined,
                                    metricId: ds?.type === 'card' ? ds.cardConfig?.metricId : undefined,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={cardBindings}
                        globalVariables={globalVariables}
                        onChange={updateCardBindings}
                    />
                </>
            )}

            {dsType === 'api' && (
                <>
                    <div className="property-row">
                        <label className="property-label">URL</label>
                        <input
                            type="text"
                            className="property-input"
                            value={ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : { method: 'GET' as const }),
                                        url: e.target.value,
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                    },
                                });
                            }}
                            placeholder="/analytics/api/card/1/query 或 https://..."
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">方法</label>
                        <select
                            className="property-input"
                            value={ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET'}
                            onChange={(e) => {
                                const method = (e.target.value as 'GET' | 'POST') || 'GET';
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method,
                                    },
                                });
                            }}
                        >
                            <option value="GET">GET</option>
                            <option value="POST">POST</option>
                        </select>
                    </div>
                    <div className="property-row">
                        <label className="property-label">Body</label>
                        <textarea
                            className="property-input"
                            rows={4}
                            value={ds?.type === 'api' ? (ds.apiConfig?.body ?? '') : ''}
                            onChange={(e) => {
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: ds?.type === 'api' ? ds.refreshInterval : undefined,
                                    apiConfig: {
                                        ...(ds?.type === 'api' ? ds.apiConfig : {}),
                                        url: ds?.type === 'api' ? (ds.apiConfig?.url ?? '') : '',
                                        method: ds?.type === 'api' ? (ds.apiConfig?.method ?? 'GET') : 'GET',
                                        body: e.target.value,
                                    },
                                });
                            }}
                            placeholder='{"parameters":[]}'
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={ds?.type === 'api' ? (ds.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'api',
                                    sourceType: 'api',
                                    refreshInterval: val > 0 ? val : undefined,
                                    apiConfig: ds?.type === 'api'
                                        ? {
                                            ...(ds.apiConfig ?? { method: 'GET' as const, url: '' }),
                                            method: ds.apiConfig?.method ?? 'GET',
                                            url: ds.apiConfig?.url ?? '',
                                        }
                                        : { method: 'GET', url: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'sql' && (
                <>
                    <div className="property-row">
                        <label className="property-label">数据库</label>
                        <DatabaseIdPicker
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(databaseId) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: databaseId > 0 ? databaseId : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">数据库ID(手工)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            value={sqlConfig?.databaseId ?? 0}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: Number.isFinite(n) && n > 0 ? n : undefined,
                                        query: base?.query ?? '',
                                    },
                                });
                            }}
                            placeholder="用于离线环境或未同步数据库列表"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">SQL</label>
                        <textarea
                            className="property-input"
                            rows={6}
                            value={sqlConfig?.query ?? ''}
                            onChange={(e) => {
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        databaseId: base?.databaseId,
                                        query: e.target.value,
                                    },
                                });
                            }}
                            placeholder="select * from public.table where day = {{day}} limit 200"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">参数提取</label>
                        <button
                            type="button"
                            className="header-btn"
                            onClick={() => {
                                const names = extractSqlTemplateParameterNames(sqlConfig?.query ?? '');
                                if (names.length === 0) {
                                    alert('未识别到 SQL 参数，占位符示例：{{day}} 或 ${day}');
                                    return;
                                }
                                const previous = new Map(
                                    (sqlBindings ?? []).map((item) => [String(item.name ?? '').trim(), item]),
                                );
                                const nextBindings: CardParameterBinding[] = names.map((name) => {
                                    const exists = previous.get(name);
                                    if (!exists) {
                                        return {
                                            name,
                                            variableKey: '',
                                            value: '',
                                        };
                                    }
                                    return {
                                        name,
                                        variableKey: exists.variableKey ?? '',
                                        value: exists.value ?? '',
                                    };
                                });
                                updateSqlBindings(nextBindings);
                            }}
                        >
                            从 SQL 提取参数
                        </button>
                    </div>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: -2 }}>
                        自动识别 &#123;&#123;param&#125;&#125; / $&#123;param&#125; 占位符并生成参数绑定。
                    </div>
                    <div className="property-row">
                        <label className="property-label">最大行数</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            step={100}
                            value={sqlConfig?.maxRows ?? 2000}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        maxRows: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认2000"
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">超时(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={1}
                            step={5}
                            value={sqlConfig?.queryTimeoutSeconds ?? 60}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: dsType === 'sql' ? ds?.refreshInterval : undefined,
                                    sqlConfig: {
                                        ...(base ?? { query: '' }),
                                        query: base?.query ?? '',
                                        queryTimeoutSeconds: Number.isFinite(n) && n > 0 ? n : undefined,
                                    },
                                });
                            }}
                            placeholder="默认60"
                        />
                    </div>
                    <CardParamBindingsEditor
                        bindings={sqlBindings}
                        globalVariables={globalVariables}
                        onChange={updateSqlBindings}
                    />
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'sql' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const base = resolveSqlConfig(ds);
                                setDataSource({
                                    type: 'sql',
                                    sourceType: 'sql',
                                    refreshInterval: val > 0 ? val : undefined,
                                    sqlConfig: base
                                        ? {
                                            ...base,
                                            query: base.query ?? '',
                                        }
                                        : { query: '' },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'dataset' && (
                <>
                    <div className="property-row">
                        <label className="property-label">QueryBody(JSON)</label>
                        <textarea
                            className="property-input"
                            rows={8}
                            value={safeJsonStringify(ds?.datasetConfig?.queryBody)}
                            onChange={(e) => {
                                const parsed = safeJsonParse(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: dsType === 'dataset' ? ds?.refreshInterval : undefined,
                                    datasetConfig: { queryBody: parsed ?? {} },
                                });
                            }}
                            placeholder='{"database":1,"type":"native","native":{"query":"select 1"}}'
                        />
                    </div>
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'dataset' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                setDataSource({
                                    type: 'dataset',
                                    sourceType: 'dataset',
                                    refreshInterval: val > 0 ? val : undefined,
                                    datasetConfig: ds?.datasetConfig ?? { queryBody: {} },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}

            {dsType === 'metric' && (
                <>
                    <div className="property-row">
                        <label className="property-label">Card</label>
                        <CardIdPicker
                            value={dsType === 'metric' ? (ds?.metricConfig?.cardId ?? 0) : 0}
                            onChange={(cardId) => {
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId,
                                    },
                                });
                            }}
                        />
                    </div>
                    <MetricBindingEditor
                        metricId={dsType === 'metric' ? ds?.metricConfig?.metricId : undefined}
                        metricVersion={dsType === 'metric' ? ds?.metricConfig?.metricVersion : undefined}
                        onMetricIdChange={(metricId) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId,
                                    metricVersion: metricId ? currentMetricConfig?.metricVersion : undefined,
                                },
                            });
                        }}
                        onMetricVersionChange={(metricVersion) => {
                            const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                            setDataSource({
                                type: 'metric',
                                sourceType: 'metric',
                                refreshInterval: dsType === 'metric' ? ds?.refreshInterval : undefined,
                                metricConfig: {
                                    ...(currentMetricConfig ?? {}),
                                    cardId: currentMetricConfig?.cardId ?? 0,
                                    metricId: currentMetricConfig?.metricId,
                                    metricVersion,
                                },
                            });
                        }}
                    />
                    <CardParamBindingsEditor
                        bindings={metricBindings}
                        globalVariables={globalVariables}
                        onChange={updateMetricBindings}
                    />
                    <div className="property-row">
                        <label className="property-label">刷新(秒)</label>
                        <input
                            type="number"
                            className="property-input"
                            min={0}
                            step={10}
                            value={dsType === 'metric' ? (ds?.refreshInterval ?? 0) : 0}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const currentMetricConfig = dsType === 'metric' ? ds?.metricConfig : undefined;
                                setDataSource({
                                    type: 'metric',
                                    sourceType: 'metric',
                                    refreshInterval: val > 0 ? val : undefined,
                                    metricConfig: {
                                        ...(currentMetricConfig ?? {}),
                                        cardId: currentMetricConfig?.cardId ?? 0,
                                    },
                                });
                            }}
                            placeholder="0=不刷新"
                        />
                    </div>
                </>
            )}
        </>
    );
}

function resolveDataSourceType(ds?: DataSourceConfig): 'static' | QuerySourceType {
    const type = ((ds?.sourceType ?? ds?.type) || 'static').toLowerCase();
    if (type === 'database' || type === 'sql') return 'sql';
    if (type === 'card' || type === 'api' || type === 'dataset' || type === 'metric') {
        return type;
    }
    return 'static';
}

function resolveExplainCardId(component: ScreenComponent): number | undefined {
    const ds = component.dataSource as DataSourceConfig | undefined;
    const dsType = resolveDataSourceType(ds);
    if (dsType === 'card') {
        const id = Number(ds?.cardConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    if (dsType === 'metric') {
        const id = Number(ds?.metricConfig?.cardId ?? 0);
        return Number.isFinite(id) && id > 0 ? id : undefined;
    }
    return undefined;
}

function resolveSqlConfig(ds?: DataSourceConfig): DataSourceConfig['sqlConfig'] | DataSourceConfig['databaseConfig'] | undefined {
    if (!ds) return undefined;
    return ds.sqlConfig ?? ds.databaseConfig;
}

function safeJsonParse(text: string): Record<string, unknown> | null {
    const raw = (text || '').trim();
    if (!raw) return {};
    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
            ? (parsed as Record<string, unknown>)
            : null;
    } catch {
        return null;
    }
}

function safeJsonStringify(value: unknown): string {
    if (!value || typeof value !== 'object') return '{}';
    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return '{}';
    }
}


const INTERACTION_COMPONENT_TYPES = new Set<ComponentType>([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'scatter-chart',
    'radar-chart',
    'funnel-chart',
]);

const ACTION_COMPONENT_TYPES = new Set<ComponentType>([
    ...INTERACTION_COMPONENT_TYPES,
    'table',
    'scroll-board',
    'scroll-ranking',
]);

const DRILL_CONFIGURABLE_TYPES = new Set<ComponentType>([
    ...Array.from(DRILLABLE_TYPES),
    'table',
]);

function getActionSourcePathCandidates(type: ComponentType): string[] {
    if (type === 'pie-chart' || type === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
    if (type === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
    if (type === 'table' || type === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
    if (type === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
    if (type === 'treemap-chart' || type === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
    if (type === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
    return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
}

function renderInteractionConfig(
    component: ScreenComponent,
    globalVariables: ScreenGlobalVariable[],
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    if (!INTERACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const interaction = component.interaction ?? {
        enabled: false,
        mappings: [] as ComponentInteractionMapping[],
        jumpEnabled: false,
        jumpUrlTemplate: '',
        jumpOpenMode: 'new-tab' as const,
    };
    const mappings = interaction.mappings ?? [];
    const sourcePathCandidates = (() => {
        const t = component.type;
        if (t === 'pie-chart' || t === 'funnel-chart') return ['name', 'value', 'percent', 'data.name'];
        if (t === 'map-chart') return ['name', 'data.name', 'data.value', 'value'];
        if (t === 'table' || t === 'scroll-board') return ['row[0]', 'row[1]', 'row[2]', 'name', 'value'];
        if (t === 'scatter-chart') return ['name', 'value', 'data[0]', 'data[1]', 'seriesName'];
        if (t === 'treemap-chart' || t === 'sunburst-chart') return ['name', 'value', 'data.name', 'treePathInfo'];
        if (t === 'radar-chart') return ['name', 'seriesName', 'value', 'data.name'];
        return ['name', 'seriesName', 'value', 'data.name', 'data.value', 'data.code'];
    })();

    const setInteraction = (next: typeof interaction) => {
        updateComponent(component.id, { interaction: next });
    };

    const updateMapping = (index: number, patch: Partial<ComponentInteractionMapping>) => {
        const next = [...mappings];
        next[index] = { ...next[index], ...patch };
        setInteraction({ ...interaction, mappings: next });
    };

    const content = (
        <>
            <div className="property-row">
                <label className="property-label">启用点击联动</label>
                <input
                    type="checkbox"
                    checked={interaction.enabled ?? false}
                    onChange={(e) => setInteraction({ ...interaction, enabled: e.target.checked })}
                />
            </div>

            {interaction.enabled && (
                <>
                    {globalVariables.length === 0 && (
                        <div style={{ fontSize: 11, color: '#888', marginBottom: 8 }}>
                            请先在顶部“变量”里创建全局变量。
                        </div>
                    )}

                    {mappings.map((mapping, index) => (
                        <div
                            key={`interaction-${index}`}
                            style={{
                                border: '1px solid rgba(255,255,255,0.06)',
                                borderRadius: 4,
                                padding: 8,
                                marginBottom: 8,
                            }}
                        >
                            <div className="property-row">
                                <label className="property-label">目标变量</label>
                                <select
                                    className="property-input"
                                    value={mapping.variableKey || ''}
                                    onChange={(e) => updateMapping(index, { variableKey: e.target.value })}
                                >
                                    <option value="">-- 请选择 --</option>
                                    {globalVariables.map((item) => (
                                        <option key={item.key} value={item.key}>
                                            {item.label || item.key} ({item.key})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="property-row">
                                <label className="property-label">取值路径</label>
                                <input
                                    list={`interaction-source-path-${index}`}
                                    className="property-input"
                                    value={mapping.sourcePath || 'name'}
                                    onChange={(e) => updateMapping(index, { sourcePath: e.target.value })}
                                    placeholder="name / data.name / value"
                                />
                                <datalist id={`interaction-source-path-${index}`}>
                                    {sourcePathCandidates.map((item) => (
                                        <option key={item} value={item} />
                                    ))}
                                </datalist>
                            </div>

                            <div className="property-row">
                                <label className="property-label">值转换</label>
                                <select
                                    className="property-input"
                                    value={String(mapping.transform || 'raw')}
                                    onChange={(e) => updateMapping(index, { transform: e.target.value as ComponentInteractionMapping['transform'] })}
                                >
                                    <option value="raw">原值</option>
                                    <option value="string">字符串</option>
                                    <option value="number">数值</option>
                                    <option value="lowercase">转小写</option>
                                    <option value="uppercase">转大写</option>
                                </select>
                            </div>

                            <div className="property-row">
                                <label className="property-label">默认值</label>
                                <input
                                    className="property-input"
                                    value={mapping.fallbackValue || ''}
                                    onChange={(e) => updateMapping(index, { fallbackValue: e.target.value })}
                                    placeholder="取值为空时写入该值"
                                />
                            </div>

                            <button
                                className="property-input"
                                onClick={() => setInteraction({ ...interaction, mappings: mappings.filter((_, i) => i !== index) })}
                                style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#ef4444' }}
                            >
                                删除联动规则
                            </button>
                        </div>
                    ))}

                    <button
                        className="property-input"
                        onClick={() => setInteraction({
                            ...interaction,
                            mappings: [...mappings, {
                                variableKey: globalVariables[0]?.key ?? '',
                                sourcePath: 'name',
                                transform: 'raw',
                                fallbackValue: '',
                            }],
                        })}
                        style={{ width: '100%', cursor: 'pointer', textAlign: 'center', color: '#6366f1' }}
                    >
                        + 添加联动规则
                    </button>
                    <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 6, lineHeight: 1.5 }}>
                        支持自定义路径，例如 <code>data.code</code>；可对值做数值/大小写转换，并设置空值回退。
                    </div>

                    <div className="property-row" style={{ marginTop: 10 }}>
                        <label className="property-label">启用点击跳转</label>
                        <input
                            type="checkbox"
                            checked={interaction.jumpEnabled === true}
                            onChange={(e) => setInteraction({ ...interaction, jumpEnabled: e.target.checked })}
                        />
                    </div>

                    {interaction.jumpEnabled === true && (
                        <>
                            <div className="property-row">
                                <label className="property-label">跳转链接模板</label>
                                <input
                                    className="property-input"
                                    value={interaction.jumpUrlTemplate || ''}
                                    onChange={(e) => setInteraction({ ...interaction, jumpUrlTemplate: e.target.value })}
                                    placeholder="https://host/path?name={{name}}&value={{value}}"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">打开方式</label>
                                <select
                                    className="property-input"
                                    value={interaction.jumpOpenMode || 'new-tab'}
                                    onChange={(e) => setInteraction({
                                        ...interaction,
                                        jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab',
                                    })}
                                >
                                    <option value="new-tab">新窗口</option>
                                    <option value="self">当前窗口</option>
                                </select>
                            </div>
                            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>
                                支持占位符: {'{{name}} / {{seriesName}} / {{value}} / {{data.name}}'}
                            </div>
                        </>
                    )}
                </>
            )}
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section">
            <div className="property-section-title">联动配置</div>
            {content}
        </div>
    );
}

function renderActionConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    if (!ACTION_COMPONENT_TYPES.has(component.type)) {
        return null;
    }

    const actions = component.actions ?? [];
    const sourcePathCandidates = getActionSourcePathCandidates(component.type);

    const setActions = (next: ScreenComponentAction[]) => {
        updateComponent(component.id, { actions: next });
    };

    const updateAction = (index: number, patch: Partial<ScreenComponentAction>) => {
        const next = [...actions];
        next[index] = { ...next[index], ...patch };
        setActions(next);
    };

    const updateMappings = (index: number, nextMappings: ComponentInteractionMapping[]) => {
        updateAction(index, { mappings: nextMappings });
    };

    const content = (
        <>
            {actions.length === 0 ? (
                <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 8 }}>
                    当前组件还没有动作入口。适合配置详情面板、跳转、变量写入或意图事件。
                </div>
            ) : null}

            {actions.map((action, index) => {
                const actionType = action.type || 'set-variable';
                const mappings = action.mappings ?? [];
                const showMappings = actionType === 'set-variable' || actionType === 'jump-url' || actionType === 'emit-intent';
                return (
                    <div
                        key={`action-${index}`}
                        style={{
                            border: '1px solid rgba(148,163,184,0.18)',
                            borderRadius: 10,
                            padding: 10,
                            marginBottom: 10,
                            background: 'rgba(248,250,252,0.72)',
                        }}
                    >
                        <div className="property-row">
                            <label className="property-label">动作标题</label>
                            <input
                                type="text"
                                className="property-input"
                                value={action.label || ''}
                                onChange={(e) => updateAction(index, { label: e.target.value })}
                                placeholder="查看详情 / 发起协调 / 跳转周报"
                            />
                        </div>

                        <div className="property-row">
                            <label className="property-label">动作类型</label>
                            <select
                                className="property-input"
                                value={actionType}
                                onChange={(e) => updateAction(index, { type: e.target.value as ScreenComponentAction['type'] })}
                            >
                                <option value="set-variable">写入变量</option>
                                <option value="drill-down">下钻</option>
                                <option value="drill-up">上卷返回</option>
                                <option value="jump-url">页面跳转</option>
                                <option value="open-panel">打开详情面板</option>
                                <option value="emit-intent">发出意图事件</option>
                            </select>
                        </div>

                        {showMappings ? (
                            <>
                                {mappings.map((mapping, mappingIndex) => (
                                    <div
                                        key={`action-${index}-mapping-${mappingIndex}`}
                                        style={{
                                            border: '1px dashed rgba(148,163,184,0.22)',
                                            borderRadius: 8,
                                            padding: 8,
                                            marginBottom: 8,
                                        }}
                                    >
                                        <div className="property-row">
                                            <label className="property-label">目标变量</label>
                                            <input
                                                type="text"
                                                className="property-input"
                                                value={mapping.variableKey || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], variableKey: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="projectId"
                                            />
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">取值路径</label>
                                            <input
                                                list={`action-source-path-${index}-${mappingIndex}`}
                                                className="property-input"
                                                value={mapping.sourcePath || ''}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], sourcePath: e.target.value };
                                                    updateMappings(index, next);
                                                }}
                                                placeholder="name / row[0] / data.owner"
                                            />
                                            <datalist id={`action-source-path-${index}-${mappingIndex}`}>
                                                {sourcePathCandidates.map((item) => (
                                                    <option key={item} value={item} />
                                                ))}
                                            </datalist>
                                        </div>
                                        <div className="property-row">
                                            <label className="property-label">值转换</label>
                                            <select
                                                className="property-input"
                                                value={String(mapping.transform || 'raw')}
                                                onChange={(e) => {
                                                    const next = [...mappings];
                                                    next[mappingIndex] = { ...next[mappingIndex], transform: e.target.value as ComponentInteractionMapping['transform'] };
                                                    updateMappings(index, next);
                                                }}
                                            >
                                                <option value="raw">原值</option>
                                                <option value="string">字符串</option>
                                                <option value="number">数值</option>
                                                <option value="lowercase">转小写</option>
                                                <option value="uppercase">转大写</option>
                                            </select>
                                        </div>
                                        <button
                                            type="button"
                                            className="property-input"
                                            onClick={() => updateMappings(index, mappings.filter((_, i) => i !== mappingIndex))}
                                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444' }}
                                        >
                                            删除映射
                                        </button>
                                    </div>
                                ))}

                                <button
                                    type="button"
                                    className="property-input"
                                    onClick={() => updateMappings(index, [
                                        ...mappings,
                                        { variableKey: '', sourcePath: 'name', transform: 'raw', fallbackValue: '' },
                                    ])}
                                    style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb' }}
                                >
                                    + 添加变量映射
                                </button>
                            </>
                        ) : null}

                        {actionType === 'jump-url' ? (
                            <>
                                <div className="property-row">
                                    <label className="property-label">跳转链接模板</label>
                                    <input
                                        type="text"
                                        className="property-input"
                                        value={action.jumpUrlTemplate || ''}
                                        onChange={(e) => updateAction(index, { jumpUrlTemplate: e.target.value })}
                                        placeholder="https://host/path?project={{name}}"
                                    />
                                </div>
                                <div className="property-row">
                                    <label className="property-label">打开方式</label>
                                    <select
                                        className="property-input"
                                        value={action.jumpOpenMode || 'new-tab'}
                                        onChange={(e) => updateAction(index, { jumpOpenMode: e.target.value === 'self' ? 'self' : 'new-tab' })}
                                    >
                                        <option value="new-tab">新窗口</option>
                                        <option value="self">当前窗口</option>
                                    </select>
                                </div>
                            </>
                        ) : null}

                        {actionType === 'open-panel' ? (
                            <>
                                <div className="property-row">
                                    <label className="property-label">面板标题模板</label>
                                    <input
                                        type="text"
                                        className="property-input"
                                        value={action.panelTitle || ''}
                                        onChange={(e) => updateAction(index, { panelTitle: e.target.value })}
                                        placeholder="项目 {{name}}"
                                    />
                                </div>
                                <div className="property-row">
                                    <label className="property-label">面板内容模板</label>
                                    <textarea
                                        className="property-input"
                                        rows={4}
                                        value={action.panelBodyTemplate || ''}
                                        onChange={(e) => updateAction(index, { panelBodyTemplate: e.target.value })}
                                        placeholder={'负责人：{{责任人}}\n状态：{{状态}}\n建议：发起协调'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {actionType === 'emit-intent' ? (
                            <>
                                <div className="property-row">
                                    <label className="property-label">意图名称</label>
                                    <input
                                        type="text"
                                        className="property-input"
                                        value={action.intentName || ''}
                                        onChange={(e) => updateAction(index, { intentName: e.target.value })}
                                        placeholder="project.follow-up"
                                    />
                                </div>
                                <div className="property-row">
                                    <label className="property-label">意图负载模板</label>
                                    <textarea
                                        className="property-input"
                                        rows={4}
                                        value={action.intentPayloadTemplate || ''}
                                        onChange={(e) => updateAction(index, { intentPayloadTemplate: e.target.value })}
                                        placeholder={'{"project":"{{name}}","owner":"{{责任人}}"}'}
                                    />
                                </div>
                            </>
                        ) : null}

                        {(actionType === 'drill-down' || actionType === 'drill-up') ? (
                            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 6 }}>
                                {actionType === 'drill-down'
                                    ? '运行态会复用当前组件的下钻链路，并使用点击值推进到下一层。'
                                    : '运行态会从当前钻取层级返回上一层。'}
                            </div>
                        ) : null}

                        <button
                            type="button"
                            className="property-input"
                            onClick={() => setActions(actions.filter((_, i) => i !== index))}
                            style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#ef4444', marginTop: 8 }}
                        >
                            删除动作
                        </button>
                    </div>
                );
            })}

            <button
                type="button"
                className="property-input"
                onClick={() => setActions([
                    ...actions,
                    { type: 'open-panel', label: '查看详情', panelTitle: '{{name}}', panelBodyTemplate: '' },
                ])}
                style={{ width: '100%', textAlign: 'center', cursor: 'pointer', color: '#2563eb' }}
            >
                + 添加动作入口
            </button>
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section">
            <div className="property-section-title">动作入口</div>
            {content}
        </div>
    );
}

function renderDrillDownConfig(
    component: ScreenComponent,
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void,
    options?: { embedded?: boolean },
) {
    const { type, dataSource, drillDown } = component;
    const cardId = dataSource?.type === 'card' ? dataSource.cardConfig?.cardId : undefined;

    // Show for drill-capable components backed by a valid card data source.
    if (!DRILL_CONFIGURABLE_TYPES.has(type) || dataSource?.type !== 'card' || !cardId || cardId <= 0) {
        return null;
    }

    const enabled = drillDown?.enabled ?? false;
    const levels = drillDown?.levels ?? [];

    const setDrillDown = (updates: Partial<typeof drillDown>) => {
        updateComponent(component.id, {
            drillDown: { enabled, levels, ...drillDown, ...updates },
        });
    };

    const updateLevel = (index: number, field: keyof DrillLevel, value: string | number) => {
        const newLevels = [...levels];
        newLevels[index] = { ...newLevels[index], [field]: value };
        setDrillDown({ levels: newLevels });
    };

    const removeLevel = (index: number) => {
        setDrillDown({ levels: levels.filter((_, i) => i !== index) });
    };

    const addLevel = () => {
        setDrillDown({ levels: [...levels, { cardId: 0, paramName: '', label: '' }] });
    };

    const content = (
        <>
            <div className="property-row">
                <label className="property-label">启用下钻</label>
                <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(e) => setDrillDown({ enabled: e.target.checked })}
                />
            </div>

            {enabled && (
                <>
                    {levels.map((level, i) => (
                        <div key={i} style={{
                            border: '1px solid rgba(255,255,255,0.1)',
                            borderRadius: 4,
                            padding: 8,
                            marginBottom: 8,
                        }}>
                            <div style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                marginBottom: 4,
                                fontSize: 11,
                                color: '#888',
                            }}>
                                <span>层级 {i + 1}</span>
                                <button
                                    className="property-btn-small"
                                    onClick={() => removeLevel(i)}
                                    style={{
                                        background: 'none', border: 'none',
                                        color: '#ef4444', cursor: 'pointer', fontSize: 11,
                                    }}
                                >
                                    删除
                                </button>
                            </div>
                            <div className="property-row">
                                <label className="property-label">Card</label>
                                <CardIdPicker
                                    value={level.cardId || 0}
                                    onChange={(cardId) => updateLevel(i, 'cardId', cardId)}
                                    placeholder="-- 下钻目标 --"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">参数名</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.paramName}
                                    onChange={(e) => updateLevel(i, 'paramName', e.target.value)}
                                    placeholder="如: region"
                                />
                            </div>
                            <div className="property-row">
                                <label className="property-label">标签</label>
                                <input
                                    type="text"
                                    className="property-input"
                                    value={level.label}
                                    onChange={(e) => updateLevel(i, 'label', e.target.value)}
                                    placeholder="如: 地区"
                                />
                            </div>
                        </div>
                    ))}

                    <button
                        className="property-input"
                        onClick={addLevel}
                        style={{
                            width: '100%', cursor: 'pointer',
                            textAlign: 'center', color: '#6366f1',
                        }}
                    >
                        + 添加下钻层级
                    </button>
                </>
            )}
        </>
    );

    if (options?.embedded) {
        return content;
    }
    return (
        <div className="property-section">
            <div className="property-section-title">下钻配置</div>
            {content}
        </div>
    );
}

/** Column config entry for scroll-board */
interface ColumnEntry {
    source: string;
    alias?: string;
    align?: 'left' | 'center' | 'right';
    width?: number;
    wrap?: boolean;
    formatter?: 'auto' | 'string' | 'number' | 'percent' | 'date';
}

interface SourceColumnOption {
    name: string;
    displayName: string;
}

function CardSourceColumnBindingsEditor({
    title,
    sourceCols,
    columns,
    defaultAlign,
    onColumnsChange,
}: {
    title: string;
    sourceCols: SourceColumnOption[];
    columns: ColumnEntry[] | undefined;
    defaultAlign: NonNullable<ColumnEntry['align']>;
    onColumnsChange: (value: ColumnEntry[] | undefined) => void;
}) {
    const fallbackColumns = sourceCols.map((item) => ({ source: item.name } as ColumnEntry));
    const effectiveColumns = columns ?? fallbackColumns;
    const usedSourceSet = new Set(effectiveColumns.map((item) => item.source));
    const unboundSources = sourceCols.filter((item) => !usedSourceSet.has(item.name));

    const updateColumn = (index: number, patch: Partial<ColumnEntry>) => {
        const next = effectiveColumns.map((item, i) => (i === index ? { ...item, ...patch } : item));
        onColumnsChange(next);
    };

    const handleSourceChange = (index: number, nextSource: string) => {
        const duplicate = effectiveColumns.some((item, i) => i !== index && item.source === nextSource);
        if (duplicate) {
            alert('该字段已被绑定，请选择其他字段');
            return;
        }
        updateColumn(index, { source: nextSource });
    };

    const handleMove = (index: number, direction: -1 | 1) => {
        const target = index + direction;
        if (target < 0 || target >= effectiveColumns.length) return;
        const next = [...effectiveColumns];
        const [current] = next.splice(index, 1);
        next.splice(target, 0, current);
        onColumnsChange(next);
    };

    const handleRemove = (index: number) => {
        onColumnsChange(effectiveColumns.filter((_, i) => i !== index));
    };

    return (
        <>
            <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                {title}
            </div>
            <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => {
                        if (!unboundSources[0]) return;
                        onColumnsChange([...effectiveColumns, { source: unboundSources[0].name, align: defaultAlign }]);
                    }}
                    disabled={unboundSources.length === 0}
                    title="追加一个未绑定字段"
                >
                    + 添加列
                </button>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => onColumnsChange(undefined)}
                    title="恢复默认映射（按数据源原始字段）"
                >
                    恢复默认
                </button>
                <button
                    type="button"
                    className="header-btn"
                    onClick={() => onColumnsChange([])}
                    disabled={effectiveColumns.length === 0}
                    title="清空当前映射"
                >
                    清空
                </button>
            </div>
            {effectiveColumns.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 4, marginBottom: 8 }}>
                    当前无字段绑定，请点击“添加列”。
                </div>
            )}
            {effectiveColumns.map((entry, index) => {
                const sourceMeta = sourceCols.find((item) => item.name === entry.source);
                return (
                    <div key={`${entry.source}-${index}`} style={{
                        border: '1px solid rgba(255,255,255,0.06)',
                        borderRadius: 4,
                        padding: '6px',
                        marginBottom: 6,
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                            <span style={{ fontSize: 11, color: '#94a3b8' }}>
                                列 {index + 1}{sourceMeta ? '' : ' (失效字段)'}
                            </span>
                            <div style={{ display: 'flex', gap: 4 }}>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleMove(index, -1)}
                                    disabled={index === 0}
                                    title="上移"
                                >
                                    ↑
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleMove(index, 1)}
                                    disabled={index >= effectiveColumns.length - 1}
                                    title="下移"
                                >
                                    ↓
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    onClick={() => handleRemove(index)}
                                    title="删除该列"
                                >
                                    删除
                                </button>
                            </div>
                        </div>
                        <div className="property-row">
                            <label className="property-label">绑定字段</label>
                            <select
                                className="property-input"
                                value={entry.source}
                                onChange={(e) => handleSourceChange(index, e.target.value)}
                            >
                                {!sourceMeta && (
                                    <option value={entry.source}>{entry.source} (失效字段)</option>
                                )}
                                {sourceCols.map((item) => (
                                    <option key={item.name} value={item.name}>
                                        {(item.displayName || item.name)} ({item.name})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="property-row">
                            <label className="property-label">表头标题</label>
                            <input
                                type="text"
                                className="property-input"
                                placeholder={sourceMeta?.displayName || entry.source}
                                value={entry.alias || ''}
                                onChange={(e) => {
                                    const nextAlias = e.target.value;
                                    if (nextAlias) {
                                        updateColumn(index, { alias: nextAlias });
                                        return;
                                    }
                                    const next = effectiveColumns.map((item, i) => {
                                        if (i !== index) return item;
                                        const { alias: _alias, ...rest } = item;
                                        return rest;
                                    });
                                    onColumnsChange(next);
                                }}
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">对齐</label>
                            <select
                                className="property-input"
                                value={(entry.align as string) || defaultAlign}
                                onChange={(e) => updateColumn(index, { align: e.target.value as ColumnEntry['align'] })}
                            >
                                <option value="left">左</option>
                                <option value="center">中</option>
                                <option value="right">右</option>
                            </select>
                        </div>
                        <div className="property-row">
                            <label className="property-label">自动换行</label>
                            <input
                                type="checkbox"
                                checked={entry.wrap === true}
                                onChange={(e) => updateColumn(index, { wrap: e.target.checked })}
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">列宽(%)</label>
                            <input
                                type="number"
                                className="property-input"
                                min={5}
                                max={100}
                                value={typeof entry.width === 'number' ? entry.width : ''}
                                placeholder="自动"
                                onChange={(e) => {
                                    const raw = e.target.value.trim();
                                    if (!raw) {
                                        updateColumn(index, { width: undefined });
                                        return;
                                    }
                                    const parsed = Number(raw);
                                    updateColumn(index, {
                                        width: Number.isFinite(parsed) ? Math.max(5, Math.min(100, parsed)) : undefined,
                                    });
                                }}
                            />
                        </div>
                        <div className="property-row">
                            <label className="property-label">格式化</label>
                            <select
                                className="property-input"
                                value={(entry.formatter as string) || 'auto'}
                                onChange={(e) => updateColumn(index, { formatter: e.target.value as ColumnEntry['formatter'] })}
                            >
                                <option value="auto">自动</option>
                                <option value="string">文本</option>
                                <option value="number">数字</option>
                                <option value="percent">百分比</option>
                                <option value="date">日期时间</option>
                            </select>
                        </div>
                    </div>
                );
            })}
        </>
    );
}

/** scroll-board 专用属性面板，支持动态列选择 */
function ScrollBoardConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    // Read _sourceColumns persisted by ComponentRenderer (no separate API call)
    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasDynamicSource = resolveDataSourceType(dataSource as DataSourceConfig | undefined) !== 'static';

    // Static fallback: use config.header when no card data source
    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    return (
        <>
            <div className="property-row">
                <label className="property-label">行数</label>
                <input
                    type="number"
                    className="property-input"
                    min={1}
                    max={20}
                    value={config.rowNum as number}
                    onChange={(e) => onChange('rowNum', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">等待时间(ms)</label>
                <input
                    type="number"
                    className="property-input"
                    min={500}
                    max={10000}
                    step={500}
                    value={config.waitTime as number || 2000}
                    onChange={(e) => onChange('waitTime', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#ffffff'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBGC as string) || '#003366'}
                    onChange={(e) => onChange('headerBGC', e.target.value)}
                />
            </div>

            {/* Card 数据源: 等待列加载 */}
            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {/* Card 数据源: 动态列选择 */}
            {hasDynamicSource && sourceCols.length > 0 && (
                <CardSourceColumnBindingsEditor
                    title="显示列 (来自数据源)"
                    sourceCols={sourceCols}
                    columns={columns}
                    defaultAlign="center"
                    onColumnsChange={(value) => onChange('columns', value)}
                />
            )}

            {/* 静态数据源: 按索引的表头别名 (保持向后兼容) */}
            {!hasDynamicSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
                                placeholder={h}
                                value={columnAlias[String(i)] || ''}
                                onChange={(e) => {
                                    const newAlias = { ...columnAlias };
                                    if (e.target.value) {
                                        newAlias[String(i)] = e.target.value;
                                    } else {
                                        delete newAlias[String(i)];
                                    }
                                    onChange('columnAlias', newAlias);
                                }}
                            />
                        </div>
                    ))}
                </>
            )}
        </>
    );
}

/** Chart annotation config: markLines, markAreas, conditionalColors */
function ChartAnnotationConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config } = component;
    const markLines = (config.markLines as ChartMarkLine[]) ?? [];
    const markAreas = (config.markAreas as ChartMarkArea[]) ?? [];
    const conditionalColors = (config.conditionalColors as SeriesConditionalColor[]) ?? [];

    const addMarkLine = () => {
        if (markLines.length >= 5) return;
        onChange('markLines', [...markLines, { type: 'value' as const, value: 0, name: '', color: '#ff6b6b', lineStyle: 'dashed' as const, axis: 'y' as const }]);
    };
    const updateMarkLine = (idx: number, patch: Partial<ChartMarkLine>) => {
        onChange('markLines', markLines.map((ml, i) => i === idx ? { ...ml, ...patch } : ml));
    };
    const removeMarkLine = (idx: number) => {
        onChange('markLines', markLines.filter((_, i) => i !== idx));
    };
    const addMarkArea = () => {
        if (markAreas.length >= 3) return;
        onChange('markAreas', [...markAreas, { from: 0, to: 100, name: '', color: 'rgba(255,107,107,0.15)', axis: 'y' as const }]);
    };
    const updateMarkArea = (idx: number, patch: Partial<ChartMarkArea>) => {
        onChange('markAreas', markAreas.map((ma, i) => i === idx ? { ...ma, ...patch } : ma));
    };
    const removeMarkArea = (idx: number) => {
        onChange('markAreas', markAreas.filter((_, i) => i !== idx));
    };
    const addConditionalColor = () => {
        if (conditionalColors.length >= 5) return;
        onChange('conditionalColors', [...conditionalColors, { operator: '>' as const, value: 0, color: '#ef4444' }]);
    };
    const updateConditionalColor = (idx: number, patch: Partial<SeriesConditionalColor>) => {
        onChange('conditionalColors', conditionalColors.map((cc, i) => i === idx ? { ...cc, ...patch } : cc));
    };
    const removeConditionalColor = (idx: number) => {
        onChange('conditionalColors', conditionalColors.filter((_, i) => i !== idx));
    };

    return (
        <div style={{ display: 'grid', gap: 8 }}>
            <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600 }}>辅助线 ({markLines.length}/5)</div>
            {markLines.map((ml, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: 'auto 1fr 60px 60px auto', gap: 4, alignItems: 'center' }}>
                    <select className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} value={ml.type}
                        onChange={(e) => updateMarkLine(idx, { type: e.target.value as ChartMarkLine['type'] })}>
                        <option value="value">固定值</option><option value="average">平均</option>
                        <option value="min">最小</option><option value="max">最大</option>
                    </select>
                    {ml.type === 'value' ? (
                        <input type="number" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} value={ml.value ?? 0}
                            onChange={(e) => updateMarkLine(idx, { value: Number(e.target.value) })} />
                    ) : <span />}
                    <input type="text" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="标签" value={ml.name ?? ''} onChange={(e) => updateMarkLine(idx, { name: e.target.value })} />
                    <input type="color" className="property-color-input" value={ml.color ?? '#ff6b6b'} onChange={(e) => updateMarkLine(idx, { color: e.target.value })} />
                    <button type="button" className="property-btn-small" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeMarkLine(idx)}>×</button>
                </div>
            ))}
            {markLines.length < 5 && (
                <button type="button" className="property-btn-small" onClick={addMarkLine} style={{ fontSize: 11, justifySelf: 'start' }}>+ 辅助线</button>
            )}

            <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, marginTop: 4 }}>标记区域 ({markAreas.length}/3)</div>
            {markAreas.map((ma, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 60px 60px auto', gap: 4, alignItems: 'center' }}>
                    <input type="number" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="起始" value={ma.from} onChange={(e) => updateMarkArea(idx, { from: Number(e.target.value) })} />
                    <input type="number" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="结束" value={ma.to} onChange={(e) => updateMarkArea(idx, { to: Number(e.target.value) })} />
                    <input type="text" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="标签" value={ma.name ?? ''} onChange={(e) => updateMarkArea(idx, { name: e.target.value })} />
                    <input type="color" className="property-color-input" value={ma.color?.startsWith('rgba') ? '#ff6b6b' : (ma.color ?? '#ff6b6b')}
                        onChange={(e) => { const h = e.target.value; const r = parseInt(h.slice(1, 3), 16); const g = parseInt(h.slice(3, 5), 16); const b = parseInt(h.slice(5, 7), 16); updateMarkArea(idx, { color: `rgba(${r},${g},${b},0.15)` }); }} />
                    <button type="button" className="property-btn-small" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeMarkArea(idx)}>×</button>
                </div>
            ))}
            {markAreas.length < 3 && (
                <button type="button" className="property-btn-small" onClick={addMarkArea} style={{ fontSize: 11, justifySelf: 'start' }}>+ 标记区域</button>
            )}

            <div style={{ fontSize: 11, color: '#94a3b8', fontWeight: 600, marginTop: 4 }}>条件着色 ({conditionalColors.length}/5)</div>
            {conditionalColors.map((cc, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: 'auto 60px 60px 40px auto', gap: 4, alignItems: 'center' }}>
                    <select className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} value={cc.operator}
                        onChange={(e) => updateConditionalColor(idx, { operator: e.target.value as SeriesConditionalColor['operator'] })}>
                        <option value=">">{'>'}</option><option value=">=">{'>='}</option><option value="<">{'<'}</option>
                        <option value="<=">{'<='}</option><option value="==">{'=='}</option><option value="between">区间</option>
                    </select>
                    <input type="number" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} value={cc.value}
                        onChange={(e) => updateConditionalColor(idx, { value: Number(e.target.value) })} />
                    {cc.operator === 'between' ? (
                        <input type="number" className="property-input" style={{ fontSize: 11, padding: '3px 4px' }} placeholder="上限" value={cc.valueTo ?? 0}
                            onChange={(e) => updateConditionalColor(idx, { valueTo: Number(e.target.value) })} />
                    ) : <span />}
                    <input type="color" className="property-color-input" value={cc.color} onChange={(e) => updateConditionalColor(idx, { color: e.target.value })} />
                    <button type="button" className="property-btn-small" style={{ padding: '2px 6px', fontSize: 11 }} onClick={() => removeConditionalColor(idx)}>×</button>
                </div>
            ))}
            {conditionalColors.length < 5 && (
                <button type="button" className="property-btn-small" onClick={addConditionalColor} style={{ fontSize: 11, justifySelf: 'start' }}>+ 条件着色</button>
            )}
        </div>
    );
}

/** table 专用属性面板，支持动态列选择和样式配置 */
function TableConfig({ component, onChange }: {
    component: ScreenComponent;
    onChange: (key: string, value: unknown) => void;
}) {
    const { config, dataSource } = component;

    const sourceCols = config._sourceColumns as Array<{ name: string; displayName: string }> ?? [];
    const columns = config.columns as ColumnEntry[] | undefined;
    const hasDynamicSource = resolveDataSourceType(dataSource as DataSourceConfig | undefined) !== 'static';

    const staticHeaders = config.header as string[] || [];
    const columnAlias = config.columnAlias as Record<string, string> || {};

    return (
        <>
            <div className="property-row">
                <label className="property-label">字号</label>
                <input
                    type="number"
                    className="property-input"
                    min={10}
                    max={24}
                    value={(config.fontSize as number) || 13}
                    onChange={(e) => onChange('fontSize', Number(e.target.value))}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerColor as string) || '#e5e7eb'}
                    onChange={(e) => onChange('headerColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">表头背景</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.headerBackground as string) || '#64748b'}
                    onChange={(e) => onChange('headerBackground', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">正文颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.bodyColor as string) || '#d1d5db'}
                    onChange={(e) => onChange('bodyColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">边框颜色</label>
                <input
                    type="color"
                    className="property-color-input"
                    value={(config.borderColor as string) || '#94a3b8'}
                    onChange={(e) => onChange('borderColor', e.target.value)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">启用排序</label>
                <input
                    type="checkbox"
                    checked={config.enableSort !== false}
                    onChange={(e) => onChange('enableSort', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">分页</label>
                <input
                    type="checkbox"
                    checked={config.enablePagination === true}
                    onChange={(e) => onChange('enablePagination', e.target.checked)}
                />
            </div>
            {config.enablePagination === true && (
                <div className="property-row">
                    <label className="property-label">每页条数</label>
                    <input
                        type="number"
                        className="property-input"
                        min={1}
                        max={200}
                        value={(config.pageSize as number) || 10}
                        onChange={(e) => onChange('pageSize', Number(e.target.value))}
                    />
                </div>
            )}
            <div className="property-row">
                <label className="property-label">冻结表头</label>
                <input
                    type="checkbox"
                    checked={config.freezeHeader !== false}
                    onChange={(e) => onChange('freezeHeader', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">冻结首列</label>
                <input
                    type="checkbox"
                    checked={config.freezeFirstColumn === true}
                    onChange={(e) => onChange('freezeFirstColumn', e.target.checked)}
                />
            </div>
            <div className="property-row">
                <label className="property-label">条件格式(JSON)</label>
                <textarea
                    className="property-input"
                    rows={4}
                    defaultValue={JSON.stringify((config.conditionalRules as unknown[]) || [], null, 2)}
                    onBlur={(e) => {
                        const raw = e.target.value.trim();
                        if (!raw) {
                            onChange('conditionalRules', []);
                            return;
                        }
                        try {
                            const parsed = JSON.parse(raw);
                            onChange('conditionalRules', Array.isArray(parsed) ? parsed : []);
                        } catch {
                            alert('条件格式 JSON 解析失败');
                        }
                    }}
                    placeholder='[{"columnKey":"amount","operator":">","value":100,"color":"#ef4444"}]'
                />
            </div>
            <div style={{ fontSize: 11, opacity: 0.75, marginTop: -2, marginBottom: 8, lineHeight: 1.5 }}>
                支持按 `columnIndex`、`columnKey` 或 `columnTitle` 匹配列；建议优先使用 `columnKey` 以避免字段重排错位。
            </div>

            {hasDynamicSource && sourceCols.length === 0 && (
                <div style={{ fontSize: 11, color: '#888', marginTop: 8, padding: '4px 0' }}>
                    等待数据源加载列信息…
                </div>
            )}

            {hasDynamicSource && sourceCols.length > 0 && (
                <CardSourceColumnBindingsEditor
                    title="字段绑定 (来自数据源)"
                    sourceCols={sourceCols}
                    columns={columns}
                    defaultAlign="left"
                    onColumnsChange={(value) => onChange('columns', value)}
                />
            )}

            {!hasDynamicSource && staticHeaders.length > 0 && (
                <>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 8, marginBottom: 4 }}>
                        表头别名
                    </div>
                    {staticHeaders.map((h, i) => (
                        <div className="property-row" key={i}>
                            <label className="property-label" title={h}>列{i + 1}</label>
                            <input
                                type="text"
                                className="property-input"
                                placeholder={h}
                                value={columnAlias[String(i)] || ''}
                                onChange={(e) => {
                                    const newAlias = { ...columnAlias };
                                    if (e.target.value) {
                                        newAlias[String(i)] = e.target.value;
                                    } else {
                                        delete newAlias[String(i)];
                                    }
                                    onChange('columnAlias', newAlias);
                                }}
                            />
                        </div>
                    ))}
                </>
            )}
        </>
    );
}
