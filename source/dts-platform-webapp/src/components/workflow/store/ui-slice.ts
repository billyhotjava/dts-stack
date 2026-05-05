import type { StateCreator } from "zustand";
import {
	DEFAULT_VIEWPORT,
	EMPTY_HELP_LINE,
	type HelpLineState,
	type UiState,
	type Viewport,
	type WorkflowContextMenuState,
} from "./types";

export interface UiSlice extends UiState {
	setSelectedNodeId: (id: string | null) => void;
	setSelectedEdgeId: (id: string | null) => void;
	setSelectedNodeIds: (ids: string[]) => void;
	setSelectedEdgeIds: (ids: string[]) => void;
	setViewport: (vp: Viewport) => void;
	setPanelOpen: (open: boolean) => void;
	setHelpLine: (hl: HelpLineState) => void;
	setContextMenu: (menu: WorkflowContextMenuState | null) => void;
	resetUi: () => void;
}

export const createUiSlice: StateCreator<UiSlice, [], [], UiSlice> = (set) => ({
	selectedNodeId: null,
	selectedEdgeId: null,
	selectedNodeIds: [],
	selectedEdgeIds: [],
	viewport: { ...DEFAULT_VIEWPORT },
	panelOpen: false,
	helpLine: { ...EMPTY_HELP_LINE },
	contextMenu: null,

	setSelectedNodeId: (id) =>
		set(() => ({
			selectedNodeId: id,
			selectedNodeIds: id ? [id] : [],
		})),

	setSelectedEdgeId: (id) =>
		set(() => ({
			selectedEdgeId: id,
			selectedEdgeIds: id ? [id] : [],
		})),

	setSelectedNodeIds: (ids) =>
		set(() => ({
			selectedNodeIds: [...ids],
			selectedNodeId: ids[0] ?? null,
		})),

	setSelectedEdgeIds: (ids) =>
		set(() => ({
			selectedEdgeIds: [...ids],
			selectedEdgeId: ids[0] ?? null,
		})),

	setViewport: (vp) =>
		set(() => ({
			viewport: { x: vp.x, y: vp.y, zoom: vp.zoom },
		})),

	setPanelOpen: (open) =>
		set(() => ({
			panelOpen: open,
		})),

	setHelpLine: (hl) =>
		set(() => ({
			helpLine: { vertical: hl.vertical, horizontal: hl.horizontal },
		})),

	setContextMenu: (menu) =>
		set(() => ({
			contextMenu: menu,
		})),

	resetUi: () =>
		set(() => ({
			selectedNodeId: null,
			selectedEdgeId: null,
			selectedNodeIds: [],
			selectedEdgeIds: [],
			viewport: { ...DEFAULT_VIEWPORT },
			panelOpen: false,
			helpLine: { ...EMPTY_HELP_LINE },
			contextMenu: null,
		})),
});
