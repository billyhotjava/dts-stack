import type { ReleaseCandidate, ReleaseCandidateWorkbench, ReleaseCandidateScopeEntryInput } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { MaterializationBuildAction } from "./ModelMaterializationActions";

/** Membership permits reading; an exact root selection permits the existing batch commands. */
export function resolveReleaseCandidateScope(
	candidate: ReleaseCandidate | null,
	selection: ModelSpecView[],
	planId: string,
	environment: string,
) {
	const containsSelection = Boolean(
		candidate &&
			selection.length &&
			candidate.planId === planId &&
			candidate.environment === environment &&
			selection.every((model) =>
				candidate.entries.some(
					(entry) =>
						entry.modelSpecId === model.id && entry.revision === model.revision && entry.checksum === model.checksum,
				),
			),
	);
	const explicitRoots = candidate?.entries.filter((entry) => entry.selectedReason === "MATERIALIZATION_ROOT") || [];
	const roots = explicitRoots.length
		? explicitRoots
		: candidate?.entries.filter((entry) => entry.selectedReason !== "AUTO_DEPENDENCY") || [];
	const exactScope =
		containsSelection &&
		roots.length === selection.length &&
		roots.every((entry) => selection.some((model) => model.id === entry.modelSpecId));
	return { containsSelection, exactScope };
}

export function resolveMaterializationBuildAction(
	workspace: ReleaseCandidateWorkbench | null,
	containsSelection: boolean,
	exactScope: boolean,
): MaterializationBuildAction | null {
	const candidate = workspace?.candidate;
	const actions = workspace?.allowedActions || [];
	if (actions.includes("CREATE_CANDIDATE")) return "CREATE_CANDIDATE";
	if (!candidate) return null;
	// Restart the immutable candidate scope; the dialog requires explicit scope confirmation.
	if (containsSelection && actions.includes("REMATERIALIZE")) return "REMATERIALIZE";
	if (!exactScope) {
		if (actions.includes("REFRESH_CANDIDATE")) return "REFRESH_AND_CREATE";
		if (actions.includes("CREATE_REPLACEMENT_CANDIDATE")) return "CREATE_AFTER_TERMINAL";
		return actions.includes("CANCEL_CANDIDATE") ? "CANCEL_AND_CREATE" : null;
	}
	if (actions.includes("REFRESH_CANDIDATE")) return "REFRESH_AND_REPLACE";
	if (actions.includes("CREATE_REPLACEMENT_CANDIDATE")) return "CREATE_REPLACEMENT";
	if (actions.includes("RETRY_BUILD")) return "RETRY_BUILD";
	return actions.includes("START_BUILD") ? "START_BUILD" : null;
}

export function materializationScopeEntries(
	candidate: ReleaseCandidate | null,
	selection: ModelSpecView[],
	action: MaterializationBuildAction | null,
): ReleaseCandidateScopeEntryInput[] {
	if (action === "REMATERIALIZE" && candidate) {
		const explicitRoots = candidate.entries.filter((entry) => entry.selectedReason === "MATERIALIZATION_ROOT");
		const roots = explicitRoots.length ? explicitRoots : candidate.entries.filter((entry) => entry.selectedReason !== "AUTO_DEPENDENCY");
		return roots.map(({ modelSpecId, sortOrder, selectedReason }) => ({ modelSpecId, sortOrder, selectedReason }));
	}
	return selection.map((model, sortOrder) => ({ modelSpecId: model.id, sortOrder, selectedReason: "从模型工作台选择" }));
}
