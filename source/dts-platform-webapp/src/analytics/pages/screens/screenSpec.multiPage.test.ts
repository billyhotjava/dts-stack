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
