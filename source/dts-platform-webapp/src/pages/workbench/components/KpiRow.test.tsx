// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import type { LeaderOverviewKpis } from "@/api/services/workbenchService";
import { KpiRow } from "./KpiRow";
import type { WorkbenchFilterState } from "./WorkbenchFilterBar";

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

const baseFilter: WorkbenchFilterState = {
	scope: "MINE",
	deptCode: null,
	bizDomain: null,
	timeRange: "MONTH",
	bizDomainAvailable: false,
};

const baseKpis: LeaderOverviewKpis = {
	reportsTotal: 12,
	reportsNewInPeriod: 3,
	visitsInPeriod: 120,
	visitsMoM: 0.08,
	assetsTotal: 45,
	assetsNewInPeriod: 5,
	assetsS1: 7,
	assetsS1S2: 18,
	assetsS1Ratio: 0.155,
};

describe("KpiRow", () => {
	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("renders_3_cards_for_EMP", () => {
		const { container, unmount } = render(<KpiRow role="EMP" filter={baseFilter} kpis={baseKpis} loading={false} />);
		const cards = container.querySelectorAll(".ant-card");
		expect(cards.length).toBe(3);
		expect(container.textContent).toContain("我常用的大屏");
		expect(container.textContent).toContain("我常用的资产");
		unmount();
	});

	it("renders_3_cards_for_DEPT_LEADER_with_S1S2", () => {
		const { container, unmount } = render(
			<KpiRow role="DEPT_LEADER" filter={baseFilter} kpis={baseKpis} loading={false} />,
		);
		const cards = container.querySelectorAll(".ant-card");
		expect(cards.length).toBe(3);
		expect(container.textContent).toContain("本部门大屏");
		expect(container.textContent).toContain("部门核心资产");
		expect(container.textContent).toContain("S1 + S2 总数");
		// assetsS1S2 = 18 should show up
		expect(container.textContent).toContain("18");
		unmount();
	});

	it("renders_4_cards_for_INST_LEADER", () => {
		const { container, unmount } = render(
			<KpiRow role="INST_LEADER" filter={baseFilter} kpis={baseKpis} loading={false} />,
		);
		const cards = container.querySelectorAll(".ant-card");
		expect(cards.length).toBe(4);
		expect(container.textContent).toContain("所内大屏");
		expect(container.textContent).toContain("核心资产（S1）");
		unmount();
	});

	it("period_label_follows_timeRange_MONTH", () => {
		const { container, unmount } = render(
			<KpiRow role="INST_LEADER" filter={{ ...baseFilter, timeRange: "MONTH" }} kpis={baseKpis} loading={false} />,
		);
		expect(container.textContent).toContain("本月访问");
		expect(container.textContent).toContain("本月新发布");
		unmount();
	});

	it("period_label_follows_timeRange_QUARTER", () => {
		const { container, unmount } = render(
			<KpiRow role="INST_LEADER" filter={{ ...baseFilter, timeRange: "QUARTER" }} kpis={baseKpis} loading={false} />,
		);
		expect(container.textContent).toContain("本季访问");
		unmount();
	});

	it("period_label_follows_timeRange_YEAR", () => {
		const { container, unmount } = render(
			<KpiRow role="INST_LEADER" filter={{ ...baseFilter, timeRange: "YEAR" }} kpis={baseKpis} loading={false} />,
		);
		expect(container.textContent).toContain("本年访问");
		unmount();
	});

	it("shows_skeleton_when_loading", () => {
		const { container, unmount } = render(<KpiRow role="EMP" filter={baseFilter} kpis={baseKpis} loading={true} />);
		expect(container.querySelectorAll(".ant-skeleton").length).toBe(3);
		unmount();
	});

	it("shows_skeleton_when_kpis_null", () => {
		const { container, unmount } = render(
			<KpiRow role="DEPT_LEADER" filter={baseFilter} kpis={null} loading={false} />,
		);
		expect(container.querySelectorAll(".ant-skeleton").length).toBe(3);
		unmount();
	});

	it("renders_mom_secondary_with_positive_arrow_for_leader", () => {
		const { container, unmount } = render(
			<KpiRow role="DEPT_LEADER" filter={baseFilter} kpis={baseKpis} loading={false} />,
		);
		// MoMSecondary renders ArrowUpOutlined for positive values
		expect(container.querySelector(".anticon-arrow-up")).not.toBeNull();
		unmount();
	});

	it("renders_ratio_secondary_for_INST_LEADER_S1_card", () => {
		const { container, unmount } = render(
			<KpiRow role="INST_LEADER" filter={baseFilter} kpis={baseKpis} loading={false} />,
		);
		expect(container.textContent).toContain("占比");
		expect(container.textContent).toContain("15.5%");
		unmount();
	});
});
