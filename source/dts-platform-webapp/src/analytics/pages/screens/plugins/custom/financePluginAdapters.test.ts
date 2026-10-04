import assert from 'node:assert/strict';
import test from 'node:test';
import { ensureBuiltinPluginAdapterFromModules, installBuiltinPluginAdaptersFromModules } from '../builtinPluginAdapters';
import { buildPluginRuntimeId, getRendererPlugin, unregisterRendererPlugin } from '../registry';
import * as shellModule from './finance-kit__shell';
import * as headerBarModule from './finance-kit__header-bar';
import * as filterStripModule from './finance-kit__filter-strip';
import * as kpiCardModule from './finance-kit__kpi-card';
import * as rankingListModule from './finance-kit__ranking-list';
import * as summaryTableModule from './finance-kit__summary-table';
import * as notePanelModule from './finance-kit__note-panel';
import * as statusGridModule from './finance-kit__status-grid';

const FINANCE_PLUGIN_ID = 'finance-kit';
const FINANCE_PLUGIN_VERSION = '1.0.0';
const FINANCE_COMPONENTS = [
    { id: 'shell', baseType: 'container' },
    { id: 'header-bar', baseType: 'container' },
    { id: 'filter-strip', baseType: 'container' },
    { id: 'kpi-card', baseType: 'number-card' },
    { id: 'ranking-list', baseType: 'table' },
    { id: 'summary-table', baseType: 'table' },
    { id: 'note-panel', baseType: 'markdown-text' },
    { id: 'status-grid', baseType: 'container' },
] as const;

test('finance plugin adapters register all builtin finance renderer plugins', () => {
    const manifests = [
        {
            id: FINANCE_PLUGIN_ID,
            version: FINANCE_PLUGIN_VERSION,
            components: FINANCE_COMPONENTS.map((component) => ({
                ...component,
                name: component.id,
                propertySchema: {
                    version: '1.0.0',
                    fields: [{ key: `${component.id}-field`, label: component.id, type: 'string', defaultValue: component.id }],
                },
            })),
        },
    ];

    for (const component of FINANCE_COMPONENTS) {
        const runtimeId = buildPluginRuntimeId(FINANCE_PLUGIN_ID, component.id, FINANCE_PLUGIN_VERSION);
        unregisterRendererPlugin(runtimeId);
    }

    installBuiltinPluginAdaptersFromModules(manifests as never, {
        './custom/finance-kit__shell.tsx': shellModule,
        './custom/finance-kit__header-bar.tsx': headerBarModule,
        './custom/finance-kit__filter-strip.tsx': filterStripModule,
        './custom/finance-kit__kpi-card.tsx': kpiCardModule,
        './custom/finance-kit__ranking-list.tsx': rankingListModule,
        './custom/finance-kit__summary-table.tsx': summaryTableModule,
        './custom/finance-kit__note-panel.tsx': notePanelModule,
        './custom/finance-kit__status-grid.tsx': statusGridModule,
    });

    for (const component of FINANCE_COMPONENTS) {
        const runtimeId = buildPluginRuntimeId(FINANCE_PLUGIN_ID, component.id, FINANCE_PLUGIN_VERSION);
        const plugin = getRendererPlugin(runtimeId);
        assert.ok(plugin, `expected renderer plugin ${runtimeId} to be registered`);
        assert.equal(plugin?.baseType, component.baseType);
        assert.equal(plugin?.id, runtimeId);
        assert.ok(plugin?.propertySchema, `expected propertySchema for ${runtimeId}`);
        assert.equal(plugin?.propertySchema?.version, '1.0.0');
        assert.equal(plugin?.propertySchema?.fields?.[0]?.key, `${component.id}-field`);
    }
});

test('finance plugin adapter can self-register from component metadata even without manifest loading', () => {
    const runtimeId = buildPluginRuntimeId(FINANCE_PLUGIN_ID, 'kpi-card', FINANCE_PLUGIN_VERSION);
    unregisterRendererPlugin(runtimeId);

    const installed = ensureBuiltinPluginAdapterFromModules(
        FINANCE_PLUGIN_ID,
        'kpi-card',
        FINANCE_PLUGIN_VERSION,
        {
            './custom/finance-kit__kpi-card.tsx': kpiCardModule,
        },
    );

    assert.equal(installed, true);
    const plugin = getRendererPlugin(runtimeId);
    assert.ok(plugin, `expected renderer plugin ${runtimeId} to be registered by fallback path`);
    assert.equal(plugin?.baseType, 'number-card');
});
