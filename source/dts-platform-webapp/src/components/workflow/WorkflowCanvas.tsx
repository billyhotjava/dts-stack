import { useCallback, useEffect, useMemo } from "react";
import {
	Background,
	BackgroundVariant,
	Controls,
	MiniMap,
	ReactFlow,
	ReactFlowProvider,
	applyEdgeChanges,
	applyNodeChanges,
	type Connection,
	type EdgeChange,
	type EdgeTypes,
	type NodeChange,
	type NodeTypes,
	type OnMoveEnd,
	type Viewport,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useWorkflowStore } from "./store/workflow-store";
import type { WorkflowEdge, WorkflowNode } from "./store/types";
import { WorkflowContextProvider, type WorkflowContextValue } from "./context";
import "./styles/canvas.css";

const MIN_ZOOM = 0.2;
const MAX_ZOOM = 2;

const EMPTY_NODE_TYPES: NodeTypes = {};
const EMPTY_EDGE_TYPES: EdgeTypes = {};

export interface WorkflowCanvasProps extends Partial<WorkflowContextValue> {
	nodeTypes?: NodeTypes;
	edgeTypes?: EdgeTypes;
	className?: string;
	/** 透传额外的 DOM 子节点（如 Operator 工具栏、HelpLine 覆盖层），渲染在 ReactFlow 内部 */
	children?: React.ReactNode;
	/** 初始 DSL 反序列化结果；F4 接入时使用，T02 仅用于初次填充节点/连线 */
	initialNodes?: WorkflowNode[];
	initialEdges?: WorkflowEdge[];
}

function WorkflowCanvasInner({
	nodeTypes,
	edgeTypes,
	className,
	children,
	initialNodes,
	initialEdges,
	readonly = false,
}: WorkflowCanvasProps) {
	const nodes = useWorkflowStore((s) => s.nodes);
	const edges = useWorkflowStore((s) => s.edges);
	const setNodes = useWorkflowStore((s) => s.setNodes);
	const setEdges = useWorkflowStore((s) => s.setEdges);
	const addEdge = useWorkflowStore((s) => s.addEdge);
	const setViewport = useWorkflowStore((s) => s.setViewport);
	const viewport = useWorkflowStore((s) => s.viewport);

	useEffect(() => {
		if (initialNodes) {
			setNodes(initialNodes);
		}
		if (initialEdges) {
			setEdges(initialEdges);
		}
	}, [initialNodes, initialEdges, setNodes, setEdges]);

	const handleNodesChange = useCallback(
		(changes: NodeChange<WorkflowNode>[]) => {
			setNodes(applyNodeChanges(changes, nodes));
		},
		[nodes, setNodes],
	);

	const handleEdgesChange = useCallback(
		(changes: EdgeChange<WorkflowEdge>[]) => {
			setEdges(applyEdgeChanges(changes, edges));
		},
		[edges, setEdges],
	);

	const handleConnect = useCallback(
		(connection: Connection) => {
			if (!connection.source || !connection.target) return;
			const id = `edge-${connection.source}-${connection.target}-${Date.now()}`;
			addEdge({
				id,
				source: connection.source,
				target: connection.target,
				sourceHandle: connection.sourceHandle ?? undefined,
				targetHandle: connection.targetHandle ?? undefined,
			});
		},
		[addEdge],
	);

	const handleMoveEnd = useCallback<OnMoveEnd>(
		(_event, vp: Viewport) => {
			setViewport(vp);
		},
		[setViewport],
	);

	const resolvedNodeTypes = nodeTypes ?? EMPTY_NODE_TYPES;
	const resolvedEdgeTypes = edgeTypes ?? EMPTY_EDGE_TYPES;
	const wrapperClass = useMemo(() => ["workflow-canvas", className].filter(Boolean).join(" "), [className]);

	return (
		<div className={wrapperClass} data-readonly={readonly ? "true" : "false"}>
			<ReactFlow<WorkflowNode, WorkflowEdge>
				nodes={nodes}
				edges={edges}
				onNodesChange={handleNodesChange}
				onEdgesChange={handleEdgesChange}
				onConnect={handleConnect}
				onMoveEnd={handleMoveEnd}
				nodeTypes={resolvedNodeTypes}
				edgeTypes={resolvedEdgeTypes}
				defaultViewport={viewport}
				minZoom={MIN_ZOOM}
				maxZoom={MAX_ZOOM}
				fitView={!viewport || viewport.zoom === 1}
				nodesDraggable={!readonly}
				nodesConnectable={!readonly}
				elementsSelectable={!readonly}
				panOnDrag
				zoomOnScroll
				deleteKeyCode={readonly ? null : ["Backspace", "Delete"]}
			>
				<Background variant={BackgroundVariant.Dots} gap={16} size={1} />
				<MiniMap pannable zoomable />
				<Controls showInteractive={false} />
				{children}
			</ReactFlow>
		</div>
	);
}

export function WorkflowCanvas(props: WorkflowCanvasProps) {
	const { readonly, projectId, onSave, ...rest } = props;
	return (
		<ReactFlowProvider>
			<WorkflowContextProvider value={{ readonly, projectId, onSave }}>
				<WorkflowCanvasInner {...rest} readonly={readonly} />
			</WorkflowContextProvider>
		</ReactFlowProvider>
	);
}
