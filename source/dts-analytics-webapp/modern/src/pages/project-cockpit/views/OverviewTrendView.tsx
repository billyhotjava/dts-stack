import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../../i18n";
import { analyticsApi, type ProjectCockpitSummaryResponse, type ProjectCockpitTrendsResponse } from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { DataTable } from "../../../components/DataTable";
import { ChartRenderer } from "../../../components/charts/ChartRenderer";
import { Spinner } from "../../../ui/Loading/Spinner";
import { HealthScoreCard, TrendPanel } from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";
import { buildOverviewTrendSnapshot } from "./overviewTrendView.helpers";

type Props = {
	summary: ProjectCockpitSummaryResponse | null;
	summaryLoading: boolean;
	locale: Locale;
};

function toFilters(state: ReturnType<typeof useProjectCockpitContext>["queryState"]) {
	return {
		programId: state.programId || undefined,
		majorProjectId: state.majorProjectId || undefined,
		dateFrom: state.dateFrom || undefined,
		dateTo: state.dateTo || undefined,
		deptId: state.deptId || undefined,
		riskLevel: state.riskLevel || undefined,
	};
}

function toTable(rows: Array<Record<string, unknown>>, columns: Array<{ key: string; label: string }>) {
	return {
		cols: columns.map((column) => ({ name: column.key, display_name: column.label })),
		rows: rows.map((row) => columns.map((column) => row[column.key] ?? "")),
	};
}

export default function OverviewTrendView({ summary, summaryLoading, locale }: Props) {
	const { queryState } = useProjectCockpitContext();
	const [trends, setTrends] = useState<ProjectCockpitTrendsResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);

	const filters = useMemo(
		() => toFilters(queryState),
		[
			queryState.dateFrom,
			queryState.dateTo,
			queryState.deptId,
			queryState.majorProjectId,
			queryState.programId,
			queryState.riskLevel,
		],
	);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		setError(null);
		analyticsApi
			.getProjectCockpitTrends(filters)
			.then((value) => {
				if (cancelled) return;
				setTrends(value);
				setLoading(false);
			})
			.catch((requestError) => {
				if (cancelled) return;
				setError(requestError);
				setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [filters]);

	const weekly = trends?.weekly ?? [];
	const snapshot = buildOverviewTrendSnapshot(
		summary?.kpis ?? [],
		summary?.ranking ?? [],
		summary?.alerts ?? [],
		weekly,
	);

	const weeklyChartData = useMemo(
		() => ({
			cols: [
				{ name: "weekLabel", display_name: "周度" },
				{ name: "completionRate", display_name: "完成率" },
				{ name: "delayedNodes", display_name: "延期节点" },
				{ name: "highRiskNodes", display_name: "高风险节点" },
			],
			rows: weekly.map((item) => [
				item.weekLabel ?? item.label ?? "",
				item.completionRate ?? 0,
				item.delayedNodes ?? 0,
				item.highRiskNodes ?? 0,
			]),
		}),
		[weekly],
	);

	const rankingTable = useMemo(
		() =>
			toTable((summary?.ranking ?? []) as Array<Record<string, unknown>>, [
				{ key: "majorProjectName", label: "重大项目" },
				{ key: "healthScore", label: "健康度" },
				{ key: "highRiskCount", label: "高风险" },
				{ key: "overdueCount", label: "延期" },
				{ key: "topDelayReason", label: "主要归因" },
			]),
		[summary?.ranking],
	);

	return (
		<div className="project-cockpit__view">
			<div className="project-cockpit__metric-grid">
				{(summary?.kpis ?? []).map((item) => (
					<HealthScoreCard
						key={item.key}
						label={item.label ?? ""}
						value={item.value ?? "0"}
						unit={item.unit}
						tone={item.key === "highRiskNodeCount" ? "error" : item.key === "overdueNodeCount" ? "warning" : "info"}
						hint={item.key === "highRiskNodeCount" ? `重点关注：${snapshot.topProjectName || "--"}` : undefined}
					/>
				))}
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel
					title="项目群趋势"
					subtitle={`最近周度变化：${snapshot.lastWeekLabel || "--"}，完成率 ${snapshot.lastWeekCompletionRate}%`}
				>
					{loading ? (
						<div className="project-cockpit__loading-card"><Spinner size="lg" /></div>
					) : (
						<div className="project-cockpit__chart-block">
							<ChartRenderer
								display="line"
								data={weeklyChartData}
								settings={{
									"graph.dimensions": ["weekLabel"],
									"graph.metrics": ["completionRate", "delayedNodes", "highRiskNodes"],
									"graph.colors": ["#2563eb", "#f59e0b", "#dc2626"],
									"graph.show_dots": true,
									"graph.x_axis.label_rotate": 36,
								}}
							/>
						</div>
					)}
				</TrendPanel>

				<TrendPanel
					title="总览摘要"
					subtitle="领导层可以先看风险头部项目和重点异常节点。"
				>
					<div className="project-cockpit__ranking-list">
						<div className="project-cockpit__ranking-row">
							<div className="project-cockpit__ranking-title">
								<strong>当前最高风险项目</strong>
								<span className="project-cockpit__ranking-meta">{snapshot.topProjectName || "--"}</span>
							</div>
							<strong>{snapshot.topProjectRiskCount}</strong>
							<span className="project-cockpit__ranking-meta">高风险+延期</span>
						</div>
						<div className="project-cockpit__ranking-row">
							<div className="project-cockpit__ranking-title">
								<strong>{snapshot.headlineRiskLabel}</strong>
								<span className="project-cockpit__ranking-meta">筛选范围内的风险头部指标</span>
							</div>
							<strong>{snapshot.headlineRiskValue}</strong>
							<span className="project-cockpit__ranking-meta">异常总量</span>
						</div>
						<div className="project-cockpit__ranking-row">
							<div className="project-cockpit__ranking-title">
								<strong>重点预警数</strong>
								<span className="project-cockpit__ranking-meta">高风险告警占比和重点节点</span>
							</div>
							<strong>{snapshot.alertCount}</strong>
							<span className="project-cockpit__ranking-meta">其中高风险 {snapshot.criticalAlertCount}</span>
						</div>
					</div>
				</TrendPanel>
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel title="重大项目排名" subtitle="按高风险、延期和健康度综合排序。">
					<DataTable cols={rankingTable.cols} rows={rankingTable.rows} pageSize={6} />
				</TrendPanel>

				<TrendPanel title="重点预警" subtitle="便于中层执行层快速接手和跟踪。">
					{summaryLoading ? (
						<div className="project-cockpit__loading-card"><Spinner size="lg" /></div>
					) : (
						<div className="project-cockpit__alert-list">
							{(summary?.alerts ?? []).map((item, index) => (
								<div key={`${item.title}-${index}`} className="project-cockpit__alert-item">
									<div className="project-cockpit__alert-title">
										<strong>{item.title}</strong>
										<span className="project-cockpit__alert-meta">
											{item.majorProjectName} / {item.subprojectName}
										</span>
									</div>
									<span className="project-cockpit__alert-meta">
										风险 {item.riskLevel}，延期 {item.delayDays} 天
									</span>
									<span className="project-cockpit__alert-meta">{item.reason}</span>
								</div>
							))}
						</div>
					)}
				</TrendPanel>
			</div>

			{error ? <ErrorNotice locale={locale} error={error} /> : null}
		</div>
	);
}
