// @vitest-environment jsdom
import type { ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { NodePanel } from "../panel";
import { resetWorkflowStoreForTest, useWorkflowStore } from "../store/workflow-store";

let root: Root | null = null;
let host: HTMLDivElement | null = null;

function render(node: ReactNode) {
	host = document.createElement("div");
	document.body.appendChild(host);
	act(() => {
		root = createRoot(host as HTMLDivElement);
		root.render(node);
	});
	return host as HTMLDivElement;
}

function setReactInputValue(input: HTMLInputElement, value: string) {
	const setter = Object.getOwnPropertyDescriptor<HTMLInputElement>(HTMLInputElement.prototype, "value")?.set;
	act(() => {
		setter?.call(input, value);
		input.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

function setReactSelectValue(select: HTMLSelectElement, value: string) {
	const setter = Object.getOwnPropertyDescriptor<HTMLSelectElement>(HTMLSelectElement.prototype, "value")?.set;
	act(() => {
		setter?.call(select, value);
		select.dispatchEvent(new Event("change", { bubbles: true }));
	});
}

afterEach(() => {
	if (root && host) {
		const currentRoot = root;
		act(() => currentRoot.unmount());
		document.body.removeChild(host);
	}
	root = null;
	host = null;
	resetWorkflowStoreForTest();
});

describe("NodePanel", () => {
	it("renders the selected node form and syncs changes into store", () => {
		resetWorkflowStoreForTest();
		useWorkflowStore.getState().addNode({
			id: "start-1",
			type: "start",
			position: { x: 0, y: 0 },
			data: { kind: "start", title: "开始", config: { trigger: "manual" } },
		});
		useWorkflowStore.getState().setSelectedNodeId("start-1");
		useWorkflowStore.getState().setPanelOpen(true);

		const container = render(<NodePanel />);
		expect(container.textContent).toContain("开始");
		const select = container.querySelector<HTMLSelectElement>("select");
		expect(select).not.toBeNull();
		if (!select) throw new Error("start trigger select should render");
		setReactSelectValue(select, "cron");

		const node = useWorkflowStore.getState().nodes[0];
		expect(node.data.config?.trigger).toBe("cron");
	});

	it("closes on Escape when unlocked", () => {
		useWorkflowStore.getState().addNode({
			id: "end-1",
			type: "end",
			position: { x: 0, y: 0 },
			data: { kind: "end", title: "结束", config: { strategy: "success" } },
		});
		useWorkflowStore.getState().setSelectedNodeId("end-1");
		useWorkflowStore.getState().setPanelOpen(true);
		render(<NodePanel />);

		act(() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })));

		expect(useWorkflowStore.getState().panelOpen).toBe(false);
		expect(useWorkflowStore.getState().selectedNodeId).toBeNull();
	});

	it("syncs note color and dimensions from the panel", () => {
		useWorkflowStore.getState().addNode({
			id: "note-1",
			type: "note",
			position: { x: 0, y: 0 },
			data: { kind: "note", title: "便签", config: { content: "备注", color: "#fef3c7", width: 220, height: 130 } },
		});
		useWorkflowStore.getState().setSelectedNodeId("note-1");
		useWorkflowStore.getState().setPanelOpen(true);

		const container = render(<NodePanel />);
		const color = container.querySelector<HTMLSelectElement>("select");
		const inputs = container.querySelectorAll<HTMLInputElement>("input[type='number']");
		expect(color).not.toBeNull();
		expect(inputs).toHaveLength(2);

		if (!color) throw new Error("note color select should render");
		setReactSelectValue(color, "#dbeafe");
		setReactInputValue(inputs[0], "260");
		setReactInputValue(inputs[1], "180");

		const node = useWorkflowStore.getState().nodes[0];
		expect(node.data.config?.color).toBe("#dbeafe");
		expect(node.data.config?.width).toBe(260);
		expect(node.data.config?.height).toBe(180);
	});
});
