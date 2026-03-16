import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById } from './screenTemplates';

test('project management command center template registers three-page Java-backed demo screen', () => {
    const template = getTemplateById('project-management-command-center');

    assert.ok(template);
    assert.equal(template?.id, 'project-management-command-center');
    assert.equal(template?.config.width, 1920);
    assert.equal(template?.config.height, 1080);
    assert.equal(template?.config.pages?.length, 3);
    assert.equal(template?.config.carouselConfig?.enabled, true);
    assert.deepEqual(
        (template?.config.globalVariables ?? []).map((item) => item.key),
        ['programId', 'majorProjectId', 'dateFrom', 'dateTo', 'deptId', 'riskLevel'],
    );

    const overviewPage = template?.config.pages?.[0];
    const executionPage = template?.config.pages?.[1];
    const riskPage = template?.config.pages?.[2];

    assert.ok(overviewPage);
    assert.ok(executionPage);
    assert.ok(riskPage);

    const overviewCard = overviewPage?.components.find((item) => item.id === 'pmcc-overview-kpi-total');
    const executionCard = executionPage?.components.find((item) => item.id === 'pmcc-execution-kpi-high-risk');
    const riskCard = riskPage?.components.find((item) => item.id === 'pmcc-risk-kpi-abnormal');

    assert.equal(overviewCard?.dataSource?.type, 'api');
    assert.equal(overviewCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/overview');
    assert.equal(overviewCard?.dataSource?.apiConfig?.responsePath, 'kpis.0');

    assert.equal(executionCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/execution');
    assert.equal(executionCard?.dataSource?.apiConfig?.responsePath, 'incompleteKpis.0');

    assert.equal(riskCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/risk');
    assert.equal(riskCard?.dataSource?.apiConfig?.responsePath, 'changeKpis.0');

    assert.ok(getTemplateById('project-management-cockpit'));
});
