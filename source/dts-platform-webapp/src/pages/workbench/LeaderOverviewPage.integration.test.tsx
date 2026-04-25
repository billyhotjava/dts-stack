// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import type {
	LeaderOverviewParams,
	LeaderOverviewResponse,
} from "@/api/services/workbenchService";
import type { WorkbenchRoleInfo } from "./hooks/useWorkbenchRole";
import { LeaderOverviewPage } from "./LeaderOverviewPage";

/**
 * Sprint-15 F6/T03 — LeaderOverviewPage integration test.
 *
 * Renders the page with the real KpiRow / TopReportsBlock / CoreAssetsBlock /
 * DomainMatrix / WorkbenchFilterBar children. Only the data-fetching service
 * layer and the role/user store are mocked; child components run their full
 * markup so role-driven branching surfaces in the DOM.
 */

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

// --- Role / user-store mock --------------------------------------------

const defaultInstLeader: WorkbenchRoleInfo = {
	role: "INST_LEADER",
	deptCode: null,
	isInstLeader: true,
	isDeptLeader: false,
	isEmp: false,
	roleLabel: "所领导",
};

const mockRole: { current: WorkbenchRoleInfo } = { current: defaultInstLeader };

vi.mock("./hooks/useWorkbenchRole", () => ({
	useWorkbenchRole: () => mockRole.current,
}));

vi.mock("@/store/userStore", () => ({
	useUserInfo: () => ({}),
	useUserRoles: () => ["ROLE_INST_LEADER"],
}));

// --- Service mocks ------------------------------------------------------

const leaderOverviewMock = vi.fn<(p: LeaderOverviewParams) => Promise<LeaderOverviewResponse>>();
vi.mock("@/api/services/workbenchService", () => ({
	default: {
		leaderOverview: (p: LeaderOverviewParams) => leaderOverviewMock(p),
	},
}));

const domainListMock = vi.fn<() => Promise<Array<{ code: string; name: string }>>>();
vi.mock("@/api/services/catalogDomainService", () => ({
	default: { list: () => domainListMock() },
}));

vi.mock("@/analytics/api/analyticsApi", () => ({
	analyticsApi: { listScreens: () => Promise.resolve([]) },
}));

vi.mock("@/api/apiClient", () => ({
	default: {
		get: () => Promise.resolve([]),
		post: () => Promise.resolve(undefined),
	},
}));

const recordAuditMock = vi.fn<(input: { event: string; payload?: unknown }) => Promise<void>>(
	() => Promise.resolve(),
);
vi.mock("@/api/services/workbenchAuditService", () => ({
	recordClientAudit: (input: { event: string; payload?: unknown }) => recordAuditMock(input),
	default: {
		recordClientAudit: (input: { event: string; payload?: unknown }) => recordAuditMock(input),
	},
}));

// --- Helpers ------------------------------------------------------------

function makeResponse(overrides: Partial<LeaderOverviewResponse> = {}): LeaderOverviewResponse {
	return {
		generatedAt: "2026-04-24T00:00:00.000Z",
		scope: "ALL",
		effectiveDeptCode: null,
		timeRange: "MONTH",
		kpis: {
			reportsTotal: 248,
			reportsNewInPeriod: 12,
			visitsInPeriod: 14321,
			visitsMoM: 0.08,
			assetsTotal: 1284,
			assetsNewInPeriod: 54,
			assetsS1: 84,
			assetsS1Ratio: 0.065,
			assetsS1S2: 180,
		},
		topReports: [
			{
				id: "r1",
				title: "月度财务月报",
				visits: 312,
				bizDomain: "FIN",
				classification: "S2",
				lastVisitedAt: "2026-04-23T09:00:00.000Z",
			},
		],
		topAssets: [
			{
				id: "a1",
				name: "核心客户表",
				classification: "S1",
				updatedAt: "2026-04-22T10:00:00.000Z",
				bizDomain: "FIN",
			},
		],
		domainMatrix: [
			{ domain: "FIN", domainName: "财务", visits: 5210 },
			{ domain: "HR", domainName: "人资", visits: 1820 },
		],
		...overrides,
	};
}

async function renderAndFlush(element: ReactElement): Promise<{
	container: HTMLElement;
	unmount: () => void;
}> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
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

async function flushMicrotasks(): Promise<void> {
	await act(async () => {
		for (let i = 0; i < 8; i += 1) await Promise.resolve();
	});
}

async function advanceAndFlush(ms: number): Promise<void> {
	await act(async () => {
		await vi.advanceTimersByTimeAsync(ms);
	});
	await flushMicrotasks();
}

// --- Tests --------------------------------------------------------------

describe("LeaderOverviewPage (integration, real children)", () => {
	beforeEach(() => {
		vi.useFakeTimers();
		mockRole.current = defaultInstLeader;
		leaderOverviewMock.mockReset();
		recordAuditMock.mockReset();
		recordAuditMock.mockImplementation(() => Promise.resolve());
		domainListMock.mockReset();
		domainListMock.mockResolvedValue([
			{ code: "FIN", name: "财务" },
			{ code: "HR", name: "人资" },
		]);
	});

	afterEach(() => {
		vi.useRealTimers();
		document.body.innerHTML = "";
	});

	async function mount(
		responses: Array<() => LeaderOverviewResponse | Promise<LeaderOverviewResponse>>,
	): Promise<{ container: HTMLElement; unmount: () => void }> {
		for (const r of responses) {
			leaderOverviewMock.mockImplementationOnce(async () => r());
		}
		const harness = await renderAndFlush(<LeaderOverviewPage />);
		await advanceAndFlush(200);
		return harness;
	}

	it("INST_LEADER full path renders 4 KPI cards, TOP report row and matrix cells", async () => {
		const { container, unmount } = await mount([() => makeResponse()]);
		const text = container.textContent ?? "";
		// 4 INST KPI titles all present
		expect(text).toContain("所内报表");
		expect(text).toContain("数据资产");
		expect(text).toContain("核心资产（S1）");
		expect(text).toContain("本月访问");
		// TOP report list row clickable
		expect(text).toContain("月度财务月报");
		// Core asset list row
		expect(text).toContain("核心客户表");
		// Domain matrix cells render — both real domains visible
		expect(text).toContain("财务");
		expect(text).toContain("人资");
		// Audit emitted on mount with INST_LEADER role
		expect(recordAuditMock).toHaveBeenCalledWith(
			expect.objectContaining({
				event: "WORKBENCH_OVERVIEW_VIEW",
				payload: expect.objectContaining({ role: "INST_LEADER" }),
			}),
		);
		unmount();
	});

	it("DEPT_LEADER scope locks dept selector and hides matrix", async () => {
		mockRole.current = {
			role: "DEPT_LEADER",
			deptCode: "FIN",
			isInstLeader: false,
			isDeptLeader: true,
			isEmp: false,
			roleLabel: "部门领导",
		};
		const { container, unmount } = await mount([
			() => makeResponse({ scope: "DEPT", effectiveDeptCode: "FIN" }),
		]);
		// Locked dept indicator (non-INST_LEADER branch in DeptSelect)
		const locked = container.querySelector('[data-testid="dept-select-locked"]');
		expect(locked).not.toBeNull();
		expect(locked?.textContent ?? "").toContain("FIN");
		// DomainMatrix is gated on isInstLeader, so it must not render
		expect(container.querySelectorAll('[role="button"][aria-pressed]')).toHaveLength(0);
		// Service called with DEPT scope
		const arg = leaderOverviewMock.mock.calls[0][0];
		expect(arg.scope).toBe("DEPT");
		expect(arg.deptCode).toBe("FIN");
		unmount();
	});

	it("hides bizDomain selector and matrix when catalogDomain API fails (soft dep)", async () => {
		domainListMock.mockReset();
		domainListMock.mockRejectedValue(new Error("boom"));
		const { container, unmount } = await mount([() => makeResponse()]);
		const text = container.textContent ?? "";
		// BizDomainSelect returns null when not available — no "全部业务域" option text
		expect(text).not.toContain("全部业务域");
		// Matrix is gated on bizDomainAvailable=true; with soft-dep failed it stays hidden
		expect(container.querySelectorAll('[role="button"][aria-pressed]')).toHaveLength(0);
		// KPI / TOP reports / core assets still render despite the soft-dep failure
		expect(text).toContain("所内报表");
		expect(text).toContain("月度财务月报");
		expect(text).toContain("核心客户表");
		unmount();
	});

	it("emits WORKBENCH_OVERVIEW_VIEW audit on mount", async () => {
		const { unmount } = await mount([() => makeResponse()]);
		expect(recordAuditMock).toHaveBeenCalled();
		const events = recordAuditMock.mock.calls.map((c) => c[0]?.event);
		expect(events).toContain("WORKBENCH_OVERVIEW_VIEW");
		unmount();
	});
});
