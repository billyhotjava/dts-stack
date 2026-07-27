// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DomainScopeNav } from "./DomainScopeNav";

let container: HTMLDivElement;
let root: Root;

const render = (element: ReactElement) => {
	act(() => {
		root.render(element);
	});
};

const click = (selector: string) => {
	const node = container.querySelector(selector) as HTMLElement | null;
	if (!node) throw new Error(`节点不存在: ${selector}`);
	act(() => {
		node.click();
	});
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

const NODES = [
	{ id: "d1", name: "地铁域", code: "DTMS", stats: { total: 0, attention: 0 } },
	{ id: "d2", name: "财务域", code: "FIN", stats: { total: 12, attention: 3 } },
];

describe("DomainScopeNav", () => {
	it("空域整行弱化，非空域不弱化", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-row-d1"]')?.getAttribute("data-empty")).toBe("true");
		expect(container.querySelector('[data-testid="domain-scope-row-d2"]')?.getAttribute("data-empty")).toBe("false");
	});

	it("待处置数仅在大于 0 时显示", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-attention-d1"]')).toBeNull();
		expect(container.querySelector('[data-testid="domain-scope-attention-d2"]')?.textContent).toContain("3");
	});

	it("缺少标识的域渲染为禁用且不触发 onChange", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={[{ id: null, name: "无标识域" }]} onChange={onChange} />);

		const row = container.querySelector('[data-testid="domain-scope-row-unidentified-0"]') as HTMLButtonElement;
		expect(row.disabled).toBe(true);
		expect(row.title).toContain("缺少标识");
		act(() => row.click());
		expect(onChange).not.toHaveBeenCalled();
	});

	it("点击业务域回传域 id，点击全部资产回传 undefined", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={NODES} onChange={onChange} />);

		click('[data-testid="domain-scope-row-d2"]');
		expect(onChange).toHaveBeenCalledWith("d2");

		click('[data-testid="domain-scope-row-all"]');
		expect(onChange).toHaveBeenCalledWith(undefined);
	});

	it("未归域独立分区且回传 __UNASSIGNED__", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={NODES} unassignedStats={{ total: 360, attention: 360 }} onChange={onChange} />);

		click('[data-testid="domain-scope-row-unassigned"]');
		expect(onChange).toHaveBeenCalledWith("__UNASSIGNED__");
	});

	it("域数不超过 8 时不渲染搜索框", () => {
		render(<DomainScopeNav nodes={NODES} onChange={vi.fn()} />);
		expect(container.querySelector('[data-testid="domain-scope-search"]')).toBeNull();
	});

	it("域数超过 8 时渲染搜索框并按名称或编码过滤", () => {
		const many = Array.from({ length: 9 }, (_, i) => ({ id: `d${i}`, name: `域${i}`, code: `C${i}` }));
		render(<DomainScopeNav nodes={many} onChange={vi.fn()} />);

		const search = container.querySelector('[data-testid="domain-scope-search"]') as HTMLInputElement;
		expect(search).not.toBeNull();

		act(() => {
			const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
			setter?.call(search, "C7");
			search.dispatchEvent(new Event("input", { bubbles: true }));
		});

		expect(container.querySelector('[data-testid="domain-scope-row-d7"]')).not.toBeNull();
		expect(container.querySelector('[data-testid="domain-scope-row-d1"]')).toBeNull();
	});

	it("统计被截断时数字加 ≥ 前缀", () => {
		render(<DomainScopeNav nodes={NODES} allStats={{ total: 5000, attention: 10 }} truncated onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-total-all"]')?.textContent).toContain("≥");
	});

	it("统计缺失时数字位显示占位符而非 0", () => {
		render(<DomainScopeNav nodes={[{ id: "d9", name: "无统计域" }]} onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-total-d9"]')?.textContent).toBe("—");
	});

	it("无主题域时给出创建引导", () => {
		render(<DomainScopeNav nodes={[]} onChange={vi.fn()} />);
		expect(container.textContent).toContain("尚未创建主题域");
	});

	it("选中行带 aria-current 标记", () => {
		render(<DomainScopeNav nodes={NODES} value="d2" onChange={vi.fn()} />);

		expect(container.querySelector('[data-testid="domain-scope-row-d2"]')?.getAttribute("aria-current")).toBe("true");
		expect(container.querySelector('[data-testid="domain-scope-row-all"]')?.getAttribute("aria-current")).toBeNull();
	});
});
