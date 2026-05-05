import {
	applyEdgeChanges,
	applyNodeChanges,
	Background,
	BackgroundVariant,
	type Connection,
	Controls,
	type EdgeChange,
	type EdgeMouseHandler,
	type EdgeTypes,
	MiniMap,
	type NodeChange,
	type NodeMouseHandler,
	type NodeTypes,
	type OnMoveEnd,
	type OnSelectionChangeFunc,
	ReactFlow,
	ReactFlowProvider,
	useReactFlow,
	type Viewport,
} from "@xyflow/react";
import { type DragEvent, type MouseEvent as ReactMouseEvent, useCallback, useEffect, useMemo } from "react";
import "@xyflow/react/dist/style.css";
import { BLOCK_DRAG_MIME, parseDraggedBlock } from "./block-selector/blocks.config";
import { createWorkflowNodeFromBlock } from "./block-selector/create-node";
import "./block-selector/styles.css";
import { WorkflowContextProvider, type WorkflowContextValue } from "./context";
import { WorkflowContextMenu } from "./context-menu";
import { CustomConnectionLine, useIsValidWorkflowConnection } from "./custom-connection-line";
import { DEFAULT_EDGE_TYPE, workflowEdgeTypes } from "./custom-edge";
import { HelpLine } from "./help-line";
import { workflowNodeTypes } from "./nodes";
import { Operator } from "./operator";
import { NodePanel } from "./panel";
import { useWorkflowShortcuts } from "./shortcuts";
import type { WorkflowEdge, WorkflowNode } from "./store/types";
import { useWorkflowStore } from "./store/workflow-store";
import "./styles/canvas.css";
import "./styles/operator.css";
import { deserializeDsl, type WorkflowDsl } from "./utils/dsl";

const MIN_ZOOM = 0.2;
const MAX_ZOOM = 2;

const DEFAULT_EDGE_OPTIONS = { type: DEFAULT_EDGE_TYPE } as const;

function hasWorkflowBlockDragType(types: ReadonlyArray<string>): boolean {
	for (let i = 0; i < types.length; i += 1) {
		if (types[i] === BLOCK_DRAG_MIME) {
			return true;
		}
	}
	return false;
}

export interface WorkflowCanvasProps extends Partial<WorkflowContextValue> {
	nodeTypes?: NodeTypes;
	edgeTypes?: EdgeTypes;
	className?: string;
	/** 透传额外的 DOM 子节点（如 Operator 工具栏、HelpLine 覆盖层），渲染在 ReactFlow 内部 */
	children?: React.ReactNode;
	/** 初始 DSL 反序列化结果；F4 接入时使用，T02 仅用于初次填充节点/连线 */
	initialNodes?: WorkflowNode[];
	initialEdges?: WorkflowEdge[];
	initialDsl?: WorkflowDsl | null;
	onSave?: () => void;
}

function WorkflowCanvasInner({
	nodeTypes,
	edgeTypes,
	className,
	children,
	initialNodes,
	initialEdges,
	initialDsl,
	onSave,
	readonly = false,
}: WorkflowCanvasProps) {
	const nodes = useWorkflowStore((s) => s.nodes);
	const edges = useWorkflowStore((s) => s.edges);
	const setNodes = useWorkflowStore((s) => s.setNodes);
	const setEdges = useWorkflowStore((s) => s.setEdges);
	const addNode = useWorkflowStore((s) => s.addNode);
	const addEdge = useWorkflowStore((s) => s.addEdge);
	const setSelectedNodeId = useWorkflowStore((s) => s.setSelectedNodeId);
	const setSelectedEdgeId = useWorkflowStore((s) => s.setSelectedEdgeId);
	const setSelectedNodeIds = useWorkflowStore((s) => s.setSelectedNodeIds);
	const setSelectedEdgeIds = useWorkflowStore((s) => s.setSelectedEdgeIds);
	const setPanelOpen = useWorkflowStore((s) => s.setPanelOpen);
	const setContextMenu = useWorkflowStore((s) => s.setContextMenu);
	const pauseHistory = useWorkflowStore((s) => s.pauseHistory);
	const clearHistory = useWorkflowStore((s) => s.clearHistory);
	const setViewport = useWorkflowStore((s) => s.setViewport);
	const viewport = useWorkflowStore((s) => s.viewport);
	const { screenToFlowPosition } = useReactFlow<WorkflowNode, WorkflowEdge>();
	useWorkflowShortcuts({ enabled: !readonly, onSave });

	useEffect(() => {
		if (initialDsl) {
			const restored = deserializeDsl(initialDsl);
			if (restored.success) {
				pauseHistory();
				setNodes(restored.state.nodes);
				setEdges(restored.state.edges);
				setViewport(restored.state.viewport);
				setSelectedNodeId(null);
				setSelectedEdgeId(null);
				setPanelOpen(false);
				clearHistory();
			}
			return;
		}
		if (initialNodes || initialEdges) {
			pauseHistory();
		}
		if (initialNodes) {
			setNodes(initialNodes);
		}
		if (initialEdges) {
			setEdges(initialEdges);
		}
		if (initialNodes || initialEdges) {
			clearHistory();
		}
	}, [
		clearHistory,
		initialDsl,
		initialNodes,
		initialEdges,
		pauseHistory,
		setEdges,
		setNodes,
		setPanelOpen,
		setSelectedEdgeId,
		setSelectedNodeId,
		setViewport,
	]);

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
				type: DEFAULT_EDGE_TYPE,
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

	const handleDragOver = useCallback(
		(event: DragEvent<HTMLDivElement>) => {
			if (readonly) return;
			if (!hasWorkflowBlockDragType(event.dataTransfer.types)) return;
			event.preventDefault();
			event.dataTransfer.dropEffect = "move";
		},
		[readonly],
	);

	const handleDrop = useCallback(
		(event: DragEvent<HTMLDivElement>) => {
			if (readonly) return;
			const block = parseDraggedBlock(event.dataTransfer.getData(BLOCK_DRAG_MIME));
			if (!block) return;
			event.preventDefault();
			const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
			addNode(createWorkflowNodeFromBlock(block, position));
		},
		[addNode, readonly, screenToFlowPosition],
	);

	const handleSelectionChange = useCallback<OnSelectionChangeFunc<WorkflowNode, WorkflowEdge>>(
		({ nodes: selectedNodes, edges: selectedEdges }) => {
			const nextNodeId = selectedNodes[0]?.id ?? null;
			setSelectedNodeIds(selectedNodes.map((node) => node.id));
			setSelectedEdgeIds(selectedEdges.map((edge) => edge.id));
			if (nextNodeId && !readonly) {
				setPanelOpen(true);
			}
		},
		[readonly, setPanelOpen, setSelectedEdgeIds, setSelectedNodeIds],
	);

	const openContextMenu = useCallback(
		(event: MouseEvent | ReactMouseEvent, patch: Parameters<typeof setContextMenu>[0]) => {
			if (readonly || !patch) return;
			event.preventDefault();
			setContextMenu(patch);
		},
		[readonly, setContextMenu],
	);

	const handlePaneContextMenu = useCallback(
		(event: MouseEvent | ReactMouseEvent) => {
			const flowPosition = screenToFlowPosition({ x: event.clientX, y: event.clientY });
			setSelectedNodeIds([]);
			setSelectedEdgeIds([]);
			openContextMenu(event, { type: "pane", x: event.clientX, y: event.clientY, flowPosition });
		},
		[openContextMenu, screenToFlowPosition, setSelectedEdgeIds, setSelectedNodeIds],
	);

	const handleNodeContextMenu = useCallback<NodeMouseHandler<WorkflowNode>>(
		(event, node) => {
			const flowPosition = screenToFlowPosition({ x: event.clientX, y: event.clientY });
			setSelectedNodeIds([node.id]);
			setSelectedEdgeIds([]);
			openContextMenu(event, { type: "node", x: event.clientX, y: event.clientY, flowPosition, nodeId: node.id });
		},
		[openContextMenu, screenToFlowPosition, setSelectedEdgeIds, setSelectedNodeIds],
	);

	const handleEdgeContextMenu = useCallback<EdgeMouseHandler<WorkflowEdge>>(
		(event, edge) => {
			const flowPosition = screenToFlowPosition({ x: event.clientX, y: event.clientY });
			setSelectedNodeIds([]);
			setSelectedEdgeIds([edge.id]);
			openContextMenu(event, { type: "edge", x: event.clientX, y: event.clientY, flowPosition, edgeId: edge.id });
		},
		[openContextMenu, screenToFlowPosition, setSelectedEdgeIds, setSelectedNodeIds],
	);

	const handleSelectionContextMenu = useCallback(
		(event: ReactMouseEvent | MouseEvent, selectedNodes: WorkflowNode[]) => {
			const flowPosition = screenToFlowPosition({ x: event.clientX, y: event.clientY });
			const nodeIds = selectedNodes.map((node) => node.id);
			setSelectedNodeIds(nodeIds);
			openContextMenu(event, { type: "multi", x: event.clientX, y: event.clientY, flowPosition, nodeIds });
		},
		[openContextMenu, screenToFlowPosition, setSelectedNodeIds],
	);

	const resolvedNodeTypes = nodeTypes ?? workflowNodeTypes;
	const resolvedEdgeTypes = edgeTypes ?? workflowEdgeTypes;
	const isValidConnection = useIsValidWorkflowConnection();
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
				onDragOver={handleDragOver}
				onDrop={handleDrop}
				onSelectionChange={handleSelectionChange}
				onPaneContextMenu={handlePaneContextMenu}
				onNodeContextMenu={handleNodeContextMenu}
				onEdgeContextMenu={handleEdgeContextMenu}
				onSelectionContextMenu={handleSelectionContextMenu}
				nodeTypes={resolvedNodeTypes}
				edgeTypes={resolvedEdgeTypes}
				connectionLineComponent={CustomConnectionLine}
				isValidConnection={isValidConnection}
				defaultEdgeOptions={DEFAULT_EDGE_OPTIONS}
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
				<Operator />
				<HelpLine />
				<NodePanel />
				<WorkflowContextMenu />
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
