// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createMemoryRouter, RouterProvider, useNavigate } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ModelWorkbenchNavigationGuard } from "./ModelWorkbenchNavigationGuard";

let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
});
async function setup(save: () => Promise<boolean>, discard = vi.fn()) {
	function Editor() {
		const navigate = useNavigate();
		return (
			<>
				<button type="button" onClick={() => navigate("/model?step=delivery")}>
					后续步骤
				</button>
				<ModelWorkbenchNavigationGuard dirty savingRef={{ current: false }} onSave={save} onDiscard={discard} />
			</>
		);
	}
	const router = createMemoryRouter([{ path: "/model", element: <Editor /> }], {
		initialEntries: ["/model?step=definition"],
	});
	await act(async () => root.render(<RouterProvider router={router} />));
	return { router, discard };
}
async function click(label: string) {
	const button = Array.from(document.body.querySelectorAll("button")).find(
		(item) => item.textContent?.replace(/\s/g, "") === label,
	)!;
	expect(button).toBeTruthy();
	await act(async () => button.click());
}
it("query-only navigation prompts and staying issues no save", async () => {
	const save = vi.fn();
	const { router } = await setup(save);
	await click("后续步骤");
	expect(router.state.location.search).toBe("?step=definition");
	await click("留在当前页");
	expect(save).not.toHaveBeenCalled();
	expect(router.state.location.search).toBe("?step=definition");
});
it("failed save keeps editor and input context", async () => {
	const save = vi.fn().mockResolvedValue(false);
	const { router, discard } = await setup(save);
	await click("后续步骤");
	await click("保存后离开");
	expect(router.state.location.search).toBe("?step=definition");
	expect(discard).not.toHaveBeenCalled();
	expect(document.body.textContent).toContain("有未保存的修改");
	expect(document.querySelector('[role="alert"]')?.textContent).toContain("保存未成功，修改仍保留在当前页");
});
it("rejected save shows feedback and allows a successful retry", async () => {
	const save = vi.fn().mockRejectedValueOnce(new Error("save failed")).mockResolvedValueOnce(true);
	const { router, discard } = await setup(save);
	await click("后续步骤");
	await click("保存后离开");
	expect(router.state.location.search).toBe("?step=definition");
	expect(document.querySelector('[role="alert"]')?.textContent).toContain("保存未成功");
	expect(discard).not.toHaveBeenCalled();
	await click("保存后离开");
	expect(router.state.location.search).toBe("?step=delivery");
	expect(save).toHaveBeenCalledTimes(2);
	expect(discard).not.toHaveBeenCalled();
});
it("successful save continues without discarding saved state", async () => {
	const { router, discard } = await setup(vi.fn().mockResolvedValue(true));
	await click("后续步骤");
	await click("保存后离开");
	expect(router.state.location.search).toBe("?step=delivery");
	expect(discard).not.toHaveBeenCalled();
});
it("explicit discard resets draft before continuing", async () => {
	const { router, discard } = await setup(vi.fn());
	await click("后续步骤");
	await click("放弃修改并离开");
	expect(discard).toHaveBeenCalledOnce();
	expect(router.state.location.search).toBe("?step=delivery");
});
