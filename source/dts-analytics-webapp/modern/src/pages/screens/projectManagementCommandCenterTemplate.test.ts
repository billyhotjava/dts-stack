import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById } from './screenTemplates';

test('project management command center template registers four-page Java-backed demo screen', () => {
    const template = getTemplateById('project-management-command-center');

    assert.ok(template);
    assert.equal(template?.id, 'project-management-command-center');
    assert.equal(template?.name, '科研项目管理指挥大屏');
    assert.equal(template?.config.name, '科研项目管理指挥大屏');
    assert.equal(template?.config.width, 1920);
    assert.equal(template?.config.height, 1080);
    assert.equal(template?.config.theme, 'glacier');
    assert.equal(template?.config.backgroundColor, '#eef5fb');
    assert.equal(template?.config.pages?.length, 4);
    assert.equal(template?.config.carouselConfig?.enabled, true);
    assert.deepEqual(
        (template?.config.globalVariables ?? []).map((item) => item.key),
        ['majorProjectId', 'dateFrom', 'dateTo', 'deptId', 'riskLevel'],
    );

    const overviewPage = template?.config.pages?.[0];
    const executionPage = template?.config.pages?.[1];
    const riskPage = template?.config.pages?.[2];

    assert.ok(overviewPage);
    assert.ok(executionPage);
    assert.ok(riskPage);

    const overviewCard = overviewPage?.components.find((item) => item.id === 'pmcc-ov-kpi-total');
    const overviewPanel = overviewPage?.components.find((item) => item.id === 'pmcc-overview-bg-top');
    const overviewFilter = overviewPage?.components.find((item) => item.id === 'pmcc-major-0');
    const overviewChart = overviewPage?.components.find((item) => item.id === 'pmcc-overview-weekly');
    const overviewTable = overviewPage?.components.find((item) => item.id === 'pmcc-overview-alerts');
    const executionCard = executionPage?.components.find((item) => item.id === 'pmcc-ex-kpi-high');
    const riskCard = riskPage?.components.find((item) => item.id === 'pmcc-rk-kpi-abnormal');

    assert.equal(overviewPage?.backgroundColor, '#eef5fb');
    assert.equal(overviewPanel?.config.fillColor, 'rgba(255, 255, 255, 0.94)');
    assert.equal(overviewPanel?.config.borderColor, 'rgba(148, 163, 184, 0.24)');
    assert.equal(overviewFilter?.config.inputBackground, 'rgba(255, 255, 255, 0.96)');
    assert.equal(overviewFilter?.config.inputBorderColor, 'rgba(148, 163, 184, 0.42)');
    assert.equal(overviewFilter?.config.inputTextColor, '#16324f');
    assert.equal(overviewCard?.config.backgroundColor, '#ffffff');
    assert.equal(overviewCard?.config.titleColor, '#5b7088');
    assert.equal(overviewCard?.config.valueColor, '#16324f');
    assert.deepEqual(overviewChart?.config.seriesColors, ['#3b82f6', '#38bdf8', '#f4b740']);
    assert.equal(overviewTable?.config.headerBackground, 'rgba(219, 234, 254, 0.96)');
    assert.equal(overviewTable?.config.bodyColor, '#35526b');

    assert.equal(overviewCard?.dataSource?.type, 'api');
    assert.equal(overviewCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/overview');
    assert.equal(overviewCard?.dataSource?.apiConfig?.responsePath, 'kpis.0');
    assert.equal(overviewCard?.dataSource?.apiConfig?.params?.majorProjectId, '{{majorProjectId}}');
    assert.equal(overviewFilter?.dataSource?.apiConfig?.params?.majorProjectId, '{{majorProjectId}}');
    assert.equal(overviewCard?.actions?.[0]?.type, 'jump-url');
    assert.equal(overviewCard?.actions?.[0]?.jumpUrlTemplate, '/analytics/project-cockpit?theme=overview&drillTarget=completion');

    assert.equal(executionCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/execution');
    assert.equal(executionCard?.dataSource?.apiConfig?.responsePath, 'incompleteKpis.0');
    assert.equal(executionCard?.actions?.[0]?.type, 'jump-url');
    assert.equal(executionCard?.actions?.[0]?.jumpUrlTemplate, '/analytics/project-cockpit?theme=risk&drillTarget=high-risk');

    assert.equal(riskCard?.dataSource?.apiConfig?.url, '/analytics/api/project-cockpit/screen/risk');
    assert.equal(riskCard?.dataSource?.apiConfig?.responsePath, 'changeKpis.0');
    assert.equal(riskCard?.actions?.[0]?.type, 'jump-url');
    assert.equal(riskCard?.actions?.[0]?.jumpUrlTemplate, '/analytics/project-cockpit?theme=risk&drillTarget=overdue');
    assert.equal(getTemplateById('project-management-cockpit'), undefined);
});
