import { Focus, Minus, Plus, RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
	classifyModelingRelationshipGraphFailure,
	listModelingRelationshipPlans,
	loadModelingRelationshipGraph,
	type ModelingRelationshipGraphFailure,
	modelingRelationshipNodePath,
	type WarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraphKind,
	type WarehousePlanRelationshipGraphNode,
} from "@/api/services/modelingRelationshipGraphService";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";

const kindOrder: WarehousePlanRelationshipGraphKind[] = ["DIMENSION", "MODEL", "STANDARD", "INDICATOR"];
const kindLabel: Record<WarehousePlanRelationshipGraphKind, string> = {
	PLAN: "规划",
	DIMENSION: "维度",
	MODEL: "模型",
	STANDARD: "数据标准",
	INDICATOR: "数据指标",
};
const classByKind: Record<WarehousePlanRelationshipGraphKind, string> = {
	PLAN: "domain",
	DIMENSION: "domain",
	MODEL: "model",
	STANDARD: "standard",
	INDICATOR: "metric",
};

type PositionedNode = WarehousePlanRelationshipGraphNode & { x: number; y: number };

const allowedKinds = (view: string): Set<WarehousePlanRelationshipGraphKind> =>
	view === "standards"
		? new Set(["MODEL", "STANDARD"])
		: view === "metrics"
			? new Set(["MODEL", "INDICATOR"])
			: new Set(["DIMENSION", "MODEL"]);

function positionNodes(graph: WarehousePlanRelationshipGraph, view: string, query: string): PositionedNode[] {
	const allowed = allowedKinds(view);
	const candidates = graph.nodes.filter((node) => allowed.has(node.kind));
	const normalized = query.trim().toLocaleLowerCase();
	const visibleIds = new Set(
		(normalized
			? candidates.filter((node) => `${node.label} ${node.status || ""}`.toLocaleLowerCase().includes(normalized))
			: candidates
		).map((node) => node.id),
	);
	if (normalized) {
		for (const edge of graph.edges) {
			if (visibleIds.has(edge.source) || visibleIds.has(edge.target)) {
				visibleIds.add(edge.source);
				visibleIds.add(edge.target);
			}
		}
	}
	const nodes = candidates.filter((node) => visibleIds.has(node.id)).slice(0, 120);
	const populated = kindOrder.filter((kind) => nodes.some((node) => node.kind === kind));
	const rows = new Map<WarehousePlanRelationshipGraphKind, number>();
	const totals = new Map(populated.map((kind) => [kind, nodes.filter((node) => node.kind === kind).length]));
	return nodes.map((node) => {
		const column = Math.max(0, populated.indexOf(node.kind));
		const row = rows.get(node.kind) || 0;
		rows.set(node.kind, row + 1);
		const total = totals.get(node.kind) || 1;
		return {
			...node,
			x: populated.length <= 1 ? 42 : 4 + column * (78 / (populated.length - 1)),
			y: total <= 1 ? 50 : 10 + row * (80 / (total - 1)),
		};
	});
}

export function RelationshipGraphPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const requestEpoch = useRef(0);
	const [planId, setPlanId] = useState("");
	const [graph, setGraph] = useState<WarehousePlanRelationshipGraph | null>(null);
	const [query, setQuery] = useState("");
	const [scale, setScale] = useState(1);
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
		setQuery("");
		setScale(1);
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);
	const nodes = useMemo(() => (graph ? positionNodes(graph, route.view, query) : []), [graph, query, route.view]);
	const nodeMap = useMemo(() => new Map(nodes.map((node) => [node.id, node])), [nodes]);
	const edges = useMemo(
		() => graph?.edges.filter((edge) => nodeMap.has(edge.source) && nodeMap.has(edge.target)) || [],
		[graph?.edges, nodeMap],
	);

	return (
		<main className="dmx-page dmx-graph-page">
			<PageHeader
				actions={
					<Button disabled={loading} onClick={() => void load(planId, query)}>
						<RefreshCw size={15} />
						刷新数据
					</Button>
				}
				description={route.description}
				title={route.title}
				trail="数据建模 / 关系图"
			/>
			<div className="dmx-graph-toolbar">
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
				<button
					aria-label="缩小"
					disabled={!nodes.length}
					onClick={() => setScale((value) => Math.max(0.6, value - 0.1))}
					type="button"
				>
					<Minus size={15} />
				</button>
				<button
					aria-label="放大"
					disabled={!nodes.length}
					onClick={() => setScale((value) => Math.min(1.4, value + 0.1))}
					type="button"
				>
					<Plus size={15} />
				</button>
				<button aria-label="适应画布" disabled={!nodes.length} onClick={() => setScale(1)} type="button">
					<Focus size={15} />
				</button>
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
				<RequestState
					description="服务端尚未返回可用的模型关系投影。"
					kind="empty"
					title="暂无关系数据"
				/>
			) : !nodes.length ? (
				<RequestState
					description="当前筛选没有返回权威节点，可清空检索词后重试。"
					kind="empty"
					title="暂无关系数据"
				/>
			) : (
				<div className="dmx-graph-canvas">
					<div className="dmx-graph-scale" style={{ transform: `scale(${scale})` }}>
						<svg aria-hidden="true" preserveAspectRatio="none" viewBox="0 0 100 100">
							{edges.map((edge) => {
								const from = nodeMap.get(edge.source);
								const to = nodeMap.get(edge.target);
								if (!from || !to) return null;
								return (
									<path
										d={`M${from.x + 8} ${from.y} C${(from.x + to.x) / 2} ${from.y}, ${(from.x + to.x) / 2} ${to.y}, ${to.x} ${to.y}`}
										key={`${edge.source}:${edge.target}:${edge.kind}`}
									/>
								);
							})}
						</svg>
						{nodes.map((node) => {
							const path = modelingRelationshipNodePath(node);
							return (
								<button
									className={`dmx-graph-node dmx-graph-node--${classByKind[node.kind]}`}
									disabled={!path}
									key={node.id}
									onClick={() => path && navigate(path)}
									style={{ left: `${node.x}%`, top: `${node.y}%` }}
									title={path ? `打开${kindLabel[node.kind]}` : "服务端未提供可访问详情"}
									type="button"
								>
									<small>{kindLabel[node.kind]}</small>
									<strong>{node.label}</strong>
									<span>{node.status || ""}</span>
								</button>
							);
						})}
					</div>
					{graph?.truncated ? (
						<div className="dmx-graph-truncated">
							<Status tone="warning">投影未完全展开</Status>
							<span>{graph.nextHint || "请使用搜索缩小范围。"}</span>
						</div>
					) : null}
				</div>
			)}
		</main>
	);
}
