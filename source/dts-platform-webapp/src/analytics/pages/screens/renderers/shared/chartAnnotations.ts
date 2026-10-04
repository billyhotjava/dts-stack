import type { ChartMarkArea, ChartMarkLine, SeriesConditionalColor } from '../../types';

export const ANNOTATABLE_TYPES = new Set(['line-chart', 'bar-chart', 'scatter-chart', 'combo-chart', 'waterfall-chart']);

export function injectChartAnnotations(
	option: Record<string, unknown>,
	config: Record<string, unknown>,
): Record<string, unknown> {
	const markLines = config.markLines as ChartMarkLine[] | undefined;
	const markAreas = config.markAreas as ChartMarkArea[] | undefined;
	const conditionalColors = config.conditionalColors as SeriesConditionalColor[] | undefined;

	if ((!markLines || markLines.length === 0) && (!markAreas || markAreas.length === 0) && (!conditionalColors || conditionalColors.length === 0)) {
		return option;
	}

	const series = option.series as Array<Record<string, unknown>> | undefined;
	if (!Array.isArray(series) || series.length === 0) return option;

	const markLineData: Array<Record<string, unknown>> = [];
	if (markLines) {
		for (const ml of markLines) {
			if (ml.type === 'value' && ml.value != null) {
				const item: Record<string, unknown> = {
					name: ml.name ?? `${ml.value}`,
					label: { formatter: ml.name ?? `${ml.value}`, position: 'insideEndTop' },
					lineStyle: { color: ml.color ?? '#ff6b6b', type: ml.lineStyle ?? 'dashed' },
				};
				if (ml.axis === 'x') {
					item.xAxis = ml.value;
				} else {
					item.yAxis = ml.value;
				}
				markLineData.push(item);
			} else if (ml.type === 'average' || ml.type === 'min' || ml.type === 'max') {
				markLineData.push({
					type: ml.type,
					name: ml.name ?? ml.type,
					label: { formatter: ml.name ?? ml.type, position: 'insideEndTop' },
					lineStyle: { color: ml.color ?? '#facc15', type: ml.lineStyle ?? 'dashed' },
				});
			}
		}
	}

	const markAreaData: Array<Array<Record<string, unknown>>> = [];
	if (markAreas) {
		for (const ma of markAreas) {
			const start: Record<string, unknown> = { name: ma.name ?? '' };
			const end: Record<string, unknown> = {};
			if (ma.axis === 'x') {
				start.xAxis = ma.from;
				end.xAxis = ma.to;
			} else {
				start.yAxis = ma.from;
				end.yAxis = ma.to;
			}
			start.itemStyle = { color: ma.color ?? 'rgba(255, 107, 107, 0.15)' };
			markAreaData.push([start, end]);
		}
	}

	let colorFn: ((params: { value: unknown }) => string) | undefined;
	if (conditionalColors && conditionalColors.length > 0) {
		colorFn = (params: { value: unknown }) => {
			const val = typeof params.value === 'number'
				? params.value
				: (Array.isArray(params.value) ? Number(params.value[1]) : Number(params.value));
			for (const rule of conditionalColors) {
				let match = false;
				switch (rule.operator) {
					case '>': match = val > rule.value; break;
					case '>=': match = val >= rule.value; break;
					case '<': match = val < rule.value; break;
					case '<=': match = val <= rule.value; break;
					case '==': match = val === rule.value; break;
					case 'between': match = val >= rule.value && val <= (rule.valueTo ?? rule.value); break;
				}
				if (match) return rule.color;
			}
			return '';
		};
	}

	const patched = series.map((s, idx) => {
		const result = { ...s };
		if (idx === 0) {
			if (markLineData.length > 0) {
				result.markLine = { symbol: ['none', 'arrow'], data: markLineData, silent: true };
			}
			if (markAreaData.length > 0) {
				result.markArea = { data: markAreaData, silent: true };
			}
		}
		if (colorFn) {
			result.itemStyle = { ...((result.itemStyle as Record<string, unknown>) || {}), color: colorFn };
		}
		return result;
	});

	return { ...option, series: patched };
}
