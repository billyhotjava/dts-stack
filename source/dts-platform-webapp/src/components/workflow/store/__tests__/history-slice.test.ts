// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import type { WorkflowNode } from "../types";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../workflow-store";

function makeNode(id: string): WorkflowNode {
	return {
		id,
		type: "transform",
		position: { x: 0, y: 0 },
		data: { kind: "transform", title: id },
	};
}

describe("workflow history-slice", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("undoes and redoes graph changes", () => {
		useWorkflowStore.getState().addNode(makeNode("n1"));
		expect(useWorkflowStore.getState().canUndo).toBe(true);
		useWorkflowStore.getState().undo();
		expect(useWorkflowStore.getState().nodes).toHaveLength(0);
		expect(useWorkflowStore.getState().canRedo).toBe(true);
		useWorkflowStore.getState().redo();
		expect(useWorkflowStore.getState().nodes.map((node) => node.id)).toEqual(["n1"]);
	});

	it("caps history at 50 snapshots", () => {
		for (let i = 0; i < 55; i += 1) {
			useWorkflowStore.getState().addNode(makeNode(`n${i}`));
		}
		expect(useWorkflowStore.getState().historyPast).toHaveLength(50);
	});

	it("does not record graph changes while paused", () => {
		useWorkflowStore.getState().pauseHistory();
		useWorkflowStore.getState().setNodes([makeNode("initial")]);
		useWorkflowStore.getState().clearHistory();
		expect(useWorkflowStore.getState().canUndo).toBe(false);
		expect(useWorkflowStore.getState().nodes.map((node) => node.id)).toEqual(["initial"]);
	});
});
