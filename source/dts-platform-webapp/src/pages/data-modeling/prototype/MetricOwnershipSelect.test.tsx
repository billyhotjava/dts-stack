// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ users: vi.fn(), departments: vi.fn(), select: vi.fn() }));
vi.mock("@/api/services/userDirectoryService", () => ({ searchUsers: mocks.users }));
vi.mock("@/api/services/deptService", () => ({ listDepartments: mocks.departments }));
vi.mock("antd", () => ({
	Select: (props: unknown) => {
		mocks.select(props);
		return null;
	},
}));

import { MetricOwnershipSelect } from "./MetricOwnershipSelect";

let root: Root;
let container: HTMLDivElement;
const changed = vi.fn();
const props = () => mocks.select.mock.lastCall?.[0];
beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	vi.resetAllMocks();
	vi.useFakeTimers();
	mocks.users.mockResolvedValue([{ id: "uuid", username: "xiezm", displayName: "谢志民" }]);
	mocks.departments.mockResolvedValue([{ code: "1152", nameZh: "项目部" }]);
	container = document.createElement("div");
	root = createRoot(container);
});
afterEach(async () => {
	await act(async () => root.unmount());
	vi.useRealTimers();
});
const render = async (kind: "user" | "department", value = "") => {
	await act(async () => root.render(<MetricOwnershipSelect kind={kind} value={value} onChange={changed} />));
	await act(async () => vi.advanceTimersByTimeAsync(250));
};

it("shows names but saves the username and department code", async () => {
	await render("user");
	expect(props().options).toEqual([{ value: "xiezm", label: "谢志民（xiezm）" }]);
	await act(async () => props().onChange("xiezm"));
	expect(changed).toHaveBeenLastCalledWith("xiezm");
	await render("department");
	expect(props().options).toEqual([{ value: "1152", label: "项目部（1152）" }]);
	await act(async () => props().onChange("1152"));
	expect(changed).toHaveBeenLastCalledWith("1152");
});

it("retains an existing value when the directory has no matching entry and allows clearing", async () => {
	mocks.users.mockResolvedValue([]);
	await render("user", "previous-owner");
	expect(props().value).toBe("previous-owner");
	expect(props().options).toContainEqual({ value: "previous-owner", label: "previous-owner" });
	expect(changed).not.toHaveBeenCalled();
	await act(async () => props().onChange(undefined));
	expect(changed).toHaveBeenLastCalledWith("");
});

it("debounces directory searches and ignores late responses", async () => {
	await render("user");
	let resolveOld!: (value: unknown[]) => void;
	mocks.users.mockReturnValueOnce(
		new Promise((resolve) => {
			resolveOld = resolve;
		}),
	);
	await act(async () => props().onSearch("old"));
	await act(async () => vi.advanceTimersByTimeAsync(250));
	await act(async () => props().onSearch("new"));
	await act(async () => vi.advanceTimersByTimeAsync(250));
	expect(mocks.users).toHaveBeenLastCalledWith("new");
	await act(async () => resolveOld([{ username: "old", displayName: "旧结果" }]));
	expect(props().options).toEqual([{ value: "xiezm", label: "谢志民（xiezm）" }]);
});
