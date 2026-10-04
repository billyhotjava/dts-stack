import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById, createConfigFromTemplate } from './screenTemplates';
import { buildScreenPayload, normalizeScreenConfig } from './screenSpec';
import { readComponentPluginMeta } from './plugins/runtime';

test('normalizeScreenConfig preserves light-business finance template theme and plugin metadata after reopen', () => {
    const template = getTemplateById('fin-auxiliary-balance');
    assert.ok(template, 'expected finance template to exist');

    const config = createConfigFromTemplate(template!);
    assert.equal(config.theme, 'light-business');

    const payload = buildScreenPayload({
        ...config,
        id: 'finance-screen-1',
    });
    const normalized = normalizeScreenConfig(payload, { id: 'finance-screen-1' });

    assert.equal(normalized.config.theme, 'light-business');
    assert.equal(normalized.warnings.length, 0);

    const pluginComponents = normalized.config.components.filter((component) => readComponentPluginMeta(component.config)?.pluginId === 'finance-kit');
    assert.ok(pluginComponents.length > 0, 'expected normalized config to keep finance plugin components');

    for (const component of pluginComponents) {
        const pluginMeta = readComponentPluginMeta(component.config);
        assert.ok(pluginMeta, `expected plugin meta on component ${component.id}`);
        assert.equal(pluginMeta?.pluginId, 'finance-kit');
        assert.ok(component.config.__pluginPropertySchema !== undefined, `expected property schema on component ${component.id}`);
    }
});
