import type { DbtReleaseSubmitResult } from "./sqlModeling.types";

export type ReleaseSubmitOutcome = "blocked" | "warning" | "submitted";

export function resolveReleaseSubmitOutcome(result?: DbtReleaseSubmitResult | null): ReleaseSubmitOutcome {
	if (result?.status === "BLOCKED" || result?.blocking) {
		return "blocked";
	}
	if (result?.status === "WARNING" || result?.warning) {
		return "warning";
	}
	return "submitted";
}
