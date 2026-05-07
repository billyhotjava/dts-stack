// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../workflow-store";
import type { WorkflowNode } from "../types";

function makeNode(id: string, overrides: Partial<WorkflowNode> = {}): WorkflowNode {
	return {
		id,
		type: "default",
		position: { x: 0, y: 0 },
		data: { kind: "transform", title: `node-${id}` },
		...overrides,
	};
}

describe("workflow nodes-slice", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("addNode appends a new node and is immutable", () => {
		const before = useWorkflowStore.getState().nodes;
		useWorkflowStore.getState().addNode(makeNode("n1"));
		const after = useWorkflowStore.getState().nodes;
		expect(after).toHaveLength(1);
		expect(after[0].id).toBe("n1");
		expect(after).not.toBe(before);
	});

	it("addNode rejects duplicate ids", () => {
		useWorkflowStore.getState().addNode(makeNode("dup"));
		useWorkflowStore.getState().addNode(makeNode("dup", { data: { kind: "sink", title: "second" } }));
		const nodes = useWorkflowStore.getState().nodes;
		expect(nodes).toHaveLength(1);
		expect(nodes[0].data.kind).toBe("transform");
	});

	it("updateNode merges shallow + nested data without mutating the original reference", () => {
		const original = makeNode("n2", { data: { kind: "source", title: "orig" } });
		useWorkflowStore.getState().addNode(original);
		useWorkflowStore.getState().updateNode("n2", { data: { title: "renamed" }, draggable: false });
		const next = useWorkflowStore.getState().nodes[0];
		expect(next).not.toBe(original);
		expect(next.data).not.toBe(original.data);
		expect(next.data.kind).toBe("source");
		expect(next.data.title).toBe("renamed");
		expect(next.draggable).toBe(false);
		expect(original.data.title).toBe("orig");
	});

	it("removeNode strips the matching id", () => {
		useWorkflowStore.getState().addNode(makeNode("a"));
		useWorkflowStore.getState().addNode(makeNode("b"));
		useWorkflowStore.getState().removeNode("a");
		expect(useWorkflowStore.getState().nodes.map((n) => n.id)).toEqual(["b"]);
	});

	it("setNodePosition replaces position via spread (no mutation)", () => {
		const original = makeNode("p1");
		useWorkflowStore.getState().addNode(original);
		useWorkflowStore.getState().setNodePosition("p1", { x: 120, y: 60 });
		const next = useWorkflowStore.getState().nodes[0];
		expect(next.position).toEqual({ x: 120, y: 60 });
		expect(next.position).not.toBe(original.position);
		expect(original.position).toEqual({ x: 0, y: 0 });
	});

	it("setNodeDragging toggles data.isDragging without losing other data", () => {
		useWorkflowStore.getState().addNode(makeNode("d1", { data: { kind: "validate", title: "v" } }));
		useWorkflowStore.getState().setNodeDragging("d1", true);
		expect(useWorkflowStore.getState().nodes[0].data.isDragging).toBe(true);
		expect(useWorkflowStore.getState().nodes[0].data.title).toBe("v");
		useWorkflowStore.getState().setNodeDragging("d1", false);
		expect(useWorkflowStore.getState().nodes[0].data.isDragging).toBe(false);
	});

	it("setNodes replaces the array via fresh copy", () => {
		const incoming: WorkflowNode[] = [makeNode("x"), makeNode("y")];
		useWorkflowStore.getState().setNodes(incoming);
		const stored = useWorkflowStore.getState().nodes;
		expect(stored).toHaveLength(2);
		expect(stored).not.toBe(incoming);
	});
});
