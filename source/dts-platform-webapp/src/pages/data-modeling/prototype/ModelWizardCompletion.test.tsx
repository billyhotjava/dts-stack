// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelWizardFrame } from "./ModelWizardFrame";
vi.mock("./ModelPublishDialog", () => ({ ModelPublishDialog: () => <div>物化操作</div> }));
let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div"); document.body.append(container); root = createRoot(container); back.mockClear();
});
afterEach(() => { act(() => root.unmount()); container.remove(); });
const back = vi.fn();
const model = { id: "model-1", revision: 3, name: "贴源订单" } as ModelSpecView;
function mount(state: string, step = "verification") {
	const delivery = { recommendedStep: "verification", environment: "dev", wizard: [], steps: [],
		modelingResult: { state, matchesCurrentTarget: true, targetRelation: "warehouse.ods.orders" },
	} as unknown as ModelDeliveryStatus;
	return act(() => root.render(<MemoryRouter initialEntries={[`/?modelId=model-1&step=${step}&environment=dev`]}>
		<ModelWizardFrame model={model} enabled delivery={delivery} loading={false} failure="" onRefresh={vi.fn()}
			canMaintain={false} onBack={back} dirty={false} commandsBlocked={false} onAssetGuardChange={vi.fn()}>
			<div>模型字段</div>
		</ModelWizardFrame>
	</MemoryRouter>));
}
it("offers exactly three modeling steps and lets a verified model finish even for a reader", () => {
	mount("SUCCEEDED");
	expect(container.querySelectorAll("nav button")).toHaveLength(3);
	expect(container.textContent).toContain("建模已完成");
	expect(container.textContent).not.toContain("物化操作");
	expect(container.querySelector("a")?.getAttribute("href")).toContain("modelSpecId=model-1&environment=dev");
	act(() => Array.from(container.querySelectorAll("button")).find(button => button.textContent === "返回模型列表")!.click());
	expect(back).toHaveBeenCalledOnce();
});
it("never treats unknown evidence as completion", () => {
	mount("UNKNOWN");
	expect(container.textContent).not.toContain("建模已完成");
	expect(container.textContent).toContain("物化操作");
});
it("keeps old delivery links read-only with an explicit data-module destination", () => {
	mount("UNKNOWN", "delivery");
	expect(container.querySelector("h3")?.textContent).toBe("历史交付结果");
	expect(container.textContent).not.toContain("物化操作");
	expect(container.querySelector("a")?.textContent).toBe("去数据管理");
});
