// @vitest-environment jsdom

import type { ChangeEvent, ChangeEventHandler, KeyboardEvent, MouseEventHandler, ReactElement, ReactNode } from "react";
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

type AlertMockProps = {
	action?: ReactNode;
	description?: ReactNode;
	message?: ReactNode;
};

type ButtonMockProps = {
	children?: ReactNode;
	disabled?: boolean;
	loading?: boolean;
	onClick?: MouseEventHandler<HTMLButtonElement>;
};

type ChildrenMockProps = {
	children?: ReactNode;
};

type EmptyMockProps = {
	description?: ReactNode;
};

type SearchMockProps = {
	onChange?: ChangeEventHandler<HTMLInputElement>;
	onSearch?: (value: string) => void;
	placeholder?: string;
	value?: string;
};

type SelectMockOption = {
	label: ReactNode;
	value: string;
};

type SelectMockProps = {
	onChange?: (value: string | undefined) => void;
	options?: SelectMockOption[];
	placeholder?: string;
	value?: string;
};

vi.mock("antd", async () => {
	const React = await import("react");
	const Search = ({ value, onChange, onSearch, placeholder }: SearchMockProps) =>
		React.createElement("input", {
			"aria-label": placeholder,
			value,
			onChange,
			onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => {
				if (event.key === "Enter") onSearch?.(event.currentTarget.value);
			},
		});
	return {
		Alert: ({ message, description, action }: AlertMockProps) =>
			React.createElement("div", { role: "alert" }, message, description, action),
		Button: ({ children, onClick, disabled, loading }: ButtonMockProps) =>
			React.createElement(
				"button",
				{ "aria-busy": loading || undefined, type: "button", onClick, disabled: disabled || loading },
				children,
			),
		Empty: ({ description }: EmptyMockProps) => React.createElement("div", { "data-empty": "true" }, description),
		Input: { Search },
		Select: ({ value, onChange, placeholder, options }: SelectMockProps) =>
			React.createElement(
				"select",
				{
					"aria-label": placeholder,
					value: value || "",
					onChange: (event: ChangeEvent<HTMLSelectElement>) => onChange?.(event.currentTarget.value || undefined),
				},
				[
					React.createElement("option", { key: "", value: "" }, "全部"),
					...(options || []).map((option) =>
						React.createElement("option", { key: option.value, value: option.value }, option.label),
					),
				],
			),
		Space: ({ children }: ChildrenMockProps) => React.createElement("div", null, children),
		Spin: () => React.createElement("div", { "data-testid": "loading" }, "loading"),
		Tag: ({ children }: ChildrenMockProps) => React.createElement("span", null, children),
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

const reactActEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
let previousReactActEnvironment: boolean | undefined;

describe("RelationshipGraphPanel cursor continuation", () => {
	beforeEach(() => {
		previousReactActEnvironment = reactActEnvironment.IS_REACT_ACT_ENVIRONMENT;
		reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
		getWarehousePlanRelationshipGraph.mockReset();
	});

	afterEach(() => {
		reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = previousReactActEnvironment;
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

	it("refreshes from the first page without reusing nextCursor and clears the previous graph", async () => {
		const refreshResult = deferred<WarehousePlanRelationshipGraph>();
		getWarehousePlanRelationshipGraph
			.mockResolvedValueOnce(graph("MODEL:first@1", "First page", "opaque-composite-cursor-v1"))
			.mockReturnValueOnce(refreshResult.promise);
		const { container, root } = await render(<RelationshipGraphPanel planId="plan-1" onNavigate={vi.fn()} />);

		const refreshButton = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "刷新",
		);
		expect(refreshButton).toBeDefined();
		await act(async () => refreshButton?.click());
		await flush();

		expect(getWarehousePlanRelationshipGraph).toHaveBeenNthCalledWith(
			2,
			"plan-1",
			expect.objectContaining({ cursor: undefined }),
		);
		expect(container.textContent).not.toContain("First page");
		expect(refreshButton?.disabled).toBe(true);

		refreshResult.resolve(graph("MODEL:refreshed@1", "Refreshed page", null));
		await flush();
		expect(container.textContent).toContain("Refreshed page");
		act(() => root.unmount());
	});

	it("retries a failed continuation from the first page and keeps the previous graph cleared", async () => {
		const retryResult = deferred<WarehousePlanRelationshipGraph>();
		getWarehousePlanRelationshipGraph
			.mockResolvedValueOnce(graph("MODEL:first@1", "First page", "opaque-composite-cursor-v1"))
			.mockRejectedValueOnce(new Error("cursor expired"))
			.mockReturnValueOnce(retryResult.promise);
		const { container, root } = await render(<RelationshipGraphPanel planId="plan-1" onNavigate={vi.fn()} />);

		const continueButton = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("继续搜索"),
		);
		await act(async () => continueButton?.click());
		await flush();
		expect(container.textContent).toContain("关系图不可用");
		expect(container.textContent).not.toContain("First page");

		const retryButton = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "重试",
		);
		expect(retryButton).toBeDefined();
		await act(async () => retryButton?.click());
		await flush();

		expect(getWarehousePlanRelationshipGraph).toHaveBeenNthCalledWith(
			3,
			"plan-1",
			expect.objectContaining({ cursor: undefined }),
		);
		expect(container.textContent).not.toContain("First page");
		expect(container.textContent).not.toContain("关系图不可用");

		retryResult.resolve(graph("MODEL:retried@1", "Retried page", null));
		await flush();
		expect(container.textContent).toContain("Retried page");
		act(() => root.unmount());
	});
});
