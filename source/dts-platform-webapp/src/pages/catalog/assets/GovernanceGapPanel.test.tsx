// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GovernanceGapPanel } from "./GovernanceGapPanel";

let container: HTMLDivElement;
let root: Root;

const render = (element: ReactElement) => {
	act(() => {
		root.render(element);
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

const REASONS = [
	{ key: "PENDING_DOMAIN", label: "待归域", count: 358 },
	{ key: "PENDING_CLAIM", label: "待认领", count: 2 },
	{ key: "PENDING_CLASSIFICATION", label: "未定密", count: 0 },
];

describe("GovernanceGapPanel", () => {
	it("零值原因用中性色，不用成功色", () => {
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={vi.fn()} />);

		const zero = container.querySelector('[data-testid="gap-reason-PENDING_CLASSIFICATION"]');
		expect(zero?.getAttribute("data-tone")).toBe("neutral");
		expect(zero?.getAttribute("data-tone")).not.toBe("success");
	});

	it("非零原因用警示色", () => {
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-reason-PENDING_DOMAIN"]')?.getAttribute("data-tone")).toBe(
			"warning",
		);
	});

	it("点击原因回传原因 key", () => {
		const onReasonClick = vi.fn();
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={onReasonClick} />);

		act(() => (container.querySelector('[data-testid="gap-reason-PENDING_DOMAIN"]') as HTMLElement).click());
		expect(onReasonClick).toHaveBeenCalledWith("PENDING_DOMAIN");
	});

	it("零值原因不可点击", () => {
		const onReasonClick = vi.fn();
		render(<GovernanceGapPanel total={360} attention={360} reasons={REASONS} onReasonClick={onReasonClick} />);

		const zero = container.querySelector('[data-testid="gap-reason-PENDING_CLASSIFICATION"]') as HTMLButtonElement;
		expect(zero.disabled).toBe(true);
		act(() => zero.click());
		expect(onReasonClick).not.toHaveBeenCalled();
	});

	it("待处置为 0 时整条转为健康态", () => {
		render(<GovernanceGapPanel total={360} attention={0} reasons={[]} onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-bar"]')?.getAttribute("data-state")).toBe("healthy");
		expect(container.textContent).toContain("当前范围无待处置资产");
	});

	it("总量为 0 时不出现除零产生的 NaN", () => {
		render(<GovernanceGapPanel total={0} attention={0} reasons={[]} onReasonClick={vi.fn()} />);

		expect(container.textContent).not.toContain("NaN");
	});

	it("百分比按待处置占比计算", () => {
		render(<GovernanceGapPanel total={200} attention={50} reasons={REASONS} onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-percent"]')?.textContent).toContain("25%");
	});

	it("统计被截断时数字加 ≥ 前缀", () => {
		render(<GovernanceGapPanel total={5000} attention={100} reasons={REASONS} truncated onReasonClick={vi.fn()} />);

		expect(container.querySelector('[data-testid="gap-summary"]')?.textContent).toContain("≥");
	});
});
