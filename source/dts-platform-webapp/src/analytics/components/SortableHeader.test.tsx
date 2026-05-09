// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { describe, expect, it, vi } from "vitest";
import type { SortState } from "../hooks/useTableSort";
import { SortableHeader } from "./SortableHeader";

function renderTH(ui: ReactElement): { container: HTMLElement; unmount: () => void } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(
			<table>
				<thead>
					<tr>{ui}</tr>
				</thead>
			</table>,
		);
	});
	return {
		container,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

describe("SortableHeader", () => {
	it("aria-sort=none and ↕ caret when not the active key", () => {
		const sortState: SortState = { key: "other", direction: "asc" };
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="name" sortState={sortState} onSort={() => {}}>
				名称
			</SortableHeader>,
		);
		const th = container.querySelector("th") as HTMLTableCellElement;
		expect(th.getAttribute("aria-sort")).toBe("none");
		expect(th.textContent).toContain("↕");
		expect(th.textContent).toContain("名称");
		unmount();
	});

	it("aria-sort=ascending and ↑ caret when active asc", () => {
		const sortState: SortState = { key: "name", direction: "asc" };
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="name" sortState={sortState} onSort={() => {}}>
				名称
			</SortableHeader>,
		);
		const th = container.querySelector("th") as HTMLTableCellElement;
		expect(th.getAttribute("aria-sort")).toBe("ascending");
		expect(th.textContent).toContain("↑");
		unmount();
	});

	it("aria-sort=descending and ↓ caret when active desc", () => {
		const sortState: SortState = { key: "name", direction: "desc" };
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="name" sortState={sortState} onSort={() => {}}>
				名称
			</SortableHeader>,
		);
		const th = container.querySelector("th") as HTMLTableCellElement;
		expect(th.getAttribute("aria-sort")).toBe("descending");
		expect(th.textContent).toContain("↓");
		unmount();
	});

	it("invokes onSort with sortKey on click", () => {
		const onSort = vi.fn();
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="updatedAt" sortState={{ key: null, direction: null }} onSort={onSort}>
				更新时间
			</SortableHeader>,
		);
		const button = container.querySelector("button") as HTMLButtonElement;
		act(() => button.click());
		expect(onSort).toHaveBeenCalledWith("updatedAt");
		unmount();
	});

	it("invokes onSort on Enter key", () => {
		const onSort = vi.fn();
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="updatedAt" sortState={{ key: null, direction: null }} onSort={onSort}>
				更新时间
			</SortableHeader>,
		);
		const button = container.querySelector("button") as HTMLButtonElement;
		act(() => {
			button.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
		});
		expect(onSort).toHaveBeenCalledWith("updatedAt");
		unmount();
	});

	it("invokes onSort on Space key", () => {
		const onSort = vi.fn();
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="updatedAt" sortState={{ key: null, direction: null }} onSort={onSort}>
				更新时间
			</SortableHeader>,
		);
		const button = container.querySelector("button") as HTMLButtonElement;
		act(() => {
			button.dispatchEvent(new KeyboardEvent("keydown", { key: " ", bubbles: true }));
		});
		expect(onSort).toHaveBeenCalledWith("updatedAt");
		unmount();
	});

	it("supports right-aligned variant", () => {
		const { container, unmount } = renderTH(
			<SortableHeader sortKey="visits" sortState={{ key: null, direction: null }} onSort={() => {}} align="right">
				访问量
			</SortableHeader>,
		);
		const th = container.querySelector("th") as HTMLTableCellElement;
		expect(th.className).toContain("text-right");
		unmount();
	});

	it("merges custom className onto the th", () => {
		const { container, unmount } = renderTH(
			<SortableHeader
				sortKey="status"
				sortState={{ key: null, direction: null }}
				onSort={() => {}}
				className="whitespace-nowrap"
			>
				状态
			</SortableHeader>,
		);
		const th = container.querySelector("th") as HTMLTableCellElement;
		expect(th.className).toContain("whitespace-nowrap");
		unmount();
	});
});
