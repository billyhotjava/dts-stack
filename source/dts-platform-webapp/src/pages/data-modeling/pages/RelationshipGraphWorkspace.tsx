import { Filter, Focus, Minus, Plus, RotateCcw } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import {
	ActionButton,
	BackendPendingButton,
	Panel,
	StatusTag,
	UiStageNotice,
	WorkspacePage,
} from "../components/WorkspacePage";
import type { WorkspacePageProps } from "../types";
import "./tools-graphs.css";

type GraphNode = {
	id: string;
	label: string;
	kind: string;
	x: number;
	y: number;
	path: string;
};

type GraphEdge = {
	from: string;
	to: string;
	label: string;
};

const GRAPH_DATA: Record<string, { nodes: GraphNode[]; edges: GraphEdge[]; legend: string[] }> = {
	models: {
		nodes: [
			{ id: "domain", label: "示例数据域", kind: "数据域", x: 90, y: 185, path: "/data-modeling/planning/domains" },
			{
				id: "process",
				label: "示例业务过程",
				kind: "业务过程",
				x: 310,
				y: 90,
				path: "/data-modeling/planning/processes",
			},
			{ id: "dimension", label: "示例维度", kind: "维度", x: 310, y: 275, path: "/data-modeling/dimensions/workbench" },
			{
				id: "fact",
				label: "示例明细模型",
				kind: "明细表",
				x: 565,
				y: 185,
				path: "/data-modeling/dimensions/workbench",
			},
			{
				id: "summary",
				label: "示例汇总模型",
				kind: "汇总表",
				x: 795,
				y: 185,
				path: "/data-modeling/dimensions/workbench",
			},
		],
		edges: [
			{ from: "domain", to: "process", label: "包含" },
			{ from: "domain", to: "dimension", label: "沉淀" },
			{ from: "process", to: "fact", label: "产生" },
			{ from: "dimension", to: "fact", label: "关联" },
			{ from: "fact", to: "summary", label: "汇总" },
		],
		legend: ["数据域", "业务过程", "维度", "模型"],
	},
	standards: {
		nodes: [
			{
				id: "standard",
				label: "示例字段标准",
				kind: "字段标准",
				x: 100,
				y: 185,
				path: "/data-modeling/standards/fields",
			},
			{ id: "code", label: "示例代码集", kind: "标准代码", x: 330, y: 90, path: "/data-modeling/standards/codes" },
			{
				id: "fieldA",
				label: "维度字段",
				kind: "模型字段",
				x: 330,
				y: 280,
				path: "/data-modeling/dimensions/workbench",
			},
			{
				id: "fieldB",
				label: "明细字段",
				kind: "模型字段",
				x: 590,
				y: 185,
				path: "/data-modeling/dimensions/workbench",
			},
			{ id: "model", label: "示例模型", kind: "模型", x: 800, y: 185, path: "/data-modeling/dimensions/workbench" },
		],
		edges: [
			{ from: "standard", to: "code", label: "引用代码" },
			{ from: "standard", to: "fieldA", label: "映射" },
			{ from: "standard", to: "fieldB", label: "映射" },
			{ from: "fieldA", to: "model", label: "属于" },
			{ from: "fieldB", to: "model", label: "属于" },
		],
		legend: ["字段标准", "标准代码", "模型字段", "模型"],
	},
	metrics: {
		nodes: [
			{
				id: "process",
				label: "示例业务过程",
				kind: "业务过程",
				x: 80,
				y: 185,
				path: "/data-modeling/planning/processes",
			},
			{ id: "field", label: "度量字段", kind: "模型字段", x: 300, y: 185, path: "/data-modeling/dimensions/workbench" },
			{ id: "atomic", label: "示例原子指标", kind: "原子指标", x: 530, y: 95, path: "/data-modeling/metrics/atomic" },
			{
				id: "derived",
				label: "示例派生指标",
				kind: "派生指标",
				x: 530,
				y: 275,
				path: "/data-modeling/metrics/derived",
			},
			{
				id: "composite",
				label: "示例复合指标",
				kind: "复合指标",
				x: 790,
				y: 185,
				path: "/data-modeling/metrics/composite",
			},
		],
		edges: [
			{ from: "process", to: "field", label: "产生" },
			{ from: "field", to: "atomic", label: "度量" },
			{ from: "atomic", to: "derived", label: "派生" },
			{ from: "atomic", to: "composite", label: "组成" },
			{ from: "derived", to: "composite", label: "组成" },
		],
		legend: ["业务过程", "模型字段", "原子指标", "派生/复合指标"],
	},
};

function GraphCanvas({ graphKey }: { graphKey: string }) {
	const navigate = useNavigate();
	const [scale, setScale] = useState(1);
	const graph = GRAPH_DATA[graphKey] || GRAPH_DATA.models;
	const nodeMap = useMemo(() => new Map(graph.nodes.map((node) => [node.id, node])), [graph.nodes]);

	return (
		<div className="dm-graph-shell">
			<div className="dm-graph-toolbar">
				<div className="dm-graph-legend">
					{graph.legend.map((item, index) => (
						<span key={item}>
							<i data-tone={index % 4} />
							{item}
						</span>
					))}
				</div>
				<div className="dm-page__actions">
					<ActionButton onClick={() => setScale((value) => Math.min(1.25, value + 0.1))} title="放大">
						<Plus aria-hidden="true" size={14} />
					</ActionButton>
					<ActionButton onClick={() => setScale((value) => Math.max(0.75, value - 0.1))} title="缩小">
						<Minus aria-hidden="true" size={14} />
					</ActionButton>
					<ActionButton onClick={() => setScale(1)} title="适应画布">
						<Focus aria-hidden="true" size={14} />
					</ActionButton>
				</div>
			</div>
			<div className="dm-graph-viewport">
				<div className="dm-graph-canvas" style={{ transform: `scale(${scale})` }}>
					<svg aria-hidden="true" className="dm-graph-edges" viewBox="0 0 930 380">
						<defs>
							<marker id={`dm-arrow-${graphKey}`} markerHeight="7" markerWidth="7" orient="auto" refX="6" refY="3.5">
								<polygon fill="#a5afbf" points="0 0, 7 3.5, 0 7" />
							</marker>
						</defs>
						{graph.edges.map((edge) => {
							const from = nodeMap.get(edge.from);
							const to = nodeMap.get(edge.to);
							if (!from || !to) return null;
							return (
								<g key={`${edge.from}-${edge.to}`}>
									<line
										markerEnd={`url(#dm-arrow-${graphKey})`}
										x1={from.x + 65}
										x2={to.x + 5}
										y1={from.y + 25}
										y2={to.y + 25}
									/>
									<text x={(from.x + to.x) / 2 + 30} y={(from.y + to.y) / 2 + 16}>
										{edge.label}
									</text>
								</g>
							);
						})}
					</svg>
					{graph.nodes.map((node, index) => (
						<button
							className="dm-graph-node"
							data-tone={index % 4}
							key={node.id}
							onClick={() => navigate(node.path)}
							style={{ left: node.x, top: node.y }}
							type="button"
						>
							<span>{node.kind}</span>
							<strong>{node.label}</strong>
						</button>
					))}
				</div>
			</div>
		</div>
	);
}

export function RelationshipGraphWorkspace({ route }: WorkspacePageProps) {
	return (
		<WorkspacePage
			actions={
				<>
					<ActionButton>
						<RotateCcw aria-hidden="true" size={15} />
						刷新布局
					</ActionButton>
					<BackendPendingButton>导出关系</BackendPendingButton>
				</>
			}
			description={route.description}
			eyebrow="关系图"
			title={route.title}
		>
			<UiStageNotice />
			<Panel
				actions={
					<>
						<label className="dm-graph-filter">
							<Filter aria-hidden="true" size={14} />
							<select aria-label="关系范围" className="dm-select" defaultValue="all">
								<option value="all">全部关系</option>
								<option value="direct">仅直接关系</option>
							</select>
						</label>
						<StatusTag tone="info">界面示例</StatusTag>
					</>
				}
				subtitle="点击节点可进入对应的新数据建模页面。"
				title="关系画布"
			>
				<GraphCanvas graphKey={route.view} />
			</Panel>
		</WorkspacePage>
	);
}
