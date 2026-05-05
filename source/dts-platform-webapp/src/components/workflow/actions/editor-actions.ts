import { BLOCKS } from "../block-selector/blocks.config";
import { createWorkflowNodeFromBlock } from "../block-selector/create-node";
import { asHistoryState, withHistory } from "../store/history-slice";
import type { WorkflowEdge, WorkflowNode, XYPosition } from "../store/types";
import { useWorkflowStore, type WorkflowStore } from "../store/workflow-store";

const PASTE_OFFSET = 40;

export interface ClipboardPayload {
	nodes: WorkflowNode[];
	edges: WorkflowEdge[];
}

type AlignDirection = "left" | "right" | "top" | "bottom" | "horizontal-center" | "vertical-center";

let copyCounter = 0;

function cloneRecord<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function selectedNodeIds(state: Pick<WorkflowStore, "selectedNodeId" | "selectedNodeIds">): string[] {
	return state.selectedNodeIds.length > 0 ? state.selectedNodeIds : state.selectedNodeId ? [state.selectedNodeId] : [];
}

function selectedEdgeIds(state: Pick<WorkflowStore, "selectedEdgeId" | "selectedEdgeIds">): string[] {
	return state.selectedEdgeIds.length > 0 ? state.selectedEdgeIds : state.selectedEdgeId ? [state.selectedEdgeId] : [];
}

function nextId(prefix: string): string {
	copyCounter += 1;
	return `${prefix}-${Date.now().toString(36)}-${copyCounter}`;
}

export function copySelection(state: WorkflowStore): ClipboardPayload | null {
	const nodeIdSet = new Set(selectedNodeIds(state));
	const edgeIdSet = new Set(selectedEdgeIds(state));
	const nodes = state.nodes.filter((node) => nodeIdSet.has(node.id));
	const internalEdges = state.edges.filter((edge) => nodeIdSet.has(edge.source) && nodeIdSet.has(edge.target));
	const explicitEdges = state.edges.filter((edge) => edgeIdSet.has(edge.id));
	const edgeMap = new Map<string, WorkflowEdge>();
	for (const edge of internalEdges) edgeMap.set(edge.id, edge);
	for (const edge of explicitEdges) edgeMap.set(edge.id, edge);
	if (nodes.length === 0 && edgeMap.size === 0) return null;
	return {
		nodes: cloneRecord(nodes),
		edges: cloneRecord(Array.from(edgeMap.values())),
	};
}

export function pasteClipboard(payload: ClipboardPayload | null, anchor?: XYPosition): void {
	if (!payload || payload.nodes.length === 0) return;
	useWorkflowStore.setState((state) => {
		const idMap = new Map<string, string>();
		const minX = Math.min(...payload.nodes.map((node) => node.position.x));
		const minY = Math.min(...payload.nodes.map((node) => node.position.y));
		const offsetX = anchor ? anchor.x - minX : PASTE_OFFSET;
		const offsetY = anchor ? anchor.y - minY : PASTE_OFFSET;
		const nodes = payload.nodes.map((node) => {
			const nextNodeId = nextId(`node-${node.data.kind}`);
			idMap.set(node.id, nextNodeId);
			return {
				...cloneRecord(node),
				id: nextNodeId,
				selected: true,
				position: {
					x: node.position.x + offsetX,
					y: node.position.y + offsetY,
				},
				data: {
					...cloneRecord(node.data),
					isDragging: false,
				},
			};
		});
		const edges = payload.edges.reduce<WorkflowEdge[]>((acc, edge) => {
			const source = idMap.get(edge.source);
			const target = idMap.get(edge.target);
			if (!source || !target) return acc;
			acc.push({
				...cloneRecord(edge),
				id: nextId("edge"),
				source,
				target,
				selected: false,
			});
			return acc;
		}, []);
		return withHistory(asHistoryState(state) as WorkflowStore, {
			nodes: [...state.nodes, ...nodes],
			edges: [...state.edges, ...edges],
			selectedNodeId: nodes[0]?.id ?? null,
			selectedNodeIds: nodes.map((node) => node.id),
			selectedEdgeId: null,
			selectedEdgeIds: [],
			panelOpen: false,
			contextMenu: null,
		});
	});
}

export function deleteSelection(): void {
	useWorkflowStore.setState((state) => {
		const nodeIdSet = new Set(selectedNodeIds(state));
		const edgeIdSet = new Set(selectedEdgeIds(state));
		if (nodeIdSet.size === 0 && edgeIdSet.size === 0) return state;
		return withHistory(asHistoryState(state) as WorkflowStore, {
			nodes: state.nodes.filter((node) => !nodeIdSet.has(node.id)),
			edges: state.edges.filter(
				(edge) => !edgeIdSet.has(edge.id) && !nodeIdSet.has(edge.source) && !nodeIdSet.has(edge.target),
			),
			selectedNodeId: null,
			selectedEdgeId: null,
			selectedNodeIds: [],
			selectedEdgeIds: [],
			panelOpen: false,
			contextMenu: null,
		});
	});
}

export function selectAll(): void {
	useWorkflowStore.setState((state) => ({
		selectedNodeId: state.nodes[0]?.id ?? null,
		selectedEdgeId: state.edges[0]?.id ?? null,
		selectedNodeIds: state.nodes.map((node) => node.id),
		selectedEdgeIds: state.edges.map((edge) => edge.id),
		contextMenu: null,
	}));
}

export function addNoteAt(position: XYPosition): void {
	const note = BLOCKS.find((block) => block.kind === "note");
	if (!note) return;
	const node = createWorkflowNodeFromBlock(
		{ kind: note.kind, label: note.label, color: note.color, defaultData: note.defaultData },
		position,
	);
	useWorkflowStore.getState().addNode(node);
	useWorkflowStore.setState(() => ({
		selectedNodeId: node.id,
		selectedNodeIds: [node.id],
		selectedEdgeId: null,
		selectedEdgeIds: [],
		panelOpen: true,
		contextMenu: null,
	}));
}

export function renameSelectedNode(title: string): void {
	const trimmed = title.trim();
	if (!trimmed) return;
	const nodeId = useWorkflowStore.getState().selectedNodeId;
	if (!nodeId) return;
	useWorkflowStore.getState().updateNode(nodeId, { data: { title: trimmed } });
}

export function alignSelection(direction: AlignDirection): void {
	useWorkflowStore.setState((state) => {
		const nodeIds = selectedNodeIds(state);
		if (nodeIds.length < 2) return state;
		const selected = state.nodes.filter((node) => nodeIds.includes(node.id));
		const xs = selected.map((node) => node.position.x);
		const ys = selected.map((node) => node.position.y);
		const left = Math.min(...xs);
		const right = Math.max(...xs);
		const top = Math.min(...ys);
		const bottom = Math.max(...ys);
		const centerX = xs.reduce((sum, x) => sum + x, 0) / xs.length;
		const centerY = ys.reduce((sum, y) => sum + y, 0) / ys.length;
		const nodeIdSet = new Set(nodeIds);
		return withHistory(asHistoryState(state) as WorkflowStore, {
			nodes: state.nodes.map((node) => {
				if (!nodeIdSet.has(node.id)) return node;
				if (direction === "left") return { ...node, position: { ...node.position, x: left } };
				if (direction === "right") return { ...node, position: { ...node.position, x: right } };
				if (direction === "top") return { ...node, position: { ...node.position, y: top } };
				if (direction === "bottom") return { ...node, position: { ...node.position, y: bottom } };
				if (direction === "horizontal-center") return { ...node, position: { ...node.position, x: centerX } };
				return { ...node, position: { ...node.position, y: centerY } };
			}),
			contextMenu: null,
		});
	});
}
