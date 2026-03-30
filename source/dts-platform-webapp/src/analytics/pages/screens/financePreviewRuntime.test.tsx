import assert from 'node:assert/strict';
import test from 'node:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { getTemplateById } from './screenTemplates';
import { readComponentPluginMeta } from './plugins/runtime';
import createHeaderBarPlugin from './plugins/custom/finance-kit__header-bar';
import createKpiCardPlugin from './plugins/custom/finance-kit__kpi-card';
import createSummaryTablePlugin from './plugins/custom/finance-kit__summary-table';
import createStatusGridPlugin from './plugins/custom/finance-kit__status-grid';
import type { RendererPluginRenderContext } from './plugins/types';
import type { ScreenComponent } from './types';

function buildRenderContext(component: ScreenComponent): RendererPluginRenderContext {
    return {
        component,
        mode: 'preview',
        theme: 'light-business',
        width: component.width,
        height: component.height,
        config: component.config,
        data: null,
        runtimeValues: {},
        setVariable: () => {},
    };
}

test('finance preview/runtime renderers tolerate template components with default config', () => {
    const templates = [
        getTemplateById('fin-auxiliary-balance'),
        getTemplateById('fin-project-fund'),
    ].filter(Boolean);

    assert.equal(templates.length, 2);

    const plugins = {
        'header-bar': createHeaderBarPlugin(),
        'kpi-card': createKpiCardPlugin(),
        'summary-table': createSummaryTablePlugin(),
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
            const html = renderToStaticMarkup(<>{plugin.render(buildRenderContext(component))}</>);
            assert.ok(html.length > 0, `expected renderer output for ${template!.id}:${component.id}`);
        }
    }
});
