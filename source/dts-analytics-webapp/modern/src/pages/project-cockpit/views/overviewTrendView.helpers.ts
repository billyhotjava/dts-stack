type KpiCard = {
	key?: string;
	label?: string;
	value?: string;
	unit?: string;
};

type RankingRow = {
	majorProjectName?: string;
	healthScore?: number;
	highRiskCount?: number;
	overdueCount?: number;
};

type AlertRow = {
	title?: string;
	riskLevel?: string;
	delayDays?: number;
};

type WeeklyPoint = {
	weekLabel?: string;
	completionRate?: number;
	delayedNodes?: number;
	highRiskNodes?: number;
};

export function buildOverviewTrendSnapshot(
	kpis: KpiCard[],
	ranking: RankingRow[],
	alerts: AlertRow[],
	weekly: WeeklyPoint[],
) {
	const sortedRanking = [...ranking].sort((left, right) => {
		const leftRisk = (left.highRiskCount ?? 0) + (left.overdueCount ?? 0);
		const rightRisk = (right.highRiskCount ?? 0) + (right.overdueCount ?? 0);
		if (rightRisk !== leftRisk) {
			return rightRisk - leftRisk;
		}
		return (left.healthScore ?? 0) - (right.healthScore ?? 0);
	});
	const headlineRiskCard = kpis.find((item) => item.key === "highRiskNodeCount") ?? kpis[0];
	const lastWeek = weekly.at(-1);
	return {
		headlineRiskLabel: headlineRiskCard?.label ?? "",
		headlineRiskValue: `${headlineRiskCard?.value ?? "0"}${headlineRiskCard?.unit ?? ""}`,
		topProjectName: sortedRanking[0]?.majorProjectName ?? "",
		topProjectRiskCount:
			(sortedRanking[0]?.highRiskCount ?? 0) + (sortedRanking[0]?.overdueCount ?? 0),
		alertCount: alerts.length,
		criticalAlertCount: alerts.filter((item) => item.riskLevel === "高").length,
		lastWeekLabel: lastWeek?.weekLabel ?? "",
		lastWeekCompletionRate: lastWeek?.completionRate ?? 0,
	};
}
