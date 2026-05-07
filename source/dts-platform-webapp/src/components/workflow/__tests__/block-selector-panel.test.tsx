// @vitest-environment jsdom

import type { ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BlockSelectorPanel } from "../block-selector/BlockSelectorPanel";
import { BLOCK_DRAG_MIME, BLOCKS } from "../block-selector/blocks.config";

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

function requireElement<T extends Element>(element: T | null, message: string): T {
	if (!element) {
		throw new Error(message);
	}
	return element;
}

afterEach(() => {
	if (root && host) {
		const currentRoot = root;
		act(() => currentRoot.unmount());
		document.body.removeChild(host);
	}
	root = null;
	host = null;
});

/** 触发 React 受控 input 的变化 — 必须走 prototype setter，否则 React 看不到 value 改变 */
function setReactInputValue(input: HTMLInputElement, value: string) {
	const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
	act(() => {
		setter?.call(input, value);
		input.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

describe("BlockSelectorPanel", () => {
	it("renders all blocks grouped by category by default", () => {
		const container = render(<BlockSelectorPanel />);
		for (const block of BLOCKS) {
			expect(container.querySelector(`[data-testid="block-${block.kind}"]`)).not.toBeNull();
		}
		expect(container.textContent).toContain("基础");
		expect(container.textContent).toContain("数据源");
	});

	it("filters blocks via the search input", () => {
		const container = render(<BlockSelectorPanel />);
		const input = requireElement(
			container.querySelector<HTMLInputElement>('input[type="search"]'),
			"search input should render",
		);
		setReactInputValue(input, "transform");
		expect(container.querySelector('[data-testid="block-transform"]')).not.toBeNull();
		expect(container.querySelector('[data-testid="block-source"]')).toBeNull();
	});

	it("shows empty hint when no match", () => {
		const container = render(<BlockSelectorPanel />);
		const input = requireElement(
			container.querySelector<HTMLInputElement>('input[type="search"]'),
			"search input should render",
		);
		setReactInputValue(input, "xyz-no-match");
		expect(container.textContent).toContain("未找到匹配节点");
	});

	it("collapse toggle hides the search input and group titles", () => {
		const container = render(<BlockSelectorPanel />);
		const toggle = container.querySelector<HTMLButtonElement>(".block-selector-panel__toggle");
		expect(toggle?.getAttribute("aria-label")).toContain("折叠");
		act(() => toggle?.click());
		expect(container.querySelector('input[type="search"]')).toBeNull();
		expect(container.querySelector(".block-selector-panel__group-title")).toBeNull();
		expect(toggle?.getAttribute("aria-label")).toContain("展开");
	});

	it("emits drag start callback with the dragged block", () => {
		const onDragStart = vi.fn();
		const container = render(<BlockSelectorPanel onBlockDragStart={onDragStart} />);
		const item = requireElement(
			container.querySelector<HTMLButtonElement>('[data-testid="block-source"]'),
			"source block should render",
		);
		const setDataMock = vi.fn();
		const setDragImageMock = vi.fn();
		const event = new Event("dragstart", { bubbles: true }) as unknown as DragEvent;
		Object.defineProperty(event, "dataTransfer", {
			value: {
				setData: setDataMock,
				setDragImage: setDragImageMock,
				get effectAllowed() {
					return "";
				},
				set effectAllowed(_v: string) {},
			},
		});
		act(() => {
			item.dispatchEvent(event);
		});
		expect(setDataMock).toHaveBeenCalledTimes(1);
		expect(setDataMock.mock.calls[0][0]).toBe(BLOCK_DRAG_MIME);
		expect(setDragImageMock).toHaveBeenCalledTimes(1);
		expect(onDragStart).toHaveBeenCalledTimes(1);
		expect(onDragStart.mock.calls[0][0].kind).toBe("source");
	});
});
