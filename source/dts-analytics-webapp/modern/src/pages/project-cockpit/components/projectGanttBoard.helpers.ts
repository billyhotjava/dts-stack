export type GanttOwnerLabelPlacement = "inside" | "outside-left" | "outside-right";

export function getGanttOwnerLabelPlacement(leftPercent: number, widthPercent: number): GanttOwnerLabelPlacement {
	const safeLeft = Math.max(0, Math.min(leftPercent, 100));
	const safeWidth = Math.max(widthPercent, 0);
	if (safeWidth >= 11) {
		return "inside";
	}
	return safeLeft + safeWidth > 82 ? "outside-left" : "outside-right";
}
