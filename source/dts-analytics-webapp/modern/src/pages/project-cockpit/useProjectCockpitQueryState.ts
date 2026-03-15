import { useCallback, useMemo } from "react";
import { useLocation, useNavigate } from "react-router";
import {
	type ProjectCockpitQueryState,
	parseProjectCockpitQueryState,
	serializeProjectCockpitQueryState,
} from "./projectCockpitQueryState";

export type ProjectCockpitQueryPatch = Partial<ProjectCockpitQueryState>;

export function mergeProjectCockpitQueryState(
	current: ProjectCockpitQueryState,
	patch: ProjectCockpitQueryPatch,
): ProjectCockpitQueryState {
	const next: ProjectCockpitQueryState = {
		...current,
		...patch,
	};
	if (patch.programId !== undefined && patch.programId !== current.programId) {
		next.majorProjectId = "";
	}
	return next;
}

export function useProjectCockpitQueryState() {
	const location = useLocation();
	const navigate = useNavigate();

	const queryState = useMemo(
		() => parseProjectCockpitQueryState(new URLSearchParams(location.search)),
		[location.search],
	);

	const updateQueryState = useCallback(
		(patch: ProjectCockpitQueryPatch, options?: { replace?: boolean }) => {
			const next = mergeProjectCockpitQueryState(queryState, patch);
			const params = serializeProjectCockpitQueryState(next);
			const search = params.toString();
			navigate(
				{
					pathname: location.pathname,
					search: search.length > 0 ? `?${search}` : "",
				},
				{ replace: options?.replace ?? true },
			);
		},
		[location.pathname, navigate, queryState],
	);

	return [queryState, updateQueryState] as const;
}
