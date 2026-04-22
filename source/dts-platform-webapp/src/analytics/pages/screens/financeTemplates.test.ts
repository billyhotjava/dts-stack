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
    'fin-auxiliary-balance': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'ranking-list', 'status-grid', 'note-panel'],
    'fin-own-fund': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'status-grid', 'note-panel'],
    'fin-personal-balance': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'ranking-list', 'status-grid', 'note-panel'],
    'fin-project-fund': ['header-bar', 'filter-strip', 'kpi-card', 'summary-table', 'ranking-list', 'status-grid', 'note-panel'],
};

const DISALLOWED_COMPONENT_TYPES = new Set(['gauge-chart', 'waterfall-chart', 'treemap-chart']);
const SQL_BOUND_COMPONENTS: Record<(typeof TEMPLATE_IDS)[number], string[]> = {
    'fin-auxiliary-balance': ['ab-kpi-total', 'ab-kpi-subject', 'ab-kpi-contract', 'ab-kpi-dept', 'ab-chart-dept', 'ab-chart-structure', 'ab-status', 'ab-ranking', 'ab-summary'],
    'fin-own-fund': ['of-kpi-open', 'of-kpi-increase', 'of-kpi-use', 'of-kpi-balance', 'of-kpi-yoy', 'of-chart-trend', 'of-chart-category', 'of-chart-structure', 'of-summary', 'of-status'],
    'fin-personal-balance': ['pb-kpi-net', 'pb-kpi-debit', 'pb-kpi-credit', 'pb-kpi-employee', 'pb-kpi-dept', 'pb-chart-employee', 'pb-chart-subject', 'pb-ranking', 'pb-status', 'pb-summary'],
    'fin-project-fund': ['pf-kpi-budget', 'pf-kpi-spent', 'pf-kpi-remaining', 'pf-kpi-overspend', 'pf-kpi-received', 'pf-kpi-receivable', 'pf-chart-budget', 'pf-chart-collection', 'pf-status', 'pf-summary', 'pf-ranking'],
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

        const disallowedComponents = (template?.config.components || []).filter((component) => DISALLOWED_COMPONENT_TYPES.has(component.type));
        assert.equal(
            disallowedComponents.length,
            0,
            `expected ${templateId} to avoid unstable finance chart types, got: ${disallowedComponents.map((component) => component.type).join(', ')}`,
        );

        for (const requiredComponent of REQUIRED_COMPONENTS[templateId]) {
            assert.ok(
                pluginComponentIds.has(requiredComponent),
                `expected ${templateId} to include finance-kit:${requiredComponent}`,
            );
        }
    }
});

test('finance templates bind live SQL data sources instead of static example values', () => {
    for (const templateId of TEMPLATE_IDS) {
        const template = getTemplateById(templateId);
        assert.ok(template, `expected template ${templateId} to exist`);

        for (const componentId of SQL_BOUND_COMPONENTS[templateId]) {
            const component = template?.config.components.find((item) => item.id === componentId);
            assert.ok(component, `expected ${templateId}:${componentId} to exist`);
            assert.equal(component?.dataSource?.type, 'sql', `expected ${templateId}:${componentId} to use sql datasource`);
            assert.equal(component?.dataSource?.sourceType, 'sql', `expected ${templateId}:${componentId} to use sql sourceType`);
            assert.equal(component?.dataSource?.sqlConfig?.databaseId, 1, `expected ${templateId}:${componentId} to target finance warehouse`);
            assert.match(component?.dataSource?.sqlConfig?.query ?? '', /public\.biz_(ads|dws|dwd)_/, `expected ${templateId}:${componentId} query to read finance marts`);
        }
    }
});
