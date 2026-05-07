// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { copySelection, deleteSelection, pasteClipboard, selectAll } from "../actions";
import type { WorkflowEdge, WorkflowNode } from "../store/types";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../store/workflow-store";

function makeNode(id: string, x: number): WorkflowNode {
	return {
		id,
		type: "transform",
		position: { x, y: 0 },
		data: { kind: "transform", title: id, config: { code: id } },
	};
}

function makeEdge(id: string): WorkflowEdge {
	return {
		id,
		type: "custom",
		source: "n1",
		target: "n2",
		sourceHandle: "out",
		targetHandle: "in",
	};
}

describe("workflow editor actions", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
		useWorkflowStore.getState().setNodes([makeNode("n1", 0), makeNode("n2", 200)]);
		useWorkflowStore.getState().setEdges([makeEdge("e1")]);
		useWorkflowStore.getState().clearHistory();
	});

	it("selectAll marks all graph elements in store", () => {
		selectAll();
		const state = useWorkflowStore.getState();
		expect(state.selectedNodeIds).toEqual(["n1", "n2"]);
		expect(state.selectedEdgeIds).toEqual(["e1"]);
	});

	it("copies selected nodes with internal edges and pastes with new ids", () => {
		useWorkflowStore.getState().setSelectedNodeIds(["n1", "n2"]);
		const payload = copySelection(useWorkflowStore.getState());
		expect(payload?.nodes).toHaveLength(2);
		expect(payload?.edges).toHaveLength(1);

		pasteClipboard(payload);
		const state = useWorkflowStore.getState();
		expect(state.nodes).toHaveLength(4);
		expect(state.edges).toHaveLength(2);
		expect(state.selectedNodeIds).toHaveLength(2);
		expect(state.selectedNodeIds.every((id) => id !== "n1" && id !== "n2")).toBe(true);
	});

	it("deleteSelection removes selected nodes and connected edges in one history entry", () => {
		useWorkflowStore.getState().setSelectedNodeIds(["n1"]);
		deleteSelection();
		let state = useWorkflowStore.getState();
		expect(state.nodes.map((node) => node.id)).toEqual(["n2"]);
		expect(state.edges).toHaveLength(0);
		expect(state.historyPast).toHaveLength(1);

		useWorkflowStore.getState().undo();
		state = useWorkflowStore.getState();
		expect(state.nodes.map((node) => node.id)).toEqual(["n1", "n2"]);
		expect(state.edges.map((edge) => edge.id)).toEqual(["e1"]);
	});
});
