import { legacyPlanningArchitectureTarget } from "@/pages/data-architecture/navigation";

export function resolveModelingOverviewTarget(target: string): string {
	const modelingPath = `/data-modeling/${target.replace(/^\/+/, "")}`;
	return legacyPlanningArchitectureTarget(modelingPath) || modelingPath;
}
