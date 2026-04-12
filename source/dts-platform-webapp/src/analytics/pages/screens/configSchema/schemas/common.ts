import type { ConfigField } from '../types';

// ---------------------------------------------------------------------------
// Shared field sets for ECharts-based charts
// ---------------------------------------------------------------------------

/** Common fields present on virtually every ECharts chart component. */
export const ECHARTS_COMMON_FIELDS: ConfigField[] = [
    { key: 'title',           label: '标题',     type: 'text',        group: 'chart' },
    { key: 'titleColor',      label: '标题颜色', type: 'color',       group: 'chart', themeTokenKey: 'textPrimary' },
    { key: 'titleFontSize',   label: '标题字号', type: 'number',      group: 'chart', min: 10, max: 48, defaultValue: 16 },
    { key: 'backgroundColor', label: '背景色',   type: 'color',       group: 'appearance', themeTokenKey: 'cardBackground' },
    { key: 'legend',         label: '图例',     type: 'legend-config', group: 'chart' },
    { key: 'seriesColors',   label: '系列颜色', type: 'color-array', group: 'chart', themeTokenKey: 'echarts.colorPalette' },
    { key: 'tooltip',        label: '提示框',   type: 'json',        group: 'advanced' },
    { key: 'markLines',      label: '标记线',   type: 'json',        group: 'advanced' },
    { key: 'animation',      label: '动画',     type: 'boolean',     group: 'behavior', defaultValue: true },
    {
        key: 'animationDuration',
        label: '动画时长(ms)',
        type: 'number',
        group: 'behavior',
        min: 0,
        max: 5000,
        step: 100,
        defaultValue: 1000,
        showIf: (config) => config.animation !== false,
    },
    {
        key: 'seriesLabelPosition',
        label: '标签位置',
        type: 'select',
        group: 'chart',
        options: [
            { label: '自动', value: 'auto' },
            { label: '外侧', value: 'outside' },
            { label: '内侧', value: 'inside' },
            { label: '隐藏', value: 'none' },
        ],
        defaultValue: 'auto',
    },
];

/** Extra fields for axis-based charts (line, bar, scatter, combo, waterfall). */
export const AXIS_CHART_FIELDS: ConfigField[] = [
    { key: 'xAxis', label: 'X 轴', type: 'axis-config', group: 'chart' },
    { key: 'yAxis', label: 'Y 轴', type: 'axis-config', group: 'chart' },
];
