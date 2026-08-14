// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { actionColumn, actionColumnWidth, RowActions } from "./RowActions";

if (typeof window.matchMedia !== "function") {
	window.matchMedia = ((query: string) => ({
		matches: false,
		media: query,
		onchange: null,
		addListener: () => {},
		removeListener: () => {},
		addEventListener: () => {},
		removeEventListener: () => {},
		dispatchEvent: () => false,
	})) as typeof window.matchMedia;
}
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

const render = (node: ReactElement) => {
	act(() => {
		root.render(node);
	});
};

// antd 会给“恰好两个汉字”的纯文字按钮插入空格（删除 → 删 除），比对前去掉空白。
const squash = (value: string | null | undefined) => (value ?? "").replace(/\s/g, "");
const buttons = () => Array.from(container.querySelectorAll("button"));
const buttonByText = (label: string) => buttons().find((item) => squash(item.textContent) === squash(label));

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => {
		root.unmount();
	});
	container.remove();
});

describe("RowActions", () => {
	it("renders every action as a small text-only bordered button", () => {
		render(
			<RowActions
				items={[
					{ key: "detail", label: "详情", onClick: vi.fn() },
					{ key: "edit", label: "编辑", onClick: vi.fn() },
				]}
			/>,
		);

		const rendered = buttons();
		expect(rendered).toHaveLength(2);
		for (const button of rendered) {
			expect(button.classList.contains("ant-btn-sm")).toBe(true);
			// 基线是带边框的默认按钮，不是 type="link" 文字链
			expect(button.classList.contains("ant-btn-link")).toBe(false);
			expect(button.querySelector("svg")).toBeNull();
		}
	});

	it("invokes the handler on click", () => {
		const onClick = vi.fn();
		render(<RowActions items={[{ key: "edit", label: "编辑", onClick }]} />);

		act(() => {
			buttonByText("编辑")?.click();
		});
		expect(onClick).toHaveBeenCalledTimes(1);
	});

	it("marks danger actions and honours disabled", () => {
		render(
			<RowActions
				items={[
					{ key: "delete", label: "删除", danger: true, disabled: true },
					{ key: "edit", label: "编辑" },
				]}
			/>,
		);

		const remove = buttonByText("删除");
		expect(remove?.classList.contains("ant-btn-dangerous")).toBe(true);
		expect(remove?.disabled).toBe(true);
		expect(buttonByText("编辑")?.disabled).toBe(false);
	});

	it("skips hidden actions", () => {
		render(
			<RowActions
				items={[
					{ key: "edit", label: "编辑" },
					{ key: "delete", label: "删除", hidden: true },
				]}
			/>,
		);

		expect(buttons()).toHaveLength(1);
		expect(buttonByText("删除")).toBeUndefined();
	});

	it("falls back to placeholder text when nothing is available", () => {
		render(<RowActions items={[{ key: "edit", label: "编辑", hidden: true }]} emptyText="内置" />);

		expect(buttons()).toHaveLength(0);
		expect(container.textContent).toBe("内置");
	});

	it("mirrors a string tooltip onto the native title attribute", () => {
		render(<RowActions items={[{ key: "delete", label: "删除", disabled: true, tooltip: "内置记录不可删除" }]} />);

		expect(buttonByText("删除")?.title).toBe("内置记录不可删除");
		expect(container.querySelector("[data-tooltip], .ant-tooltip-open, span")).not.toBeNull();
	});

	it("defers the handler to Popconfirm when confirm is set", () => {
		const onClick = vi.fn();
		render(<RowActions items={[{ key: "delete", label: "删除", danger: true, confirm: "确认删除？", onClick }]} />);

		act(() => {
			buttonByText("删除")?.click();
		});
		// 点击只应打开确认气泡，不能直接触发动作
		expect(onClick).not.toHaveBeenCalled();
		expect(document.body.textContent).toContain("确认删除？");
	});
});

describe("actionColumnWidth", () => {
	it("keeps the data-integration baseline width for a single action", () => {
		expect(actionColumnWidth(1)).toBe(90);
		expect(actionColumnWidth(0)).toBe(90);
	});

	it("scales with the number of actions", () => {
		expect(actionColumnWidth(2)).toBe(168);
		expect(actionColumnWidth(3)).toBe(240);
	});
});

describe("actionColumn", () => {
	it("produces the standard right-pinned action column", () => {
		const column = actionColumn<{ id: string }>(() => [{ key: "detail", label: "详情" }], { maxActions: 3 });

		expect(column.title).toBe("操作");
		expect(column.key).toBe("actions");
		expect(column.fixed).toBe("right");
		expect(column.width).toBe(240);
	});

	it("lets the caller override width and pinning", () => {
		const column = actionColumn<{ id: string }>(() => [], { width: 120, fixed: false });

		expect(column.width).toBe(120);
		expect(column.fixed).toBeUndefined();
	});
});
