// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

const auditLogMock = vi.fn();
vi.mock("@/utils/audit", () => ({
	auditLog: (...args: unknown[]) => auditLogMock(...args),
}));

import { DomainMatrix, type DomainCell } from "./DomainMatrix";

function render(element: ReactElement): { container: HTMLElement; unmount: () => void } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(element);
	});
	return {
		container,
		unmount: () => {
			act(() => {
				root.unmount();
			});
			container.remove();
		},
	};
}

const sampleCells: DomainCell[] = [
	{ domain: "SCI", domainName: "科研", visits: 120 },
	{ domain: "FIN", domainName: "财务", visits: 80 },
	{ domain: "HR", domainName: "人事", visits: 40 },
	{ domain: "__OTHER__", domainName: "其他", visits: 20 },
];

describe("DomainMatrix", () => {
	beforeEach(() => {
		auditLogMock.mockReset();
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("not_rendered_when_visible_false", () => {
		const { container, unmount } = render(
			<DomainMatrix visible={false} cells={sampleCells} activeDomain={null} onSelect={() => {}} />,
		);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("not_rendered_when_cells_empty", () => {
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={[]} activeDomain={null} onSelect={() => {}} />,
		);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("renders_all_cells_in_grid", () => {
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain={null} onSelect={() => {}} />,
		);
		// Each cell has a textual domainName; four cells should render.
		expect(container.textContent).toContain("科研");
		expect(container.textContent).toContain("财务");
		expect(container.textContent).toContain("人事");
		expect(container.textContent).toContain("其他");
		// Real (non-synthetic) cells get role="button"; three buttons expected.
		expect(container.querySelectorAll('[role="button"]').length).toBe(3);
		unmount();
	});

	it("highlights_active_domain", () => {
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain="FIN" onSelect={() => {}} />,
		);
		const active = container.querySelector('[aria-pressed="true"]');
		expect(active).not.toBeNull();
		expect(active?.textContent).toContain("财务");
		unmount();
	});

	it("emits_onSelect_with_domain_when_cell_clicked", () => {
		const onSelect = vi.fn();
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain={null} onSelect={onSelect} />,
		);
		const buttons = container.querySelectorAll('[role="button"]');
		act(() => {
			(buttons[0] as HTMLElement).click();
		});
		expect(onSelect).toHaveBeenCalledWith("SCI");
		unmount();
	});

	it("emits_onSelect_null_when_active_cell_clicked_again", () => {
		const onSelect = vi.fn();
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain="SCI" onSelect={onSelect} />,
		);
		const active = container.querySelector('[aria-pressed="true"]');
		expect(active).not.toBeNull();
		act(() => {
			(active as HTMLElement).click();
		});
		expect(onSelect).toHaveBeenCalledWith(null);
		unmount();
	});

	it("other_bucket_click_is_noop", () => {
		const onSelect = vi.fn();
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain={null} onSelect={onSelect} />,
		);
		// Find the synthetic "其他" cell (no role attribute).
		const allCells = container.querySelectorAll("div[style*='border-radius: 8px']");
		// The fourth real cell is __OTHER__; by iteration find it by text.
		let otherCell: HTMLElement | null = null;
		allCells.forEach((el) => {
			if ((el as HTMLElement).textContent?.includes("其他")) {
				otherCell = el as HTMLElement;
			}
		});
		expect(otherCell).not.toBeNull();
		act(() => {
			otherCell?.click();
		});
		expect(onSelect).not.toHaveBeenCalled();
		expect(auditLogMock).not.toHaveBeenCalled();
		unmount();
	});

	it("uncategorized_bucket_click_is_noop", () => {
		const onSelect = vi.fn();
		const cells: DomainCell[] = [
			{ domain: "SCI", domainName: "科研", visits: 100 },
			{ domain: "__UNCATEGORIZED__", domainName: "未分类", visits: 15 },
		];
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={cells} activeDomain={null} onSelect={onSelect} />,
		);
		const allCells = container.querySelectorAll("div[style*='border-radius: 8px']");
		let uncatCell: HTMLElement | null = null;
		allCells.forEach((el) => {
			if ((el as HTMLElement).textContent?.includes("未分类")) {
				uncatCell = el as HTMLElement;
			}
		});
		expect(uncatCell).not.toBeNull();
		act(() => {
			uncatCell?.click();
		});
		expect(onSelect).not.toHaveBeenCalled();
		expect(auditLogMock).not.toHaveBeenCalled();
		unmount();
	});

	it("audit_event_emitted_on_valid_click", () => {
		const onSelect = vi.fn();
		const { container, unmount } = render(
			<DomainMatrix visible={true} cells={sampleCells} activeDomain={null} onSelect={onSelect} />,
		);
		const buttons = container.querySelectorAll('[role="button"]');
		act(() => {
			(buttons[1] as HTMLElement).click();
		});
		expect(auditLogMock).toHaveBeenCalledWith("WORKBENCH_DOMAIN_DRILL", { domain: "FIN" });
		unmount();
	});
});
