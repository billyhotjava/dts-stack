import type { ReleaseCandidate } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";

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
