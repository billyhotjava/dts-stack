import { toFilters, toTable } from "../utils/viewHelpers";
import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../../i18n";
import { analyticsApi, type ProjectCockpitExecutionResponse } from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { DataTable } from "../../../components/DataTable";
import { ChartRenderer } from "../../../components/charts/ChartRenderer";
import { Spin } from "antd";
import {
	ExecutionKpiPanel,
	ProjectGanttBoard,
	TrendPanel,
} from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";
import { buildExecutionSnapshot } from "./executionView.helpers";



export default function ExecutionView({ locale }: { locale: Locale }) {
	const { effectiveQueryState, openDrill } = useProjectCockpitContext();
	const [data, setData] = useState<ProjectCockpitExecutionResponse | null>(null);
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
			.getProjectCockpitExecution(filters)
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

	const snapshot = buildExecutionSnapshot(
		(data?.ganttTasks ?? []) as Array<Record<string, unknown>>,
		(data?.milestones ?? []) as Array<Record<string, unknown>>,
		(data?.dueList ?? []) as Array<Record<string, unknown>>,
		(data?.workload ?? []) as Array<Record<string, unknown>>,
	);

	const workloadChart = useMemo(
		() => ({
			cols: [
				{ name: "dept", display_name: "责任科室" },
				{ name: "activeCount", display_name: "在途任务" },
			],
			rows: (data?.workload ?? []).map((item) => [item.dept ?? "", item.activeCount ?? 0]),
		}),
		[data?.workload],
	);

	const stageChart = useMemo(
		() => ({
			cols: [
				{ name: "name", display_name: "阶段" },
				{ name: "value", display_name: "数量" },
			],
			rows: (data?.stageBuckets ?? []).map((item) => [item.name ?? "", item.value ?? 0]),
		}),
		[data?.stageBuckets],
	);

	const dueTable = useMemo(
		() =>
			toTable((data?.dueList ?? []) as Array<Record<string, unknown>>, [
				{ key: "name", label: "节点" },
				{ key: "dept", label: "责任科室" },
				{ key: "planDate", label: "计划日期" },
				{ key: "status", label: "状态" },
				{ key: "delayDays", label: "延期天数" },
			]),
		[data?.dueList],
	);

	return (
		<div className="project-cockpit__view">
			<ExecutionKpiPanel
				{...snapshot}
				onDrillOverdue={() => openDrill("overdue")}
				onDrillMilestone={() => openDrill("milestone", { nodeType: "milestone" })}
			/>

			<div className="project-cockpit__two-column">
				<TrendPanel title="项目甘特图">
					{loading ? (
						<div className="project-cockpit__loading-card"><Spin size="large" /></div>
					) : (
						<ProjectGanttBoard tasks={data?.ganttTasks ?? []} />
					)}
				</TrendPanel>

				<TrendPanel title="里程碑与近期节点">
					<div className="project-cockpit__milestone-list">
						{(data?.milestones ?? []).map((item, index) => (
							<div key={`${item.name}-${index}`} className="project-cockpit__milestone-item">
								<strong>{String(item.name ?? "--")}</strong>
								<span className="project-cockpit__milestone-meta">
									{String(item.majorProjectName ?? "--")} / {String(item.subprojectName ?? "--")}
								</span>
								<span className="project-cockpit__milestone-meta">
									{String(item.planDate ?? "--")} · {String(item.status ?? "--")}
								</span>
							</div>
						))}
					</div>
				</TrendPanel>
			</div>

			<div className="project-cockpit__two-column">
				<TrendPanel title="责任科室负载">
					<div className="project-cockpit__chart-block">
						<ChartRenderer
							display="row"
							data={workloadChart}
							settings={{
								"graph.dimensions": ["dept"],
								"graph.metrics": ["activeCount"],
								"graph.colors": ["#0f766e"],
							}}
						/>
					</div>
				</TrendPanel>
				<TrendPanel title="节点类型分布">
					<div className="project-cockpit__chart-block">
						<ChartRenderer
							display="pie"
							data={stageChart}
							settings={{
								"graph.dimensions": ["name"],
								"graph.metrics": ["value"],
								"pie.show_total": true,
								"graph.colors": ["#2563eb", "#f59e0b", "#14b8a6", "#ef4444", "#8b5cf6"],
							}}
						/>
					</div>
				</TrendPanel>
			</div>

			<TrendPanel title="到期 / 超期列表">
				<DataTable cols={dueTable.cols} rows={dueTable.rows} pageSize={8} />
			</TrendPanel>

			{error ? <ErrorNotice locale={locale} error={error} /> : null}
		</div>
	);
}
