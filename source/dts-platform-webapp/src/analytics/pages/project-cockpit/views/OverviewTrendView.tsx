// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { toFilters, toTable } from "../utils/viewHelpers";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Locale } from "../../../i18n";
import { analyticsApi, type ProjectCockpitSummaryResponse, type ProjectCockpitTrendsResponse } from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { DataTable } from "../../../components/DataTable";
import { ChartRenderer } from "../../../components/charts/ChartRenderer";
import { HealthScoreCard, TrendPanel } from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";
import { buildOverviewTrendSnapshot, computeWeeklyKpiTrend } from "./overviewTrendView.helpers";
import { exportChartPng, exportCsv } from "../utils/csvExport";
import { Spin, Button } from "antd";

type Props = {
	summary: ProjectCockpitSummaryResponse | null;
	summaryLoading: boolean;
	locale: Locale;
};



const KPI_DRILL_MAP: Record<string, { target: "high-risk" | "overdue" | "completion" | "milestone"; params?: Record<string, unknown> }> = {
	highRiskNodeCount: { target: "high-risk", params: { riskLevel: "高" } },
	overdueNodeCount: { target: "overdue" },
	completionRate: { target: "completion" },
	milestoneCompletionRate: { target: "milestone", params: { nodeType: "milestone" } },
};

const KPI_TREND_MAP: Record<string, { metric: "completionRate" | "delayedNodes" | "highRiskNodes"; polarity: "positive" | "negative" }> = {
	highRiskNodeCount: { metric: "highRiskNodes", polarity: "negative" },
	overdueNodeCount: { metric: "delayedNodes", polarity: "negative" },
	completionRate: { metric: "completionRate", polarity: "positive" },
};

export default function OverviewTrendView({ summary, summaryLoading, locale }: Props) {
	const { effectiveQueryState, openDrill } = useProjectCockpitContext();
	const [trends, setTrends] = useState<ProjectCockpitTrendsResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);

	const filters = useMemo(
		() => toFilters(effectiveQueryState),
		[
			effectiveQueryState.dateFrom,
			effectiveQueryState.dateTo,
			effectiveQueryState.deptId,
			effectiveQueryState.majorProjectId,
			effectiveQueryState.riskLevel,
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
				{ key: "majorProjectName", label: "项目" },
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
				{(summary?.kpis ?? []).map((item) => {
					const drill = item.key ? KPI_DRILL_MAP[item.key] : undefined;
					const trendCfg = item.key ? KPI_TREND_MAP[item.key] : undefined;
					const trend = trendCfg ? computeWeeklyKpiTrend(weekly, trendCfg.metric) : undefined;
					return (
						<HealthScoreCard
							key={item.key}
							label={item.label ?? ""}
							value={item.value ?? "0"}
							unit={item.unit}
							tone={item.key === "highRiskNodeCount" ? "error" : item.key === "overdueNodeCount" ? "warning" : "info"}
							hint={item.key === "highRiskNodeCount" ? `重点关注：${snapshot.topProjectName || "--"}` : undefined}
							onDrill={drill ? () => openDrill(drill.target, drill.params) : undefined}
							trend={trend}
							trendPolarity={trendCfg?.polarity}
						/>
					);
				})}
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel
					title="项目趋势"
					subtitle={`最近周度变化：${snapshot.lastWeekLabel || "--"}，完成率 ${snapshot.lastWeekCompletionRate}%`}
					action={
						<Button
							type="text"
							size="small"
							onClick={() => {
								const svg = document.querySelector<SVGSVGElement>(".project-cockpit__chart-block svg");
								exportChartPng(svg, "项目趋势");
							}}
						>
							导出
						</Button>
					}
				>
					{loading ? (
						<div className="project-cockpit__loading-card"><Spin size="large" /></div>
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
									"graph.reference_lines": [
										{ y: 80, color: "#dc2626", label: "目标 80%", dashArray: "6 4" },
									],
								}}
							/>
						</div>
					)}
				</TrendPanel>

				<TrendPanel title="总览摘要">
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
				<TrendPanel
					title="项目排名"
					action={
						<Button
							type="text"
							size="small"
							onClick={() => {
								const cols = [
									{ key: "majorProjectName", label: "项目" },
									{ key: "healthScore", label: "健康度" },
									{ key: "highRiskCount", label: "高风险" },
									{ key: "overdueCount", label: "延期" },
									{ key: "topDelayReason", label: "主要归因" },
								];
								exportCsv(cols, (summary?.ranking ?? []) as Array<Record<string, unknown>>, "项目排名");
							}}
						>
							导出
						</Button>
					}
				>
					<DataTable cols={rankingTable.cols} rows={rankingTable.rows} pageSize={6} />
				</TrendPanel>

				<TrendPanel title="重点预警">
					{summaryLoading ? (
						<div className="project-cockpit__loading-card"><Spin size="large" /></div>
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
