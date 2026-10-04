import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

// ---------------------------------------------------------------------------
// Enterprise component schemas — stat-card, section-panel, divider
// These are rendered in BasicRenderer.tsx but had NO config panel previously.
// ---------------------------------------------------------------------------

const statCardSchema: ComponentConfigSchema = {
    type: 'stat-card',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance],
    fields: [
        // Content
        { key: 'title',          label: '标题',       type: 'text',    group: 'content', defaultValue: '指标' },
        { key: 'value',          label: '数值',       type: 'text',    group: 'content', defaultValue: '0' },
        { key: 'prefix',         label: '前缀',       type: 'text',    group: 'content' },
        { key: 'suffix',         label: '后缀',       type: 'text',    group: 'content' },
        { key: 'icon',           label: '图标',       type: 'text',    group: 'content' },
        { key: 'trend',          label: '趋势',       type: 'select',  group: 'content', options: [
            { label: '无',   value: 'none' },
            { label: '上升', value: 'up' },
            { label: '下降', value: 'down' },
        ], defaultValue: 'none' },
        { key: 'trendValue',     label: '趋势值',     type: 'text',    group: 'content' },
        // Typography
        { key: 'titleColor',     label: '标题颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textSecondary' },
        { key: 'valueColor',     label: '数值颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textPrimary' },
        // v2 自适应字号（ratio > 0 时启用）
        { key: 'titleFontSizeRatio', label: '标题自适应比例', type: 'number', group: 'typography', min: 0, max: 0.15, step: 0.005, defaultValue: 0 },
        { key: 'titleFontSizeMin',   label: '标题自适应最小值', type: 'number', group: 'typography', min: 8,  max: 24,  defaultValue: 11 },
        { key: 'titleFontSizeMax',   label: '标题自适应最大值', type: 'number', group: 'typography', min: 12, max: 48,  defaultValue: 18 },
        { key: 'valueFontSizeRatio', label: '数值自适应比例', type: 'number', group: 'typography', min: 0, max: 0.3,  step: 0.005, defaultValue: 0 },
        { key: 'valueFontSizeMin',   label: '数值自适应最小值', type: 'number', group: 'typography', min: 10, max: 48,  defaultValue: 16 },
        { key: 'valueFontSizeMax',   label: '数值自适应最大值', type: 'number', group: 'typography', min: 20, max: 160, defaultValue: 64 },
        // Appearance
        { key: 'accentColor',    label: '强调色',     type: 'color',   group: 'appearance', defaultValue: '#3b82f6' },
        { key: 'showAccentBar',  label: '显示强调条', type: 'boolean', group: 'appearance', defaultValue: true },
        { key: 'borderRadius',   label: '圆角',       type: 'number',  group: 'appearance', min: 0, max: 40, defaultValue: 10 },
        { key: 'backgroundColor', label: '背景色',    type: 'color',   group: 'appearance', themeTokenKey: 'cardBackground' },
        { key: 'shadow',         label: '阴影',       type: 'select',  group: 'appearance', options: [
            { label: '无',   value: 'none' },
            { label: '轻微', value: 'subtle' },
            { label: '中等', value: 'medium' },
        ], defaultValue: 'subtle' },
    ],
};

const sectionPanelSchema: ComponentConfigSchema = {
    type: 'section-panel',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance, STANDARD_GROUPS.layout],
    fields: [
        // Content
        { key: 'title',            label: '标题',       type: 'text',    group: 'content' },
        { key: 'titleIcon',        label: '标题图标',   type: 'text',    group: 'content' },
        { key: 'showHeader',       label: '显示表头',   type: 'boolean', group: 'content', defaultValue: true },
        // Typography
        { key: 'titleColor',       label: '标题颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textPrimary' },
        { key: 'titleAlign',       label: '标题对齐',   type: 'select',  group: 'typography', options: [
            { label: '左对齐', value: 'left' },
            { label: '居中',   value: 'center' },
        ], defaultValue: 'left' },
        // Appearance
        { key: 'borderStyle',      label: '边框样式',   type: 'select',  group: 'appearance', options: [
            { label: '实线',   value: 'solid' },
            { label: '渐变',   value: 'gradient' },
            { label: '发光',   value: 'glow' },
        ], defaultValue: 'solid' },
        { key: 'borderWidth',      label: '边框宽度',   type: 'number',  group: 'appearance', min: 0, max: 10, defaultValue: 1 },
        { key: 'borderRadius',     label: '圆角',       type: 'number',  group: 'appearance', min: 0, max: 40, defaultValue: 12 },
        { key: 'borderColor',      label: '边框颜色',   type: 'color-array', group: 'appearance' },
        { key: 'backgroundColor',  label: '背景色',     type: 'color',   group: 'appearance', defaultValue: 'rgba(30, 41, 59, 0.85)' },
        { key: 'headerBackground', label: '表头背景',   type: 'color',   group: 'appearance', defaultValue: 'transparent' },
        { key: 'shadow',           label: '阴影',       type: 'select',  group: 'appearance', options: [
            { label: '无',   value: 'none' },
            { label: '轻微', value: 'subtle' },
            { label: '中等', value: 'medium' },
            { label: '强烈', value: 'strong' },
        ], defaultValue: 'none' },
        { key: 'backdropBlur',     label: '模糊程度',   type: 'number',  group: 'appearance', min: 0, max: 30, defaultValue: 0 },
        // Layout
        { key: 'headerHeight',     label: '表头高度',   type: 'number',  group: 'layout', min: 24, max: 80, defaultValue: 36,
            showIf: (config) => config.showHeader !== false,
        },
        { key: 'padding',          label: '内边距',     type: 'number',  group: 'layout', min: 0, max: 60, defaultValue: 16 },
    ],
};

const dividerSchema: ComponentConfigSchema = {
    type: 'divider',
    groups: [STANDARD_GROUPS.appearance],
    fields: [
        { key: 'direction',      label: '方向',     type: 'radio',       group: 'appearance', options: [
            { label: '水平', value: 'horizontal' },
            { label: '垂直', value: 'vertical' },
        ], defaultValue: 'horizontal' },
        { key: 'lineStyle',      label: '线条样式', type: 'select',      group: 'appearance', options: [
            { label: '实线', value: 'solid' },
            { label: '虚线', value: 'dashed' },
            { label: '点线', value: 'dotted' },
            { label: '渐变', value: 'gradient' },
        ], defaultValue: 'solid' },
        { key: 'lineColor',      label: '线条颜色', type: 'color',       group: 'appearance', defaultValue: 'rgba(148, 163, 184, 0.3)' },
        { key: 'lineWidth',      label: '线条宽度', type: 'number',      group: 'appearance', min: 1, max: 10, defaultValue: 1 },
        { key: 'gradientColors', label: '渐变颜色', type: 'color-array', group: 'appearance',
            showIf: (config) => config.lineStyle === 'gradient',
        },
    ],
};

export const ENTERPRISE_SCHEMAS: ComponentConfigSchema[] = [
    statCardSchema,
    sectionPanelSchema,
    dividerSchema,
];
