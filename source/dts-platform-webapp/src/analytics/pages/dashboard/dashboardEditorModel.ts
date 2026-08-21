import type {
	CardListItem,
	DashboardCard,
	DashboardDetail,
	DashboardPublicationAudience,
	DashboardPublicationIssue,
	PlatformOrgNode,
} from "../../api/analyticsApi";
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

export function hasRequiredPublicationAudience(
	audience: Pick<DashboardPublicationAudience, "deptCodes" | "roleCodes">,
): boolean {
	return [...audience.deptCodes, ...audience.roleCodes].some((code) => code.trim().length > 0);
}

export function flattenDepartmentOptions(
	nodes: PlatformOrgNode[],
	parentNames: string[] = [],
): Array<{ value: string; label: string }> {
	const options: Array<{ value: string; label: string }> = [];
	for (const node of nodes) {
		const name = node.name?.trim();
		const value = node.deptCode?.trim() || String(node.id ?? "").trim();
		const path = name ? [...parentNames, name] : parentNames;
		if (value && name) {
			options.push({ value, label: path.join(" / ") });
		}
		if (Array.isArray(node.children) && node.children.length > 0) {
			options.push(...flattenDepartmentOptions(node.children, path));
		}
	}
	return options;
}

export function isPublishedAnalysisCard(
	card: Pick<CardListItem, "type" | "lifecycle_status" | "published_revision_id"> | null | undefined,
): boolean {
	return (
		card?.type === "analysis" && card.lifecycle_status === "PUBLISHED" && typeof card.published_revision_id === "number"
	);
}

export function publicationIssueMessage(issue: DashboardPublicationIssue, dashcards: DashboardCard[]): string {
	const componentMatch = issue.path?.match(/components\[(\d+)]/);
	const componentIndex = componentMatch ? Number.parseInt(componentMatch[1], 10) : -1;
	const componentName =
		componentIndex >= 0 ? dashcards[componentIndex]?.card?.name || `第 ${componentIndex + 1} 个组件` : null;

	switch (issue.code) {
		case "DASHBOARD_AUDIENCE_REQUIRED":
			return "请选择至少一个可见部门或可见角色。";
		case "DASHBOARD_ANALYSIS_REQUIRED":
			return componentName
				? `组件“${componentName}”不是已发布的治理分析，请替换后重新校验。`
				: "看板包含非治理分析组件，请替换后重新校验。";
		case "DASHBOARD_ANALYSIS_NOT_PUBLISHED":
		case "DASHBOARD_ANALYSIS_REVISION_REQUIRED":
			return componentName
				? `组件“${componentName}”没有可用的发布版本，请重新发布或替换分析。`
				: "看板组件没有可用的分析发布版本。";
		case "DASHBOARD_CLASSIFICATION_DOWNGRADE":
			return "看板发布密级不能低于其数据依赖密级。";
		case "DASHBOARD_EXPIRY_INVALID":
			return "有效期必须晚于当前时间。";
		default:
			return issue.message || "发布校验未通过，请检查配置。";
	}
}
