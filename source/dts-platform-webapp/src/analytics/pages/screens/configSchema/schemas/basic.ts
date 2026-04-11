import type { ComponentConfigSchema } from '../types';
import { STANDARD_GROUPS } from '../types';

// ---------------------------------------------------------------------------
// Basic component schemas — 14 components rendered by BasicRenderer
// ---------------------------------------------------------------------------

const titleSchema: ComponentConfigSchema = {
    type: 'title',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography],
    fields: [
        { key: 'text',       label: '文本内容', type: 'text',        group: 'content' },
        { key: 'fontSize',   label: '字号',     type: 'number',      group: 'typography', min: 10, max: 120, defaultValue: 16 },
        { key: 'fontWeight',  label: '字重',     type: 'select',      group: 'typography', options: [
            { label: '正常', value: 'normal' },
            { label: '粗体', value: 'bold' },
            { label: '300', value: '300' },
            { label: '500', value: '500' },
            { label: '600', value: '600' },
            { label: '700', value: '700' },
        ], defaultValue: 'normal' },
        { key: 'color',      label: '颜色',     type: 'color',       group: 'typography', themeTokenKey: 'textPrimary' },
        { key: 'textAlign',  label: '对齐方式', type: 'select',      group: 'typography', options: [
            { label: '左对齐', value: 'flex-start' },
            { label: '居中',   value: 'center' },
            { label: '右对齐', value: 'flex-end' },
        ], defaultValue: 'flex-start' },
    ],
};

const numberCardSchema: ComponentConfigSchema = {
    type: 'number-card',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'title',           label: '标题',       type: 'text',   group: 'content' },
        { key: 'value',           label: '数值',       type: 'number', group: 'content' },
        { key: 'prefix',          label: '前缀',       type: 'text',   group: 'content' },
        { key: 'suffix',          label: '后缀',       type: 'text',   group: 'content' },
        { key: 'titleFontSize',   label: '标题字号',   type: 'number', group: 'typography', min: 10, max: 60, defaultValue: 16 },
        { key: 'titleColor',      label: '标题颜色',   type: 'color',  group: 'typography', themeTokenKey: 'numberCard.titleColor' },
        { key: 'valueFontSize',   label: '数值字号',   type: 'number', group: 'typography', min: 12, max: 120, defaultValue: 34 },
        { key: 'valueColor',      label: '数值颜色',   type: 'color',  group: 'typography', themeTokenKey: 'numberCard.valueColor' },
        { key: 'backgroundColor', label: '背景色',     type: 'color',  group: 'appearance', themeTokenKey: 'numberCard.background' },
    ],
};

const markdownTextSchema: ComponentConfigSchema = {
    type: 'markdown-text',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography],
    fields: [
        { key: 'markdown',   label: 'Markdown 内容', type: 'textarea', group: 'content' },
        { key: 'fontSize',   label: '字号',           type: 'number',   group: 'typography', min: 10, max: 60, defaultValue: 14 },
        { key: 'color',      label: '颜色',           type: 'color',    group: 'typography', themeTokenKey: 'textPrimary' },
        { key: 'lineHeight', label: '行高',           type: 'number',   group: 'typography', min: 1, max: 3, step: 0.1, defaultValue: 1.6 },
    ],
};

const richtextSchema: ComponentConfigSchema = {
    type: 'richtext',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.layout],
    fields: [
        { key: 'content',       label: 'HTML 内容',  type: 'textarea', group: 'content' },
        { key: 'padding',       label: '内边距',      type: 'number',   group: 'layout', min: 0, max: 100, defaultValue: 12 },
        { key: 'overflow',      label: '溢出',        type: 'select',   group: 'layout', options: [
            { label: '隐藏', value: 'hidden' },
            { label: '可见', value: 'visible' },
            { label: '滚动', value: 'scroll' },
        ], defaultValue: 'hidden' },
        { key: 'verticalAlign', label: '垂直对齐',    type: 'select',   group: 'layout', options: [
            { label: '顶部', value: 'top' },
            { label: '居中', value: 'middle' },
            { label: '底部', value: 'bottom' },
        ], defaultValue: 'top' },
    ],
};

const datetimeSchema: ComponentConfigSchema = {
    type: 'datetime',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography],
    fields: [
        { key: 'format',   label: '格式',   type: 'text',   group: 'content', defaultValue: 'YYYY-MM-DD HH:mm:ss', placeholder: 'YYYY-MM-DD HH:mm:ss' },
        { key: 'fontSize', label: '字号',   type: 'number', group: 'typography', min: 10, max: 60 },
        { key: 'color',    label: '颜色',   type: 'color',  group: 'typography', themeTokenKey: 'textPrimary' },
    ],
};

const countdownSchema: ComponentConfigSchema = {
    type: 'countdown',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'title',             label: '标题',           type: 'text',    group: 'content', defaultValue: '倒计时' },
        { key: 'targetTime',        label: '目标时间',       type: 'text',    group: 'content', placeholder: '2026-12-31T00:00:00' },
        { key: 'targetVariableKey', label: '目标时间变量',   type: 'text',    group: 'content' },
        { key: 'showDays',          label: '显示天数',       type: 'boolean', group: 'content', defaultValue: true },
        { key: 'digitFontSize',     label: '数字字号',       type: 'number',  group: 'typography', min: 12, max: 80, defaultValue: 26 },
        { key: 'labelFontSize',     label: '标签字号',       type: 'number',  group: 'typography', min: 10, max: 40, defaultValue: 12 },
        { key: 'accentColor',       label: '高亮颜色',       type: 'color',   group: 'appearance', themeTokenKey: 'accentColor' },
        { key: 'color',             label: '文字颜色',       type: 'color',   group: 'typography', themeTokenKey: 'textSecondary' },
    ],
};

const marqueeSchema: ComponentConfigSchema = {
    type: 'marquee',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'text',            label: '滚动文本',   type: 'text',   group: 'content' },
        { key: 'speed',           label: '滚动速度(s)', type: 'number', group: 'behavior', min: 10, max: 200, defaultValue: 40 },
        { key: 'color',           label: '文字颜色',   type: 'color',  group: 'typography', themeTokenKey: 'textPrimary' },
        { key: 'fontSize',        label: '字号',       type: 'number', group: 'typography', min: 10, max: 60, defaultValue: 14 },
        { key: 'backgroundColor', label: '背景色',     type: 'color',  group: 'appearance' },
    ],
};

const carouselSchema: ComponentConfigSchema = {
    type: 'carousel',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.typography, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'title',           label: '卡片标题',   type: 'text',    group: 'content', defaultValue: '轮播卡片' },
        { key: 'items',           label: '轮播内容',   type: 'json',    group: 'content' },
        { key: 'showDots',        label: '显示指示点', type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'showControls',    label: '显示控制按钮', type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'pauseOnHover',    label: '悬停暂停',   type: 'boolean', group: 'behavior', defaultValue: true },
        { key: 'color',           label: '文字颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textPrimary' },
        { key: 'titleColor',      label: '标题颜色',   type: 'color',   group: 'typography', themeTokenKey: 'textSecondary' },
        { key: 'fontSize',        label: '字号',       type: 'number',  group: 'typography', min: 12, max: 80, defaultValue: 24 },
        { key: 'backgroundColor', label: '背景色',     type: 'color',   group: 'appearance', themeTokenKey: 'cardBackground' },
        { key: 'borderRadius',    label: '圆角',       type: 'number',  group: 'appearance', min: 0, max: 40, defaultValue: 10 },
    ],
};

const tabSwitcherSchema: ComponentConfigSchema = {
    type: 'tab-switcher',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'label',                   label: '标签文字',     type: 'text',    group: 'content', defaultValue: '切换' },
        { key: 'variableKey',             label: '绑定变量',     type: 'text',    group: 'behavior' },
        { key: 'options',                 label: 'Tab 选项',    type: 'json',    group: 'content' },
        { key: 'defaultValue',            label: '默认值',       type: 'text',    group: 'content' },
        { key: 'compact',                 label: '紧凑模式',     type: 'boolean', group: 'appearance', defaultValue: false },
        { key: 'activeTextColor',         label: '选中文字色',   type: 'color',   group: 'appearance', defaultValue: '#0f172a' },
        { key: 'activeBackgroundColor',   label: '选中背景色',   type: 'color',   group: 'appearance', defaultValue: '#38bdf8' },
        { key: 'inactiveTextColor',       label: '未选中文字色', type: 'color',   group: 'appearance', themeTokenKey: 'textSecondary' },
        { key: 'inactiveBackgroundColor', label: '未选中背景色', type: 'color',   group: 'appearance' },
    ],
};

const progressBarSchema: ComponentConfigSchema = {
    type: 'progress-bar',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'value',       label: '进度值(%)', type: 'slider',  group: 'content', min: 0, max: 100, defaultValue: 50 },
        { key: 'showLabel',   label: '显示标签',   type: 'boolean', group: 'content', defaultValue: false },
        { key: 'trackHeight', label: '轨道高度',   type: 'number',  group: 'appearance', min: 4, max: 40, defaultValue: 12 },
    ],
};

const shapeSchema: ComponentConfigSchema = {
    type: 'shape',
    groups: [STANDARD_GROUPS.appearance],
    fields: [
        { key: 'shapeType',   label: '形状类型', type: 'select', group: 'appearance', options: [
            { label: '矩形',   value: 'rect' },
            { label: '圆形',   value: 'circle' },
            { label: '线条',   value: 'line' },
            { label: '箭头',   value: 'arrow' },
        ], defaultValue: 'rect' },
        { key: 'fillColor',   label: '填充色',   type: 'color',  group: 'appearance', defaultValue: 'rgba(59,130,246,0.2)' },
        { key: 'borderColor', label: '边框颜色', type: 'color',  group: 'appearance', defaultValue: '#60a5fa' },
        { key: 'borderWidth', label: '边框宽度', type: 'number', group: 'appearance', min: 0, max: 20, defaultValue: 2 },
        { key: 'radius',      label: '圆角',     type: 'number', group: 'appearance', min: 0, max: 100, defaultValue: 8,
            showIf: (config) => config.shapeType !== 'line' && config.shapeType !== 'arrow' && config.shapeType !== 'circle',
        },
    ],
};

const containerSchema: ComponentConfigSchema = {
    type: 'container',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'title',           label: '标题',     type: 'text',   group: 'content', defaultValue: '容器' },
        { key: 'padding',         label: '内边距',   type: 'number', group: 'appearance', min: 0, max: 60, defaultValue: 12 },
        { key: 'borderWidth',     label: '边框宽度', type: 'number', group: 'appearance', min: 0, max: 10, defaultValue: 1 },
        { key: 'borderColor',     label: '边框颜色', type: 'color',  group: 'appearance', themeTokenKey: 'cardBorder' },
        { key: 'radius',          label: '圆角',     type: 'number', group: 'appearance', min: 0, max: 40, defaultValue: 10 },
        { key: 'backgroundColor', label: '背景色',   type: 'color',  group: 'appearance', themeTokenKey: 'cardBackground' },
        { key: 'titleColor',      label: '标题颜色', type: 'color',  group: 'appearance', themeTokenKey: 'textPrimary' },
    ],
};

const imageSchema: ComponentConfigSchema = {
    type: 'image',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.appearance],
    fields: [
        { key: 'src', label: '图片地址', type: 'image-url', group: 'content' },
        { key: 'fit', label: '缩放方式', type: 'select',    group: 'appearance', options: [
            { label: '覆盖', value: 'cover' },
            { label: '包含', value: 'contain' },
            { label: '拉伸', value: 'fill' },
        ], defaultValue: 'cover' },
    ],
};

const videoSchema: ComponentConfigSchema = {
    type: 'video',
    groups: [STANDARD_GROUPS.content, STANDARD_GROUPS.behavior],
    fields: [
        { key: 'src',      label: '视频地址', type: 'text',    group: 'content' },
        { key: 'autoplay', label: '自动播放', type: 'boolean', group: 'behavior', defaultValue: false },
        { key: 'loop',     label: '循环播放', type: 'boolean', group: 'behavior', defaultValue: false },
        { key: 'muted',    label: '静音',     type: 'boolean', group: 'behavior', defaultValue: false },
    ],
};

const iframeSchema: ComponentConfigSchema = {
    type: 'iframe',
    groups: [STANDARD_GROUPS.content],
    fields: [
        { key: 'src', label: '嵌入地址', type: 'text', group: 'content' },
    ],
};

export const BASIC_SCHEMAS: ComponentConfigSchema[] = [
    titleSchema,
    numberCardSchema,
    markdownTextSchema,
    richtextSchema,
    datetimeSchema,
    countdownSchema,
    marqueeSchema,
    carouselSchema,
    tabSwitcherSchema,
    progressBarSchema,
    shapeSchema,
    containerSchema,
    imageSchema,
    videoSchema,
    iframeSchema,
];
