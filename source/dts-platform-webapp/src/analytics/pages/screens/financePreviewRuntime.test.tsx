import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { getTemplateById } from './screenTemplates';
import { readComponentPluginMeta } from './plugins/runtime';
import createHeaderBarPlugin from './plugins/custom/finance-kit__header-bar';
import createFilterStripPlugin from './plugins/custom/finance-kit__filter-strip';
import createKpiCardPlugin from './plugins/custom/finance-kit__kpi-card';
import createRankingListPlugin from './plugins/custom/finance-kit__ranking-list';
import createSummaryTablePlugin from './plugins/custom/finance-kit__summary-table';
import createNotePanelPlugin from './plugins/custom/finance-kit__note-panel';
import createStatusGridPlugin from './plugins/custom/finance-kit__status-grid';
import type { RendererPluginRenderContext } from './plugins/types';
import type { CardData, ScreenComponent } from './types';

function buildRenderContext(component: ScreenComponent, data: CardData | null = null): RendererPluginRenderContext {
    return {
        component,
        mode: 'preview',
        theme: 'light-business',
        width: component.width,
        height: component.height,
        config: component.config,
        data,
        runtimeValues: {},
        setVariable: () => {},
    };
}

test('finance preview/runtime renderers tolerate template components with default config', () => {
    const templates = [
        getTemplateById('fin-auxiliary-balance'),
        getTemplateById('fin-own-fund'),
        getTemplateById('fin-personal-balance'),
        getTemplateById('fin-project-fund'),
    ].filter(Boolean);

    assert.equal(templates.length, 4);

    const plugins = {
        'header-bar': createHeaderBarPlugin(),
        'filter-strip': createFilterStripPlugin(),
        'kpi-card': createKpiCardPlugin(),
        'ranking-list': createRankingListPlugin(),
        'summary-table': createSummaryTablePlugin(),
        'note-panel': createNotePanelPlugin(),
        'status-grid': createStatusGridPlugin(),
    };

    for (const template of templates) {
        const targetComponents = template!.config.components.filter((component) => {
            const meta = readComponentPluginMeta(component.config);
            return meta?.pluginId === 'finance-kit' && meta.componentId in plugins;
        });

        assert.ok(targetComponents.length > 0, `expected finance plugin components in ${template!.id}`);

        for (const component of targetComponents) {
            const meta = readComponentPluginMeta(component.config);
            assert.ok(meta);
            const plugin = plugins[meta!.componentId as keyof typeof plugins];
            const html = renderToStaticMarkup(<React.Fragment>{plugin.render(buildRenderContext(component))}</React.Fragment>);
            assert.ok(html.length > 0, `expected renderer output for ${template!.id}:${component.id}`);
        }
    }
});

test('finance plugin renderers consume runtime SQL results for value, ranking, table, and status blocks', () => {
    const kpiPlugin = createKpiCardPlugin();
    const rankingPlugin = createRankingListPlugin();
    const summaryPlugin = createSummaryTablePlugin();
    const statusPlugin = createStatusGridPlugin();

    const kpiComponent: ScreenComponent = {
        id: 'kpi-runtime',
        type: 'number-card',
        name: '金额指标',
        x: 0,
        y: 0,
        width: 320,
        height: 120,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '余额合计',
            unit: '元',
            precision: 2,
            value: 2218800,
        },
    };
    const rankingComponent: ScreenComponent = {
        id: 'ranking-runtime',
        type: 'table',
        name: '排行',
        x: 0,
        y: 0,
        width: 320,
        height: 200,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '合同TOP',
            nameField: 'name',
            valueField: 'value',
            extraField: 'extra',
        },
    };
    const summaryComponent: ScreenComponent = {
        id: 'summary-runtime',
        type: 'table',
        name: '汇总表',
        x: 0,
        y: 0,
        width: 640,
        height: 240,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '项目明细',
            headers: ['项目', '余额'],
            maxRows: 2,
        },
    };
    const statusComponent: ScreenComponent = {
        id: 'status-runtime',
        type: 'container',
        name: '状态卡',
        x: 0,
        y: 0,
        width: 400,
        height: 240,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '状态总览',
            titleField: 'title',
            valueField: 'value',
            hintField: 'hint',
            toneField: 'tone',
        },
    };

    const rankingData: CardData = {
        cols: [
            { name: 'name', display_name: '名称', base_type: 'type/Text' },
            { name: 'value', display_name: '金额', base_type: 'type/Text' },
            { name: 'extra', display_name: '附加信息', base_type: 'type/Text' },
        ],
        rows: [['中兴-元器件采购合同2026', '432,000.00', '物资采购科']],
    };
    const summaryData: CardData = {
        cols: [
            { name: 'project_name', display_name: '项目', base_type: 'type/Text' },
            { name: 'balance', display_name: '余额', base_type: 'type/Text' },
        ],
        rows: [['XM-2026-B01', '3,765.60']],
    };
    const statusData: CardData = {
        cols: [
            { name: 'title', display_name: '标题', base_type: 'type/Text' },
            { name: 'value', display_name: '值', base_type: 'type/Text' },
            { name: 'hint', display_name: '说明', base_type: 'type/Text' },
            { name: 'tone', display_name: '语义', base_type: 'type/Text' },
        ],
        rows: [['超支项目', '0 个', '当前无负余额项目', 'success']],
    };

    const kpiHtml = renderToStaticMarkup(<React.Fragment>{kpiPlugin.render(buildRenderContext(kpiComponent))}</React.Fragment>);
    const rankingHtml = renderToStaticMarkup(<React.Fragment>{rankingPlugin.render(buildRenderContext(rankingComponent, rankingData))}</React.Fragment>);
    const summaryHtml = renderToStaticMarkup(<React.Fragment>{summaryPlugin.render(buildRenderContext(summaryComponent, summaryData))}</React.Fragment>);
    const statusHtml = renderToStaticMarkup(<React.Fragment>{statusPlugin.render(buildRenderContext(statusComponent, statusData))}</React.Fragment>);

    assert.match(kpiHtml, /2,218,800\.00/);
    assert.match(rankingHtml, /中兴-元器件采购合同2026/);
    assert.match(summaryHtml, /XM-2026-B01/);
    assert.match(statusHtml, /超支项目/);
    assert.match(statusHtml, /当前无负余额项目/);
});

test('finance kpi-card honors editable number-card prefix and suffix fields', () => {
    const kpiPlugin = createKpiCardPlugin();
    const component: ScreenComponent = {
        id: 'kpi-prefix-suffix',
        type: 'number-card',
        name: '金额指标',
        x: 0,
        y: 0,
        width: 320,
        height: 120,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '预算余额',
            value: 1280.5,
            precision: 1,
            prefix: '¥',
            suffix: '万',
            unit: '元',
            hint: '可编辑前后缀',
        },
    };

    const html = renderToStaticMarkup(<React.Fragment>{kpiPlugin.render(buildRenderContext(component))}</React.Fragment>);

    assert.match(html, /预算余额/);
    assert.match(html, /¥/);
    assert.match(html, /1,280\.5/);
    assert.match(html, /万/);
    assert.doesNotMatch(html, />元</);
});

test('finance summary-table honors manual headers and title font size overrides', () => {
    const summaryPlugin = createSummaryTablePlugin();
    const component: ScreenComponent = {
        id: 'summary-manual-header',
        type: 'table',
        name: '汇总表',
        x: 0,
        y: 0,
        width: 640,
        height: 240,
        zIndex: 1,
        locked: false,
        visible: true,
        config: {
            title: '财务明细',
            titleFontSize: 22,
            headerSourceMode: 'manual',
            headers: ['旧表头'],
            header: ['新表头'],
            data: [['示例值']],
            headerBackground: '#ddeeff',
            headerColor: '#123456',
            headerFontSize: 18,
        },
    };

    const html = renderToStaticMarkup(<React.Fragment>{summaryPlugin.render(buildRenderContext(component))}</React.Fragment>);

    assert.match(html, /新表头/);
    assert.doesNotMatch(html, /旧表头/);
    assert.match(html, /font-size:22px/);
    assert.match(html, /font-size:18px/);
    assert.match(html, /#ddeeff/i);
});
