// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import type { WorkbenchRoleInfo } from "./useWorkbenchRole";

type MockState = {
	userInfo: unknown;
	roles: unknown;
};

const mockState: MockState = { userInfo: {}, roles: [] };

vi.mock("@/store/userStore", () => ({
	useUserInfo: () => mockState.userInfo,
	useUserRoles: () => mockState.roles,
}));

async function invokeHook(): Promise<WorkbenchRoleInfo> {
	// Re-import the hook after the mock is registered.
	const mod = await import("./useWorkbenchRole");
	let captured: WorkbenchRoleInfo | null = null;
	function Probe() {
		captured = mod.useWorkbenchRole();
		return null;
	}
	renderToStaticMarkup(<Probe />);
	if (captured === null) {
		throw new Error("hook did not run");
	}
	return captured;
}

describe("useWorkbenchRole", () => {
	afterEach(() => {
		mockState.userInfo = {};
		mockState.roles = [];
	});

	it("identifies_INST_LEADER_from_roles", async () => {
		mockState.roles = ["ROLE_INST_LEADER"];
		const info = await invokeHook();
		expect(info.role).toBe("INST_LEADER");
		expect(info.isInstLeader).toBe(true);
		expect(info.roleLabel).toBe("所领导");
	});

	it("identifies_DEPT_LEADER_from_roles", async () => {
		mockState.roles = ["ROLE_DEPT_LEADER"];
		const info = await invokeHook();
		expect(info.role).toBe("DEPT_LEADER");
		expect(info.isDeptLeader).toBe(true);
		expect(info.roleLabel).toBe("部门领导");
	});

	it("falls_back_to_EMP_when_no_matching_role", async () => {
		mockState.roles = ["ROLE_USER", "ROLE_DATA_ANALYST"];
		const info = await invokeHook();
		expect(info.role).toBe("EMP");
		expect(info.isEmp).toBe(true);
		expect(info.roleLabel).toBe("员工");
	});

	it("empty_roles_array_returns_EMP", async () => {
		mockState.roles = [];
		const info = await invokeHook();
		expect(info.role).toBe("EMP");
	});

	it("prefers_INST_LEADER_over_DEPT_LEADER_when_both_present", async () => {
		mockState.roles = ["ROLE_DEPT_LEADER", "ROLE_INST_LEADER"];
		const info = await invokeHook();
		expect(info.role).toBe("INST_LEADER");
	});

	it("extracts_deptCode_from_attributes_department", async () => {
		mockState.userInfo = { attributes: { department: ["D-042"] } };
		const info = await invokeHook();
		expect(info.deptCode).toBe("D-042");
	});

	it("prefers_top_level_deptCode_over_attributes", async () => {
		mockState.userInfo = {
			deptCode: "D-TOP",
			attributes: { department: ["D-042"] },
		};
		const info = await invokeHook();
		expect(info.deptCode).toBe("D-TOP");
	});

	it("returns_null_deptCode_when_missing", async () => {
		mockState.userInfo = { attributes: {} };
		const info = await invokeHook();
		expect(info.deptCode).toBeNull();
	});

	// P1-6 — `attributes.dept_code` is a legacy alias still emitted by some
	// realms; resolveDeptCode supports it but had no test coverage, masking
	// regressions if the alias branch is ever removed.
	it("extracts_deptCode_from_attributes_dept_code_alias", async () => {
		mockState.userInfo = { attributes: { dept_code: "D-ALIAS" } };
		const info = await invokeHook();
		expect(info.deptCode).toBe("D-ALIAS");
	});

	it("extracts_deptCode_from_attributes_dept_code_array_alias", async () => {
		mockState.userInfo = { attributes: { dept_code: ["D-ALIAS-2"] } };
		const info = await invokeHook();
		expect(info.deptCode).toBe("D-ALIAS-2");
	});

	// P1-6 — userInfo from the store may legitimately be undefined when
	// a request fires before login state hydrates. The resolver must not
	// crash and must report deptCode=null.
	it("handles_undefined_userInfo_gracefully", async () => {
		mockState.userInfo = undefined;
		const info = await invokeHook();
		expect(info.deptCode).toBeNull();
		expect(info.role).toBe("EMP");
	});
});
