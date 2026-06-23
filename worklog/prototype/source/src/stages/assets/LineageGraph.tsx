import { Background, Handle, Position, ReactFlow, ReactFlowProvider } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { Node, NodeProps } from "@xyflow/react";
import { useMemo } from "react";
import type { DatasetLineage, LineageKind } from "@/types/asset";

type LineageNodeData = { label: string; kind: LineageKind; [key: string]: unknown };
type LineageRFNode = Node<LineageNodeData, "lineage">;

const KIND_STYLE: Record<LineageKind, { color: string; bg: string; tag: string }> = {
	source: { color: "var(--ink-muted)", bg: "var(--surface-sunken)", tag: "源" },
	model: { color: "var(--accent)", bg: "var(--accent-soft)", tag: "模型" },
	dataset: { color: "var(--success)", bg: "var(--success-soft)", tag: "资产" },
	metric: { color: "var(--warning)", bg: "var(--warning-soft)", tag: "指标" },
};
const dot = { width: 7, height: 7, background: "var(--hairline-strong)", border: "none" };

function LineageNodeView({ data }: NodeProps<LineageRFNode>) {
	const s = KIND_STYLE[data.kind];
	return (
		<div
			style={{
				display: "flex",
				alignItems: "center",
				gap: 6,
				padding: "6px 12px",
				borderRadius: 999,
				border: `1.5px solid ${s.color}`,
				background: s.bg,
				fontSize: 12,
				fontWeight: 600,
				color: "var(--ink)",
				whiteSpace: "nowrap",
			}}
		>
			<Handle type="target" position={Position.Left} style={dot} />
			<span style={{ fontSize: 10, color: s.color }}>{s.tag}</span>
			{data.label}
			<Handle type="source" position={Position.Right} style={dot} />
		</div>
	);
}

const nodeTypes = { lineage: LineageNodeView };

/** 只读血缘 DAG（reactflow；现网用 G6，回植可替换）。 */
export function LineageGraph({ lineage }: { lineage: DatasetLineage }) {
	const nodes = useMemo<LineageRFNode[]>(
		() => lineage.nodes.map((n) => ({ id: n.id, type: "lineage", position: { x: n.x, y: n.y }, data: { label: n.label, kind: n.kind } })),
		[lineage],
	);
	const edges = useMemo(() => lineage.edges.map((e, i) => ({ id: `le-${i}`, source: e.source, target: e.target, animated: true })), [lineage]);

	return (
		<div style={{ height: 280, border: "1px solid var(--hairline)", borderRadius: "var(--radius-md)", background: "var(--surface)" }}>
			<ReactFlowProvider>
				<ReactFlow
					nodes={nodes}
					edges={edges}
					nodeTypes={nodeTypes}
					fitView
					fitViewOptions={{ padding: 0.2 }}
					nodesDraggable={false}
					nodesConnectable={false}
					elementsSelectable={false}
					proOptions={{ hideAttribution: true }}
					minZoom={0.3}
				>
					<Background gap={16} color="hsl(220, 16%, 92%)" />
				</ReactFlow>
			</ReactFlowProvider>
		</div>
	);
}
