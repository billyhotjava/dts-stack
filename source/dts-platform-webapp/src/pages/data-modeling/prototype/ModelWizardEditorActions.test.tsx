// @vitest-environment jsdom
import { act, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ModelWizardEditorActions } from "./ModelWizardEditorActions";

type Props = ComponentProps<typeof ModelWizardEditorActions>;
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
const props = (patch: Partial<Props> = {}): Props => ({
	definition: true,
	persisted: false,
	published: false,
	dirty: true,
	busy: false,
	readOnly: false,
	canMaintain: true,
	context: null,
	onSave: vi.fn(),
	onStash: vi.fn(),
	onSubmit: vi.fn(),
	onNext: vi.fn(),
	onPrevious: vi.fn(),
	onFork: vi.fn(),
	...patch,
});
const render = async (p: Props) => {
	await act(async () => root.render(<ModelWizardEditorActions {...p} />));
};
const main = () => Array.from(container.querySelectorAll("button")).at(-1)!;
it("first step saves only", async () => {
	const p = props();
	await render(p);
	expect(main().textContent).toBe("保存并继续");
	act(() => main().click());
	expect(p.onSave).toHaveBeenCalledOnce();
	expect(p.onSubmit).not.toHaveBeenCalled();
	expect(p.onNext).not.toHaveBeenCalled();
});
it("implementation submits once through the pipeline", async () => {
	const p = props({
		definition: false,
		persisted: true,
		context: { allowedActions: ["SAVE", "VALIDATE"], openDraft: { state: "EDITING" } } as any,
	});
	await render(p);
	expect(main().textContent).toBe("提交实现并继续");
	act(() => main().click());
	expect(p.onSubmit).toHaveBeenCalledOnce();
	expect(p.onNext).not.toHaveBeenCalled();
	expect(container.textContent).toContain("暂存草稿");
});
it("committed implementation advances without a write", async () => {
	const p = props({
		definition: false,
		persisted: true,
		dirty: false,
		context: { implementation: {}, openDraft: null, allowedActions: [] } as any,
	});
	await render(p);
	expect(main().textContent).toBe("下一步");
	act(() => main().click());
	expect(p.onNext).toHaveBeenCalledOnce();
	expect(p.onSubmit).not.toHaveBeenCalled();
});
it("latest permissions and busy state stop commands", async () => {
	const p = props();
	await render(p);
	await render({ ...p, busy: true });
	act(() => main().click());
	await render({ ...p, canMaintain: false });
	act(() => main().click());
	expect(p.onSave).not.toHaveBeenCalled();
});
it("published content requires an authorized fork", async () => {
	const p = props({ published: true, context: { allowedActions: [] } as any });
	await render(p);
	expect(main().disabled).toBe(true);
	await render({ ...p, context: { allowedActions: ["FORK_DRAFT"] } as any });
	act(() => main().click());
	expect(p.onFork).toHaveBeenCalledOnce();
	expect(p.onSave).not.toHaveBeenCalled();
});
