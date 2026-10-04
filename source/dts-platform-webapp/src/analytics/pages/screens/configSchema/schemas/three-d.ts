import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';
// Note: 3D charts use a subset of ECHARTS_COMMON_FIELDS — fields are listed explicitly
// because globe/bar3d/scatter3d skip legend, tooltip, and other 2D-only options.

// ---------------------------------------------------------------------------
// 3D chart schemas — globe-chart, bar3d-chart, scatter3d-chart
// Rendered in ComponentRenderer.tsx via ECharts GL
// ---------------------------------------------------------------------------

const globeChartSchema: ComponentConfigSchema = {
    type: 'globe-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        // Only title-related fields from ECHARTS_COMMON, not axis/legend
        { key: 'title',          label: '标题',       type: 'text',    group: 'chart' },
        { key: 'titleColor',     label: '标题颜色',   type: 'color',   group: 'chart', themeTokenKey: 'textPrimary' },
        { key: 'titleFontSize',  label: '标题字号',   type: 'number',  group: 'chart', min: 10, max: 48, defaultValue: 16 },
        // Globe-specific
        { key: 'autoRotate',      label: '自动旋转',   type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'rotateSpeed',     label: '旋转速度',   type: 'number',  group: 'behavior', min: 1, max: 50, defaultValue: 10 },
        { key: 'viewDistance',    label: '视距',       type: 'number',  group: 'behavior', min: 50, max: 500, defaultValue: 200 },
        { key: 'showAtmosphere',  label: '显示大气层', type: 'boolean', group: 'appearance', defaultValue: true },
        { key: 'globeBackground', label: '背景色',     type: 'color',   group: 'appearance', defaultValue: '#000' },
        { key: 'baseTexture',     label: '底纹贴图',   type: 'text',    group: 'appearance' },
        { key: 'heightTexture',   label: '高度贴图',   type: 'text',    group: 'appearance' },
        { key: 'pointSize',       label: '散点大小',   type: 'number',  group: 'chart', min: 2, max: 40, defaultValue: 12 },
        // Data
        { key: 'scatterData',    label: '散点数据',   type: 'json',    group: 'advanced' },
        { key: 'flowData',       label: '飞线数据',   type: 'json',    group: 'advanced' },
    ],
};

const bar3dChartSchema: ComponentConfigSchema = {
    type: 'bar3d-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        { key: 'title',         label: '标题',       type: 'text',    group: 'chart' },
        { key: 'titleColor',    label: '标题颜色',   type: 'color',   group: 'chart', themeTokenKey: 'textPrimary' },
        { key: 'titleFontSize', label: '标题字号',   type: 'number',  group: 'chart', min: 10, max: 48, defaultValue: 16 },
        // 3D-specific
        { key: 'xAxisData',    label: 'X 轴数据',   type: 'json',    group: 'chart' },
        { key: 'yAxisData',    label: 'Y 轴数据',   type: 'json',    group: 'chart' },
        { key: 'data',         label: '数据',       type: 'json',    group: 'chart' },
        { key: 'colorRange',   label: '色彩范围',   type: 'json',    group: 'appearance', defaultValue: ['#313695', '#a50026'] },
        { key: 'viewAlpha',    label: '视角 Alpha', type: 'number',  group: 'behavior', min: 0, max: 90, defaultValue: 40 },
        { key: 'viewBeta',     label: '视角 Beta',  type: 'number',  group: 'behavior', min: 0, max: 360, defaultValue: 30 },
        { key: 'autoRotate',   label: '自动旋转',   type: 'boolean', group: 'behavior', defaultValue: false },
        { key: 'showLabel',    label: '显示标签',   type: 'boolean', group: 'appearance', defaultValue: false },
        { key: 'boxWidth',     label: '盒宽',       type: 'number',  group: 'appearance', min: 20, max: 300, defaultValue: 100 },
        { key: 'boxDepth',     label: '盒深',       type: 'number',  group: 'appearance', min: 20, max: 300, defaultValue: 80 },
        { key: 'boxHeight',    label: '盒高',       type: 'number',  group: 'appearance', min: 20, max: 200, defaultValue: 60 },
    ],
};

const scatter3dChartSchema: ComponentConfigSchema = {
    type: 'scatter3d-chart',
    groups: [STANDARD_GROUPS.chart, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior, STANDARD_GROUPS.advanced],
    fields: [
        { key: 'title',         label: '标题',       type: 'text',    group: 'chart' },
        { key: 'titleColor',    label: '标题颜色',   type: 'color',   group: 'chart', themeTokenKey: 'textPrimary' },
        { key: 'titleFontSize', label: '标题字号',   type: 'number',  group: 'chart', min: 10, max: 48, defaultValue: 16 },
        // 3D-specific
        { key: 'data',         label: '数据',       type: 'json',    group: 'chart' },
        { key: 'xAxisName',    label: 'X 轴名称',   type: 'text',    group: 'chart', defaultValue: 'X' },
        { key: 'yAxisName',    label: 'Y 轴名称',   type: 'text',    group: 'chart', defaultValue: 'Y' },
        { key: 'zAxisName',    label: 'Z 轴名称',   type: 'text',    group: 'chart', defaultValue: 'Z' },
        { key: 'colorRange',   label: '色彩范围',   type: 'json',    group: 'appearance', defaultValue: ['#50a3ba', '#eac736'] },
        { key: 'pointSize',    label: '散点大小',   type: 'number',  group: 'appearance', min: 2, max: 40, defaultValue: 8 },
        { key: 'viewAlpha',    label: '视角 Alpha', type: 'number',  group: 'behavior', min: 0, max: 90, defaultValue: 40 },
        { key: 'viewBeta',     label: '视角 Beta',  type: 'number',  group: 'behavior', min: 0, max: 360, defaultValue: 30 },
        { key: 'autoRotate',   label: '自动旋转',   type: 'boolean', group: 'behavior', defaultValue: false },
        { key: 'showLabel',    label: '显示标签',   type: 'boolean', group: 'appearance', defaultValue: false },
    ],
};

export const THREE_D_SCHEMAS: ComponentConfigSchema[] = [
    globeChartSchema,
    bar3dChartSchema,
    scatter3dChartSchema,
];
