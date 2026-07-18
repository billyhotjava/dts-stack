import { useCallback, useMemo, useRef, useState } from "react";
import {
	buildDrillSnapshot,
	normalizeDrillLevel,
	resolveNextDrillEntry,
	type DrillEntry,
} from "../drillRuntime";
import type { DataSourceConfig, DrillDownConfig } from "../types";

export interface DrillState {
	effectiveDataSource: DataSourceConfig | undefined;
	queryParameters: Array<{ name: string; value: string }>;
	breadcrumbs: Array<{ label: string; depth: number }>;
	canDrillDown: boolean;
	handleDrill: (clickPayload: Record<string, unknown>) => boolean;
	handleRollUp: (targetDepth: number) => void;
	reset: () => void;
}

export function useDrillDown(
	rootDataSource: DataSourceConfig | undefined,
	drillConfig: DrillDownConfig | undefined,
): DrillState {
	const [stack, setStack] = useState<DrillEntry[]>([]);
	const pendingDepthRef = useRef<number | null>(null);
	const levels = useMemo(() => {
		if (drillConfig?.enabled !== true) return [];
		const normalized = [];
		for (const level of drillConfig.levels ?? []) {
			const next = normalizeDrillLevel(level);
			if (!next) break;
			normalized.push(next);
		}
		return normalized;
	}, [drillConfig]);
	const snapshot = useMemo(
		() => buildDrillSnapshot(rootDataSource, levels, stack),
		[rootDataSource, levels, stack],
	);
	if (pendingDepthRef.current !== null && pendingDepthRef.current !== snapshot.depth) {
		pendingDepthRef.current = null;
	}
	const canDrillDown = drillConfig?.enabled === true && snapshot.depth < levels.length;

	const handleDrill = useCallback(
		(clickPayload: Record<string, unknown>) => {
			if (drillConfig?.enabled !== true || pendingDepthRef.current === snapshot.depth) return false;
			const level = levels[snapshot.depth];
			if (!level) return false;
			const entry = resolveNextDrillEntry(level, clickPayload);
			if (!entry) return false;
			pendingDepthRef.current = snapshot.depth;
			setStack((currentStack) => (
				currentStack.length === snapshot.depth ? [...currentStack, entry] : currentStack
			));
			return true;
		},
		[drillConfig?.enabled, levels, snapshot.depth],
	);

	const handleRollUp = useCallback((targetDepth: number) => {
		if (!Number.isFinite(targetDepth) || targetDepth < 0) return;
		setStack((currentStack) => currentStack.slice(0, Math.floor(targetDepth)));
	}, []);

	const reset = useCallback(() => {
		pendingDepthRef.current = null;
		setStack([]);
	}, []);

	return {
		effectiveDataSource: snapshot.effectiveDataSource,
		queryParameters: snapshot.queryParameters,
		breadcrumbs: snapshot.breadcrumbs,
		canDrillDown,
		handleDrill,
		handleRollUp,
		reset,
	};
}
