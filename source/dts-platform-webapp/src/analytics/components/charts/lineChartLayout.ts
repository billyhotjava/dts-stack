import { getCategoryAxisLabelLayout } from "./chartAxisLabelLayout";

export function buildLineChartLayout(labels: string[], xAxisLabelRotate = 0) {
	const axisLabelLayout = getCategoryAxisLabelLayout(labels.length, xAxisLabelRotate);
	return {
		axisLabelLayout,
		padding: {
			top: 20,
			right: 20,
			bottom: axisLabelLayout.bottomPadding,
			left: 60,
		},
	};
}
