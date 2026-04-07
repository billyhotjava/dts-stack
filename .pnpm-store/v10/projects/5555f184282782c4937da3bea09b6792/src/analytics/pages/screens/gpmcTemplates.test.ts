import assert from 'node:assert/strict';
import test from 'node:test';
import { getTemplateById } from './screenTemplates';

test('gpmc cost board template emphasizes planned, actual, and variance trends', () => {
	const template = getTemplateById('gpmc-cost-board');

	assert.ok(template);

	const monthlyTrend = template?.config.components.find((item) => item.id === 'gpmc-cost-monthly');
	const varianceRanking = template?.config.components.find((item) => item.id === 'gpmc-cost-rank');
	const monthlySeries = (monthlyTrend?.config.series as Array<{ name: string }> | undefined) ?? [];

	assert.equal(monthlyTrend?.config.title, '月度支出与偏差趋势');
	assert.deepEqual(
		monthlySeries.map((item) => item.name),
		['计划支出', '实际支出', '预算偏差'],
	);
	assert.equal(varianceRanking?.config.title, '成本偏差预警榜');
});

test('gpmc templates keep platform jump paths under a single /bi prefix', () => {
	const overviewTemplate = getTemplateById('gpmc-strategic-overview');
	const executionTemplate = getTemplateById('gpmc-execution-board');

	assert.ok(overviewTemplate);
	assert.ok(executionTemplate);

	const overviewTotalKpi = overviewTemplate?.config.components.find((item) => item.id === 'gpmc-overview-kpi-total');
	const overviewExecutionTab = overviewTemplate?.config.components.find((item) => item.id === 'overview-topic-execution-shape');
	const executionDelayTable = executionTemplate?.config.components.find((item) => item.id === 'gpmc-execution-delay-top');
	const executionDelayKpi = executionTemplate?.config.components.find((item) => item.id === 'gpmc-execution-kpi-max-delay');

	assert.equal(
		overviewTotalKpi?.actions?.[0]?.jumpUrlTemplate,
		'screen-ref:GPMC%20%E9%A1%B9%E7%9B%AE%E6%89%A7%E8%A1%8C%E7%9B%91%E6%8E%A7|%2Fbi%2Fgpmc%2Fexecution',
	);
	assert.equal(
		overviewExecutionTab?.actions?.[0]?.jumpUrlTemplate,
		'screen-ref:GPMC%20%E9%A1%B9%E7%9B%AE%E6%89%A7%E8%A1%8C%E7%9B%91%E6%8E%A7|%2Fbi%2Fgpmc%2Fexecution',
	);
	assert.equal(executionDelayTable?.actions?.length ?? 0, 0);
	assert.equal(executionDelayKpi?.actions?.length ?? 0, 0);
});
