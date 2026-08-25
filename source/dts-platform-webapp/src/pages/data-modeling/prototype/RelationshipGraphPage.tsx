import { Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import {
	classifyModelingRelationshipGraphFailure,
	listModelingRelationshipPlans,
	loadModelingRelationshipGraph,
	type ModelingRelationshipContextHeader,
	type ModelingRelationshipGraph,
	type ModelingRelationshipGraphFailure,
	modelingRelationshipNodePath,
} from "@/api/services/modelingRelationshipGraphService";
import { LineageGraph } from "@/components/lineage";
import { statusLabel } from "@/utils/customerDisplayLabels";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";
import { projectModelingRelationshipGraph } from "./relationshipGraphProjection";

const emptyStateByView: Record<string, { title: string; description: string }> = {
	models: {
		title: "暂无模型关系",
		description: "当前规划中的模型尚未建立模型依赖或维度引用关系。",
	},
	standards: {
		title: "暂无标准关系",
		description: "当前规划中的模型字段尚未绑定可展示的数据标准。",
	},
	metrics: {
		title: "暂无指标血缘",
		description: "当前规划中的模型与指标尚未建立引用或依赖关系。",
	},
};

export function RelationshipGraphPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const requestEpoch = useRef(0);
	const initialQuery = useRef(searchParams.get("query") || "");
	const initialPlanId = useRef(searchParams.get("planId") || "");
	const [plans, setPlans] = useState<ModelingRelationshipContextHeader[]>([]);
	const [planId, setPlanId] = useState("");
	const [graph, setGraph] = useState<ModelingRelationshipGraph | null>(null);
	const [query, setQuery] = useState("");
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<ModelingRelationshipGraphFailure | null>(null);
	const load = useCallback(
		async (requestedPlanId?: string, requestedQuery = "") => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setFailure(null);
			try {
				const nextPlans = await listModelingRelationshipPlans();
				if (requestEpoch.current !== epoch) return;
				setPlans(nextPlans);
				const nextPlanId = nextPlans.some((plan) => plan.id === requestedPlanId)
					? requestedPlanId || ""
					: nextPlans[0]?.id || "";
				const nextGraph = nextPlanId
					? await loadModelingRelationshipGraph(nextPlanId, { view: route.view, query: requestedQuery })
					: null;
				if (requestEpoch.current !== epoch) return;
				setPlanId(nextPlanId);
				setGraph(nextGraph);
			} catch (error) {
				if (requestEpoch.current !== epoch) return;
				setPlans([]);
				setPlanId("");
				setGraph(null);
				setFailure(classifyModelingRelationshipGraphFailure(error));
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[route.view],
	);
	useEffect(() => {
		setQuery(initialQuery.current);
		void load(initialPlanId.current, initialQuery.current);
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);
	const projection = useMemo(
		() => (graph ? projectModelingRelationshipGraph(graph, route.view, query) : null),
		[graph, query, route.view],
	);
	const sourceNodeMap = useMemo(() => new Map(graph?.nodes.map((node) => [node.id, node]) || []), [graph?.nodes]);
	const sourceNodeMapRef = useRef(sourceNodeMap);
	sourceNodeMapRef.current = sourceNodeMap;
	const openNode = useCallback(
		(node: { id?: string }) => {
			const source = node.id ? sourceNodeMapRef.current.get(node.id) : null;
			const path = source ? modelingRelationshipNodePath(source) : null;
			if (path) navigate(path);
		},
		[navigate],
	);
	const emptyState = emptyStateByView[route.view] || emptyStateByView.models;

	return (
		<main className="dmx-page dmx-graph-page">
			<PageHeader
				actions={
					<Button disabled={loading} onClick={() => void load(planId, query)}>
						刷新数据
					</Button>
				}
				description={route.description}
				title={route.title}
				trail="数据建模 / 关系图"
			/>
			<div className="dmx-graph-toolbar">
				<select
					aria-label="选择数仓规划"
					disabled={loading || !plans.length}
					onChange={(event) => void load(event.target.value, query)}
					value={planId}
				>
					{plans.length ? null : <option value="">暂无可用规划</option>}
					{plans.map((plan) => (
						<option key={plan.id} value={plan.id}>
							{plan.name} · {statusLabel(plan.lifecycleStatus)}
						</option>
					))}
				</select>
				<div className="dmx-graph-search">
					<Search size={14} />
					<input
						aria-label="搜索关系节点"
						disabled={!planId || loading}
						onChange={(event) => setQuery(event.target.value)}
						onKeyDown={(event) => {
							if (event.key === "Enter") void load(planId, query);
						}}
						placeholder="搜索节点"
						value={query}
					/>
				</div>
				<Button disabled={!planId || loading} onClick={() => void load(planId, query)}>
					查询
				</Button>
				<span />
				{projection?.nodes.length ? (
					<strong className="dmx-graph-summary">
						{projection.nodes.length} 个节点 · {projection.edges.length} 条关系
					</strong>
				) : null}
			</div>
			{loading ? (
				<RequestState description="正在读取模型关系投影。" kind="loading" title="正在加载关系图" />
			) : failure ? (
				<RequestState
					description={failure.message}
					kind={failure.kind === "permission" ? "permission" : "error"}
					onRetry={failure.kind === "request" ? () => void load(planId, query) : undefined}
					title={failure.kind === "permission" ? "无权访问关系图" : "关系图加载失败"}
				/>
			) : !graph ? (
				<RequestState description="服务端尚未返回可用的模型关系投影。" kind="empty" title="暂无关系数据" />
			) : !projection?.nodes.length ? (
				<RequestState
					description={query.trim() ? "当前搜索没有命中已建立的关系，请调整关键词后重试。" : emptyState.description}
					kind="empty"
					title={query.trim() ? "未找到关联节点" : emptyState.title}
				/>
			) : (
				<div className="dmx-graph-canvas">
					<LineageGraph
						edges={projection.edges}
						emptyText={emptyState.title}
						height={620}
						layoutDirection="LR"
						minimumReadableZoom={0.65}
						nodes={projection.nodes}
						onNodeClick={openNode}
					/>
					{graph.truncated || projection.limited ? (
						<div className="dmx-graph-truncated">
							<Status tone="warning">当前图谱已精简</Status>
							<span>
								{projection.limited
									? `共有 ${projection.totalNodeCount} 个关联节点，当前展示前 80 个，请搜索定位。`
									: graph.nextHint || "请使用搜索缩小范围。"}
							</span>
						</div>
					) : null}
				</div>
			)}
		</main>
	);
}
