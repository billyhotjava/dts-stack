// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, expect, it, vi } from "vitest";
import type { PlanExecutionBinding } from "@/api/modelSpecApi";

const api = vi.hoisted(() => ({
	listWarehousePlans: vi.fn(),
	getPlanExecutionWorkspace: vi.fn(),
	runPlanExecutionNow: vi.fn(),
	repairPlanExecutionBinding: vi.fn(),
    listPlanExecutionPublications: vi.fn(), enablePlanExecution: vi.fn(), deployPlanExecution: vi.fn(),
}));

vi.mock("@/api/warehousePlanApi", () => ({ listWarehousePlans: api.listWarehousePlans }));
vi.mock("@/api/modelSpecApi", () => ({
	getPlanExecutionWorkspace: api.getPlanExecutionWorkspace,
	runPlanExecutionNow: api.runPlanExecutionNow,
	repairPlanExecutionBinding: api.repairPlanExecutionBinding,
    listPlanExecutionPublications: api.listPlanExecutionPublications, enablePlanExecution: api.enablePlanExecution, deployPlanExecution: api.deployPlanExecution,
}));

import { PlanExecutionSchedulePanel } from "./PlanExecutionSchedulePanel";

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

let root: Root;
let container: HTMLDivElement;

const plan = (id: string, name: string) => ({ id, name, code: id, tenantId: "t", ownerId: "o" });
const binding = (
	id: string,
	allowedActions: PlanExecutionBinding["allowedActions"],
	extra: Partial<PlanExecutionBinding> = {},
) =>
	({
		id,
		version: 4,
		environment: "prod",
		state: "ONLINE",
		scheduleMode: "MANUAL_ONLY",
		deploymentStatus: "ACTIVE",
		desiredDeploymentChecksum: "c",
		airflowDagId: `dag_${id}`,
		airflowState: "OBSERVED",
		latestOperationalRun: {},
		latestRelation: {},
		allowedActions,
		...extra,
	}) as PlanExecutionBinding;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
	api.listWarehousePlans.mockResolvedValue([plan("plan-a", "财务规划"), plan("plan-b", "项目规划")]);
	api.getPlanExecutionWorkspace.mockImplementation(async (planId: string) => ({
		planId,
		state: "READY",
		bindings:
			planId === "plan-a"
				? [binding("binding-a", ["RUN_NOW"])]
				: [
						binding("binding-b", ["REPAIR_DEPLOYMENT"], {
							state: "DEGRADED",
							primaryBlocker: { code: "DAG_MISSING", message: "DAG 未注册" } as never,
						}),
					],
	}));
	api.listPlanExecutionPublications.mockResolvedValue([]);
    api.enablePlanExecution.mockResolvedValue({});
    api.runPlanExecutionNow.mockResolvedValue({});
	api.repairPlanExecutionBinding.mockResolvedValue({});
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

async function mount(focusPlanId?: string) {
	await act(async () => root.render(<PlanExecutionSchedulePanel focusPlanId={focusPlanId} />));
	await act(async () => Promise.resolve());
}

const rows = () =>
	Array.from(container.querySelectorAll("tbody tr")).filter((row) => row.textContent?.includes("规划"));
const buttonIn = (row: Element | undefined, label: string) =>
	Array.from(row?.querySelectorAll("button") ?? []).find((item) => item.textContent?.replace(/\s/g, "") === label);

it("lists each plan's bindings with its state and puts the focused plan first", async () => {
	await mount("plan-b");

	expect(rows().map((row) => row.textContent?.includes("项目规划"))).toEqual([true, false]);
	expect(rows()[0].textContent).toContain("异常");
	expect(rows()[0].textContent).toContain("DAG 未注册");
	expect(rows()[1].textContent).toContain("仅手动运行");
	expect(container.textContent).toContain("运行失败只影响本次运行");
});

it("runs a binding now and repairs another only through the actions the server allows", async () => {
	await mount();
	const finance = rows().find((row) => row.textContent?.includes("财务规划"));
	const project = rows().find((row) => row.textContent?.includes("项目规划"));
	expect(buttonIn(finance, "修复部署")).toBeUndefined();
	expect(buttonIn(project, "立即运行并核验")).toBeUndefined();

	await act(async () => buttonIn(finance, "立即运行并核验")?.click());
	expect(api.runPlanExecutionNow).toHaveBeenCalledWith("plan-a", "binding-a", expect.any(String));
	expect(container.textContent).toContain("「财务规划」已提交运行");

	await act(async () =>
		buttonIn(
			rows().find((row) => row.textContent?.includes("项目规划")),
			"修复部署",
		)?.click(),
	);
	expect(api.repairPlanExecutionBinding).toHaveBeenCalledWith("plan-b", "binding-b", 4);
});

it("keeps readable plans when one plan's execution state cannot be read", async () => {
	api.getPlanExecutionWorkspace.mockImplementation(async (planId: string) => {
		if (planId === "plan-b") throw new Error("timeout");
		return { planId, state: "READY", bindings: [binding("binding-a", ["RUN_NOW"])] };
	});

	await mount();

	expect(rows()).toHaveLength(1);
	expect(container.textContent).toContain("有 1 个建模规划的调度状态读取失败");
});

it("shows a retryable error when plans cannot be listed", async () => {
	api.listWarehousePlans.mockRejectedValue(new Error("down"));

	await mount();

	expect(container.querySelector('[role="alert"]')).not.toBeNull();
	const retry = Array.from(container.querySelectorAll("button")).find(
		(item) => item.textContent?.replace(/\s/g, "") === "重新读取",
	);
	api.listWarehousePlans.mockResolvedValue([plan("plan-a", "财务规划")]);
	await act(async () => retry?.click());
	await act(async () => Promise.resolve());
	expect(rows()).toHaveLength(1);
});

it("locates a focused plan after the former fifty-plan boundary", async () => {
    api.listWarehousePlans.mockResolvedValue(Array.from({ length: 61 }, (_, i) => plan(`plan-${i}`, `规划${i}`)));
    await mount("plan-60");
    expect(api.getPlanExecutionWorkspace).toHaveBeenCalledWith("plan-60");
    expect(api.getPlanExecutionWorkspace).toHaveBeenCalledTimes(10);
    expect(container.textContent).toContain("共 61 个规划");
});

it("enables a deployed plan without submitting a run", async () => {
    api.getPlanExecutionWorkspace.mockResolvedValue({ planId: "plan-a", state: "READY", bindings: [binding("paused", ["ENABLE"], { state: "DISABLED" })] });
    await mount();
    const button = Array.from(container.querySelectorAll("button")).find((button) => button.textContent === "启用运行");
    await act(async () => button?.click());
    expect(api.enablePlanExecution).toHaveBeenCalledWith("plan-a", "paused", 4);
    expect(api.runPlanExecutionNow).not.toHaveBeenCalled();
});
