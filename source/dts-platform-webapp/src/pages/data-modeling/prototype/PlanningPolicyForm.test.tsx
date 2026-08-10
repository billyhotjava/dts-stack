// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	loadPlanningContextPolicy: vi.fn(),
	saveWarehousePlanPolicy: vi.fn(),
}));

vi.mock("./services/planningContextPolicyService", () => ({
	loadPlanningContextPolicy: mocks.loadPlanningContextPolicy,
}));
vi.mock("@/api/warehousePlanApi", () => ({
	saveWarehousePlanPolicy: mocks.saveWarehousePlanPolicy,
}));

import { PlanningPolicyForm } from "./PlanningPolicyForm";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	mocks.loadPlanningContextPolicy.mockResolvedValue({
		planId: "plan-87",
		version: 3,
		policy: {
			layerScheme: "CLASSIC_ODS_DWD_DWS_ADS",
			namingPolicy: "CLASSIC_LOWER_SNAKE",
			historyPolicy: "PRESERVE_BUSINESS_HISTORY",
			defaultTimeZone: "Asia/Shanghai",
			conceptualDesignAllowed: false,
			standardCoverage: "KEY_AND_MEASURE",
			qualityGate: "BLOCKING",
			businessCategoryMode: "SINGLE_DEFAULT",
			defaultBusinessCategoryId: "category-87",
			businessProcessMode: "AUTO_SELECT_SINGLE",
			readiness: "IMPLEMENTATION_READY",
			issues: [],
		},
		categories: [
			{
				domainId: "category-87",
				confirmationStatus: "CONFIRMED",
				resolutionStatus: "AVAILABLE",
				name: "研究所业务",
				code: "INSTITUTE",
			},
		],
	});
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

describe("PlanningPolicyForm", () => {
	it("loads the canonical policy and saves the simplified context with the existing version", async () => {
		mocks.saveWarehousePlanPolicy.mockResolvedValue({
			version: 4,
			value: {
				businessCategoryMode: "SINGLE_DEFAULT",
				defaultBusinessCategoryId: "category-87",
				businessProcessMode: "MANAGED",
			},
		});

		await act(async () => root.render(<PlanningPolicyForm canMaintain />));
		await act(async () => undefined);

		expect(container.textContent).toContain("研究所业务");
		expect(container.textContent).toContain("唯一过程自动选择");
		const processMode = container.querySelector('select[aria-label="业务过程模式"]') as HTMLSelectElement;
		await act(async () => {
			processMode.value = "MANAGED";
			processMode.dispatchEvent(new Event("change", { bubbles: true }));
		});
		const save = [...container.querySelectorAll("button")].find((button) =>
			button.textContent?.includes("保存规划参数"),
		);
		await act(async () => (save as HTMLButtonElement).click());

		expect(mocks.saveWarehousePlanPolicy).toHaveBeenCalledWith(
			"plan-87",
			3,
			expect.objectContaining({
				businessCategoryMode: "SINGLE_DEFAULT",
				defaultBusinessCategoryId: "category-87",
				businessProcessMode: "MANAGED",
			}),
		);
		expect(container.textContent).toContain("规划参数已保存");
	});
});
