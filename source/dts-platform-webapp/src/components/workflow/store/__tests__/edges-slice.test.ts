// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../workflow-store";
import { canConnect } from "../edges-slice";
import type { WorkflowEdge } from "../types";

function makeEdge(id: string, overrides: Partial<WorkflowEdge> = {}): WorkflowEdge {
	return {
		id,
		source: "src",
		target: "tgt",
		...overrides,
	};
}

describe("workflow edges-slice", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("addEdge appends a valid edge", () => {
		useWorkflowStore.getState().addEdge(makeEdge("e1"));
		expect(useWorkflowStore.getState().edges).toHaveLength(1);
	});

	it("addEdge rejects self-loop edges", () => {
		useWorkflowStore.getState().addEdge(makeEdge("loop", { source: "n1", target: "n1" }));
		expect(useWorkflowStore.getState().edges).toHaveLength(0);
	});

	it("addEdge rejects duplicates by source/target/handle", () => {
		const a = makeEdge("a", { source: "n1", target: "n2", sourceHandle: "out", targetHandle: "in" });
		const b = makeEdge("b", { source: "n1", target: "n2", sourceHandle: "out", targetHandle: "in" });
		useWorkflowStore.getState().addEdge(a);
		useWorkflowStore.getState().addEdge(b);
		expect(useWorkflowStore.getState().edges).toHaveLength(1);
	});

	it("addEdge rejects duplicate ids even with different endpoints", () => {
		useWorkflowStore.getState().addEdge(makeEdge("same", { source: "a", target: "b" }));
		useWorkflowStore.getState().addEdge(makeEdge("same", { source: "c", target: "d" }));
		const edges = useWorkflowStore.getState().edges;
		expect(edges).toHaveLength(1);
		expect(edges[0].source).toBe("a");
	});

	it("updateEdge merges shallow + nested data immutably", () => {
		const original = makeEdge("e2", { data: { label: "orig" } });
		useWorkflowStore.getState().addEdge(original);
		useWorkflowStore.getState().updateEdge("e2", { data: { condition: "x>0" }, animated: true });
		const next = useWorkflowStore.getState().edges[0];
		expect(next).not.toBe(original);
		expect(next.data?.label).toBe("orig");
		expect(next.data?.condition).toBe("x>0");
		expect(next.animated).toBe(true);
	});

	it("removeEdge strips matching id", () => {
		useWorkflowStore.getState().addEdge(makeEdge("e3"));
		useWorkflowStore.getState().addEdge(makeEdge("e4", { source: "x", target: "y" }));
		useWorkflowStore.getState().removeEdge("e3");
		expect(useWorkflowStore.getState().edges.map((e) => e.id)).toEqual(["e4"]);
	});

	it("setEdges replaces with a fresh copy", () => {
		const incoming: WorkflowEdge[] = [makeEdge("z1"), makeEdge("z2", { source: "p", target: "q" })];
		useWorkflowStore.getState().setEdges(incoming);
		expect(useWorkflowStore.getState().edges).toHaveLength(2);
		expect(useWorkflowStore.getState().edges).not.toBe(incoming);
	});

	it("canConnect rejects empty source or target and self-loops", () => {
		expect(canConnect([], { source: "", target: "n2" })).toBe(false);
		expect(canConnect([], { source: "n1", target: "" })).toBe(false);
		expect(canConnect([], { source: "n1", target: "n1" })).toBe(false);
	});

	it("canConnect distinguishes connections by handle pair", () => {
		const existing: WorkflowEdge[] = [
			{ id: "x", source: "a", target: "b", sourceHandle: "out-1", targetHandle: "in-1" },
		];
		expect(
			canConnect(existing, { source: "a", target: "b", sourceHandle: "out-1", targetHandle: "in-1" }),
		).toBe(false);
		expect(
			canConnect(existing, { source: "a", target: "b", sourceHandle: "out-2", targetHandle: "in-1" }),
		).toBe(true);
	});
});
