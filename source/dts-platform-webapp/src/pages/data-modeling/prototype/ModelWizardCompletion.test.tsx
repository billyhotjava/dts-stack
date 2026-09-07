// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelWizardFrame } from "./ModelWizardFrame";
vi.mock("./ModelPublishDialog", () => ({ ModelPublishDialog: () => <div>物化操作</div> }));
afterEach(cleanup);
const back = vi.fn();
const model = { id: "model-1", revision: 3, name: "贴源订单" } as ModelSpecView;
function mount(state: string, step = "verification") {
	const delivery = { recommendedStep: "verification", environment: "dev", wizard: [], steps: [],
		modelingResult: { state, matchesCurrentTarget: true, targetRelation: "warehouse.ods.orders" },
	} as unknown as ModelDeliveryStatus;
	return render(<MemoryRouter initialEntries={[`/?modelId=model-1&step=${step}&environment=dev`]}>
		<ModelWizardFrame model={model} enabled delivery={delivery} loading={false} failure="" onRefresh={vi.fn()}
			canMaintain={false} onBack={back} dirty={false} commandsBlocked={false} onAssetGuardChange={vi.fn()}>
			<div>模型字段</div>
		</ModelWizardFrame>
	</MemoryRouter>);
}
it("offers exactly three modeling steps and lets a verified model finish even for a reader", () => {
	mount("SUCCEEDED");
	expect(screen.getByRole("navigation", { name: "建模步骤" }).querySelectorAll("button")).toHaveLength(3);
	expect(screen.getByText("建模已完成")).toBeTruthy();
	expect(screen.queryByText("物化操作")).toBeNull();
	expect(screen.getByRole("link", { name: "去数据管理" }).getAttribute("href")).toContain("modelSpecId=model-1&environment=dev");
	fireEvent.click(screen.getByRole("button", { name: "返回模型列表" }));
	expect(back).toHaveBeenCalledOnce();
});
it("never treats unknown evidence as completion", () => {
	mount("UNKNOWN");
	expect(screen.queryByText("建模已完成")).toBeNull();
	expect(screen.getByText("物化操作")).toBeTruthy();
});
it("keeps old delivery links read-only with an explicit data-module destination", () => {
	mount("UNKNOWN", "delivery");
	expect(screen.getByRole("heading", { name: "历史交付结果" })).toBeTruthy();
	expect(screen.queryByText("物化操作")).toBeNull();
	expect(screen.getByRole("link", { name: "去数据管理" })).toBeTruthy();
});
