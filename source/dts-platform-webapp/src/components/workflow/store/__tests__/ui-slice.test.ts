// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../workflow-store";
import { DEFAULT_VIEWPORT } from "../types";

describe("workflow ui-slice", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("starts with default viewport and no selection", () => {
		const state = useWorkflowStore.getState();
		expect(state.selectedNodeId).toBeNull();
		expect(state.selectedEdgeId).toBeNull();
		expect(state.viewport).toEqual(DEFAULT_VIEWPORT);
		expect(state.panelOpen).toBe(false);
	});

	it("setSelectedNodeId clears edge selection independently", () => {
		useWorkflowStore.getState().setSelectedEdgeId("e1");
		useWorkflowStore.getState().setSelectedNodeId("n1");
		expect(useWorkflowStore.getState().selectedNodeId).toBe("n1");
		expect(useWorkflowStore.getState().selectedEdgeId).toBe("e1");
	});

	it("setViewport stores a fresh object (no shared reference)", () => {
		const incoming = { x: 10, y: 20, zoom: 0.8 };
		useWorkflowStore.getState().setViewport(incoming);
		const stored = useWorkflowStore.getState().viewport;
		expect(stored).toEqual(incoming);
		expect(stored).not.toBe(incoming);
	});

	it("setPanelOpen toggles drawer state", () => {
		useWorkflowStore.getState().setPanelOpen(true);
		expect(useWorkflowStore.getState().panelOpen).toBe(true);
		useWorkflowStore.getState().setPanelOpen(false);
		expect(useWorkflowStore.getState().panelOpen).toBe(false);
	});

	it("setHelpLine accepts partial axis values", () => {
		useWorkflowStore.getState().setHelpLine({ vertical: 100, horizontal: null });
		expect(useWorkflowStore.getState().helpLine).toEqual({ vertical: 100, horizontal: null });
		useWorkflowStore.getState().setHelpLine({ vertical: null, horizontal: 200 });
		expect(useWorkflowStore.getState().helpLine).toEqual({ vertical: null, horizontal: 200 });
	});

	it("resetUi restores defaults and detaches helpLine reference", () => {
		useWorkflowStore.getState().setSelectedNodeId("n1");
		useWorkflowStore.getState().setPanelOpen(true);
		useWorkflowStore.getState().setHelpLine({ vertical: 50, horizontal: 50 });
		useWorkflowStore.getState().resetUi();
		const state = useWorkflowStore.getState();
		expect(state.selectedNodeId).toBeNull();
		expect(state.panelOpen).toBe(false);
		expect(state.helpLine).toEqual({ vertical: null, horizontal: null });
	});
});
