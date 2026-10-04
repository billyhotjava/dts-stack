export type CategoryAxisLabelLayout = {
	rotate: number;
	step: number;
	textAnchor: "middle" | "end";
	bottomPadding: number;
};

export function getCategoryAxisLabelLayout(labelCount: number, rotate = 0): CategoryAxisLabelLayout {
	const safeCount = Math.max(labelCount, 0);
	const safeRotate = Math.max(0, rotate);
	if (safeRotate > 0) {
		return {
			rotate: safeRotate,
			step: 1,
			textAnchor: "end",
			bottomPadding: safeRotate >= 30 ? 78 : 64,
		};
	}

	return {
		rotate: 0,
		step: safeCount <= 12 ? 1 : Math.ceil(safeCount / 12),
		textAnchor: "middle",
		bottomPadding: 40,
	};
}
