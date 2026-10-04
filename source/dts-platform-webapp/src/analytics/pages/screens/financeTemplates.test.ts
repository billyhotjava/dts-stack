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
    'fin-auxiliary-balance': ['header-bar', 'filter-strip', 'kpi-card', 'status-grid', 'note-panel'],
    'fin-own-fund': ['header-bar', 'filter-strip', 'kpi-card', 'status-grid', 'note-panel'],
    'fin-personal-balance': ['header-bar', 'filter-strip', 'kpi-card', 'status-grid', 'note-panel'],
    'fin-project-fund': ['header-bar', 'filter-strip', 'kpi-card', 'status-grid', 'note-panel'],
};

const DISALLOWED_COMPONENT_TYPES = new Set(['gauge-chart', 'waterfall-chart', 'treemap-chart']);
const SQL_BOUND_COMPONENTS: Record<(typeof TEMPLATE_IDS)[number], string[]> = {
    'fin-auxiliary-balance': ['ab-kpi-total', 'ab-kpi-subject', 'ab-kpi-contract', 'ab-kpi-dept', 'ab-chart-dept', 'ab-chart-structure', 'ab-status', 'ab-ranking', 'ab-summary'],
    'fin-own-fund': ['of-kpi-open', 'of-kpi-increase', 'of-kpi-use', 'of-kpi-balance', 'of-kpi-yoy', 'of-chart-trend', 'of-chart-category', 'of-chart-structure', 'of-summary', 'of-status'],
    'fin-personal-balance': ['pb-kpi-net', 'pb-kpi-debit', 'pb-kpi-credit', 'pb-kpi-employee', 'pb-kpi-dept', 'pb-chart-employee', 'pb-chart-subject', 'pb-ranking', 'pb-status', 'pb-summary'],
    'fin-project-fund': ['pf-kpi-budget', 'pf-kpi-spent', 'pf-kpi-remaining', 'pf-kpi-overspend', 'pf-kpi-received', 'pf-kpi-receivable', 'pf-chart-budget', 'pf-chart-collection', 'pf-status', 'pf-summary', 'pf-ranking'],
};

const TABLE_COMPONENTS: Record<(typeof TEMPLATE_IDS)[number], Record<string, string[]>> = {
    'fin-auxiliary-balance': {
        'ab-ranking': ['合同名称', '余额(元)', '部门名称'],
        'ab-summary': ['科目编号', '科目名称', '部门名称', '合同名称', '余额(元)'],
    },
    'fin-own-fund': {
        'of-summary': ['年度', '基金类别', '年初余额(万)', '预计增加(万)', '预计使用(万)', '年末余额(万)', '同比增长率'],
    },
    'fin-personal-balance': {
        'pb-ranking': ['职工姓名', '贷方净额(元)', '部门名称'],
        'pb-summary': ['职工姓名', '部门名称', '借方余额(元)', '贷方余额(元)', '净余额(元)', '财务关注'],
    },
    'fin-project-fund': {
        'pf-summary': ['项目编号', '研究室', '总经费(万)', '总支出(万)', '剩余经费(万)', '已收款(万)', '待收经费(万)', '项目属性'],
        'pf-ranking': ['项目编号', '待收经费(万)', '研究室'],
    },
};

test('finance templates keep finance-kit only for non-table finance blocks', () => {
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

        assert.ok(!pluginComponentIds.has('summary-table'), `expected ${templateId} to stop using finance-kit:summary-table`);
        assert.ok(!pluginComponentIds.has('ranking-list'), `expected ${templateId} to stop using finance-kit:ranking-list`);
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

test('finance templates use native table components with Chinese headers', () => {
    for (const templateId of TEMPLATE_IDS) {
        const template = getTemplateById(templateId);
        assert.ok(template, `expected template ${templateId} to exist`);

        for (const [componentId, expectedHeaders] of Object.entries(TABLE_COMPONENTS[templateId])) {
            const component = template?.config.components.find((item) => item.id === componentId);
            assert.ok(component, `expected ${templateId}:${componentId} to exist`);
            assert.equal(component?.type, 'table', `expected ${templateId}:${componentId} to use native table`);
            assert.equal(component?.config?.__plugin, undefined, `expected ${templateId}:${componentId} to avoid plugin table wrappers`);

            const aliases = Array.isArray(component?.config?.columns)
                ? component!.config.columns.map((item: { alias?: string }) => item.alias ?? '')
                : [];
            assert.deepEqual(aliases, expectedHeaders, `expected ${templateId}:${componentId} to expose Chinese table headers`);
        }
    }
});

test('project fund template maps is_major_project as boolean instead of string labels', () => {
    const template = getTemplateById('fin-project-fund');
    assert.ok(template, 'expected fin-project-fund template to exist');

    const summaryComponent = template?.config.components.find((item) => item.id === 'pf-summary');
    assert.ok(summaryComponent, 'expected fin-project-fund:pf-summary to exist');

    const query = summaryComponent?.dataSource?.sqlConfig?.query ?? '';
    assert.match(query, /WHEN is_major_project IS true THEN '重大项目'/);
    assert.match(query, /WHEN is_major_project IS false THEN '非重大项目'/);
    assert.doesNotMatch(query, /is_major_project\s*=\s*'是'/);
    assert.doesNotMatch(query, /is_major_project\s*=\s*'否'/);
});
