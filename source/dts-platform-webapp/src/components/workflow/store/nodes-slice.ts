import type { StateCreator } from "zustand";
import type { WorkflowNode, WorkflowNodeData, XYPosition } from "./types";

export interface NodesSlice {
	nodes: WorkflowNode[];
	setNodes: (nodes: WorkflowNode[]) => void;
	addNode: (node: WorkflowNode) => void;
	updateNode: (id: string, patch: Partial<WorkflowNode> & { data?: Partial<WorkflowNodeData> }) => void;
	removeNode: (id: string) => void;
	setNodePosition: (id: string, position: XYPosition) => void;
	setNodeDragging: (id: string, isDragging: boolean) => void;
}

function applyNodePatch(
	node: WorkflowNode,
	patch: Partial<WorkflowNode> & { data?: Partial<WorkflowNodeData> },
): WorkflowNode {
	const { data: dataPatch, ...rest } = patch;
	const next: WorkflowNode = { ...node, ...rest };
	if (dataPatch) {
		next.data = { ...node.data, ...dataPatch };
	}
	return next;
}

export const createNodesSlice: StateCreator<NodesSlice, [], [], NodesSlice> = (set) => ({
	nodes: [],

	setNodes: (nodes) =>
		set(() => ({
			nodes: [...nodes],
		})),

	addNode: (node) =>
		set((state) => {
			if (state.nodes.some((existing) => existing.id === node.id)) {
				return state;
			}
			return { nodes: [...state.nodes, node] };
		}),

	updateNode: (id, patch) =>
		set((state) => ({
			nodes: state.nodes.map((node) => (node.id === id ? applyNodePatch(node, patch) : node)),
		})),

	removeNode: (id) =>
		set((state) => ({
			nodes: state.nodes.filter((node) => node.id !== id),
		})),

	setNodePosition: (id, position) =>
		set((state) => ({
			nodes: state.nodes.map((node) =>
				node.id === id ? { ...node, position: { ...position } } : node,
			),
		})),

	setNodeDragging: (id, isDragging) =>
		set((state) => ({
			nodes: state.nodes.map((node) =>
				node.id === id ? { ...node, data: { ...node.data, isDragging } } : node,
			),
		})),
});
