/**
 * Sprint-15 F5 — Shared classification → antd Tag color helper.
 *
 * Backend normalizes classification values to "S1" / "S2" / "S3" / "S4".
 * Anything outside that set falls back to "default" so unexpected values
 * render as a neutral tag instead of crashing.
 */
export type ClassificationTagColor = "red" | "volcano" | "orange" | "blue" | "default";

const COLOR_BY_LEVEL: Record<"S1" | "S2" | "S3" | "S4", ClassificationTagColor> = {
	S1: "red",
	S2: "volcano",
	S3: "orange",
	S4: "blue",
};

export function classificationColor(c: string): ClassificationTagColor {
	const key = c as "S1" | "S2" | "S3" | "S4";
	return COLOR_BY_LEVEL[key] ?? "default";
}
