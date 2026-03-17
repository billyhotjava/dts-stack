import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../../i18n";
import {
	analyticsApi,
	type ProjectCockpitTreeNode,
	type ProjectCockpitTreeResponse,
} from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { Spinner } from "../../../ui/Loading/Spinner";
import {
	HealthScoreCard,
	ProjectTreeDetailPanel,
	ProjectTreeProgressBoard,
	TrendPanel,
} from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";
import { findTreeNodeById, flattenProjectTree } from "./majorProjectTreeView.helpers";

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

export default function MajorProjectTreeView({ locale }: { locale: Locale }) {
	const { queryState, updateQueryState } = useProjectCockpitContext();
	const [data, setData] = useState<ProjectCockpitTreeResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);
	const [selectedNodeId, setSelectedNodeId] = useState("");

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
			.getProjectCockpitTree(filters)
			.then((value) => {
				if (cancelled) return;
				setData(value);
				setLoading(false);
				if (!queryState.majorProjectId && value.selectedMajorProjectId) {
					updateQueryState({ majorProjectId: String(value.selectedMajorProjectId) });
				}
			})
			.catch((requestError) => {
				if (cancelled) return;
				setError(requestError);
				setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [filters, queryState.majorProjectId, updateQueryState]);

	const flatTree = useMemo(
		() => flattenProjectTree((data?.tree ?? []) as never as Array<ProjectCockpitTreeNode>),
		[data?.tree],
	);

	useEffect(() => {
		if (flatTree.length === 0) {
			setSelectedNodeId("");
			return;
		}
		if (!selectedNodeId || !flatTree.some((item) => item.id === selectedNodeId)) {
			setSelectedNodeId(String(flatTree[0]?.id ?? ""));
		}
	}, [flatTree, selectedNodeId]);

	const selectedNode = useMemo(
		() =>
			findTreeNodeById(
				(data?.tree ?? []) as never as Array<ProjectCockpitTreeNode>,
				selectedNodeId,
			),
		[data?.tree, selectedNodeId],
	);

	return (
		<div className="project-cockpit__view">
			<div className="project-cockpit__metric-grid">
				<HealthScoreCard
					label="当前主项目节点数"
					value={String(data?.summary?.totalNodes ?? 0)}
					unit="个"
					tone="info"
				/>
				<HealthScoreCard
					label="已完成节点"
					value={String(data?.summary?.completedNodes ?? 0)}
					unit="个"
					tone="success"
				/>
				<HealthScoreCard
					label="高风险节点"
					value={String(data?.summary?.highRiskNodes ?? 0)}
					unit="个"
					tone="error"
				/>
				<HealthScoreCard
					label="平均健康度"
					value={String(data?.summary?.avgHealthScore ?? 0)}
					tone="warning"
				/>
			</div>

			<div className="project-cockpit__tree-layout">
				<TrendPanel title="项目树状进度">
					{loading ? (
						<div className="project-cockpit__loading-card"><Spinner size="lg" /></div>
					) : (
						<ProjectTreeProgressBoard
							tree={data?.tree ?? []}
							selectedId={selectedNodeId}
							onSelect={setSelectedNodeId}
						/>
					)}
				</TrendPanel>

				<ProjectTreeDetailPanel node={selectedNode as ProjectCockpitTreeNode | null} />
			</div>

			{error ? <ErrorNotice locale={locale} error={error} /> : null}
		</div>
	);
}
