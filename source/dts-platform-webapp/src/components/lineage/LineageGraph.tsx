import "@/polyfills/legacy-browser";
import { useEffect, useMemo, useRef } from "react";
import G6 from "@antv/g6";
import type { Graph, GraphData, Item, ComboConfig } from "@antv/g6";
import { Empty } from "antd";
import {
	type ColumnLineage,
	type ImpactEdge,
	type ImpactNode,
	type LayoutDirection,
	edgeEndpoint,
	edgeLabel,
	nodeTone,
	relationStroke,
} from "@/pages/catalog/lineageShared";

export interface LineageGraphProps {
	nodes: ImpactNode[];
	edges: ImpactEdge[];
	columnLineages?: ColumnLineage[];
	height?: number | string;
	layoutDirection?: LayoutDirection;
	selectedNodeId?: string | null;
	/** 字段血缘开关：true 时把含列血缘的表渲染为 combo，列作为子节点 */
	showColumns?: boolean;
	/** 关键字高亮：命中节点 highlight，未命中 dim */
	highlightKeyword?: string;
	emptyText?: string;
	showMiniMap?: boolean;
	showToolbar?: boolean;
	onNodeClick?: (node: ImpactNode) => void;
}

const NODE_WIDTH = 200;
const NODE_HEIGHT = 64;
const COLUMN_NODE_WIDTH = 160;
const COLUMN_NODE_HEIGHT = 24;
const PADDING = 24;

const COLUMN_NODE_TYPE = "lineage-column";
const TABLE_NODE_TYPE = "lineage-card";

type LineageNodeModel = {
	id: string;
	type: typeof TABLE_NODE_TYPE;
	label: string;
	subTitle: string;
	detail: string;
	style: { fill: string; stroke: string; radius: number };
	textColor: string;
	raw: ImpactNode;
};

type LineageColumnModel = {
	id: string;
	type: typeof COLUMN_NODE_TYPE;
	label: string;
	comboId: string;
	datasetId: string;
	columnName: string;
};

type LineageEdgeModel = {
	id: string;
	source: string;
	target: string;
	label: string;
	stroke: string;
	dashed: boolean;
	relationType?: string;
	raw: ImpactEdge | ColumnLineage;
	isColumnEdge: boolean;
};

type LineageComboModel = ComboConfig & {
	raw: ImpactNode;
};

type BuildResult = {
	tableNodes: LineageNodeModel[];
	columnNodes: LineageColumnModel[];
	combos: LineageComboModel[];
	edges: LineageEdgeModel[];
};

const toTableNode = (n: ImpactNode): LineageNodeModel => {
	const tone = nodeTone(n);
	const kind = String(n.kind || "dataset").toUpperCase();
	const detail =
		n.kind === "job"
			? n.jobType || n.relationType || "JOB"
			: n.kind === "source"
				? n.type || "SOURCE"
				: n.layer || n.assetType || n.type || "DATASET";
	return {
		id: String(n.id),
		type: TABLE_NODE_TYPE,
		label: String(n.name || n.table || "未知节点"),
		subTitle: kind,
		detail: String(detail),
		style: {
			fill: tone.bg,
			stroke: tone.border,
			radius: n.kind === "job" ? 12 : 6,
		},
		textColor: tone.color,
		raw: n,
	};
};

const buildModel = (
	nodes: ImpactNode[],
	edges: ImpactEdge[],
	columnLineages: ColumnLineage[],
	showColumns: boolean,
): BuildResult => {
	const validNodes = nodes.filter((n) => n.id);

	if (!showColumns || columnLineages.length === 0) {
		const tableNodes = validNodes.map(toTableNode);
		const validIds = new Set(tableNodes.map((m) => m.id));
		const edgeModels = edges
			.map<LineageEdgeModel | null>((e, i) => {
				const from = edgeEndpoint(e, "from");
				const to = edgeEndpoint(e, "to");
				if (!from || !to || !validIds.has(from) || !validIds.has(to)) return null;
				const verifStatus = String(e.verificationStatus || "").toUpperCase();
				return {
					id: String(e.id ?? `e-${i}`),
					source: from,
					target: to,
					label: edgeLabel(e),
					stroke: relationStroke(e.relationType),
					dashed: verifStatus === "KNOWN_UNVERIFIED" || e.relationType === "MANUAL",
					relationType: e.relationType,
					raw: e,
					isColumnEdge: false,
				};
			})
			.filter((m): m is LineageEdgeModel => m !== null);
		return { tableNodes, columnNodes: [], combos: [], edges: edgeModels };
	}

	// 列血缘开启：聚合每个表的列名
	const datasetColumns = new Map<string, Set<string>>();
	for (const c of columnLineages) {
		if (c.upstreamDatasetId && c.upstreamColumn) {
			if (!datasetColumns.has(c.upstreamDatasetId)) datasetColumns.set(c.upstreamDatasetId, new Set());
			datasetColumns.get(c.upstreamDatasetId)!.add(c.upstreamColumn);
		}
		if (c.downstreamDatasetId && c.downstreamColumn) {
			if (!datasetColumns.has(c.downstreamDatasetId)) datasetColumns.set(c.downstreamDatasetId, new Set());
			datasetColumns.get(c.downstreamDatasetId)!.add(c.downstreamColumn);
		}
	}

	const tableNodes: LineageNodeModel[] = [];
	const combos: LineageComboModel[] = [];
	const columnNodes: LineageColumnModel[] = [];

	for (const n of validNodes) {
		const dsid = String(n.id);
		const columns = datasetColumns.get(dsid);
		const isDataset = !n.kind || n.kind === "dataset";
		if (isDataset && columns && columns.size > 0) {
			const tone = nodeTone(n);
			combos.push({
				id: dsid,
				label: String(n.name || n.table || dsid),
				type: "rect",
				padding: [28, 8, 8, 8],
				style: {
					fill: tone.bg,
					stroke: tone.border,
					lineWidth: 1,
					radius: 8,
				},
				labelCfg: {
					position: "top",
					refY: 4,
					style: { fontSize: 12, fontWeight: 600, fill: "#0f172a" },
				},
				raw: n,
			});
			for (const col of columns) {
				columnNodes.push({
					id: `${dsid}::${col}`,
					type: COLUMN_NODE_TYPE,
					label: col,
					comboId: dsid,
					datasetId: dsid,
					columnName: col,
				});
			}
		} else {
			tableNodes.push(toTableNode(n));
		}
	}

	const validNodeIds = new Set([...tableNodes.map((m) => m.id), ...combos.map((c) => String(c.id))]);
	const validColumnIds = new Set(columnNodes.map((c) => c.id));

	const tableEdgeModels = edges
		.map<LineageEdgeModel | null>((e, i) => {
			const from = edgeEndpoint(e, "from");
			const to = edgeEndpoint(e, "to");
			if (!from || !to) return null;
			if (!validNodeIds.has(from) || !validNodeIds.has(to)) return null;
			const verifStatus = String(e.verificationStatus || "").toUpperCase();
			return {
				id: String(e.id ?? `e-${i}`),
				source: from,
				target: to,
				label: edgeLabel(e),
				stroke: relationStroke(e.relationType),
				dashed: verifStatus === "KNOWN_UNVERIFIED" || e.relationType === "MANUAL",
				relationType: e.relationType,
				raw: e,
				isColumnEdge: false,
			};
		})
		.filter((m): m is LineageEdgeModel => m !== null);

	const columnEdgeModels = columnLineages
		.map<LineageEdgeModel | null>((c, i) => {
			if (!c.upstreamDatasetId || !c.upstreamColumn || !c.downstreamDatasetId || !c.downstreamColumn) return null;
			const sourceId = `${c.upstreamDatasetId}::${c.upstreamColumn}`;
			const targetId = `${c.downstreamDatasetId}::${c.downstreamColumn}`;
			if (!validColumnIds.has(sourceId) || !validColumnIds.has(targetId)) return null;
			return {
				id: String(c.id ?? `col-e-${i}`),
				source: sourceId,
				target: targetId,
				label: String(c.relationType || ""),
				stroke: "#1677ff",
				dashed: String(c.confidence || "").toUpperCase() === "INFERRED",
				relationType: c.relationType,
				raw: c,
				isColumnEdge: true,
			};
		})
		.filter((m): m is LineageEdgeModel => m !== null);

	return {
		tableNodes,
		columnNodes,
		combos,
		edges: [...tableEdgeModels, ...columnEdgeModels],
	};
};

const registerCustomNodes = (() => {
	let registered = false;
	return () => {
		if (registered) return;
		registered = true;
		G6.registerNode(
			TABLE_NODE_TYPE,
			{
				draw(cfg, group) {
					const model = cfg as unknown as LineageNodeModel;
					const w = NODE_WIDTH;
					const h = NODE_HEIGHT;
					const rect = group.addShape("rect", {
						attrs: {
							x: -w / 2,
							y: -h / 2,
							width: w,
							height: h,
							radius: model.style.radius,
							fill: model.style.fill,
							stroke: model.style.stroke,
							lineWidth: 1,
							cursor: "pointer",
						},
						name: "card-bg",
						draggable: true,
					});
					group.addShape("text", {
						attrs: {
							x: -w / 2 + 12,
							y: -h / 2 + 18,
							text: `${model.subTitle} · ${model.detail}`,
							fontSize: 10,
							fill: "#64748b",
							textBaseline: "middle",
						},
						name: "card-sub",
						draggable: true,
					});
					group.addShape("text", {
						attrs: {
							x: -w / 2 + 12,
							y: -h / 2 + 40,
							text: model.label.length > 22 ? `${model.label.slice(0, 21)}…` : model.label,
							fontSize: 13,
							fontWeight: 600,
							fill: "#0f172a",
							textBaseline: "middle",
						},
						name: "card-title",
						draggable: true,
					});
					return rect;
				},
				setState(name, value, item) {
					if (!item) return;
					const group = item.getContainer();
					const bg = group.find((shape) => shape.get("name") === "card-bg");
					if (!bg) return;
					if (name === "selected") {
						bg.attr("lineWidth", value ? 3 : 1);
						bg.attr("shadowBlur", value ? 8 : 0);
						bg.attr("shadowColor", value ? "rgba(22,119,255,0.45)" : "transparent");
					}
					if (name === "dimmed") {
						group.attr("opacity", value ? 0.25 : 1);
					}
					if (name === "highlight") {
						bg.attr("lineWidth", value ? 2 : 1);
					}
					if (name === "searched") {
						bg.attr("shadowBlur", value ? 12 : 0);
						bg.attr("shadowColor", value ? "rgba(250,204,21,0.85)" : "transparent");
						bg.attr("stroke", value ? "#facc15" : (item.getModel() as unknown as LineageNodeModel).style.stroke);
						bg.attr("lineWidth", value ? 2.4 : 1);
					}
				},
			},
			"single-node",
		);

		G6.registerNode(
			COLUMN_NODE_TYPE,
			{
				draw(cfg, group) {
					const model = cfg as unknown as LineageColumnModel;
					const w = COLUMN_NODE_WIDTH;
					const h = COLUMN_NODE_HEIGHT;
					const rect = group.addShape("rect", {
						attrs: {
							x: -w / 2,
							y: -h / 2,
							width: w,
							height: h,
							radius: 4,
							fill: "#ffffff",
							stroke: "#cbd5e1",
							lineWidth: 1,
							cursor: "pointer",
						},
						name: "col-bg",
						draggable: true,
					});
					group.addShape("text", {
						attrs: {
							x: -w / 2 + 8,
							y: 0,
							text: model.label.length > 18 ? `${model.label.slice(0, 17)}…` : model.label,
							fontSize: 11,
							fill: "#1f2937",
							textBaseline: "middle",
						},
						name: "col-label",
						draggable: true,
					});
					return rect;
				},
				getAnchorPoints() {
					return [
						[0, 0.5],
						[1, 0.5],
					];
				},
				setState(name, value, item) {
					if (!item) return;
					const group = item.getContainer();
					const bg = group.find((shape) => shape.get("name") === "col-bg");
					if (!bg) return;
					if (name === "selected") {
						bg.attr("lineWidth", value ? 2 : 1);
						bg.attr("stroke", value ? "#1677ff" : "#cbd5e1");
					}
					if (name === "dimmed") {
						group.attr("opacity", value ? 0.25 : 1);
					}
					if (name === "highlight") {
						bg.attr("lineWidth", value ? 1.6 : 1);
						bg.attr("stroke", value ? "#1677ff" : "#cbd5e1");
					}
					if (name === "searched") {
						bg.attr("stroke", value ? "#facc15" : "#cbd5e1");
						bg.attr("lineWidth", value ? 1.8 : 1);
					}
				},
			},
			"single-node",
		);
	};
})();

const computeAdjacency = (edges: LineageEdgeModel[]) => {
	const upstream = new Map<string, Set<string>>();
	const downstream = new Map<string, Set<string>>();
	for (const e of edges) {
		if (!downstream.has(e.source)) downstream.set(e.source, new Set());
		downstream.get(e.source)!.add(e.target);
		if (!upstream.has(e.target)) upstream.set(e.target, new Set());
		upstream.get(e.target)!.add(e.source);
	}
	return { upstream, downstream };
};

const collectReachable = (
	startId: string,
	upstream: Map<string, Set<string>>,
	downstream: Map<string, Set<string>>,
): Set<string> => {
	const out = new Set<string>([startId]);
	const queue: Array<[string, "up" | "down"]> = [[startId, "up"], [startId, "down"]];
	while (queue.length) {
		const [id, dir] = queue.shift()!;
		const map = dir === "up" ? upstream : downstream;
		const next = map.get(id);
		if (!next) continue;
		for (const n of next) {
			if (out.has(n)) continue;
			out.add(n);
			queue.push([n, dir]);
		}
	}
	return out;
};

const matchKeyword = (model: LineageNodeModel | LineageColumnModel, keywordLower: string): boolean => {
	if (!keywordLower) return false;
	if (model.type === COLUMN_NODE_TYPE) {
		const col = model as LineageColumnModel;
		return col.label.toLowerCase().includes(keywordLower) || col.datasetId.toLowerCase().includes(keywordLower);
	}
	const t = model as LineageNodeModel;
	const raw = t.raw;
	const text = `${t.label} ${raw.db || ""} ${raw.table || ""} ${raw.owner || ""} ${raw.ownerDept || ""} ${raw.jobType || ""} ${raw.layer || ""}`.toLowerCase();
	return text.includes(keywordLower);
};

const buildLayoutConfig = (showColumns: boolean, layoutDirection: LayoutDirection) => {
	if (!showColumns) {
		return {
			type: "dagre",
			rankdir: layoutDirection,
			nodesep: 24,
			ranksep: 60,
			controlPoints: true,
		};
	}
	return {
		type: "comboCombined",
		outerLayout: new (G6 as any).Layout.dagre({
			rankdir: layoutDirection,
			nodesep: 30,
			ranksep: 80,
		}),
		innerLayout: new (G6 as any).Layout.grid({
			rows: undefined,
			cols: 1,
			condense: true,
			preventOverlap: true,
		}),
	};
};

export function LineageGraph({
	nodes,
	edges,
	columnLineages = [],
	height = 540,
	layoutDirection = "LR",
	selectedNodeId,
	showColumns = false,
	highlightKeyword = "",
	emptyText = "暂无血缘节点",
	showMiniMap = true,
	showToolbar = true,
	onNodeClick,
}: LineageGraphProps): JSX.Element {
	const containerRef = useRef<HTMLDivElement>(null);
	const graphRef = useRef<Graph | null>(null);
	const lastShowColumnsRef = useRef<boolean>(showColumns);
	const lastDirectionRef = useRef<LayoutDirection>(layoutDirection);

	const built = useMemo(
		() => buildModel(nodes, edges, columnLineages, showColumns),
		[nodes, edges, columnLineages, showColumns],
	);

	useEffect(() => {
		registerCustomNodes();
	}, []);

	useEffect(() => {
		const container = containerRef.current;
		if (!container || graphRef.current) return;

		const width = container.clientWidth || 800;
		const numericHeight = typeof height === "number" ? height : container.clientHeight || 540;

		const plugins: any[] = [];
		if (showMiniMap) {
			plugins.push(
				new G6.Minimap({ size: [160, 100], type: "keyShape", className: "g6-minimap" }),
			);
		}
		if (showToolbar) {
			plugins.push(new G6.ToolBar({ position: { x: 12, y: 12 } }));
		}

		const graph = new G6.Graph({
			container,
			width,
			height: numericHeight,
			fitView: true,
			fitViewPadding: PADDING,
			animate: false,
			plugins,
			modes: {
				default: ["drag-canvas", "zoom-canvas", "drag-node", "drag-combo", "collapse-expand-combo"],
			},
			layout: buildLayoutConfig(lastShowColumnsRef.current, lastDirectionRef.current),
			defaultNode: {
				type: TABLE_NODE_TYPE,
				size: [NODE_WIDTH, NODE_HEIGHT],
			},
			defaultEdge: {
				type: "polyline",
				style: {
					stroke: "#bfbfbf",
					lineWidth: 1.6,
					endArrow: {
						path: G6.Arrow.triangle(8, 10, 0),
						fill: "#8c8c8c",
						stroke: "#8c8c8c",
					},
					radius: 8,
					offset: 16,
				},
				labelCfg: {
					autoRotate: false,
					style: {
						fill: "#595959",
						fontSize: 10,
						fontWeight: 600,
						background: {
							fill: "#ffffff",
							stroke: "#e5e7eb",
							padding: [3, 6, 3, 6],
							radius: 4,
						},
					},
				},
			},
			defaultCombo: {
				type: "rect",
				padding: [28, 8, 8, 8],
			},
			nodeStateStyles: {},
			edgeStateStyles: {
				highlight: { stroke: "#1677ff", lineWidth: 2.4 },
				dimmed: { opacity: 0.18 },
				searched: { stroke: "#facc15", lineWidth: 2.2 },
			},
			comboStateStyles: {
				selected: { lineWidth: 2, stroke: "#1677ff" },
				dimmed: { opacity: 0.35 },
				searched: { stroke: "#facc15", lineWidth: 2.2 },
			},
		});

		graph.on("node:click", (evt) => {
			const item = evt.item as Item | null;
			if (!item) return;
			const model = item.getModel() as unknown as LineageNodeModel | LineageColumnModel;
			if (model.type === COLUMN_NODE_TYPE) return;
			onNodeClick?.((model as LineageNodeModel).raw);
		});

		graph.on("combo:click", (evt) => {
			const item = evt.item as Item | null;
			if (!item) return;
			const model = item.getModel() as unknown as LineageComboModel;
			if (model.raw) onNodeClick?.(model.raw);
		});

		graph.on("canvas:click", () => {
			graph.getNodes().forEach((n) => {
				graph.setItemState(n, "selected", false);
				graph.setItemState(n, "dimmed", false);
				graph.setItemState(n, "highlight", false);
				graph.setItemState(n, "searched", false);
			});
			graph.getEdges().forEach((e) => {
				graph.setItemState(e, "highlight", false);
				graph.setItemState(e, "dimmed", false);
				graph.setItemState(e, "searched", false);
			});
			graph.getCombos().forEach((c) => {
				graph.setItemState(c, "selected", false);
				graph.setItemState(c, "dimmed", false);
				graph.setItemState(c, "searched", false);
			});
		});

		graphRef.current = graph;

		const handleResize = () => {
			if (!containerRef.current || !graphRef.current) return;
			const w = containerRef.current.clientWidth;
			const h = typeof height === "number" ? height : containerRef.current.clientHeight;
			graphRef.current.changeSize(w || 800, h || 540);
		};
		const ro = new ResizeObserver(handleResize);
		ro.observe(container);

		return () => {
			ro.disconnect();
			graph.destroy();
			graphRef.current = null;
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	useEffect(() => {
		const graph = graphRef.current;
		if (!graph) return;

		const layoutChanged =
			lastShowColumnsRef.current !== showColumns || lastDirectionRef.current !== layoutDirection;
		if (layoutChanged) {
			(graph as any).updateLayout(buildLayoutConfig(showColumns, layoutDirection));
			lastShowColumnsRef.current = showColumns;
			lastDirectionRef.current = layoutDirection;
		}

		const data: GraphData = {
			nodes: [
				...built.tableNodes.map((m) => ({ ...m })),
				...built.columnNodes.map((m) => ({ ...m })),
			],
			combos: built.combos.map((c) => ({ ...c })),
			edges: built.edges.map((e) => ({
				id: e.id,
				source: e.source,
				target: e.target,
				label: e.label,
				style: {
					stroke: e.stroke,
					lineWidth: e.relationType === "MANUAL" ? 1.2 : 1.8,
					lineDash: e.dashed ? [4, 4] : undefined,
					endArrow: {
						path: G6.Arrow.triangle(8, 10, 0),
						fill: e.stroke,
						stroke: e.stroke,
					},
				},
			})),
		};
		graph.changeData(data);
		graph.layout();
		graph.fitView(PADDING);
	}, [built, showColumns, layoutDirection]);

	useEffect(() => {
		const graph = graphRef.current;
		if (!graph) return;
		const allNodes = graph.getNodes();
		const allEdges = graph.getEdges();
		const allCombos = graph.getCombos();
		const adj = computeAdjacency(built.edges);
		const reachable = selectedNodeId
			? collectReachable(selectedNodeId, adj.upstream, adj.downstream)
			: null;

		const keywordLower = highlightKeyword.trim().toLowerCase();
		const allModels = [...built.tableNodes, ...built.columnNodes];
		const searchedIds = keywordLower
			? new Set(allModels.filter((m) => matchKeyword(m, keywordLower)).map((m) => m.id))
			: null;
		// 当只有 keyword 没有 selected 时，dim 未命中节点
		const keywordDimSet =
			keywordLower && !reachable
				? new Set(allModels.filter((m) => !searchedIds!.has(m.id)).map((m) => m.id))
				: null;

		allNodes.forEach((n) => {
			const id = String(n.getModel().id);
			if (reachable) {
				graph.setItemState(n, "selected", id === selectedNodeId);
				graph.setItemState(n, "dimmed", !reachable.has(id));
				graph.setItemState(n, "highlight", reachable.has(id) && id !== selectedNodeId);
			} else {
				graph.setItemState(n, "selected", false);
				graph.setItemState(n, "highlight", false);
				graph.setItemState(n, "dimmed", Boolean(keywordDimSet && keywordDimSet.has(id)));
			}
			graph.setItemState(n, "searched", Boolean(searchedIds && searchedIds.has(id)));
		});

		allCombos.forEach((c) => {
			const id = String(c.getModel().id);
			if (reachable) {
				graph.setItemState(c, "selected", id === selectedNodeId);
				graph.setItemState(c, "dimmed", !reachable.has(id));
			} else {
				graph.setItemState(c, "selected", false);
				graph.setItemState(c, "dimmed", Boolean(keywordDimSet && keywordDimSet.has(id)));
			}
			graph.setItemState(c, "searched", Boolean(searchedIds && searchedIds.has(id)));
		});

		allEdges.forEach((e) => {
			const m = e.getModel();
			const sourceId = String(m.source);
			const targetId = String(m.target);
			if (reachable) {
				const onPath = reachable.has(sourceId) && reachable.has(targetId);
				graph.setItemState(e, "highlight", onPath);
				graph.setItemState(e, "dimmed", !onPath);
				graph.setItemState(e, "searched", false);
			} else if (searchedIds) {
				const onSearch = searchedIds.has(sourceId) || searchedIds.has(targetId);
				graph.setItemState(e, "searched", onSearch);
				graph.setItemState(e, "dimmed", !onSearch);
				graph.setItemState(e, "highlight", false);
			} else {
				graph.setItemState(e, "highlight", false);
				graph.setItemState(e, "dimmed", false);
				graph.setItemState(e, "searched", false);
			}
		});
	}, [selectedNodeId, highlightKeyword, built]);

	const isEmpty = built.tableNodes.length === 0 && built.combos.length === 0;

	return (
		<div
			style={{
				height,
				width: "100%",
				position: "relative",
				border: "1px solid #e8e8e8",
				borderRadius: 8,
				overflow: "hidden",
				background: "#fff",
			}}
		>
			{isEmpty ? (
				<div className="flex h-full items-center justify-center">
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
				</div>
			) : null}
			<div ref={containerRef} style={{ width: "100%", height: "100%", display: isEmpty ? "none" : "block" }} />
		</div>
	);
}

export default LineageGraph;
