import { createContext, useContext, useMemo, type ReactNode } from "react";
import type { ProjectCockpitTheme } from "./projectCockpitQueryState";
import {
	type ProjectCockpitQueryPatch,
	useProjectCockpitQueryState,
} from "./useProjectCockpitQueryState";

type ProjectCockpitContextValue = {
	queryState: ReturnType<typeof useProjectCockpitQueryState>[0];
	updateQueryState: ReturnType<typeof useProjectCockpitQueryState>[1];
	setTheme: (theme: ProjectCockpitTheme) => void;
};

const ProjectCockpitContext = createContext<ProjectCockpitContextValue | null>(null);

export function ProjectCockpitProvider({ children }: { children: ReactNode }) {
	const [queryState, updateQueryState] = useProjectCockpitQueryState();
	const value = useMemo<ProjectCockpitContextValue>(
		() => ({
			queryState,
			updateQueryState,
			setTheme: (theme: ProjectCockpitTheme) => updateQueryState({ theme }),
		}),
		[queryState, updateQueryState],
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
	const { queryState, updateQueryState } = useProjectCockpitContext();
	return useMemo(
		() => ({
			queryState,
			updateQueryState: (patch: ProjectCockpitQueryPatch) => updateQueryState(patch),
		}),
		[queryState, updateQueryState],
	);
}
