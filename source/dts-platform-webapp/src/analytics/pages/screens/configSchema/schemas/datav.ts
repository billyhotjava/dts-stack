import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

// ---------------------------------------------------------------------------
// DataV component schemas — rendered by DataVRenderer, TableRenderer, or
// ComponentRenderer (water-level, digital-flop, percent-pond)
// ---------------------------------------------------------------------------

const borderBoxSchema: ComponentConfigSchema = {
    type: 'border-box',
    groups: [STANDARD_GROUPS.appearance],
    fields: [
        { key: 'boxType', label: '边框样式', type: 'select', group: 'appearance', options: [
            { label: '样式 1', value: 1 },
            { label: '样式 2', value: 2 },
            { label: '样式 3', value: 3 },
            { label: '样式 4', value: 4 },
            { label: '样式 5', value: 5 },
            { label: '样式 6', value: 6 },
            { label: '样式 7', value: 7 },
            { label: '样式 8', value: 8 },
        ], defaultValue: 1 },
        { key: 'color', label: '颜色', type: 'color-array', group: 'appearance' },
    ],
};

const decorationSchema: ComponentConfigSchema = {
    type: 'decoration',
    groups: [STANDARD_GROUPS.appearance],
    fields: [
        { key: 'decorationType', label: '装饰样式', type: 'select', group: 'appearance', options: [
            { label: '样式 1',  value: 1 },
            { label: '样式 2',  value: 2 },
            { label: '样式 3',  value: 3 },
            { label: '样式 4',  value: 4 },
            { label: '样式 5',  value: 5 },
            { label: '样式 6',  value: 6 },
            { label: '样式 8',  value: 8 },
            { label: '样式 10', value: 10 },
        ], defaultValue: 1 },
        { key: 'color', label: '颜色', type: 'color-array', group: 'appearance' },
    ],
};

const scrollBoardSchema: ComponentConfigSchema = {
    type: 'scroll-board',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.header, STANDARD_GROUPS.body, STANDARD_GROUPS.behavior],
    fields: [
        // Content / data
        { key: 'header',    label: '表头数据',   type: 'json',    group: 'content' },
        { key: 'data',      label: '表体数据',   type: 'json',    group: 'content' },
        // Header
        { key: 'headerBGC', label: '表头背景色', type: 'color',   group: 'header', themeTokenKey: 'scrollBoard.headerBGC' },
        // Body
        { key: 'oddRowBGC',  label: '奇数行背景', type: 'color',  group: 'body', themeTokenKey: 'scrollBoard.oddRowBGC' },
        { key: 'evenRowBGC', label: '偶数行背景', type: 'color',  group: 'body', themeTokenKey: 'scrollBoard.evenRowBGC' },
        // Behavior
        { key: 'rowNum',   label: '显示行数',     type: 'number',  group: 'behavior', min: 1, max: 20, defaultValue: 5 },
        { key: 'waitTime', label: '轮播等待(ms)', type: 'number',  group: 'behavior', min: 500, max: 10000, step: 500, defaultValue: 2000 },
    ],
};

const scrollRankingSchema: ComponentConfigSchema = {
    type: 'scroll-ranking',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'data',     label: '排名数据',     type: 'json',   group: 'content' },
        { key: 'rowNum',   label: '显示行数',     type: 'number', group: 'behavior', min: 1, max: 20, defaultValue: 5 },
        { key: 'waitTime', label: '轮播等待(ms)', type: 'number', group: 'behavior', min: 500, max: 10000, step: 500, defaultValue: 2000 },
    ],
};

const waterLevelSchema: ComponentConfigSchema = {
    type: 'water-level',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'value', label: '水位值',   type: 'number', group: 'content', min: 0, max: 100 },
        { key: 'shape', label: '形状',     type: 'select', group: 'appearance', options: [
            { label: '圆形',     value: 'round' },
            { label: '矩形',     value: 'rect' },
            { label: '圆角矩形', value: 'roundRect' },
        ], defaultValue: 'round' },
    ],
};

const digitalFlopSchema: ComponentConfigSchema = {
    type: 'digital-flop',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography],
    fields: [
        { key: 'number',       label: '数值',   type: 'json',   group: 'content' },
        { key: 'content',      label: '模板',   type: 'text',   group: 'content', placeholder: '{nt}个' },
        { key: 'style.fontSize', label: '字号', type: 'number', group: 'typography', min: 10, max: 80 },
        { key: 'style.fill',    label: '颜色',  type: 'color',  group: 'typography' },
    ],
};

const percentPondSchema: ComponentConfigSchema = {
    type: 'percent-pond',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'value',        label: '百分比值',  type: 'slider',      group: 'content', min: 0, max: 100 },
        { key: 'colors',       label: '颜色',      type: 'color-array', group: 'appearance', themeTokenKey: 'progressBar.fillColor' },
        { key: 'borderRadius', label: '圆角',      type: 'number',      group: 'appearance', min: 0, max: 20, defaultValue: 5 },
        { key: 'borderWidth',  label: '边框宽度',  type: 'number',      group: 'appearance', min: 0, max: 10, defaultValue: 2 },
    ],
};

const flylineChartSchema: ComponentConfigSchema = {
    type: 'flyline-chart',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'points', label: '坐标点', type: 'json',  group: 'content' },
        { key: 'lines',  label: '飞线数据', type: 'json', group: 'content' },
        { key: 'color',  label: '颜色',   type: 'color', group: 'appearance' },
    ],
};

export const DATAV_SCHEMAS: ComponentConfigSchema[] = [
    borderBoxSchema,
    decorationSchema,
    scrollBoardSchema,
    scrollRankingSchema,
    waterLevelSchema,
    digitalFlopSchema,
    percentPondSchema,
    flylineChartSchema,
];
