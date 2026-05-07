// @vitest-environment jsdom

import type { ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/components/workflow", () => ({
	BlockSelectorPanel: () => <div data-testid="block-selector-panel">blocks</div>,
	deserializeDsl: () => ({ success: true }),
	serializeDsl: () => ({ dslVersion: "1.0", nodes: [], edges: [] }),
	useWorkflowStore: {
		getState: () => ({
			edges: [],
			nodes: [],
			viewport: { x: 0, y: 0, zoom: 1 },
		}),
	},
	WorkflowCanvas: () => <div data-testid="workflow-canvas">canvas</div>,
}));

vi.mock("../OrchestrationRunsTab", () => ({
	default: () => <div data-testid="runs-tab">runs</div>,
}));

import OrchestrationPage from "../OrchestrationPage";

let root: Root | null = null;
let host: HTMLDivElement | null = null;

function render(node: ReactNode): HTMLDivElement {
	host = document.createElement("div");
	document.body.appendChild(host);
	act(() => {
		root = createRoot(host as HTMLDivElement);
		root.render(node);
	});
	return host as HTMLDivElement;
}

afterEach(() => {
	if (root && host) {
		act(() => root?.unmount());
		document.body.removeChild(host);
	}
	root = null;
	host = null;
});

describe("OrchestrationPage", () => {
	it("renders both tab labels and defaults to canvas", () => {
		const container = render(
			<MemoryRouter>
				<OrchestrationPage />
			</MemoryRouter>,
		);
		expect(container.textContent).toContain("编排画布");
		expect(container.textContent).toContain("运行实例");
		// 默认激活 canvas tab
		expect(container.querySelector('[data-testid="workflow-canvas"]')).not.toBeNull();
	});

	it("switches to runs tab when 运行实例 is clicked", () => {
		const container = render(
			<MemoryRouter>
				<OrchestrationPage />
			</MemoryRouter>,
		);
		const runsTab = Array.from(container.querySelectorAll('[role="tab"]')).find((el) =>
			el.textContent?.includes("运行实例"),
		) as HTMLElement | undefined;
		expect(runsTab).not.toBeUndefined();
		act(() => {
			runsTab?.click();
		});
		// 由于 destroyInactiveTabPane=false，画布始终在 DOM；这里通过聚焦 active 状态判断
		expect(runsTab?.getAttribute("aria-selected")).toBe("true");
	});
});
