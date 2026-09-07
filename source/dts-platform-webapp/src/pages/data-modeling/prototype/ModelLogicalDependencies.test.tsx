// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelLogicalDependencies } from "./ModelLogicalDependencies";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
});
afterEach(() => { act(() => root.unmount()); container.remove(); });
const source = (patch: Partial<ModelSpecView> = {}): ModelSpecView => ({
	id: "ods-1", planId: "plan-1", modelType: "SOURCE", layer: "ODS", name: "订单贴源",
	contractVersion: 2, compatibilityMode: "CANONICAL", status: "DRAFT", revision: 2,
	sourceRefs: [], dependsOn: [], dimensionRefs: [], ...patch,
} as ModelSpecView);
const draft = (patch: Partial<ModelSpecDraft> = {}): ModelSpecDraft => ({
	createKind: "fact", planId: "plan-1", base: null, dependsOn: [], sourceRefs: [],
	implementationInputMode: "PHYSICAL_ASSET", ...patch,
} as ModelSpecDraft);

it("selects a not-yet-materialized ODS design without changing the physical input", () => {
	const value = draft();
	const onChange = vi.fn();
	act(() => root.render(<ModelLogicalDependencies draft={value} modelType="FACT" models={[source()]} onChange={onChange} />));
	act(() => container.querySelector<HTMLInputElement>("input")!.click());
	expect(onChange).toHaveBeenCalledWith({ ...value, dependsOn: [{ modelSpecId: "ods-1", revision: 2 }] });
});
it("retains the pinned revision until the user explicitly refreshes it", () => {
	const value = draft({ dependsOn: [{ modelSpecId: "ods-1", revision: 1 }] });
	const onChange = vi.fn();
	act(() => root.render(<ModelLogicalDependencies draft={value} modelType="FACT" models={[source()]} onChange={onChange} />));
	expect(container.textContent).toContain("设计版本 r1");
	expect(onChange).not.toHaveBeenCalled();
	act(() => container.querySelector<HTMLButtonElement>("button")!.click());
	expect(onChange).toHaveBeenCalledWith({ ...value, dependsOn: [{ modelSpecId: "ods-1", revision: 2 }] });
});
it("excludes unpublished cross-plan designs and preserves unavailable references", () => {
	const onChange = vi.fn();
	act(() => root.render(<ModelLogicalDependencies
		draft={draft({ dependsOn: [{ modelSpecId: "ods-1", revision: 1 }] })} modelType="FACT"
		models={[source({ planId: "other-plan" })]} readOnly onChange={onChange} />));
	expect(container.querySelector("input")).toBeNull();
	expect(container.textContent).toContain("已保留设计版本 r1");
	expect(container.querySelector<HTMLButtonElement>("button")!.disabled).toBe(true);
	expect(onChange).not.toHaveBeenCalled();
});
