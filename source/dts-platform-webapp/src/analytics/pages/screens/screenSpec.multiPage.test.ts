import assert from 'node:assert/strict';
import test from 'node:test';
import { buildScreenPayload, normalizeScreenConfig, validateScreenPayload } from './screenSpec';
import type { ScreenConfig } from './types';

const multiPageConfig: ScreenConfig = {
    id: 'draft',
    name: '多页项目大屏',
    description: '用于验证 pages/carousel 持久化',
    width: 1920,
    height: 1080,
    backgroundColor: '#08121f',
    theme: 'legacy-dark',
    components: [],
    globalVariables: [
        { key: 'majorProjectId', label: '项目', type: 'string', defaultValue: '' },
    ],
    pages: [
        {
            id: 'page-overview',
            name: '总体态势',
            backgroundColor: '#08121f',
            components: [
                {
                    id: 'overview-title',
                    type: 'title',
                    name: '总体态势标题',
                    x: 40,
                    y: 24,
                    width: 400,
                    height: 48,
                    zIndex: 1,
                    locked: false,
                    visible: true,
                    config: {
                        text: '总体态势',
                        fontSize: 36,
                        color: '#d7ecff',
                    },
                },
            ],
        },
        {
            id: 'page-risk',
            name: '风险与变更',
            backgroundColor: '#08121f',
            components: [
                {
                    id: 'risk-title',
                    type: 'title',
                    name: '风险页标题',
                    x: 40,
                    y: 24,
                    width: 400,
                    height: 48,
                    zIndex: 1,
                    locked: false,
                    visible: true,
                    config: {
                        text: '风险与变更',
                        fontSize: 36,
                        color: '#d7ecff',
                    },
                },
            ],
        },
    ],
    carouselConfig: {
        enabled: true,
        intervalSeconds: 15,
        transition: 'fade',
        transitionDuration: 800,
        loop: true,
    },
};

test('buildScreenPayload and normalizeScreenConfig preserve multi-page screen definitions', () => {
    const payload = buildScreenPayload(multiPageConfig);

    assert.ok(Array.isArray(payload.pages));
    assert.equal((payload.pages as Array<unknown>).length, 2);
    assert.deepEqual(payload.carouselConfig, multiPageConfig.carouselConfig);

    const normalized = normalizeScreenConfig({
        id: 'screen-1',
        ...payload,
    });

    assert.equal(normalized.config.pages?.length, 2);
    assert.equal(normalized.config.pages?.[0]?.components[0]?.id, 'overview-title');
    assert.equal(normalized.config.pages?.[1]?.components[0]?.id, 'risk-title');
    assert.equal(normalized.config.carouselConfig?.enabled, true);
    assert.equal(normalized.config.carouselConfig?.intervalSeconds, 15);
});

test('normalizeScreenConfig migrates legacy project operations gantt screen component to board mode', () => {
    const normalized = normalizeScreenConfig({
        id: 'screen-legacy-pmcc',
        name: '项目运营管理大屏',
        width: 1920,
        height: 1080,
        backgroundColor: '#eef5fb',
        pages: [
            {
                id: 'page-execution',
                name: '执行与里程碑',
                components: [
                    {
                        id: 'pmcc-execution-gantt',
                        type: 'gantt-chart',
                        name: '任务甘特图',
                        x: 56,
                        y: 286,
                        width: 878,
                        height: 520,
                        zIndex: 10,
                        visible: true,
                        locked: false,
                        config: {
                            title: '任务甘特图',
                        },
                        dataSource: {
                            type: 'api',
                            apiConfig: {
                                url: '/bi/api/project-cockpit/screen/execution',
                                responsePath: 'ganttTasks',
                            },
                        },
                    },
                ],
            },
        ],
    });

    const component = normalized.config.pages?.[0]?.components[0];
    assert.equal(component?.id, 'pmcc-execution-gantt');
    assert.equal(component?.config.renderMode, 'board');
    assert.equal(component?.config.nameField, 'name');
    assert.equal(component?.config.startField, 'planDate');
    assert.equal(component?.config.endField, 'actualDate');
    assert.equal(component?.config.categoryField, 'majorProjectName');
    assert.equal(component?.config.statusField, 'riskLevel');
});

test('normalizeScreenConfig migrates legacy generated screen component fields to editor schema', () => {
    const normalized = normalizeScreenConfig({
        id: 'screen-legacy-generated',
        name: '自动生成大屏',
        width: 1920,
        height: 1080,
        backgroundColor: '#08121f',
        components: [
            {
                id: 'legacy-date-filter',
                type: 'filter-date-range',
                name: '日期筛选',
                x: 1260,
                y: 28,
                width: 320,
                height: 58,
                zIndex: 1,
                visible: true,
                locked: false,
                config: {
                    label: '统计周期',
                    startVariableKey: 'dateFrom',
                    endVariableKey: 'dateTo',
                },
            },
            {
                id: 'legacy-bar',
                type: 'bar-chart',
                name: '项目状态分布图',
                x: 56,
                y: 328,
                width: 568,
                height: 420,
                zIndex: 2,
                visible: true,
                locked: false,
                config: {
                    categoryKey: '项目编号',
                    orientation: 'horizontal',
                    stack: 'total',
                    colors: ['#8fa7c4', '#2ee6a6'],
                    series: [
                        { name: '总节点数', dataKey: '总节点数', color: '#8fa7c4' },
                        { name: '按时完成', dataKey: '按时完成', color: '#2ee6a6' },
                    ],
                },
            },
            {
                id: 'legacy-table',
                type: 'table',
                name: '任务进度列表',
                x: 56,
                y: 760,
                width: 820,
                height: 260,
                zIndex: 3,
                visible: true,
                locked: false,
                config: {
                    backgroundColor: 'rgba(9,36,90,0.50)',
                    stripeColor: 'rgba(88,180,255,0.08)',
                },
            },
        ],
    });

    const [dateFilter, barChart, table] = normalized.config.components;

    assert.equal(dateFilter.config.startKey, 'dateFrom');
    assert.equal(dateFilter.config.endKey, 'dateTo');
    assert.equal(dateFilter.config.startVariableKey, 'dateFrom');
    assert.equal(dateFilter.config.endVariableKey, 'dateTo');

    assert.equal(barChart.config.xAxisField, '项目编号');
    assert.equal(barChart.config.horizontal, true);
    assert.equal(barChart.config.stackMode, 'stack');
    assert.deepEqual(barChart.config.seriesColors, ['#8fa7c4', '#2ee6a6']);
    assert.deepEqual(barChart.config.series, [
        { name: '总节点数', dataKey: '总节点数', color: '#8fa7c4', field: '总节点数' },
        { name: '按时完成', dataKey: '按时完成', color: '#2ee6a6', field: '按时完成' },
    ]);

    assert.equal(table.config.bodyBackground, 'rgba(9,36,90,0.50)');
    assert.equal(table.config.oddRowBackground, 'rgba(9,36,90,0.50)');
    assert.equal(table.config.evenRowBackground, 'rgba(88,180,255,0.08)');
});

test('normalizeScreenConfig migrates legacy generated number-card fields', () => {
    const normalized = normalizeScreenConfig({
        id: 'screen-legacy-number',
        name: '自动生成指标卡',
        width: 1920,
        height: 1080,
        backgroundColor: '#08121f',
        components: [
            {
                id: 'legacy-number-card',
                type: 'number-card',
                name: '节点完成百分比',
                x: 48,
                y: 96,
                width: 250,
                height: 118,
                zIndex: 1,
                visible: true,
                locked: false,
                config: {
                    title: '节点完成百分比',
                    value: '72.4',
                    unit: '%',
                    fontSize: 36,
                },
            },
        ],
    });

    const component = normalized.config.components[0];

    assert.equal(component.config.suffix, '%');
    assert.equal(component.config.valueFontSize, 36);
});

test('validateScreenPayload accepts open-panel actions with empty body template', () => {
    const payload = buildScreenPayload({
        ...multiPageConfig,
        components: [
            {
                id: 'panel-source-title',
                type: 'title',
                name: '可点击标题',
                x: 40,
                y: 24,
                width: 400,
                height: 48,
                zIndex: 1,
                locked: false,
                visible: true,
                config: { text: '项目详情' },
                actions: [
                    {
                        type: 'open-panel',
                        label: '查看详情',
                        panelTitle: '{{name}}',
                        panelBodyTemplate: '',
                    },
                ],
            },
        ],
        pages: undefined,
        carouselConfig: undefined,
    });

    const validation = validateScreenPayload(payload);

    assert.deepEqual(validation.errors, []);
});

test('screen spec preserves classification and custom theme metadata', () => {
    const payload = buildScreenPayload({
        ...multiPageConfig,
        theme: 'brand-custom',
        customTheme: {
            primaryColor: '#0052cc',
            backgroundColor: '#f8fafc',
            textPrimary: '#0f172a',
        },
        classification: 'SECRET',
    });

    assert.equal(payload.classification, 'SECRET');
    assert.deepEqual(payload.customTheme, {
        primaryColor: '#0052cc',
        backgroundColor: '#f8fafc',
        textPrimary: '#0f172a',
    });

    const normalized = normalizeScreenConfig(payload);
    assert.equal(normalized.config.classification, 'SECRET');
    assert.equal(normalized.config.theme, 'brand-custom');
    assert.equal(normalized.config.customTheme?.primaryColor, '#0052cc');
});
