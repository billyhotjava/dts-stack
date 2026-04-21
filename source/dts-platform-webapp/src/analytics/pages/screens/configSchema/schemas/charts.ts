import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';
import { ECHARTS_COMMON_FIELDS, AXIS_CHART_FIELDS } from './common';

// ---------------------------------------------------------------------------
// ECharts chart schemas — 14 chart types rendered by EChartsRenderer
// ---------------------------------------------------------------------------

const lineChartSchema: ComponentConfigSchema = {
    type: 'line-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'stackMode', label: '堆叠模式', type: 'select', group: 'chart', options: [
            { label: '关闭', value: 'off' },
            { label: '堆叠', value: 'stack' },
        ], defaultValue: 'off' },
        { key: 'smooth', label: '平滑曲线', type: 'boolean', group: 'chart', defaultValue: true },
        { key: 'showArea', label: '面积填充', type: 'boolean', group: 'chart', defaultValue: true },
        { key: 'lineWidth', label: '线宽', type: 'number', group: 'chart', min: 1, max: 6, step: 0.5, defaultValue: 2.5 },
        { key: 'enableDataZoom', label: '启用缩放滑块', type: 'boolean', group: 'behavior', defaultValue: false },
    ],
};

const barChartSchema: ComponentConfigSchema = {
    type: 'bar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
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
        { key: 'barBorderRadius', label: '柱子圆角', type: 'number', group: 'chart', min: 0, max: 16, step: 1, defaultValue: 6 },
        { key: 'enableDataZoom', label: '启用缩放滑块', type: 'boolean', group: 'behavior', defaultValue: false },
    ],
};

const pieChartSchema: ComponentConfigSchema = {
    type: 'pie-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'roseType', label: '玫瑰模式', type: 'select', group: 'chart', options: [
            { label: '关闭', value: '' },
            { label: '按半径', value: 'radius' },
            { label: '按面积', value: 'area' },
        ], defaultValue: '' },
        { key: 'padAngle', label: '扇形间距', type: 'number', group: 'chart', min: 0, max: 10, step: 0.5, defaultValue: 2 },
        { key: 'pieBorderRadius', label: '扇形圆角', type: 'number', group: 'chart', min: 0, max: 20, step: 1, defaultValue: 6 },
    ],
};

const gaugeChartSchema: ComponentConfigSchema = {
    type: 'gauge-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'min',         label: '最小值',   type: 'number', group: 'chart', defaultValue: 0 },
        { key: 'max',         label: '最大值',   type: 'number', group: 'chart', defaultValue: 100 },
        { key: 'value',       label: '当前值',   type: 'number', group: 'chart' },
    ],
};

const scatterChartSchema: ComponentConfigSchema = {
    type: 'scatter-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'xAxisName', label: 'X 轴名称', type: 'text', group: 'chart' },
        { key: 'yAxisName', label: 'Y 轴名称', type: 'text', group: 'chart' },
        { key: 'enableVisualMap', label: '启用视觉映射', type: 'boolean', group: 'chart', defaultValue: false },
        { key: 'visualMapMin', label: '映射最小值', type: 'number', group: 'chart',
            showIf: (c) => c.enableVisualMap === true },
        { key: 'visualMapMax', label: '映射最大值', type: 'number', group: 'chart',
            showIf: (c) => c.enableVisualMap === true },
        { key: 'enableDataZoom', label: '启用缩放滑块', type: 'boolean', group: 'behavior', defaultValue: false },
    ],
};

const radarChartSchema: ComponentConfigSchema = {
    type: 'radar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'indicator', label: '指标配置', type: 'json', group: 'chart' },
    ],
};

const funnelChartSchema: ComponentConfigSchema = {
    type: 'funnel-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const mapChartSchema: ComponentConfigSchema = {
    type: 'map-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
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
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
    ],
};

const treemapChartSchema: ComponentConfigSchema = {
    type: 'treemap-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const sunburstChartSchema: ComponentConfigSchema = {
    type: 'sunburst-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const wordcloudChartSchema: ComponentConfigSchema = {
    type: 'wordcloud-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
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
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
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

const sankeyChartSchema: ComponentConfigSchema = {
    type: 'sankey-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'nodeAlign', label: '节点对齐', type: 'select', group: 'chart', options: [
            { label: '左对齐', value: 'left' },
            { label: '右对齐', value: 'right' },
            { label: '两端对齐', value: 'justify' },
        ] },
        { key: 'orient', label: '方向', type: 'radio', group: 'chart', options: [
            { label: '水平', value: 'horizontal' },
            { label: '垂直', value: 'vertical' },
        ] },
        { key: 'draggable', label: '拖拽节点', type: 'boolean', group: 'chart' },
    ],
};

const heatmapChartSchema: ComponentConfigSchema = {
    type: 'heatmap-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'visualMapMin', label: '最小值', type: 'number', group: 'chart', min: 0 },
        { key: 'visualMapMax', label: '最大值', type: 'number', group: 'chart' },
        { key: 'visualMapColors', label: '色带颜色', type: 'color-array', group: 'chart' },
    ],
};

const graphChartSchema: ComponentConfigSchema = {
    type: 'graph-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'layout', label: '布局', type: 'select', group: 'chart', options: [
            { label: '力导向', value: 'force' },
            { label: '环形', value: 'circular' },
            { label: '自定义', value: 'none' },
        ] },
        { key: 'roam', label: '缩放平移', type: 'boolean', group: 'chart' },
        { key: 'draggable', label: '拖拽节点', type: 'boolean', group: 'chart' },
        { key: 'repulsion', label: '斥力', type: 'slider', group: 'chart', min: 50, max: 500, step: 10, defaultValue: 200 },
        { key: 'symbolSize', label: '节点大小', type: 'slider', group: 'chart', min: 5, max: 50, defaultValue: 20 },
    ],
};

const candlestickChartSchema: ComponentConfigSchema = {
    type: 'candlestick-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        // 使用 Tailwind 化的红/绿：在浅色与暗色主题下对比度都更稳定；
        // 纯 #ec0000 / #00da3c 在暗色主题上容易糊成一团。
        { key: 'upColor', label: '阳线颜色', type: 'color', group: 'chart', defaultValue: '#ef4444' },
        { key: 'downColor', label: '阴线颜色', type: 'color', group: 'chart', defaultValue: '#10b981' },
        { key: 'showMA', label: '显示均线', type: 'boolean', group: 'chart' },
        { key: 'maPeriods', label: '均线周期', type: 'text', group: 'chart', placeholder: '5,10,20',
            showIf: (config) => !!config.showMA,
        },
    ],
};

const boxplotChartSchema: ComponentConfigSchema = {
    type: 'boxplot-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'orient', label: '方向', type: 'radio', group: 'chart', options: [
            { label: '垂直', value: 'vertical' },
            { label: '水平', value: 'horizontal' },
        ] },
        { key: 'showOutliers', label: '显示异常值', type: 'boolean', group: 'chart' },
    ],
};

const parallelChartSchema: ComponentConfigSchema = {
    type: 'parallel-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'lineOpacity', label: '线条透明度', type: 'slider', group: 'chart', min: 0, max: 1, step: 0.05, defaultValue: 0.5 },
        { key: 'smooth', label: '平滑曲线', type: 'boolean', group: 'chart' },
    ],
};

const calendarChartSchema: ComponentConfigSchema = {
    type: 'calendar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'cellSize', label: '格子大小', type: 'number', group: 'chart', min: 10, max: 30, defaultValue: 16 },
        { key: 'orient', label: '方向', type: 'radio', group: 'chart', options: [
            { label: '水平', value: 'horizontal' },
            { label: '垂直', value: 'vertical' },
        ] },
        { key: 'yearRange', label: '年份范围', type: 'text', group: 'chart', placeholder: '2026' },
        { key: 'visualMapColors', label: '色带颜色', type: 'color-array', group: 'chart' },
    ],
};

const treeChartSchema: ComponentConfigSchema = {
    type: 'tree-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        { key: 'layout', label: '布局', type: 'select', group: 'chart', options: [
            { label: '正交', value: 'orthogonal' },
            { label: '径向', value: 'radial' },
        ] },
        { key: 'orient', label: '方向', type: 'select', group: 'chart', options: [
            { label: '左到右', value: 'LR' },
            { label: '右到左', value: 'RL' },
            { label: '上到下', value: 'TB' },
            { label: '下到上', value: 'BT' },
        ], showIf: (config) => config.layout !== 'radial' },
        { key: 'expandAndCollapse', label: '展开折叠', type: 'boolean', group: 'chart' },
        { key: 'symbolSize', label: '节点大小', type: 'slider', group: 'chart', min: 5, max: 30, defaultValue: 14 },
    ],
};

const themeRiverChartSchema: ComponentConfigSchema = {
    type: 'themeRiver-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
    ],
};

const pictorialBarChartSchema: ComponentConfigSchema = {
    type: 'pictorialBar-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        ...ECHARTS_COMMON_FIELDS,
        ...AXIS_CHART_FIELDS,
        { key: 'symbol', label: '图形', type: 'select', group: 'chart', options: [
            { label: '圆形', value: 'circle' },
            { label: '矩形', value: 'rect' },
            { label: '圆角矩形', value: 'roundRect' },
            { label: '三角形', value: 'triangle' },
            { label: '菱形', value: 'diamond' },
            { label: '箭头', value: 'arrow' },
        ] },
        { key: 'symbolRepeat', label: '重复填充', type: 'boolean', group: 'chart' },
        { key: 'barWidth', label: '柱宽', type: 'slider', group: 'chart', min: 10, max: 60, defaultValue: 30 },
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
    sankeyChartSchema,
    heatmapChartSchema,
    graphChartSchema,
    candlestickChartSchema,
    boxplotChartSchema,
    parallelChartSchema,
    calendarChartSchema,
    treeChartSchema,
    themeRiverChartSchema,
    pictorialBarChartSchema,
];
