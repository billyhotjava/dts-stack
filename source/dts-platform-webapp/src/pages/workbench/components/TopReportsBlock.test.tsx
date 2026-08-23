// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

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

const visitMock = vi.fn();
vi.mock("@/api/services/reportsService", () => ({
	default: {
		visit: (...args: unknown[]) => visitMock(...args),
	},
}));

const windowOpenMock = vi.fn();

import { type TopReportItem, TopReportsBlock } from "./TopReportsBlock";

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

async function flush() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

const sample: TopReportItem[] = [
	{
		id: "r1",
		title: "销售月报",
		visits: 120,
		bizDomain: "SALES",
		classification: "S1",
		lastVisitedAt: new Date().toISOString(),
		url: "/bi/screens/1/preview",
		engine: "DTS_BI",
	},
	{
		id: "r2",
		title: "财务季报",
		visits: 80,
		bizDomain: null,
		classification: "S2",
		lastVisitedAt: null,
		url: "/bi/screens/2/preview",
		engine: "DTS_BI",
	},
	{
		id: "r3",
		title: "HR 分析",
		visits: 60,
		bizDomain: "HR",
		classification: "S3",
		lastVisitedAt: null,
		url: "/bi/screens/3/preview",
		engine: "DTS_BI",
	},
];

describe("TopReportsBlock", () => {
	beforeEach(() => {
		visitMock.mockReset();
		visitMock.mockResolvedValue({ ok: true });
		windowOpenMock.mockReset();
		Object.defineProperty(window, "open", {
			configurable: true,
			writable: true,
			value: windowOpenMock,
		});
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("shows_skeleton_when_loading", () => {
		const { container, unmount } = render(<TopReportsBlock role="EMP" items={[]} loading={true} />);
		expect(container.querySelector(".ant-skeleton")).not.toBeNull();
		unmount();
	});

	it("shows_empty_for_EMP_with_link", () => {
		const { container, unmount } = render(<TopReportsBlock role="EMP" items={[]} loading={false} />);
		const anchors = container.querySelectorAll("a");
		// One is the "查看全部" extra, another is inside the empty-state description.
		const anyLinkTo = Array.from(anchors).some((a) => a.textContent?.includes("大屏"));
		expect(anyLinkTo).toBe(true);
		unmount();
	});

	it("shows_empty_for_DEPT_LEADER", () => {
		const { container, unmount } = render(<TopReportsBlock role="DEPT_LEADER" items={[]} loading={false} />);
		expect(container.textContent).toContain("暂无已发布大屏");
		const hasReportCenterLink = Array.from(container.querySelectorAll("a")).some((a) =>
			a.textContent?.includes("去大屏"),
		);
		expect(hasReportCenterLink).toBe(false);
		unmount();
	});

	it("renders_items_when_non_empty", () => {
		const { container, unmount } = render(
			<TopReportsBlock
				role="INST_LEADER"
				items={sample}
				loading={false}
				domainLabels={{ SALES: "销售域", HR: "人力资源域" }}
			/>,
		);
		expect(container.textContent).toContain("销售月报");
		expect(container.textContent).toContain("财务季报");
		expect(container.textContent).toContain("HR 分析");
		expect(container.textContent).toContain("销售域");
		expect(container.textContent).toContain("人力资源域");
		expect(container.textContent).not.toContain("SALES");
		// Three list items
		expect(container.querySelectorAll(".ant-list-item").length).toBe(3);
		unmount();
	});

	it("calls_reportsService_visit_on_row_click", async () => {
		const { container, unmount } = render(<TopReportsBlock role="INST_LEADER" items={sample} loading={false} />);
		const firstItem = container.querySelector(".ant-list-item") as HTMLElement;
		act(() => {
			firstItem.click();
		});
		await flush();
		// Sprint-17 hotfix: now uses resolveBiLinkForOpen(item.url, item.engine).
		// engine !== "HETU", so the app-internal URL follows the current router mode.
		expect(windowOpenMock).toHaveBeenCalledWith("/bi/screens/1/preview", "_blank", "noopener,noreferrer");
		expect(visitMock).toHaveBeenCalledWith({
			id: "r1",
			title: "销售月报",
			url: "/bi/screens/1/preview",
			engine: "DTS_BI",
			classification: "S1",
		});
		unmount();
	});

	it("still_opens_when_visit_fails", async () => {
		visitMock.mockRejectedValueOnce(new Error("boom"));
		const { container, unmount } = render(<TopReportsBlock role="INST_LEADER" items={sample} loading={false} />);
		const firstItem = container.querySelector(".ant-list-item") as HTMLElement;
		act(() => {
			firstItem.click();
		});
		await flush();
		expect(windowOpenMock).toHaveBeenCalled();
		unmount();
	});

	it("renders_bizDomain_tag_only_when_not_null", () => {
		const { container, unmount } = render(<TopReportsBlock role="INST_LEADER" items={sample} loading={false} />);
		const geekblueTags = container.querySelectorAll(".ant-tag-geekblue");
		// Only two items have bizDomain (SALES, HR)
		expect(geekblueTags.length).toBe(2);
		unmount();
	});

	it("renders_classification_tag_with_correct_color_for_S1", () => {
		const { container, unmount } = render(<TopReportsBlock role="INST_LEADER" items={[sample[0]!]} loading={false} />);
		// ant-tag with "red" class for S1
		expect(container.querySelector(".ant-tag-red")).not.toBeNull();
		unmount();
	});
});
