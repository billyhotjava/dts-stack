import type { ConfigField, ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

// ---------------------------------------------------------------------------
// Filter component schemas — rendered by FilterRenderer
// ---------------------------------------------------------------------------

/** Shared fields across all three filter component types. */
const FILTER_SHARED_FIELDS: ConfigField[] = [
    { key: 'label',       label: '标签文字', type: 'text', group: 'content', defaultValue: '筛选' },
    { key: 'variableKey', label: '绑定变量', type: 'text', group: 'behavior' },
    { key: 'placeholder', label: '占位文字', type: 'text', group: 'content', defaultValue: '请输入' },
    { key: 'scopeHint',   label: '作用域提示', type: 'text', group: 'content' },
];

/** Shared style fields across all three filter component types. */
const FILTER_STYLE_FIELDS: ConfigField[] = [
    { key: 'labelColor',      label: '标签颜色',     type: 'color', group: 'appearance', themeTokenKey: 'textSecondary' },
    { key: 'inputTextColor',  label: '输入文字颜色', type: 'color', group: 'appearance', themeTokenKey: 'textPrimary' },
    { key: 'inputBorderColor', label: '输入边框色',  type: 'color', group: 'appearance' },
    { key: 'inputBackground', label: '输入背景色',   type: 'color', group: 'appearance' },
];

const filterInputSchema: ComponentConfigSchema = {
    type: 'filter-input',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        ...FILTER_SHARED_FIELDS,
        { key: 'debounceMs', label: '防抖延迟(ms)', type: 'number', group: 'behavior', min: 0, max: 5000, step: 100 },
        ...FILTER_STYLE_FIELDS,
    ],
};

const filterSelectSchema: ComponentConfigSchema = {
    type: 'filter-select',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        ...FILTER_SHARED_FIELDS,
        { key: 'options',  label: '选项列表', type: 'json',    group: 'content' },
        { key: 'optionSourceMode', label: '选项来源', type: 'select', group: 'content', options: [
            { label: '手工配置', value: 'manual' },
            { label: '数据字段', value: 'data' },
        ], defaultValue: 'manual' },
        { key: 'dataOptionLabelField', label: '选项标签字段', type: 'text', group: 'content',
            showIf: (config) => config.optionSourceMode === 'data',
        },
        { key: 'dataOptionValueField', label: '选项值字段', type: 'text', group: 'content',
            showIf: (config) => config.optionSourceMode === 'data',
        },
        { key: 'dataOptionMax', label: '最大选项数', type: 'number', group: 'content', min: 1, max: 1000, defaultValue: 200,
            showIf: (config) => config.optionSourceMode === 'data',
        },
        { key: 'multiple', label: '多选',     type: 'boolean', group: 'behavior', defaultValue: false },
        ...FILTER_STYLE_FIELDS,
        { key: 'optionBackground', label: '下拉背景色', type: 'color', group: 'appearance' },
        { key: 'optionTextColor', label: '下拉文字色', type: 'color', group: 'appearance' },
    ],
};

const filterDateRangeSchema: ComponentConfigSchema = {
    type: 'filter-date-range',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'label',       label: '标签文字',   type: 'text', group: 'content', defaultValue: '日期区间' },
        { key: 'scopeHint',   label: '作用域提示', type: 'text', group: 'content' },
        { key: 'startKey', label: '开始日期变量', type: 'text', group: 'behavior' },
        { key: 'endKey',   label: '结束日期变量', type: 'text', group: 'behavior' },
        { key: 'format',      label: '日期格式',   type: 'text',    group: 'content', defaultValue: 'YYYY-MM-DD' },
        { key: 'showTime',    label: '显示时间',   type: 'boolean', group: 'content', defaultValue: false },
        ...FILTER_STYLE_FIELDS,
    ],
};

export const FILTER_SCHEMAS: ComponentConfigSchema[] = [
    filterInputSchema,
    filterSelectSchema,
    filterDateRangeSchema,
];
