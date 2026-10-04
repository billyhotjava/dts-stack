import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { createEdgesSlice, type EdgesSlice } from "./edges-slice";
import { createHistorySlice, type HistorySlice } from "./history-slice";
import { createNodesSlice, type NodesSlice } from "./nodes-slice";
import { DEFAULT_VIEWPORT, EMPTY_HELP_LINE } from "./types";
import { createUiSlice, type UiSlice } from "./ui-slice";

export type WorkflowStore = NodesSlice & EdgesSlice & UiSlice & HistorySlice;

const STORAGE_KEY = "workflow-canvas.viewport.v1";

/**
 * 入口 store — 组合三层 slice。
 *
 * 持久化策略：仅 viewport 写入 localStorage（节点/连线统一走后端 graph_dsl）。
 * 选中态、panel 开关、helpLine 等运行时 UI 状态不持久化。
 */
export const useWorkflowStore = create<WorkflowStore>()(
	persist(
		(set, get, store) => ({
			...createNodesSlice(set, get, store),
			...createEdgesSlice(set, get, store),
			...createUiSlice(set, get, store),
			...createHistorySlice(set, get, store),
		}),
		{
			name: STORAGE_KEY,
			version: 1,
			storage: createJSONStorage(() => localStorage),
			partialize: (state) => ({ viewport: state.viewport }),
		},
	),
);

export function resetWorkflowStoreForTest(): void {
	useWorkflowStore.setState((state) => ({
		...state,
		nodes: [],
		edges: [],
		selectedNodeId: null,
		selectedEdgeId: null,
		selectedNodeIds: [],
		selectedEdgeIds: [],
		viewport: { ...DEFAULT_VIEWPORT },
		panelOpen: false,
		helpLine: { ...EMPTY_HELP_LINE },
		contextMenu: null,
		historyPast: [],
		historyFuture: [],
		historyPaused: false,
		canUndo: false,
		canRedo: false,
	}));
	if (typeof window !== "undefined") {
		try {
			window.localStorage.removeItem(STORAGE_KEY);
		} catch {
			/* ignore */
		}
	}
}
