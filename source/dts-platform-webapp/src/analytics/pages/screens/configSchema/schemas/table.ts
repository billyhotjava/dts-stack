import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

// ---------------------------------------------------------------------------
// Table component schema — rendered by TableRenderer
// ---------------------------------------------------------------------------

const tableSchema: ComponentConfigSchema = {
    type: 'table',
    groups: [
        STANDARD_GROUPS.header,
        STANDARD_GROUPS.body,
        STANDARD_GROUPS.column,
        STANDARD_GROUPS.pagination,
        STANDARD_GROUPS.appearance,
        STANDARD_GROUPS.behavior,
        STANDARD_GROUPS.advanced,
    ],
    fields: [
        // Header group
        { key: 'headerBackground', label: '表头背景',   type: 'color',  group: 'header', defaultValue: 'rgba(148, 163, 184, 0.16)' },
        { key: 'headerColor',      label: '表头文字色', type: 'color',  group: 'header', themeTokenKey: 'textPrimary' },
        { key: 'headerFontSize',   label: '表头字号',   type: 'number', group: 'header', min: 10, max: 30, defaultValue: 16 },
        // Body group
        { key: 'bodyColor',          label: '数据文字色',   type: 'color',  group: 'body', themeTokenKey: 'textSecondary' },
        { key: 'bodyBackground',     label: '数据行背景',   type: 'color',  group: 'body', defaultValue: 'transparent' },
        { key: 'oddRowBackground',   label: '奇数行背景',   type: 'color',  group: 'body', defaultValue: 'transparent' },
        { key: 'evenRowBackground',  label: '偶数行背景',   type: 'color',  group: 'body', defaultValue: 'rgba(148, 163, 184, 0.06)' },
        { key: 'fontSize',           label: '数据字号',     type: 'number', group: 'body', min: 10, max: 30, defaultValue: 16 },
        // Column group
        { key: 'columns',           label: '列配置',       type: 'column-style', group: 'column' },
        { key: 'conditionalRules',  label: '条件样式规则', type: 'json',         group: 'column' },
        // Pagination group
        { key: 'enablePagination', label: '启用分页',     type: 'boolean', group: 'pagination', defaultValue: false },
        { key: 'pageSize',        label: '每页行数',     type: 'number',  group: 'pagination', min: 1, max: 100, defaultValue: 10,
            showIf: (config) => config.enablePagination === true,
        },
        // Appearance group
        { key: 'borderColor',     label: '边框颜色',     type: 'color',   group: 'appearance', defaultValue: 'rgba(148, 163, 184, 0.24)' },
        // Behavior group
        { key: 'enableSort',         label: '启用排序',     type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'freezeHeader',       label: '冻结表头',     type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'freezeFirstColumn',  label: '冻结首列',     type: 'boolean', group: 'behavior', defaultValue: false },
        // Advanced group
        { key: 'renderMode',     label: '渲染模式',     type: 'select',  group: 'advanced', options: [
            { label: '标准表格',     value: '' },
            { label: '归因矩阵',     value: 'delay-reason-matrix' },
        ], defaultValue: '' },
    ],
};

export const TABLE_SCHEMAS: ComponentConfigSchema[] = [
    tableSchema,
];
