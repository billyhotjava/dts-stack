// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	DomainScopeNav,
	flattenScopeNodes,
	rankScopeNodes,
	SCOPE_SEARCH_THRESHOLD,
	SCOPE_TOP_N,
} from "./DomainScopeNav";

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

const domain = (id: string | null, name: string, total: number, attention = 0, children?: any[]) => ({
	id,
	name,
	stats: { total, attention },
	...(children ? { children } : {}),
});

const PARENT_AND_CHILD = [
	domain("p1", "研发域", 20, 2, [domain("c1", "项目域", 12, 1), domain("c2", "代码域", 8)]),
	domain("d2", "财务域", 32, 5),
	domain("d3", "人力资源域", 21, 1),
	domain("d4", "供应链域", 14),
	domain("d5", "客户主数据域", 8),
	domain("d6", "风控域", 3),
	domain("d7", "审计域", 1),
	domain("d8", "档案域", 0),
];

describe("flattenScopeNodes", () => {
	it("递归展开为一层并保留统计，父域不重复计数", () => {
		const flat = flattenScopeNodes(PARENT_AND_CHILD);
		expect(flat).toHaveLength(10);
		expect(flat.map((n) => n.id)).toEqual(["p1", "c1", "c2", "d2", "d3", "d4", "d5", "d6", "d7", "d8"]);
		expect(flat[0].stats).toEqual({ total: 20, attention: 2 });
		expect(flat.every((n) => n.children === undefined)).toBe(true);
	});
});

describe("rankScopeNodes", () => {
	it("按 total 倒序取 Top N，剩余进入 overflow", () => {
		const { visible, overflow } = rankScopeNodes(flattenScopeNodes(PARENT_AND_CHILD), SCOPE_TOP_N);
		expect(visible.map((n) => n.id)).toEqual(["d2", "d3", "p1", "d4", "c1", "c2"]);
		expect(overflow.map((n) => n.id)).toEqual(["d5", "d6", "d7", "d8"]);
	});

	it("id === null 的节点恒排末尾且不计入 Top N", () => {
		const withUnidentified = [...PARENT_AND_CHILD, domain(null, "无标识域", 999)];
		const { visible, overflow } = rankScopeNodes(flattenScopeNodes(withUnidentified), SCOPE_TOP_N);
		expect(visible.every((n) => n.id !== null)).toBe(true);
		expect(overflow[overflow.length - 1].id).toBeNull();
		expect(overflow[overflow.length - 1].stats?.total).toBe(999);
	});
});

describe("DomainScopeNav", () => {
	it("渲染 Top N 域行 + 折叠行 + 未归域，不出现层级缩进与代号", () => {
		render(
			<DomainScopeNav
				nodes={PARENT_AND_CHILD}
				value={undefined}
				onChange={() => undefined}
				unassignedStats={{ total: 6, attention: 6 }}
			/>,
		);
		const rows = container.querySelectorAll('[data-testid^="domain-scope-row-"]');
		// 全部资产 + Top6 + 未归域 = 8 行（折叠行不算域行）
		expect(rows.length).toBe(8);
		expect(container.textContent).toContain("更多 4 个域");
		expect(container.textContent).not.toContain("DTMS");
		expect(container.textContent).toContain("项目域"); // c1(12) 在 Top6 内
	});

	it("点折叠行就地展开剩余域，再点收起", () => {
		render(<DomainScopeNav nodes={PARENT_AND_CHILD} value={undefined} onChange={() => undefined} />);
		click('[data-testid="domain-scope-more"]');
		expect(container.textContent).toContain("收起");
		expect(container.textContent).toContain("审计域");
		click('[data-testid="domain-scope-more"]');
		expect(container.textContent).not.toContain("审计域");
	});

	it("点击业务域回传域 id，点击全部资产回传 undefined", () => {
		const onChange = vi.fn();
		render(<DomainScopeNav nodes={PARENT_AND_CHILD} value={undefined} onChange={onChange} />);
		click('[data-testid="domain-scope-row-d2"]');
		expect(onChange).toHaveBeenCalledWith("d2");
		click('[data-testid="domain-scope-row-all"]');
		expect(onChange).toHaveBeenCalledWith(undefined);
	});

	it("未归域独立分区且回传 __UNASSIGNED__", () => {
		const onChange = vi.fn();
		render(
			<DomainScopeNav nodes={[]} value={undefined} onChange={onChange} unassignedStats={{ total: 6, attention: 6 }} />,
		);
		click('[data-testid="domain-scope-row-unassigned"]');
		expect(onChange).toHaveBeenCalledWith("__UNASSIGNED__");
	});

	it("待处置数量降级为琥珀圆点而非第二数字列", () => {
		render(<DomainScopeNav nodes={PARENT_AND_CHILD} value={undefined} onChange={() => undefined} />);
		const attentionDot = container.querySelector('[data-testid="domain-scope-attention-d2"]');
		expect(attentionDot).not.toBeNull();
		const textAfterDot = container.querySelector('[data-testid="domain-scope-attention-d2"]')?.textContent ?? "";
		expect(textAfterDot).toBe(""); // 圆点无文本
	});

	it("缺少标识的域在折叠区渲染为禁用且不触发 onChange", () => {
		const onChange = vi.fn();
		render(
			<DomainScopeNav
				nodes={[domain("d2", "财务域", 32), domain(null, "无标识域", 999)]}
				value={undefined}
				onChange={onChange}
			/>,
		);
		click('[data-testid="domain-scope-more"]');
		const row = container.querySelector('[data-testid="domain-scope-row-overflow-0"]') as HTMLButtonElement;
		expect(row).not.toBeNull();
		expect(row.disabled).toBe(true);
		act(() => row.click());
		expect(onChange).not.toHaveBeenCalled();
	});

	it(`域数不超过 ${SCOPE_SEARCH_THRESHOLD} 时不渲染搜索框`, () => {
		render(<DomainScopeNav nodes={PARENT_AND_CHILD} value={undefined} onChange={() => undefined} />);
		expect(container.querySelector('[data-testid="domain-scope-search"]')).toBeNull();
	});

	it(`域数超过 ${SCOPE_SEARCH_THRESHOLD} 时渲染搜索框且过滤态不做 Top N 截断`, () => {
		const many = Array.from({ length: 14 }, (_, i) => domain(`m${i}`, `域${i}`, i));
		render(<DomainScopeNav nodes={many} value={undefined} onChange={() => undefined} />);
		expect(container.querySelector('[data-testid="domain-scope-search"]')).not.toBeNull();
		const input = container.querySelector('[data-testid="domain-scope-search"]') as HTMLInputElement;
		const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
		act(() => {
			setter.call(input, "域13");
			input.dispatchEvent(new Event("input", { bubbles: true }));
		});
		expect(container.textContent).toContain("域13");
		expect(container.querySelector('[data-testid="domain-scope-more"]')).toBeNull(); // 过滤态不截断
	});

	it("统计被截断时数字加 ≥ 前缀", () => {
		render(
			<DomainScopeNav
				nodes={PARENT_AND_CHILD}
				value={undefined}
				onChange={() => undefined}
				allStats={{ total: 126, attention: 18 }}
				truncated
			/>,
		);
		expect(container.querySelector('[data-testid="domain-scope-total-all"]')?.textContent).toBe("≥126");
	});

	it("无业务归属数据域时给出创建引导，全部资产与未归域仍渲染", () => {
		render(<DomainScopeNav nodes={[]} value={undefined} onChange={() => undefined} />);
		expect(container.textContent).toContain("尚未创建业务归属数据域");
		expect(container.querySelector('[data-testid="domain-scope-row-all"]')).not.toBeNull();
		expect(container.querySelector('[data-testid="domain-scope-row-unassigned"]')).not.toBeNull();
	});

	it("选中行带 aria-current 标记", () => {
		render(<DomainScopeNav nodes={PARENT_AND_CHILD} value="d2" onChange={() => undefined} />);
		expect(container.querySelector('[data-testid="domain-scope-row-d2"]')?.getAttribute("aria-current")).toBe("true");
		expect(container.querySelector('[data-testid="domain-scope-row-d4"]')?.getAttribute("aria-current")).toBeNull();
	});
});
