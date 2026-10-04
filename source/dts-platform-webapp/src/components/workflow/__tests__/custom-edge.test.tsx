// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import {
	DEFAULT_EDGE_TYPE,
	pickEdgeColor,
	resolveEdgeStroke,
	shouldShowRemoveButton,
	workflowEdgeTypes,
	CustomEdge,
} from "../custom-edge";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../store/workflow-store";

describe("custom-edge — pure helpers", () => {
	it("pickEdgeColor returns default slate when key missing", () => {
		expect(pickEdgeColor(undefined, "sourceColor")).toBe("#94a3b8");
		expect(pickEdgeColor({ label: "x" }, "sourceColor")).toBe("#94a3b8");
		expect(pickEdgeColor({ sourceColor: "" } as never, "sourceColor")).toBe("#94a3b8");
	});

	it("pickEdgeColor honors explicit color hex", () => {
		expect(pickEdgeColor({ sourceColor: "#10b981" } as never, "sourceColor")).toBe("#10b981");
		expect(pickEdgeColor({ targetColor: "#f59e0b" } as never, "targetColor")).toBe("#f59e0b");
	});

	it("resolveEdgeStroke prioritises selected > hover > gradient", () => {
		expect(resolveEdgeStroke({ gradientId: "g1", selected: true, hover: true })).toBe("#3b82f6");
		expect(resolveEdgeStroke({ gradientId: "g1", selected: true, hover: false })).toBe("#3b82f6");
		expect(resolveEdgeStroke({ gradientId: "g1", selected: false, hover: true })).toBe("#64748b");
		expect(resolveEdgeStroke({ gradientId: "g1", selected: false, hover: false })).toBe("url(#g1)");
	});

	it("shouldShowRemoveButton hides when condition exists, shows on hover or selected", () => {
		expect(shouldShowRemoveButton({ selected: false, hover: false, data: undefined })).toBe(false);
		expect(shouldShowRemoveButton({ selected: true, hover: false, data: undefined })).toBe(true);
		expect(shouldShowRemoveButton({ selected: false, hover: true, data: undefined })).toBe(true);
		expect(shouldShowRemoveButton({ selected: true, hover: true, data: { condition: "x>0" } })).toBe(false);
	});
});

describe("custom-edge — registry", () => {
	it("workflowEdgeTypes binds CustomEdge to the 'custom' key", () => {
		expect(DEFAULT_EDGE_TYPE).toBe("custom");
		expect(workflowEdgeTypes.custom).toBe(CustomEdge);
	});
});

describe("custom-edge — store integration", () => {
	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	it("removeEdge action drops the edge by id (immutable)", () => {
		const store = useWorkflowStore.getState();
		store.addEdge({ id: "e-a", type: DEFAULT_EDGE_TYPE, source: "n1", target: "n2" });
		store.addEdge({ id: "e-b", type: DEFAULT_EDGE_TYPE, source: "n2", target: "n3" });
		const beforeArr = useWorkflowStore.getState().edges;
		useWorkflowStore.getState().removeEdge("e-a");
		const afterArr = useWorkflowStore.getState().edges;
		expect(afterArr.map((e) => e.id)).toEqual(["e-b"]);
		expect(afterArr).not.toBe(beforeArr);
	});
});
