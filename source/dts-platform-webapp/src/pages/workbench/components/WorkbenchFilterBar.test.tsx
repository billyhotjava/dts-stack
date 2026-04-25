// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import type { WorkbenchRoleInfo } from "../hooks/useWorkbenchRole";
import type { WorkbenchFilterState } from "./WorkbenchFilterBar";

type MockState = {
	role: WorkbenchRoleInfo;
	domainResponse: Promise<unknown> | null;
	orgResponse: Promise<unknown> | null;
};

const defaultEmp: WorkbenchRoleInfo = {
	role: "EMP",
	deptCode: null,
	isInstLeader: false,
	isDeptLeader: false,
	isEmp: true,
	roleLabel: "员工",
};

const mockState: MockState = {
	role: defaultEmp,
	domainResponse: null,
	orgResponse: null,
};

vi.mock("../hooks/useWorkbenchRole", () => ({
	useWorkbenchRole: () => mockState.role,
}));

vi.mock("@/api/services/catalogDomainService", () => ({
	default: {
		list: () => mockState.domainResponse ?? Promise.resolve([]),
	},
}));

vi.mock("@/api/apiClient", () => ({
	default: {
		get: () => mockState.orgResponse ?? Promise.resolve([]),
	},
}));

const auditLogMock = vi.fn();
vi.mock("@/utils/audit", () => ({
	auditLog: (...args: unknown[]) => auditLogMock(...args),
}));

async function renderAndFlush(element: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(element);
	});
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
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

describe("initialFilterState", () => {
	it("initialFilterState_for_EMP_returns_scope_MINE", async () => {
		const { initialFilterState } = await import("./WorkbenchFilterBar");
		const state = initialFilterState({ ...defaultEmp });
		expect(state.scope).toBe("MINE");
		expect(state.deptCode).toBeNull();
		expect(state.timeRange).toBe("MONTH");
		expect(state.bizDomain).toBeNull();
		expect(state.bizDomainAvailable).toBe(false);
	});

	it("initialFilterState_for_DEPT_LEADER_returns_scope_DEPT_with_self_dept", async () => {
		const { initialFilterState } = await import("./WorkbenchFilterBar");
		const state = initialFilterState({
			role: "DEPT_LEADER",
			deptCode: "FIN",
			isInstLeader: false,
			isDeptLeader: true,
			isEmp: false,
			roleLabel: "部门领导",
		});
		expect(state.scope).toBe("DEPT");
		expect(state.deptCode).toBe("FIN");
	});

	it("initialFilterState_for_INST_LEADER_returns_scope_ALL_null_dept", async () => {
		const { initialFilterState } = await import("./WorkbenchFilterBar");
		const state = initialFilterState({
			role: "INST_LEADER",
			deptCode: "HQ",
			isInstLeader: true,
			isDeptLeader: false,
			isEmp: false,
			roleLabel: "所领导",
		});
		expect(state.scope).toBe("ALL");
		expect(state.deptCode).toBeNull();
	});
});

describe("WorkbenchFilterBar", () => {
	beforeEach(() => {
		auditLogMock.mockReset();
		mockState.role = defaultEmp;
		mockState.domainResponse = Promise.resolve([{ code: "D1", name: "科研" }]);
		mockState.orgResponse = Promise.resolve([]);
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	async function mount(
		initial: WorkbenchFilterState,
	): Promise<{ getState: () => WorkbenchFilterState; unmount: () => void }> {
		const { WorkbenchFilterBar } = await import("./WorkbenchFilterBar");
		let state = initial;
		const onChange = (next: WorkbenchFilterState) => {
			state = next;
		};
		const harness = await renderAndFlush(<WorkbenchFilterBar value={state} onChange={onChange} />);
		return {
			getState: () => state,
			unmount: harness.unmount,
		};
	}

	// P1-6 — exercise the production helper directly (no shadow re-implementation).
	it("INST_LEADER_selecting_dept_sets_scope_ALL_with_deptCode", async () => {
		const role: WorkbenchRoleInfo = {
			role: "INST_LEADER",
			deptCode: "HQ",
			isInstLeader: true,
			isDeptLeader: false,
			isEmp: false,
			roleLabel: "所领导",
		};
		const { initialFilterState, deriveFilterAfterDeptChange } = await import("./WorkbenchFilterBar");
		const next = deriveFilterAfterDeptChange(initialFilterState(role), role, "FIN");
		expect(next.scope).toBe("ALL");
		expect(next.deptCode).toBe("FIN");
	});

	it("INST_LEADER_clearing_dept_to_ALL_resets_deptCode_to_null", async () => {
		const role: WorkbenchRoleInfo = {
			role: "INST_LEADER",
			deptCode: "HQ",
			isInstLeader: true,
			isDeptLeader: false,
			isEmp: false,
			roleLabel: "所领导",
		};
		const { initialFilterState, deriveFilterAfterDeptChange } = await import("./WorkbenchFilterBar");
		const prev: WorkbenchFilterState = {
			...initialFilterState(role),
			deptCode: "FIN",
		};
		const next = deriveFilterAfterDeptChange(prev, role, "ALL");
		expect(next.scope).toBe("ALL");
		expect(next.deptCode).toBeNull();
	});

	it("DEPT_LEADER_dept_change_ignored_self_dept_kept", async () => {
		const role: WorkbenchRoleInfo = {
			role: "DEPT_LEADER",
			deptCode: "FIN",
			isInstLeader: false,
			isDeptLeader: true,
			isEmp: false,
			roleLabel: "部门领导",
		};
		const { initialFilterState, deriveFilterAfterDeptChange } = await import("./WorkbenchFilterBar");
		// P1-6: even if a malformed input requests "D999", the derivation
		// must clamp to the user's own dept and force scope=DEPT.
		const next = deriveFilterAfterDeptChange(initialFilterState(role), role, "D999");
		expect(next.scope).toBe("DEPT");
		expect(next.deptCode).toBe("FIN");
	});

	it("EMP_dept_change_locks_scope_to_MINE_with_null_dept", async () => {
		const role: WorkbenchRoleInfo = {
			role: "EMP",
			deptCode: null,
			isInstLeader: false,
			isDeptLeader: false,
			isEmp: true,
			roleLabel: "员工",
		};
		const { initialFilterState, deriveFilterAfterDeptChange } = await import("./WorkbenchFilterBar");
		const next = deriveFilterAfterDeptChange(initialFilterState(role), role, "ANY");
		expect(next.scope).toBe("MINE");
		expect(next.deptCode).toBeNull();
	});

	it("bizDomain_availability_false_clears_selected_bizDomain", async () => {
		mockState.role = defaultEmp;
		mockState.domainResponse = Promise.resolve([]); // triggers availability=false
		const initial: WorkbenchFilterState = {
			scope: "MINE",
			deptCode: null,
			bizDomain: "D1",
			timeRange: "MONTH",
			bizDomainAvailable: true,
		};
		const { getState, unmount } = await mount(initial);
		const state = getState();
		expect(state.bizDomainAvailable).toBe(false);
		expect(state.bizDomain).toBeNull();
		unmount();
	});

	it("audit_event_emitted_when_bizDomain_becomes_unavailable", async () => {
		// Availability change path does not emit audit (by design), but domain API failure does.
		mockState.role = defaultEmp;
		mockState.domainResponse = Promise.reject(new Error("boom"));
		const initial: WorkbenchFilterState = {
			scope: "MINE",
			deptCode: null,
			bizDomain: null,
			timeRange: "MONTH",
			bizDomainAvailable: false,
		};
		const { unmount } = await mount(initial);
		expect(auditLogMock).toHaveBeenCalledWith("WORKBENCH_DOMAIN_API_FAIL", expect.any(Object));
		unmount();
	});
});
