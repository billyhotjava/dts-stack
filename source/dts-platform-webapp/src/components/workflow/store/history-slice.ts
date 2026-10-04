import type { StateCreator } from "zustand";
import type { WorkflowEdge, WorkflowNode } from "./types";

const HISTORY_LIMIT = 50;

export interface WorkflowGraphSnapshot {
	nodes: WorkflowNode[];
	edges: WorkflowEdge[];
}

export interface HistorySlice {
	historyPast: WorkflowGraphSnapshot[];
	historyFuture: WorkflowGraphSnapshot[];
	historyPaused: boolean;
	canUndo: boolean;
	canRedo: boolean;
	undo: () => void;
	redo: () => void;
	clearHistory: () => void;
	pauseHistory: () => void;
	resumeHistory: () => void;
}

export interface HistoryAwareState {
	nodes: WorkflowNode[];
	edges: WorkflowEdge[];
	historyPast: WorkflowGraphSnapshot[];
	historyFuture: WorkflowGraphSnapshot[];
	historyPaused: boolean;
	canUndo: boolean;
	canRedo: boolean;
}

export function asHistoryState(state: unknown): HistoryAwareState {
	return state as HistoryAwareState;
}

function snapshot(state: { nodes: WorkflowNode[]; edges: WorkflowEdge[] }): WorkflowGraphSnapshot {
	return {
		nodes: [...state.nodes],
		edges: [...state.edges],
	};
}

function sameGraph(
	state: { nodes: WorkflowNode[]; edges: WorkflowEdge[] },
	patch: Partial<WorkflowGraphSnapshot>,
): boolean {
	return (
		(patch.nodes === undefined || patch.nodes === state.nodes) &&
		(patch.edges === undefined || patch.edges === state.edges)
	);
}

export function withHistory<T extends HistoryAwareState>(state: T, patch: Partial<T>): Partial<T> {
	const graphPatch: Partial<WorkflowGraphSnapshot> = {};
	if (patch.nodes !== undefined) graphPatch.nodes = patch.nodes;
	if (patch.edges !== undefined) graphPatch.edges = patch.edges;
	if (graphPatch.nodes === undefined && graphPatch.edges === undefined) return patch;
	if (state.historyPaused || sameGraph(state, graphPatch)) return patch;

	const past = [...state.historyPast, snapshot(state)].slice(-HISTORY_LIMIT);
	return {
		...patch,
		historyPast: past,
		historyFuture: [],
		canUndo: past.length > 0,
		canRedo: false,
	};
}

export const createHistorySlice: StateCreator<HistorySlice, [], [], HistorySlice> = (set) => ({
	historyPast: [],
	historyFuture: [],
	historyPaused: false,
	canUndo: false,
	canRedo: false,

	undo: () =>
		set((state) => {
			const current = asHistoryState(state);
			const previous = current.historyPast[current.historyPast.length - 1];
			if (!previous) return state;
			const nextPast = current.historyPast.slice(0, -1);
			const nextFuture = [snapshot(current), ...current.historyFuture].slice(0, HISTORY_LIMIT);
			return {
				nodes: previous.nodes,
				edges: previous.edges,
				selectedNodeId: null,
				selectedEdgeId: null,
				selectedNodeIds: [],
				selectedEdgeIds: [],
				panelOpen: false,
				contextMenu: null,
				historyPast: nextPast,
				historyFuture: nextFuture,
				canUndo: nextPast.length > 0,
				canRedo: nextFuture.length > 0,
			};
		}),

	redo: () =>
		set((state) => {
			const current = asHistoryState(state);
			const next = current.historyFuture[0];
			if (!next) return state;
			const nextPast = [...current.historyPast, snapshot(current)].slice(-HISTORY_LIMIT);
			const nextFuture = current.historyFuture.slice(1);
			return {
				nodes: next.nodes,
				edges: next.edges,
				selectedNodeId: null,
				selectedEdgeId: null,
				selectedNodeIds: [],
				selectedEdgeIds: [],
				panelOpen: false,
				contextMenu: null,
				historyPast: nextPast,
				historyFuture: nextFuture,
				canUndo: nextPast.length > 0,
				canRedo: nextFuture.length > 0,
			};
		}),

	clearHistory: () =>
		set(() => ({
			historyPast: [],
			historyFuture: [],
			canUndo: false,
			canRedo: false,
			historyPaused: false,
		})),

	pauseHistory: () => set(() => ({ historyPaused: true })),
	resumeHistory: () => set(() => ({ historyPaused: false })),
});
