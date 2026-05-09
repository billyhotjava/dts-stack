import { describe, expect, it } from 'vitest';
import { ANNOTATABLE_TYPES, injectChartAnnotations } from './chartAnnotations';

describe('chartAnnotations', () => {
	it('keeps annotatable chart types centralized outside ComponentRenderer', () => {
		expect(ANNOTATABLE_TYPES.has('line-chart')).toBe(true);
		expect(ANNOTATABLE_TYPES.has('bar-chart')).toBe(true);
		expect(ANNOTATABLE_TYPES.has('title')).toBe(false);
	});

	it('injects mark lines, mark areas, and conditional colors into the first series', () => {
		const option = {
			series: [
				{ type: 'bar', itemStyle: { borderRadius: 2 } },
				{ type: 'line' },
			],
		};

		const annotated = injectChartAnnotations(option, {
			markLines: [{ type: 'value', axis: 'y', value: 100, name: '目标线', color: '#ff0000' }],
			markAreas: [{ axis: 'x', from: '一月', to: '二月', name: '活动期', color: 'rgba(0,0,0,0.1)' }],
			conditionalColors: [{ operator: '>=', value: 100, color: '#22c55e' }],
		});

		const series = annotated.series as Array<Record<string, unknown>>;
		expect(series[0]?.markLine).toMatchObject({ silent: true });
		expect(series[0]?.markArea).toMatchObject({ silent: true });
		expect((series[0]?.itemStyle as Record<string, unknown>).borderRadius).toBe(2);
		expect(typeof (series[0]?.itemStyle as Record<string, unknown>).color).toBe('function');
		expect(series[1]?.markLine).toBeUndefined();
	});
});
