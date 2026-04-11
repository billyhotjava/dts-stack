import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';
import { ECHARTS_COMMON_FIELDS, AXIS_CHART_FIELDS } from './common';

// ---------------------------------------------------------------------------
// ECharts chart schemas — 14 chart types rendered by EChartsRenderer
// ---------------------------------------------------------------------------

const lineChartSchema: ComponentConfigSchema = {
    type: 'line-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'stackMode', label: '堆叠模式', type: 'select', group: 'chart', options: [
            { label: '关闭', value: 'off' },
            { label: '堆叠', value: 'stack' },
        ], defaultValue: 'off' },
    ],
};

const barChartSchema: ComponentConfigSchema = {
    type: 'bar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'horizontal', label: '方向', type: 'radio', group: 'chart', options: [
            { label: '纵向', value: false },
            { label: '横向', value: true },
        ], defaultValue: false },
        { key: 'stackMode', label: '堆叠模式', type: 'select', group: 'chart', options: [
            { label: '关闭', value: 'off' },
            { label: '堆叠', value: 'stack' },
        ], defaultValue: 'off' },
    ],
};

const pieChartSchema: ComponentConfigSchema = {
    type: 'pie-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const gaugeChartSchema: ComponentConfigSchema = {
    type: 'gauge-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'min',         label: '最小值',   type: 'number', group: 'chart', defaultValue: 0 },
        { key: 'max',         label: '最大值',   type: 'number', group: 'chart', defaultValue: 100 },
        { key: 'value',       label: '当前值',   type: 'number', group: 'chart' },
    ],
};

const scatterChartSchema: ComponentConfigSchema = {
    type: 'scatter-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'xAxisName', label: 'X 轴名称', type: 'text', group: 'chart' },
        { key: 'yAxisName', label: 'Y 轴名称', type: 'text', group: 'chart' },
    ],
};

const radarChartSchema: ComponentConfigSchema = {
    type: 'radar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'indicator', label: '指标配置', type: 'json', group: 'chart' },
    ],
};

const funnelChartSchema: ComponentConfigSchema = {
    type: 'funnel-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const mapChartSchema: ComponentConfigSchema = {
    type: 'map-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'mapScope', label: '地图范围', type: 'select', group: 'chart', options: [
            { label: '中国', value: 'china' },
            { label: '世界', value: 'world' },
        ], defaultValue: 'china' },
        { key: 'mapMode', label: '地图样式', type: 'select', group: 'chart', options: [
            { label: '区域填充', value: 'region' },
            { label: '气泡图',   value: 'bubble' },
            { label: '热力图',   value: 'heatmap' },
            { label: '散点图',   value: 'scatter' },
            { label: '流向图',   value: 'flow' },
        ], defaultValue: 'region' },
        { key: 'mapName',              label: '地图名称',       type: 'text',    group: 'chart' },
        { key: 'regions',              label: '区域数据',       type: 'json',    group: 'chart' },
        { key: 'enableRegionDrill',    label: '启用区域下钻',   type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'regionVariableKey',    label: '区域变量Key',    type: 'text',    group: 'behavior' },
        { key: 'regionCodeVariableKey', label: '区域编码变量Key', type: 'text',  group: 'behavior' },
        { key: 'scatterData',          label: '散点数据',       type: 'json',    group: 'advanced',
            showIf: (config) => config.mapMode === 'bubble' || config.mapMode === 'scatter',
        },
        { key: 'bubbleSizeRange',      label: '气泡大小范围',   type: 'json',    group: 'advanced',
            showIf: (config) => config.mapMode === 'bubble',
        },
        { key: 'bubbleColor',          label: '气泡颜色',       type: 'color',   group: 'advanced',
            showIf: (config) => config.mapMode === 'bubble' || config.mapMode === 'scatter',
        },
        { key: 'heatmapData',          label: '热力数据',       type: 'json',    group: 'advanced',
            showIf: (config) => config.mapMode === 'heatmap',
        },
        { key: 'heatmapRadius',        label: '热力半径',       type: 'number',  group: 'advanced', min: 5, max: 60, defaultValue: 20,
            showIf: (config) => config.mapMode === 'heatmap',
        },
        { key: 'flowData',             label: '流向数据',       type: 'json',    group: 'advanced',
            showIf: (config) => config.mapMode === 'flow',
        },
        { key: 'flowLineStyle',        label: '流向线样式',     type: 'json',    group: 'advanced',
            showIf: (config) => config.mapMode === 'flow',
        },
        { key: 'showFlowEffect',       label: '流向动效',       type: 'boolean', group: 'advanced', defaultValue: true,
            showIf: (config) => config.mapMode === 'flow',
        },
    ],
};

const comboChartSchema: ComponentConfigSchema = {
    type: 'combo-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
    ],
};

const treemapChartSchema: ComponentConfigSchema = {
    type: 'treemap-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const sunburstChartSchema: ComponentConfigSchema = {
    type: 'sunburst-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const wordcloudChartSchema: ComponentConfigSchema = {
    type: 'wordcloud-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'shape', label: '词云形状', type: 'select', group: 'chart', options: [
            { label: '圆形',     value: 'circle' },
            { label: '心形',     value: 'cardioid' },
            { label: '菱形',     value: 'diamond' },
            { label: '三角形',   value: 'triangle-forward' },
            { label: '倒三角',   value: 'triangle' },
            { label: '五边形',   value: 'pentagon' },
            { label: '星形',     value: 'star' },
        ], defaultValue: 'circle' },
        { key: 'fontSizeRange',  label: '字号范围',   type: 'json', group: 'chart', defaultValue: [14, 60] },
        { key: 'rotationRange',  label: '旋转角度范围', type: 'json', group: 'chart', defaultValue: [-45, 45] },
    ],
};

const waterfallChartSchema: ComponentConfigSchema = {
    type: 'waterfall-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
    ],
};

const ganttChartSchema: ComponentConfigSchema = {
    type: 'gantt-chart',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.advanced],
    fields: [
        { key: 'title',         label: '标题',       type: 'text',   group: 'content' },
        { key: 'renderMode',    label: '渲染模式',   type: 'select', group: 'chart', options: [
            { label: '甘特图(ECharts)', value: '' },
            { label: '看板',             value: 'board' },
            { label: '详细看板',         value: 'board-hierarchical' },
        ], defaultValue: '' },
        { key: 'sideTextColor', label: '侧边文字色', type: 'color',  group: 'appearance' },
        { key: 'tasks',         label: '任务数据',   type: 'json',   group: 'advanced' },
    ],
};

export const CHART_SCHEMAS: ComponentConfigSchema[] = [
    lineChartSchema,
    barChartSchema,
    pieChartSchema,
    gaugeChartSchema,
    scatterChartSchema,
    radarChartSchema,
    funnelChartSchema,
    mapChartSchema,
    comboChartSchema,
    treemapChartSchema,
    sunburstChartSchema,
    wordcloudChartSchema,
    waterfallChartSchema,
    ganttChartSchema,
];
