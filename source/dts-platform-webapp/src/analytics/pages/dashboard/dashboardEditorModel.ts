import type { DashboardCard, DashboardDetail } from "../../api/analyticsApi";
import type { DashboardParameter } from "./DashboardFilterBar";

export function toEditableDashcards(dashboard: DashboardDetail): DashboardCard[] {
	return Array.isArray(dashboard.ordered_cards) ? dashboard.ordered_cards : [];
}

export function toDashboardParams(dashboard: DashboardDetail): DashboardParameter[] {
	if (!Array.isArray(dashboard.parameters)) return [];
	return dashboard.parameters
		.map((parameter: any) => ({
			id: String(parameter?.id ?? ""),
			name: typeof parameter?.name === "string" ? parameter.name : undefined,
			slug: typeof parameter?.slug === "string" ? parameter.slug : undefined,
			type: typeof parameter?.type === "string" ? parameter.type : undefined,
		}))
		.filter((parameter) => parameter.id);
}

export function publicationErrorMessage(error: unknown): string {
	return error instanceof Error ? error.message : "仪表板发布请求失败";
}
