// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { getWarehousePlanRelationshipGraph } = vi.hoisted(() => ({
	getWarehousePlanRelationshipGraph: vi.fn(),
}));

vi.mock("@/api/warehousePlanApi", () => ({
	getWarehousePlanRelationshipGraph,
}));

vi.mock("lucide-react", () => ({
	RefreshCw: () => null,
}));

vi.mock("@/components/lineage/LineageGraph", () => ({
	LineageGraph: ({ nodes }: { nodes: Array<{ id: string; name: string }> }) => (
		<div data-testid="lineage-graph">
			{nodes.map((node) => (
				<span data-node-id={node.id} key={node.id}>
					{node.name}
				</span>
			))}
		</div>
	),
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const Search = ({ value, onChange, onSearch, placeholder }: any) =>
		React.createElement("input", {
			"aria-label": placeholder,
			value,
			onChange,
			onKeyDown: (event: any) => {
				if (event.key === "Enter") onSearch(event.currentTarget.value);
			},
		});
	return {
		Alert: ({ message, description, action }: any) =>
			React.createElement("div", { role: "alert" }, message, description, action),
		Button: ({ children, onClick, disabled }: any) =>
			React.createElement("button", { type: "button", onClick, disabled }, children),
		Empty: ({ description }: any) => React.createElement("div", { "data-empty": "true" }, description),
		Input: { Search },
		Select: ({ value, onChange, placeholder, options }: any) =>
			React.createElement(
				"select",
				{
					"aria-label": placeholder,
					value: value || "",
					onChange: (event: any) => onChange(event.currentTarget.value || undefined),
				},
				[
					React.createElement("option", { key: "", value: "" }, "全部"),
					...(options || []).map((option: any) =>
						React.createElement("option", { key: option.value, value: option.value }, option.label),
					),
				],
			),
		Space: ({ children }: any) => React.createElement("div", null, children),
		Spin: () => React.createElement("div", { "data-testid": "loading" }, "loading"),
		Tag: ({ children }: any) => React.createElement("span", null, children),
	};
});

import type { WarehousePlanRelationshipGraph } from "@/api/warehousePlanApi";
import RelationshipGraphPanel from "./RelationshipGraphPanel";

type Deferred<T> = {
	promise: Promise<T>;
	resolve: (value: T) => void;
};

const deferred = <T,>(): Deferred<T> => {
	let resolve!: (value: T) => void;
	const promise = new Promise<T>((nextResolve) => {
		resolve = nextResolve;
	});
	return { promise, resolve };
};

const graph = (id: string, label: string, nextCursor: string | null): WarehousePlanRelationshipGraph => ({
	planId: "plan-1",
	nodes: [{ id, kind: "MODEL", label, status: "DRAFT", route: `/models/${id}` }],
	edges: [],
	truncated: nextCursor !== null,
	nextHint: nextCursor ? "CONTINUE_WITH_CURSOR" : null,
	nextCursor,
});

const flush = async () => {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
};

const render = async (ui: ReactElement): Promise<{ container: HTMLElement; root: Root }> => {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flush();
	return { container, root };
};

describe("RelationshipGraphPanel cursor continuation", () => {
	beforeEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
		getWarehousePlanRelationshipGraph.mockReset();
	});

	afterEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
		document.body.innerHTML = "";
	});

	it("continues the same filter with nextCursor and replaces the previous page", async () => {
		const cursor = "opaque-composite-cursor-v1";
		getWarehousePlanRelationshipGraph
			.mockResolvedValueOnce(graph("MODEL:first@1", "First page", cursor))
			.mockResolvedValueOnce(graph("MODEL:second@1", "Second page", null));
		const { container, root } = await render(<RelationshipGraphPanel planId="plan-1" onNavigate={vi.fn()} />);

		const continueButton = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("继续搜索"),
		);
		expect(continueButton).toBeDefined();
		await act(async () => continueButton?.click());
		await flush();

		expect(getWarehousePlanRelationshipGraph).toHaveBeenNthCalledWith(2, "plan-1", expect.objectContaining({ cursor }));
		expect(container.textContent).toContain("Second page");
		expect(container.textContent).not.toContain("First page");
		act(() => root.unmount());
	});

	it("resets cursor and clears stale nodes when filters change while ignoring an older response", async () => {
		const cursor = "opaque-composite-cursor-v1";
		const staleContinuation = deferred<WarehousePlanRelationshipGraph>();
		const currentFilter = deferred<WarehousePlanRelationshipGraph>();
		getWarehousePlanRelationshipGraph
			.mockResolvedValueOnce(graph("MODEL:first@1", "First page", cursor))
			.mockReturnValueOnce(staleContinuation.promise)
			.mockReturnValueOnce(currentFilter.promise);
		const { container, root } = await render(<RelationshipGraphPanel planId="plan-1" onNavigate={vi.fn()} />);

		const continueButton = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("继续搜索"),
		);
		await act(async () => continueButton?.click());
		await flush();
		expect(container.textContent).not.toContain("First page");

		const kindSelect = container.querySelector("select[aria-label='请选择节点类型']") as HTMLSelectElement;
		await act(async () => {
			kindSelect.value = "MODEL";
			kindSelect.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await flush();

		expect(getWarehousePlanRelationshipGraph).toHaveBeenNthCalledWith(
			3,
			"plan-1",
			expect.objectContaining({ kind: "MODEL", cursor: undefined }),
		);
		currentFilter.resolve(graph("MODEL:filtered@1", "Filtered page", null));
		await flush();
		staleContinuation.resolve(graph("MODEL:stale@1", "Stale page", null));
		await flush();

		expect(container.textContent).toContain("Filtered page");
		expect(container.textContent).not.toContain("Stale page");
		act(() => root.unmount());
	});
});
