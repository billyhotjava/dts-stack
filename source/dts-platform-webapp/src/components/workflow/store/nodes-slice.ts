import type { StateCreator } from "zustand";
import { asHistoryState, withHistory } from "./history-slice";
import type { WorkflowNode, WorkflowNodeData, XYPosition } from "./types";

type WorkflowNodePatch = Omit<Partial<WorkflowNode>, "data"> & { data?: Partial<WorkflowNodeData> };

export interface NodesSlice {
	nodes: WorkflowNode[];
	setNodes: (nodes: WorkflowNode[]) => void;
	addNode: (node: WorkflowNode) => void;
	addNodes: (nodes: WorkflowNode[]) => void;
	updateNode: (id: string, patch: WorkflowNodePatch) => void;
	removeNode: (id: string) => void;
	removeNodes: (ids: string[]) => void;
	setNodePosition: (id: string, position: XYPosition) => void;
	setNodeDragging: (id: string, isDragging: boolean) => void;
}

function applyNodePatch(node: WorkflowNode, patch: WorkflowNodePatch): WorkflowNode {
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
		set((state) => ({
			...withHistory(asHistoryState(state), { nodes: [...nodes] }),
		})),

	addNode: (node) =>
		set((state) => {
			if (state.nodes.some((existing) => existing.id === node.id)) {
				return state;
			}
			return withHistory(asHistoryState(state), { nodes: [...state.nodes, node] });
		}),

	addNodes: (nodes) =>
		set((state) => {
			const existingIds = new Set(state.nodes.map((node) => node.id));
			const nextNodes = nodes.filter((node) => !existingIds.has(node.id));
			if (nextNodes.length === 0) return state;
			return withHistory(asHistoryState(state), { nodes: [...state.nodes, ...nextNodes] });
		}),

	updateNode: (id, patch) =>
		set((state) =>
			withHistory(asHistoryState(state), {
				nodes: state.nodes.map((node) => (node.id === id ? applyNodePatch(node, patch) : node)),
			}),
		),

	removeNode: (id) =>
		set((state) => withHistory(asHistoryState(state), { nodes: state.nodes.filter((node) => node.id !== id) })),

	removeNodes: (ids) =>
		set((state) => {
			const idSet = new Set(ids);
			return withHistory(asHistoryState(state), { nodes: state.nodes.filter((node) => !idSet.has(node.id)) });
		}),

	setNodePosition: (id, position) =>
		set((state) =>
			withHistory(asHistoryState(state), {
				nodes: state.nodes.map((node) => (node.id === id ? { ...node, position: { ...position } } : node)),
			}),
		),

	setNodeDragging: (id, isDragging) =>
		set((state) => ({
			nodes: state.nodes.map((node) => (node.id === id ? { ...node, data: { ...node.data, isDragging } } : node)),
		})),
});
