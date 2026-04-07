import { toFilters, toTable } from "../utils/viewHelpers";
import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../../i18n";
import { analyticsApi, type ProjectCockpitRiskAttributionResponse } from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { DataTable } from "../../../components/DataTable";
import { ChartRenderer } from "../../../components/charts/ChartRenderer";
import { Spin } from "antd";
import { DelayReasonMatrix, HealthScoreCard, TrendPanel } from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";



export default function RiskAttributionView({ locale }: { locale: Locale }) {
	const { effectiveQueryState, openDrill } = useProjectCockpitContext();
	const [data, setData] = useState<ProjectCockpitRiskAttributionResponse | null>(null);
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
			.getProjectCockpitRiskAttribution(filters)
			.then((value) => {
				if (cancelled) return;
				setData(value);
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

	const riskChart = useMemo(
		() => ({
			cols: [
				{ name: "name", display_name: "风险等级" },
				{ name: "value", display_name: "数量" },
			],
			rows: (data?.riskBreakdown ?? []).map((item) => [item.name ?? "", item.value ?? 0]),
		}),
		[data?.riskBreakdown],
	);

	const reasonChart = useMemo(
		() => ({
			cols: [
				{ name: "label", display_name: "延期原因" },
				{ name: "value", display_name: "数量" },
			],
			rows: (data?.delayReasonBreakdown ?? []).map((item) => [item.label ?? item.name ?? "", item.value ?? 0]),
		}),
		[data?.delayReasonBreakdown],
	);

	const weeklyDelayChart = useMemo(
		() => ({
			cols: [
				{ name: "weekLabel", display_name: "周度" },
				{ name: "delayedNodes", display_name: "延期节点" },
				{ name: "highRiskNodes", display_name: "高风险节点" },
			],
			rows: (data?.weeklyDelayTrend ?? []).map((item) => [
				item.weekLabel ?? "",
				item.delayedNodes ?? 0,
				item.highRiskNodes ?? 0,
			]),
		}),
		[data?.weeklyDelayTrend],
	);

	const delayedTable = useMemo(
		() =>
			toTable((data?.delayedProjects ?? []) as Array<Record<string, unknown>>, [
				{ key: "majorProjectName", label: "项目" },
				{ key: "subprojectName", label: "子项目" },
				{ key: "nodeTask", label: "节点" },
				{ key: "riskLevel", label: "风险" },
				{ key: "delayDays", label: "延期天数" },
				{ key: "reason", label: "原因" },
				{ key: "dept", label: "责任科室" },
			]),
		[data?.delayedProjects],
	);

	return (
		<div className="project-cockpit__view">
			<div className="project-cockpit__metric-grid">
				<HealthScoreCard
					label="高风险节点"
					value={String((data?.riskBreakdown ?? []).find((item) => item.name === "高")?.value ?? 0)}
					unit="个"
					tone="error"
					onDrill={() => openDrill("high-risk", { riskLevel: "高" })}
				/>
				<HealthScoreCard
					label="延期项目"
					value={String((data?.delayedProjects ?? []).length)}
					unit="项"
					tone="warning"
					onDrill={() => openDrill("overdue")}
				/>
				<HealthScoreCard
					label="延期主因"
					value={String((data?.delayReasonBreakdown ?? [])[0]?.label ?? "正常推进")}
					tone="info"
				/>
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel title="风险等级分布">
					{loading ? (
						<div className="project-cockpit__loading-card"><Spin size="large" /></div>
					) : (
						<div className="project-cockpit__chart-block">
							<ChartRenderer
								display="pie"
								data={riskChart}
								settings={{
									"graph.dimensions": ["name"],
									"graph.metrics": ["value"],
									"pie.show_total": true,
									"graph.colors": ["#dc2626", "#f59e0b", "#60a5fa", "#94a3b8"],
								}}
							/>
						</div>
					)}
				</TrendPanel>
				<TrendPanel title="延期原因结构">
					<div className="project-cockpit__chart-block">
						<ChartRenderer
							display="row"
							data={reasonChart}
							settings={{
								"graph.dimensions": ["label"],
								"graph.metrics": ["value"],
								"graph.colors": ["#7c3aed"],
							}}
						/>
					</div>
				</TrendPanel>
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel title="延期趋势">
					<div className="project-cockpit__chart-block">
						<ChartRenderer
							display="line"
							data={weeklyDelayChart}
							settings={{
								"graph.dimensions": ["weekLabel"],
								"graph.metrics": ["delayedNodes", "highRiskNodes"],
								"graph.colors": ["#f59e0b", "#dc2626"],
								"graph.x_axis.label_rotate": 36,
							}}
						/>
					</div>
				</TrendPanel>

				<TrendPanel title="延期原因矩阵">
					<DelayReasonMatrix
						rows={(data?.delayReasonMatrix ?? []) as Array<Record<string, unknown>>}
						onDrillReason={(dept, reason) => openDrill("delay-reason", { dept, reason })}
						onDrillDept={(dept) => openDrill("delay-dept", { dept })}
					/>
				</TrendPanel>
			</div>

			<TrendPanel title="重点延期项目">
				<DataTable cols={delayedTable.cols} rows={delayedTable.rows} pageSize={8} />
			</TrendPanel>

			{error ? <ErrorNotice locale={locale} error={error} /> : null}
		</div>
	);
}
