import { Background, Controls, MiniMap, ReactFlow, ReactFlowProvider, useReactFlow } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { App as AntApp } from "antd";
import { useCallback, useEffect } from "react";
import { unwrap } from "@/mock/client";
import { transformService } from "@/mock/services/transformService";
import type { TransformNodeKind } from "@/types/transform";
import { DND_MIME, NodePalette } from "./NodePalette";
import { TransformNodeView } from "./TransformNodeView";
import { useTransformGraphStore } from "./transformGraphStore";

const nodeTypes = { transform: TransformNodeView };

function CanvasInner({ projectSpaceId }: { projectSpaceId: string }) {
	const nodes = useTransformGraphStore((s) => s.nodes);
	const edges = useTransformGraphStore((s) => s.edges);
	const onNodesChange = useTransformGraphStore((s) => s.onNodesChange);
	const onEdgesChange = useTransformGraphStore((s) => s.onEdgesChange);
	const onConnect = useTransformGraphStore((s) => s.onConnect);
	const addNode = useTransformGraphStore((s) => s.addNode);
	const setSelected = useTransformGraphStore((s) => s.setSelected);
	const load = useTransformGraphStore((s) => s.load);
	const lastRejection = useTransformGraphStore((s) => s.lastRejection);
	const clearRejection = useTransformGraphStore((s) => s.clearRejection);
	const { screenToFlowPosition } = useReactFlow();
	const { message } = AntApp.useApp();

	useEffect(() => {
		void transformService.getGraph(projectSpaceId).then((r) => load(unwrap(r)));
	}, [projectSpaceId, load]);

	useEffect(() => {
		if (lastRejection) {
			message.warning(lastRejection);
			clearRejection();
		}
	}, [lastRejection, message, clearRejection]);

	const onDrop = useCallback(
		(e: React.DragEvent) => {
			e.preventDefault();
			const kind = e.dataTransfer.getData(DND_MIME) as TransformNodeKind;
			if (!kind) return;
			const position = screenToFlowPosition({ x: e.clientX, y: e.clientY });
			addNode(kind, position);
		},
		[screenToFlowPosition, addNode],
	);

	const onDragOver = useCallback((e: React.DragEvent) => {
		e.preventDefault();
		e.dataTransfer.dropEffect = "move";
	}, []);

	return (
		<div
			style={{
				display: "flex",
				height: 560,
				border: "1px solid var(--hairline)",
				borderRadius: "var(--radius-md)",
				overflow: "hidden",
				background: "var(--surface)",
			}}
		>
			<NodePalette onAdd={(k) => addNode(k, { x: 100, y: 100 })} />
			{/* biome-ignore lint/a11y: 画布拖放区，键盘路径由面板"添加"按钮提供 */}
			<div style={{ flex: 1, minWidth: 0, height: "100%" }} onDrop={onDrop} onDragOver={onDragOver}>
				<ReactFlow
					nodes={nodes}
					edges={edges}
					nodeTypes={nodeTypes}
					onNodesChange={onNodesChange}
					onEdgesChange={onEdgesChange}
					onConnect={onConnect}
					onNodeClick={(_e, n) => setSelected(n.id)}
					onPaneClick={() => setSelected(null)}
					fitView
					minZoom={0.3}
					proOptions={{ hideAttribution: true }}
				>
					<Background gap={16} color="hsl(220, 16%, 88%)" />
					<Controls />
					<MiniMap pannable zoomable nodeColor="hsl(222, 80%, 48%)" />
				</ReactFlow>
			</div>
		</div>
	);
}

/** 可视化 ELT 画布 —— 节点面板 + reactflow 画布。 */
export function EltCanvas({ projectSpaceId }: { projectSpaceId: string }) {
	return (
		<ReactFlowProvider>
			<CanvasInner projectSpaceId={projectSpaceId} />
		</ReactFlowProvider>
	);
}
