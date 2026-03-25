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
