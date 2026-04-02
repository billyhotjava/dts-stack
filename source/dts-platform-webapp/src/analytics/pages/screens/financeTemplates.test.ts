import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById } from './screenTemplates';

const TEMPLATE_IDS = [
    'fin-auxiliary-balance',
    'fin-own-fund',
    'fin-personal-balance',
    'fin-project-fund',
] as const;

const REQUIRED_COMPONENTS: Record<(typeof TEMPLATE_IDS)[number], string[]> = {
    'fin-auxiliary-balance': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'ranking-list'],
    'fin-own-fund': ['header-bar', 'filter-strip', 'kpi-card', 'note-panel'],
    'fin-personal-balance': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'ranking-list'],
    'fin-project-fund': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table'],
};

test('finance templates are rebuilt on top of finance-kit plugin components', () => {
    for (const templateId of TEMPLATE_IDS) {
        const template = getTemplateById(templateId);
        assert.ok(template, `expected template ${templateId} to exist`);

        const pluginComponentIds = new Set(
            (template?.config.components || [])
                .map((component) => component.config?.__plugin as { pluginId?: string; componentId?: string } | undefined)
                .filter((plugin): plugin is { pluginId: string; componentId: string } => plugin?.pluginId === 'finance-kit' && !!plugin.componentId)
                .map((plugin) => plugin.componentId),
        );

        for (const requiredComponent of REQUIRED_COMPONENTS[templateId]) {
            assert.ok(
                pluginComponentIds.has(requiredComponent),
                `expected ${templateId} to include finance-kit:${requiredComponent}`,
            );
        }
    }
});
