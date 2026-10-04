// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
const editor = vi.fn((_props: unknown) => <div data-testid="editor" />);
vi.mock("@/pages/catalog/CatalogDatasetGovernanceSummaryEditor", () => ({
	CatalogDatasetGovernanceSummaryEditor: (p: unknown) => editor(p),
}));
import { ModelCatalogDeliveryPanel } from "./ModelCatalogDeliveryPanel";
let node: HTMLDivElement;
let root: ReturnType<typeof createRoot>;
beforeEach(() => {
	node = document.createElement("div");
	document.body.append(node);
	root = createRoot(node);
	editor.mockClear();
});
afterEach(() => {
	act(() => root.unmount());
	node.remove();
});
const out = (id: string | null, state: "SUCCEEDED" | "FAILED" = "SUCCEEDED", current = true) => ({
	resourceId: id,
	state,
	reasonCode: null,
	message: "",
	matchesCurrentTarget: current,
	updatedAt: null,
});
const render = (outputs: ReturnType<typeof out>[], canMaintain = true) =>
	act(() =>
		root.render(
			<MemoryRouter><ModelCatalogDeliveryPanel
				modelName="销售明细"
				canMaintain={canMaintain}
				onSaved={vi.fn()}
				onNavigationGuardChange={vi.fn()}
				delivery={{
					steps: [
						{
							key: "catalog",
							state: "WAITING_INPUT",
							reasonCode: null,
							message: "",
							evidenceRevision: null,
							matchesCurrentTarget: true,
							resourceId: "legacy",
							updatedAt: null,
							outputs,
						},
					],
				}}
			/></MemoryRouter>,
		),
	);
it("renders one editor for two registered outputs", () => {
	render([out("a"), out("b")]);
	expect(editor).toHaveBeenCalledTimes(1);
});
it("only maintains succeeded output", () => {
	render([out("a"), out("b", "FAILED")]);
	expect(editor.mock.calls[0][0]).toMatchObject({ datasetId: "a" });
	expect(node.querySelectorAll("button")).toHaveLength(1);
});
it("passes read only maintenance", () => {
	render([out("a")], false);
	expect(editor.mock.calls[0][0]).toMatchObject({ canMaintain: false });
});
it("does not fallback when outputs are stale", () => {
	render([out("a", "SUCCEEDED", false)]);
	expect(editor).not.toHaveBeenCalled();
});
it("shows unregistered failed output", () => {
	render([out(null, "FAILED")]);
	expect(node.textContent).toContain("失败");
	expect(editor).not.toHaveBeenCalled();
});
