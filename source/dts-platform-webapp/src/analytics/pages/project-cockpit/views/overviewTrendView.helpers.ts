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

export type KpiTrend = {
	rate: number;
	direction: "up" | "down" | "flat";
};

/**
 * Compute week-over-week trend for a given KPI key from weekly data.
 * Compares the last two weeks.
 */
export function computeWeeklyKpiTrend(
	weekly: WeeklyPoint[],
	metricKey: "completionRate" | "delayedNodes" | "highRiskNodes",
): KpiTrend | undefined {
	if (weekly.length < 2) return undefined;
	const current = weekly[weekly.length - 1];
	const previous = weekly[weekly.length - 2];
	const cur = Number((current as Record<string, unknown>)[metricKey] ?? 0);
	const prev = Number((previous as Record<string, unknown>)[metricKey] ?? 0);
	if (prev === 0 && cur === 0) return { rate: 0, direction: "flat" };
	if (prev === 0) return { rate: 1, direction: "up" };
	const rate = (cur - prev) / Math.abs(prev);
	if (rate === 0) return { rate: 0, direction: "flat" };
	return { rate: Math.abs(rate), direction: rate > 0 ? "up" : "down" };
}

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
