// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { detectAlignment } from "../hooks/use-help-line";
import type { WorkflowNode } from "../store/types";
import { resetWorkflowStoreForTest } from "../store/workflow-store";

function makeNode(id: string, x: number, y: number): WorkflowNode {
	return {
		id,
		type: "default",
		position: { x, y },
		data: { kind: "transform", title: id },
	};
}

describe("detectAlignment", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("returns nulls when there are no other nodes", () => {
		const dragging = makeNode("a", 100, 100);
		expect(detectAlignment({ draggingNode: dragging, otherNodes: [] })).toEqual({
			vertical: null,
			horizontal: null,
		});
	});

	it("aligns vertically when x within threshold and picks the closest", () => {
		const dragging = makeNode("a", 100, 50);
		const others = [makeNode("b", 102, 200), makeNode("c", 105, 300), makeNode("d", 100, 400)];
		expect(detectAlignment({ draggingNode: dragging, otherNodes: others, threshold: 5 })).toEqual({
			vertical: 100,
			horizontal: null,
		});
	});

	it("aligns horizontally when y within threshold", () => {
		const dragging = makeNode("a", 50, 100);
		const others = [makeNode("b", 200, 102)];
		expect(detectAlignment({ draggingNode: dragging, otherNodes: others, threshold: 5 })).toEqual({
			vertical: null,
			horizontal: 102,
		});
	});

	it("returns nulls when distance exceeds threshold", () => {
		const dragging = makeNode("a", 100, 100);
		const others = [makeNode("b", 110, 110)];
		expect(detectAlignment({ draggingNode: dragging, otherNodes: others, threshold: 5 })).toEqual({
			vertical: null,
			horizontal: null,
		});
	});

	it("ignores the dragging node itself", () => {
		const dragging = makeNode("a", 100, 100);
		expect(
			detectAlignment({ draggingNode: dragging, otherNodes: [dragging], threshold: 5 }),
		).toEqual({ vertical: null, horizontal: null });
	});

	it("returns both axes when both align", () => {
		const dragging = makeNode("a", 200, 300);
		const others = [makeNode("b", 200, 300)];
		expect(detectAlignment({ draggingNode: dragging, otherNodes: others, threshold: 1 })).toEqual({
			vertical: 200,
			horizontal: 300,
		});
	});
});
