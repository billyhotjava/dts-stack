export type GanttOwnerLabelPlacement = "inside" | "outside-left" | "outside-right";

export function getGanttOwnerLabelPlacement(leftPercent: number, widthPercent: number): GanttOwnerLabelPlacement {
	const safeLeft = Math.max(0, Math.min(leftPercent, 100));
	const safeWidth = Math.max(widthPercent, 0);
	if (safeWidth >= 11) {
		return "inside";
	}
	return safeLeft + safeWidth > 82 ? "outside-left" : "outside-right";
}

type BaselineRangeSource = {
	planDate?: string;
	baselineStartDate?: string;
	baselineEndDate?: string;
};

export function resolveGanttBaselineRange(source: BaselineRangeSource): { startDate?: string; endDate?: string } {
	const startDate = source.baselineStartDate?.trim() || source.planDate?.trim();
	const endDate = source.baselineEndDate?.trim() || startDate;
	if (!startDate && !endDate) {
		return {};
	}
	if (!startDate) {
		return { startDate: endDate, endDate };
	}
	if (!endDate) {
		return { startDate, endDate: startDate };
	}
	return startDate <= endDate
		? { startDate, endDate }
		: { startDate: endDate, endDate: startDate };
}

export function resolveGanttSideTextStyle(sideTextColor?: string): { color: string } | undefined {
	const normalizedColor = sideTextColor?.trim();
	return normalizedColor ? { color: normalizedColor } : undefined;
}
