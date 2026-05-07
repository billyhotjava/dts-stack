// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import {
	CONNECTION_INVALID_COLOR,
	CONNECTION_PENDING_COLOR,
	CONNECTION_VALID_COLOR,
	pickConnectionStroke,
	useIsValidWorkflowConnection,
} from "../custom-connection-line";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../store/workflow-store";
import type { IsValidConnection } from "@xyflow/react";
import type { WorkflowEdge } from "../store/types";

describe("custom-connection-line — pickConnectionStroke", () => {
	it("maps connectionStatus to colour", () => {
		expect(pickConnectionStroke("valid")).toBe(CONNECTION_VALID_COLOR);
		expect(pickConnectionStroke("invalid")).toBe(CONNECTION_INVALID_COLOR);
		expect(pickConnectionStroke(null)).toBe(CONNECTION_PENDING_COLOR);
	});
});

interface HookHarness {
	get: () => IsValidConnection<WorkflowEdge>;
	unmount: () => void;
}

function mountHook(): HookHarness {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let captured: IsValidConnection<WorkflowEdge> | null = null;
	function HookProbe() {
		captured = useIsValidWorkflowConnection();
		return null;
	}
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(<HookProbe />);
	});
	return {
		get: () => {
			if (!captured) throw new Error("hook not initialised");
			return captured;
		},
		unmount: () => {
			act(() => root.unmount());
			document.body.removeChild(container);
		},
	};
}

describe("custom-connection-line — useIsValidWorkflowConnection", () => {
	let harness: HookHarness | null = null;

	beforeEach(() => {
		resetWorkflowStoreForTest();
	});

	afterEach(() => {
		harness?.unmount();
		harness = null;
	});

	it("rejects self-loop and empty endpoints", () => {
		harness = mountHook();
		const isValid = harness.get();
		expect(isValid({ source: "n1", target: "n1", sourceHandle: null, targetHandle: null })).toBe(false);
		expect(isValid({ source: "", target: "n2", sourceHandle: null, targetHandle: null })).toBe(false);
	});

	it("accepts new distinct connection but rejects exact duplicates already in store", () => {
		act(() => {
			useWorkflowStore.getState().addEdge({
				id: "e1",
				source: "n1",
				target: "n2",
				sourceHandle: "out",
				targetHandle: "in",
			});
		});
		harness = mountHook();
		const isValid = harness.get();
		expect(
			isValid({ source: "n1", target: "n2", sourceHandle: "out2", targetHandle: "in" }),
		).toBe(true);
		expect(
			isValid({ source: "n1", target: "n2", sourceHandle: "out", targetHandle: "in" }),
		).toBe(false);
	});
});
