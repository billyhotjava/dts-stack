// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeAll, expect, it, vi } from "vitest";
import type { MenuTree } from "#/entity";
import { useMenuStore } from "@/store/menuStore";
import { AuthorizedWorkbenchRoute, permittedLandingPath } from "./AuthorizedWorkbenchRoute";

vi.mock("react-router", () => ({ Navigate: ({ to }: { to: string }) => <div data-target={to} /> }));
vi.mock("@/components/loading", () => ({ LineLoading: () => <div>loading</div> }));
vi.mock("@/global-config", () => ({ GLOBAL_CONFIG: { defaultRoute: "/workbench" } }));
beforeAll(() => {
	Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
});
afterEach(() => {
	useMenuStore.getState().clearMenus();
});
const menu = (path: string): MenuTree => ({ id: path, name: path, path, type: 1 }) as MenuTree;

it("selects the authorized menu instead of the ungranted default", () => {
	expect(permittedLandingPath([menu("/bi/screens")], "/workbench")).toBe("/bi/screens");
	expect(permittedLandingPath([menu("/bi/screens"), menu("/workbench")], "/workbench")).toBe("/workbench");
	expect(permittedLandingPath([], "/workbench")).toBeNull();
});

it("does not mount workbench before menu loading or without authorization", () => {
	const container = document.createElement("div");
	const root = createRoot(container);
	const mounted = vi.fn();
	function Workbench() {
		mounted();
		return <div>workbench content</div>;
	}
	act(() =>
		root.render(
			<AuthorizedWorkbenchRoute>
				<Workbench />
			</AuthorizedWorkbenchRoute>,
		),
	);
	expect(container.textContent).toBe("loading");
	expect(mounted).not.toHaveBeenCalled();
	act(() => useMenuStore.getState().setMenus([menu("/bi/screens")]));
	expect(container.querySelector("[data-target]")?.getAttribute("data-target")).toBe("/bi/screens");
	expect(mounted).not.toHaveBeenCalled();
	act(() => useMenuStore.getState().setMenus([]));
	expect(container.textContent).toContain("尚未分配");
	expect(mounted).not.toHaveBeenCalled();
	act(() => useMenuStore.getState().setMenus([menu("/workbench")]));
	expect(container.textContent).toBe("workbench content");
	act(() => root.unmount());
});

it("does not treat an ancestor retained for a child as a workbench grant", () => {
	const tree = [{ ...menu("/workbench"), type: 0, children: [menu("/workbench/todo")] }] as MenuTree[];
	expect(permittedLandingPath(tree, "/workbench")).toBe("/workbench/todo");
});
