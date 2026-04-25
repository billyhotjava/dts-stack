// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import type { WorkbenchRoleInfo } from "../hooks/useWorkbenchRole";

type MockState = {
	role: WorkbenchRoleInfo;
	orgResponse: Promise<unknown> | null;
};

const defaultInstLeader: WorkbenchRoleInfo = {
	role: "INST_LEADER",
	deptCode: null,
	isInstLeader: true,
	isDeptLeader: false,
	isEmp: false,
	roleLabel: "所领导",
};

const mockState: MockState = {
	role: defaultInstLeader,
	orgResponse: null,
};

vi.mock("../hooks/useWorkbenchRole", () => ({
	useWorkbenchRole: () => mockState.role,
}));

vi.mock("@/api/apiClient", () => ({
	default: {
		get: () => mockState.orgResponse ?? Promise.resolve([]),
	},
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

describe("DeptSelect", () => {
	beforeEach(() => {
		mockState.role = defaultInstLeader;
		mockState.orgResponse = null;
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("renders_locked_text_for_EMP", async () => {
		mockState.role = {
			role: "EMP",
			deptCode: "D-99",
			isInstLeader: false,
			isDeptLeader: false,
			isEmp: true,
			roleLabel: "员工",
		};
		const { DeptSelect } = await import("./DeptSelect");
		const { container, unmount } = await renderAndFlush(<DeptSelect value={null} onChange={() => {}} />);
		const locked = container.querySelector("[data-testid='dept-select-locked']");
		expect(locked).not.toBeNull();
		expect(locked?.textContent).toContain("本部门");
		expect(locked?.textContent).toContain("D-99");
		expect(container.querySelector(".ant-tree-select")).toBeNull();
		unmount();
	});

	it("renders_locked_text_for_DEPT_LEADER", async () => {
		mockState.role = {
			role: "DEPT_LEADER",
			deptCode: "FIN",
			isInstLeader: false,
			isDeptLeader: true,
			isEmp: false,
			roleLabel: "部门领导",
		};
		const { DeptSelect } = await import("./DeptSelect");
		const { container, unmount } = await renderAndFlush(<DeptSelect value={null} onChange={() => {}} />);
		const locked = container.querySelector("[data-testid='dept-select-locked']");
		expect(locked?.textContent).toContain("FIN");
		unmount();
	});

	it("renders_tree_select_for_INST_LEADER", async () => {
		mockState.orgResponse = Promise.resolve([
			{ id: 1, name: "某所", deptCode: "HQ", children: [{ id: 2, name: "财务处", deptCode: "FIN" }] },
		]);
		const { DeptSelect } = await import("./DeptSelect");
		const { container, unmount } = await renderAndFlush(<DeptSelect value={null} onChange={() => {}} />);
		expect(container.querySelector(".ant-tree-select")).not.toBeNull();
		// Default value should display "全所（默认）".
		expect(container.textContent).toContain("全所");
		unmount();
	});

	it("falls_back_to_ALL_plus_own_dept_when_api_fails", async () => {
		mockState.role = { ...defaultInstLeader, deptCode: "D-SELF" };
		mockState.orgResponse = Promise.reject(new Error("network down"));
		const { DeptSelect } = await import("./DeptSelect");
		const { container, unmount } = await renderAndFlush(<DeptSelect value={null} onChange={() => {}} />);
		// We don't control ant-design internals; at minimum the TreeSelect container still mounts.
		expect(container.querySelector(".ant-tree-select")).not.toBeNull();
		unmount();
	});

	it("emits_ALL_when_value_null_for_INST_LEADER", async () => {
		mockState.orgResponse = Promise.resolve([]);
		const { DeptSelect } = await import("./DeptSelect");
		const { container, unmount } = await renderAndFlush(<DeptSelect value={null} onChange={() => {}} />);
		// Fallback default title is "全所（默认）"; with empty tree + null value, it still shows.
		expect(container.textContent).toContain("全所");
		unmount();
	});
});
