import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import {
	type ProjectCockpitPublishedPeriod,
	type ProjectCockpitTheme,
	resolveProjectCockpitEffectiveQueryState,
} from "./projectCockpitQueryState";
import {
	type ProjectCockpitQueryPatch,
	useProjectCockpitQueryState,
} from "./useProjectCockpitQueryState";

// --- Drill-down state ---
export type DrillTarget =
	| "high-risk"
	| "overdue"
	| "completion"
	| "milestone"
	| "delay-reason"
	| "delay-dept"
	| "trend-week"
	| null;

export type DrillState = {
	target: DrillTarget;
	params: Record<string, unknown>;
};

// --- Cross-view selection state ---
export type SelectionState = {
	week?: string;
	nodeId?: string;
};

const EMPTY_DRILL: DrillState = { target: null, params: {} };
const EMPTY_SELECTION: SelectionState = {};

type ProjectCockpitContextValue = {
	queryState: ReturnType<typeof useProjectCockpitQueryState>[0];
	effectiveQueryState: ReturnType<typeof useProjectCockpitQueryState>[0];
	updateQueryState: ReturnType<typeof useProjectCockpitQueryState>[1];
	setTheme: (theme: ProjectCockpitTheme) => void;
	drillState: DrillState;
	openDrill: (target: NonNullable<DrillTarget>, params?: Record<string, unknown>) => void;
	closeDrill: () => void;
	selectionState: SelectionState;
	updateSelection: (patch: Partial<SelectionState>) => void;
};

const ProjectCockpitContext = createContext<ProjectCockpitContextValue | null>(null);

export function ProjectCockpitProvider({
	children,
	publishedPeriod,
}: {
	children: ReactNode;
	publishedPeriod?: ProjectCockpitPublishedPeriod | null;
}) {
	const [queryState, updateQueryState] = useProjectCockpitQueryState();
	const effectiveQueryState = useMemo(
		() => resolveProjectCockpitEffectiveQueryState(queryState, publishedPeriod),
		[queryState, publishedPeriod],
	);
	const [drillState, setDrillState] = useState<DrillState>(EMPTY_DRILL);
	const [selectionState, setSelectionState] = useState<SelectionState>(EMPTY_SELECTION);

	const openDrill = useCallback(
		(target: NonNullable<DrillTarget>, params: Record<string, unknown> = {}) =>
			setDrillState({ target, params }),
		[],
	);
	const closeDrill = useCallback(() => setDrillState(EMPTY_DRILL), []);
	const updateSelection = useCallback(
		(patch: Partial<SelectionState>) => setSelectionState((prev) => ({ ...prev, ...patch })),
		[],
	);

	const value = useMemo<ProjectCockpitContextValue>(
		() => ({
			queryState,
			effectiveQueryState,
			updateQueryState,
			setTheme: (theme: ProjectCockpitTheme) => updateQueryState({ theme }),
			drillState,
			openDrill,
			closeDrill,
			selectionState,
			updateSelection,
		}),
		[queryState, effectiveQueryState, updateQueryState, drillState, openDrill, closeDrill, selectionState, updateSelection],
	);
	return <ProjectCockpitContext.Provider value={value}>{children}</ProjectCockpitContext.Provider>;
}

export function useProjectCockpitContext() {
	const context = useContext(ProjectCockpitContext);
	if (!context) {
		throw new Error("ProjectCockpit components must be used within ProjectCockpitProvider");
	}
	return context;
}

export function useProjectCockpitFilters() {
	const { effectiveQueryState, updateQueryState } = useProjectCockpitContext();
	return useMemo(
		() => ({
			queryState: effectiveQueryState,
			updateQueryState: (patch: ProjectCockpitQueryPatch) => updateQueryState(patch),
		}),
		[effectiveQueryState, updateQueryState],
	);
}
