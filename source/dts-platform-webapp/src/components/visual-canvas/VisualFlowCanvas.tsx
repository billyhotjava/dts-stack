import "@/polyfills/legacy-browser";
import { type CSSProperties, type DragEvent, type ReactNode, useCallback } from "react";
import {
	Background,
	Controls,
	MiniMap,
	ReactFlow,
	ReactFlowProvider,
	useReactFlow,
	type Edge,
	type Node,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Empty } from "antd";

export type VisualFlowDropPayload = {
	kind?: string;
	value?: unknown;
};

export type VisualFlowDropEvent = {
	payload: VisualFlowDropPayload;
	position: { x: number; y: number };
};

export type VisualFlowCanvasProps = {
	nodes: Node[];
	edges?: Edge[];
	height?: number | string;
	fitView?: boolean;
	showMiniMap?: boolean;
	emptyText?: ReactNode;
	className?: string;
	style?: CSSProperties;
	nodeColor?: (node: Node) => string;
	onNodeClick?: (node: Node) => void;
	onDropItem?: (event: VisualFlowDropEvent) => void;
};

function VisualFlowCanvasInner({
	nodes,
	edges = [],
	height = 420,
	fitView = true,
	showMiniMap = false,
	emptyText = "暂无画布节点",
	className,
	style,
	nodeColor,
	onNodeClick,
	onDropItem,
}: VisualFlowCanvasProps) {
	const reactFlow = useReactFlow();

	const handleDragOver = useCallback((event: DragEvent<HTMLDivElement>) => {
		if (!onDropItem) return;
		event.preventDefault();
		event.dataTransfer.dropEffect = "move";
	}, [onDropItem]);

	const handleDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
		if (!onDropItem) return;
		event.preventDefault();
		const raw = event.dataTransfer.getData("application/json") || event.dataTransfer.getData("text/plain");
		let payload: VisualFlowDropPayload = {};
		if (raw) {
			try {
				payload = JSON.parse(raw);
			} catch {
				payload = { value: raw };
			}
		}
		onDropItem({
			payload,
			position: reactFlow.screenToFlowPosition({ x: event.clientX, y: event.clientY }),
		});
	}, [onDropItem, reactFlow]);

	return (
		<div
			className={className}
			style={{
				height,
				border: "1px solid #e8e8e8",
				borderRadius: 8,
				overflow: "hidden",
				background: "#fff",
				...style,
			}}
			onDragOver={handleDragOver}
			onDrop={handleDrop}
		>
			{nodes.length ? (
				<ReactFlow
					nodes={nodes}
					edges={edges}
					fitView={fitView}
					onNodeClick={(_, node) => onNodeClick?.(node)}
				>
					<Background />
					<Controls />
					{showMiniMap ? (
						<MiniMap
							position="bottom-right"
							nodeStrokeWidth={2}
							nodeColor={nodeColor}
						/>
					) : null}
				</ReactFlow>
			) : (
				<div className="flex h-full items-center justify-center">
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
				</div>
			)}
		</div>
	);
}

export function VisualFlowCanvas(props: VisualFlowCanvasProps) {
	return (
		<ReactFlowProvider>
			<VisualFlowCanvasInner {...props} />
		</ReactFlowProvider>
	);
}
