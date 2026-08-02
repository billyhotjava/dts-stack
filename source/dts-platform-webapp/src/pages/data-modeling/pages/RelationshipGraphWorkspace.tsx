import { Filter, Focus, Minus, Plus, RefreshCw, RotateCcw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
	classifyModelingRelationshipGraphFailure,
	listModelingRelationshipPlans,
	loadModelingRelationshipGraph,
	type ModelingRelationshipGraphFailure,
	modelingRelationshipNodePath,
	type WarehousePlanHeader,
	type WarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraphEdge,
	type WarehousePlanRelationshipGraphKind,
	type WarehousePlanRelationshipGraphNode,
} from "@/api/services/modelingRelationshipGraphService";
import { ActionButton, EmptyState, Panel, StatusTag, WorkspacePage } from "../components/WorkspacePage";
import type { WorkspacePageProps } from "../types";
import "./tools-graphs.css";

const MAX_RENDER_NODES = 120;
const NODE_WIDTH = 142;
const NODE_HEIGHT = 54;
const CANVAS_WIDTH = 930;

const KIND_LABELS: Record<WarehousePlanRelationshipGraphKind, string> = {
	PLAN: "建设计划",
	DIMENSION: "维度",
	MODEL: "模型",
	STANDARD: "数据标准",
	INDICATOR: "数据指标",
};

const KIND_ORDER: WarehousePlanRelationshipGraphKind[] = ["PLAN", "DIMENSION", "MODEL", "STANDARD", "INDICATOR"];

type PositionedNode = WarehousePlanRelationshipGraphNode & { x: number; y: number };

type PositionedGraph = {
	nodes: PositionedNode[];
	edges: WarehousePlanRelationshipGraphEdge[];
	height: number;
};

const edgeKey = (edge: WarehousePlanRelationshipGraphEdge) =>
	`${edge.source}\u0000${edge.target}\u0000${edge.kind}\u0000${edge.label || ""}`;

function mergeGraph(
	current: WarehousePlanRelationshipGraph | null,
	next: WarehousePlanRelationshipGraph,
): WarehousePlanRelationshipGraph {
	if (!current || current.planId !== next.planId) return next;
	const nodeMap = new Map(current.nodes.map((node) => [node.id, node]));
	for (const node of next.nodes) nodeMap.set(node.id, node);
	const edgeMap = new Map(current.edges.map((edge) => [edgeKey(edge), edge]));
	for (const edge of next.edges) edgeMap.set(edgeKey(edge), edge);
	return {
		...next,
		nodes: Array.from(nodeMap.values()).slice(0, MAX_RENDER_NODES),
		edges: Array.from(edgeMap.values()),
		truncated: next.truncated || nodeMap.size > MAX_RENDER_NODES,
	};
}

function allowedKinds(view: string): Set<WarehousePlanRelationshipGraphKind> {
	if (view === "standards") return new Set(["MODEL", "STANDARD"]);
	if (view === "metrics") return new Set(["MODEL", "INDICATOR"]);
	return new Set(["PLAN", "DIMENSION", "MODEL"]);
}

function projectGraph(
	graph: WarehousePlanRelationshipGraph,
	view: string,
	focusKind: WarehousePlanRelationshipGraphKind | "ALL",
): Pick<WarehousePlanRelationshipGraph, "nodes" | "edges"> {
	const allowed = allowedKinds(view);
	const allowedNodes = graph.nodes.filter((node) => allowed.has(node.kind));
	const allowedIds = new Set(allowedNodes.map((node) => node.id));
	const allowedEdges = graph.edges.filter((edge) => allowedIds.has(edge.source) && allowedIds.has(edge.target));
	if (focusKind === "ALL") return { nodes: allowedNodes, edges: allowedEdges };

	const focusIds = new Set(allowedNodes.filter((node) => node.kind === focusKind).map((node) => node.id));
	const contextIds = new Set(focusIds);
	for (const edge of allowedEdges) {
		if (focusIds.has(edge.source) || focusIds.has(edge.target)) {
			contextIds.add(edge.source);
			contextIds.add(edge.target);
		}
	}
	return {
		nodes: allowedNodes.filter((node) => contextIds.has(node.id)),
		edges: allowedEdges.filter(
			(edge) =>
				contextIds.has(edge.source) &&
				contextIds.has(edge.target) &&
				(focusIds.has(edge.source) || focusIds.has(edge.target)),
		),
	};
}

function layoutGraph(
	nodes: WarehousePlanRelationshipGraphNode[],
	edges: WarehousePlanRelationshipGraphEdge[],
	revision: number,
): PositionedGraph {
	if (revision % 2 === 1) {
		const columns = Math.min(5, Math.max(1, Math.ceil(Math.sqrt(nodes.length))));
		const rows = Math.max(1, Math.ceil(nodes.length / columns));
		return {
			nodes: nodes.map((node, index) => ({
				...node,
				x: 34 + (index % columns) * Math.floor((CANVAS_WIDTH - 90) / columns),
				y: 34 + Math.floor(index / columns) * 82,
			})),
			edges,
			height: Math.max(380, rows * 82 + 70),
		};
	}

	const populatedKinds = KIND_ORDER.filter((kind) => nodes.some((node) => node.kind === kind));
	const columns = Math.max(1, populatedKinds.length);
	const columnWidth = Math.floor((CANVAS_WIDTH - NODE_WIDTH - 60) / Math.max(1, columns - 1));
	const rowsByKind = new Map<WarehousePlanRelationshipGraphKind, number>();
	const positioned = nodes.map((node) => {
		const column = Math.max(0, populatedKinds.indexOf(node.kind));
		const row = rowsByKind.get(node.kind) || 0;
		rowsByKind.set(node.kind, row + 1);
		return {
			...node,
			x: 30 + column * columnWidth,
			y: 34 + row * 82,
		};
	});
	const maxRows = Math.max(1, ...Array.from(rowsByKind.values()));
	return { nodes: positioned, edges, height: Math.max(380, maxRows * 82 + 70) };
}

function GraphCanvas({ graph, markerKey }: { graph: PositionedGraph; markerKey: string }) {
	const navigate = useNavigate();
	const [scale, setScale] = useState(1);

	const nodeMap = useMemo(() => new Map(graph.nodes.map((node) => [node.id, node])), [graph.nodes]);
	const legend = useMemo(
		() => KIND_ORDER.filter((kind) => graph.nodes.some((node) => node.kind === kind)),
		[graph.nodes],
	);

	return (
		<div className="dm-graph-shell">
			<div className="dm-graph-toolbar">
				<div className="dm-graph-legend">
					{legend.map((kind) => (
						<span key={kind}>
							<i data-tone={KIND_ORDER.indexOf(kind) % 4} />
							{KIND_LABELS[kind]}
						</span>
					))}
				</div>
				<div className="dm-page__actions">
					<ActionButton onClick={() => setScale((value) => Math.min(1.4, value + 0.1))} title="放大关系图">
						<Plus aria-hidden="true" size={14} />
					</ActionButton>
					<ActionButton onClick={() => setScale((value) => Math.max(0.6, value - 0.1))} title="缩小关系图">
						<Minus aria-hidden="true" size={14} />
					</ActionButton>
					<ActionButton onClick={() => setScale(1)} title="恢复原始缩放">
						<Focus aria-hidden="true" size={14} />
					</ActionButton>
				</div>
			</div>
			<div className="dm-graph-viewport">
				<div
					className="dm-graph-canvas"
					style={{ height: graph.height, transform: `scale(${scale})`, width: CANVAS_WIDTH }}
				>
					<svg aria-hidden="true" className="dm-graph-edges" viewBox={`0 0 ${CANVAS_WIDTH} ${graph.height}`}>
						<defs>
							<marker id={`dm-arrow-${markerKey}`} markerHeight="7" markerWidth="7" orient="auto" refX="6" refY="3.5">
								<polygon fill="#a5afbf" points="0 0, 7 3.5, 0 7" />
							</marker>
						</defs>
						{graph.edges.map((edge) => {
							const from = nodeMap.get(edge.source);
							const to = nodeMap.get(edge.target);
							if (!from || !to) return null;
							return (
								<g key={edgeKey(edge)}>
									<line
										markerEnd={`url(#dm-arrow-${markerKey})`}
										x1={from.x + NODE_WIDTH / 2}
										x2={to.x + NODE_WIDTH / 2}
										y1={from.y + NODE_HEIGHT / 2}
										y2={to.y + NODE_HEIGHT / 2}
									/>
									<text x={(from.x + to.x) / 2 + NODE_WIDTH / 2} y={(from.y + to.y) / 2 + 18}>
										{edge.label || edge.kind}
									</text>
								</g>
							);
						})}
					</svg>
					{graph.nodes.map((node) => {
						const path = modelingRelationshipNodePath(node);
						return (
							<button
								className="dm-graph-node"
								data-tone={KIND_ORDER.indexOf(node.kind) % 4}
								disabled={!path}
								key={node.id}
								onClick={() => path && navigate(path)}
								style={{ left: node.x, top: node.y }}
								title={path ? `打开${KIND_LABELS[node.kind]}` : "服务端未提供可访问的详情入口"}
								type="button"
							>
								<span>
									{KIND_LABELS[node.kind]}
									{node.status ? ` · ${node.status}` : ""}
								</span>
								<strong>{node.label}</strong>
							</button>
						);
					})}
				</div>
			</div>
		</div>
	);
}

export function RelationshipGraphWorkspace({ route }: WorkspacePageProps) {
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [activePlanId, setActivePlanId] = useState("");
	const [graph, setGraph] = useState<WarehousePlanRelationshipGraph | null>(null);
	const [queryInput, setQueryInput] = useState("");
	const [focusKind, setFocusKind] = useState<WarehousePlanRelationshipGraphKind | "ALL">("ALL");
	const [layoutRevision, setLayoutRevision] = useState(0);
	const [loading, setLoading] = useState(true);
	const [loadingMore, setLoadingMore] = useState(false);
	const [failure, setFailure] = useState<ModelingRelationshipGraphFailure | null>(null);
	const [loadedAt, setLoadedAt] = useState<Date | null>(null);
	const activePlanRef = useRef("");
	const queryRef = useRef("");
	const requestSequence = useRef(0);

	const reloadAll = useCallback(async () => {
		const requestId = ++requestSequence.current;
		setLoading(true);
		setFailure(null);
		try {
			const nextPlans = await listModelingRelationshipPlans();
			if (requestId !== requestSequence.current) return;
			const nextPlanId = nextPlans.some((plan) => plan.id === activePlanRef.current)
				? activePlanRef.current
				: nextPlans[0]?.id || "";
			const nextGraph = nextPlanId
				? await loadModelingRelationshipGraph(nextPlanId, { view: route.view, query: queryRef.current })
				: null;
			if (requestId !== requestSequence.current) return;
			setPlans(nextPlans);
			setActivePlanId(nextPlanId);
			activePlanRef.current = nextPlanId;
			setGraph(nextGraph);
			setLoadedAt(nextGraph ? new Date() : null);
		} catch (error) {
			if (requestId !== requestSequence.current) return;
			setPlans([]);
			setActivePlanId("");
			activePlanRef.current = "";
			setGraph(null);
			setFailure(classifyModelingRelationshipGraphFailure(error));
		} finally {
			if (requestId === requestSequence.current) setLoading(false);
		}
	}, [route.view]);

	const loadGraphPage = useCallback(
		async (planId: string, query: string, cursor?: string, append = false) => {
			const requestId = ++requestSequence.current;
			append ? setLoadingMore(true) : setLoading(true);
			setFailure(null);
			try {
				const nextGraph = await loadModelingRelationshipGraph(planId, {
					view: route.view,
					query,
					...(cursor ? { cursor } : {}),
				});
				if (requestId !== requestSequence.current) return;
				setGraph((current) => (append ? mergeGraph(current, nextGraph) : nextGraph));
				setLoadedAt(new Date());
			} catch (error) {
				if (requestId !== requestSequence.current) return;
				if (!append) setGraph(null);
				setFailure(classifyModelingRelationshipGraphFailure(error));
			} finally {
				if (requestId === requestSequence.current) {
					setLoading(false);
					setLoadingMore(false);
				}
			}
		},
		[route.view],
	);

	useEffect(() => {
		activePlanRef.current = "";
		queryRef.current = "";
		setQueryInput("");
		setFocusKind("ALL");
		setLayoutRevision(0);
		void reloadAll();
		return () => {
			requestSequence.current += 1;
		};
	}, [reloadAll]);

	const projection = useMemo(
		() => (graph ? projectGraph(graph, route.view, focusKind) : { nodes: [], edges: [] }),
		[focusKind, graph, route.view],
	);
	const positioned = useMemo(
		() => layoutGraph(projection.nodes, projection.edges, layoutRevision),
		[layoutRevision, projection.edges, projection.nodes],
	);
	const visibleKinds = useMemo(() => allowedKinds(route.view), [route.view]);
	const availableKinds = useMemo(
		() => KIND_ORDER.filter((kind) => visibleKinds.has(kind) && graph?.nodes.some((node) => node.kind === kind)),
		[graph?.nodes, visibleKinds],
	);
	const atSafetyLimit = Boolean(graph && graph.nodes.length >= MAX_RENDER_NODES && graph.truncated);
	const canLoadMore = Boolean(graph?.truncated && graph.nextCursor && !atSafetyLimit);

	const selectPlan = (planId: string) => {
		setActivePlanId(planId);
		activePlanRef.current = planId;
		queryRef.current = "";
		setQueryInput("");
		setFocusKind("ALL");
		void loadGraphPage(planId, "");
	};

	const applySearch = () => {
		if (!activePlanId || loading) return;
		const query = queryInput.trim();
		queryRef.current = query;
		setFocusKind("ALL");
		void loadGraphPage(activePlanId, query);
	};

	return (
		<WorkspacePage
			actions={
				<>
					<ActionButton disabled={loading || loadingMore} onClick={() => void reloadAll()} title="重新读取权威关系数据">
						<RefreshCw aria-hidden="true" size={15} />
						刷新数据
					</ActionButton>
					<ActionButton
						disabled={positioned.nodes.length === 0 || loading}
						onClick={() => setLayoutRevision((current) => current + 1)}
						title={positioned.nodes.length === 0 ? "暂无节点可重新布局" : "在层级布局与网格布局间切换"}
					>
						<RotateCcw aria-hidden="true" size={15} />
						重新布局
					</ActionButton>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 关系图"
			title={route.title}
		>
			{failure ? (
				<Panel title={failure.kind === "permission" ? "无权访问关系图" : "关系图加载失败"}>
					<div className="dm-stage-notice" role="alert">
						<span>{failure.message}</span>
						<ActionButton disabled={loading} onClick={() => void reloadAll()}>
							重新加载
						</ActionButton>
					</div>
				</Panel>
			) : null}

			<Panel subtitle="关系来自当前建设计划的权威模型、维度、标准和指标投影，不在前端保存副本。" title="关系上下文">
				<div className="dm-toolbar">
					<label className="dm-form-field">
						<span>当前建设计划</span>
						<select
							aria-label="当前建设计划"
							className="dm-select"
							disabled={loading || plans.length === 0}
							onChange={(event) => selectPlan(event.target.value)}
							value={activePlanId}
						>
							{plans.length === 0 ? <option value="">暂无建设计划</option> : null}
							{plans.map((plan) => (
								<option key={plan.id} value={plan.id}>
									{plan.name} · {plan.code}
								</option>
							))}
						</select>
					</label>
					<label className="dm-form-field">
						<span>搜索节点</span>
						<input
							aria-label="搜索关系节点"
							className="dm-input"
							disabled={!activePlanId || loading}
							onChange={(event) => setQueryInput(event.target.value)}
							onKeyDown={(event) => {
								if (event.key === "Enter") {
									event.preventDefault();
									applySearch();
								}
							}}
							placeholder="按名称或标识检索"
							value={queryInput}
						/>
					</label>
					<ActionButton disabled={!activePlanId || loading} onClick={applySearch} title="从服务端重新投影关系">
						<Search aria-hidden="true" size={14} />
						查询
					</ActionButton>
				</div>
				{loading ? (
					<output className="dm-context-strip">
						<StatusTag tone="info">正在加载</StatusTag>
						正在读取当前建设计划的关系投影…
					</output>
				) : null}
				{!loading && !failure && plans.length === 0 ? (
					<EmptyState title="暂无建设计划" description="先创建建设计划并归属模型后，关系图才有权威上下文。" />
				) : null}
			</Panel>

			{graph && !loading ? (
				<Panel
					actions={
						<>
							<label className="dm-graph-filter">
								<Filter aria-hidden="true" size={14} />
								<span>节点类型</span>
								<select
									aria-label="节点类型"
									className="dm-select"
									onChange={(event) => setFocusKind(event.target.value as WarehousePlanRelationshipGraphKind | "ALL")}
									value={focusKind}
								>
									<option value="ALL">全部类型</option>
									{availableKinds.map((kind) => (
										<option key={kind} value={kind}>
											{KIND_LABELS[kind]}（含相邻上下文）
										</option>
									))}
								</select>
							</label>
							<StatusTag tone="success">
								成功 · {projection.nodes.length} 个节点 / {projection.edges.length} 条关系
							</StatusTag>
						</>
					}
					subtitle={loadedAt ? `最近读取：${loadedAt.toLocaleTimeString()}` : "点击有详情入口的节点可继续下钻。"}
					title="关系画布"
				>
					{projection.nodes.length === 0 ? (
						<EmptyState title="暂无关系数据" description="当前筛选没有返回权威节点；可清空检索词或切换建设计划。" />
					) : (
						<GraphCanvas graph={positioned} key={`${route.view}-${layoutRevision}`} markerKey={route.view} />
					)}
					{graph.truncated ? (
						<output className="dm-context-strip">
							<StatusTag tone="warning">投影未完全展开</StatusTag>
							<span>
								{atSafetyLimit
									? "图规模已达安全上限，请使用搜索或节点类型缩小范围。"
									: graph.nextHint || "仍有更多关系。"}
							</span>
							{canLoadMore ? (
								<ActionButton
									disabled={loadingMore}
									onClick={() =>
										void loadGraphPage(activePlanId, queryRef.current, graph.nextCursor || undefined, true)
									}
									title="继续读取服务端关系投影"
								>
									{loadingMore ? "正在加载…" : "加载更多"}
								</ActionButton>
							) : null}
						</output>
					) : null}
				</Panel>
			) : null}
		</WorkspacePage>
	);
}
