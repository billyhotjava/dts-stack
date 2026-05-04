import type { StateCreator } from "zustand";
import type { WorkflowEdge, WorkflowEdgeData } from "./types";

export interface EdgesSlice {
	edges: WorkflowEdge[];
	setEdges: (edges: WorkflowEdge[]) => void;
	addEdge: (edge: WorkflowEdge) => void;
	updateEdge: (id: string, patch: Partial<WorkflowEdge> & { data?: Partial<WorkflowEdgeData> }) => void;
	removeEdge: (id: string) => void;
}

export interface ConnectionEndpoints {
	source: string;
	target: string;
	sourceHandle?: string | null;
	targetHandle?: string | null;
}

/**
 * F1-T01 仅落最小可连规则：source/target 不能为同一节点，且不允许重复连线。
 * 真正的 handle 兼容性矩阵在 F3-T01 BaseNode 落地后再补。
 */
export function canConnect(existing: WorkflowEdge[], next: ConnectionEndpoints): boolean {
	if (!next.source || !next.target) return false;
	if (next.source === next.target) return false;
	return !existing.some(
		(edge) =>
			edge.source === next.source &&
			edge.target === next.target &&
			(edge.sourceHandle ?? null) === (next.sourceHandle ?? null) &&
			(edge.targetHandle ?? null) === (next.targetHandle ?? null),
	);
}

function applyEdgePatch(
	edge: WorkflowEdge,
	patch: Partial<WorkflowEdge> & { data?: Partial<WorkflowEdgeData> },
): WorkflowEdge {
	const { data: dataPatch, ...rest } = patch;
	const next: WorkflowEdge = { ...edge, ...rest };
	if (dataPatch) {
		next.data = { ...edge.data, ...dataPatch };
	}
	return next;
}

export const createEdgesSlice: StateCreator<EdgesSlice, [], [], EdgesSlice> = (set) => ({
	edges: [],

	setEdges: (edges) =>
		set(() => ({
			edges: [...edges],
		})),

	addEdge: (edge) =>
		set((state) => {
			if (!canConnect(state.edges, edge)) {
				return state;
			}
			if (state.edges.some((existing) => existing.id === edge.id)) {
				return state;
			}
			return { edges: [...state.edges, edge] };
		}),

	updateEdge: (id, patch) =>
		set((state) => ({
			edges: state.edges.map((edge) => (edge.id === id ? applyEdgePatch(edge, patch) : edge)),
		})),

	removeEdge: (id) =>
		set((state) => ({
			edges: state.edges.filter((edge) => edge.id !== id),
		})),
});
