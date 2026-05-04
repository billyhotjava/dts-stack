import type { StateCreator } from "zustand";
import { DEFAULT_VIEWPORT, EMPTY_HELP_LINE, type HelpLineState, type UiState, type Viewport } from "./types";

export interface UiSlice extends UiState {
	setSelectedNodeId: (id: string | null) => void;
	setSelectedEdgeId: (id: string | null) => void;
	setViewport: (vp: Viewport) => void;
	setPanelOpen: (open: boolean) => void;
	setHelpLine: (hl: HelpLineState) => void;
	resetUi: () => void;
}

export const createUiSlice: StateCreator<UiSlice, [], [], UiSlice> = (set) => ({
	selectedNodeId: null,
	selectedEdgeId: null,
	viewport: { ...DEFAULT_VIEWPORT },
	panelOpen: false,
	helpLine: { ...EMPTY_HELP_LINE },

	setSelectedNodeId: (id) =>
		set(() => ({
			selectedNodeId: id,
		})),

	setSelectedEdgeId: (id) =>
		set(() => ({
			selectedEdgeId: id,
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

	resetUi: () =>
		set(() => ({
			selectedNodeId: null,
			selectedEdgeId: null,
			viewport: { ...DEFAULT_VIEWPORT },
			panelOpen: false,
			helpLine: { ...EMPTY_HELP_LINE },
		})),
});
