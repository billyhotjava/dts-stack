export interface PointerPoint {
	x: number;
	y: number;
}

export function resolveInteractionScale(element: HTMLElement | null, designWidth: number): number {
	if (!element || !Number.isFinite(designWidth) || designWidth <= 0) {
		return 1;
	}
	const renderedWidth = element.getBoundingClientRect().width;
	if (!Number.isFinite(renderedWidth) || renderedWidth <= 0) {
		return 1;
	}
	return Math.max(0.1, renderedWidth / designWidth);
}

export function resolveScaledPointerDelta(start: PointerPoint, current: PointerPoint, scale: number): PointerPoint {
	const safeScale = Number.isFinite(scale) && scale > 0 ? scale : 1;
	return {
		x: (current.x - start.x) / safeScale,
		y: (current.y - start.y) / safeScale,
	};
}
