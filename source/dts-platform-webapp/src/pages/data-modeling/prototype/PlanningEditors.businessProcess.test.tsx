// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ create: vi.fn(), update: vi.fn(), domains: vi.fn() }));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	createBusinessProcessApi: mocks.create,
	updateBusinessProcessApi: mocks.update,
}));
vi.mock("./services/planningCatalogDomainService", () => ({ listPlanningCatalogDomains: mocks.domains }));
vi.mock("./services/planningProjectionService", () => ({
	normalizeModelingRequestFailure: (_error: unknown, fallback: string) => ({ message: fallback }),
}));

import { BusinessProcessForm } from "./PlanningEditors";

const original = {
	id: "record-1",
	processId: "prj_mgmt",
	domainId: "project",
	name: "项目管理业务",
	description: "原业务定义",
	lifecycleStatus: "ACTIVE",
};
let container: HTMLDivElement;
let root: Root;
const done = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	vi.resetAllMocks();
	mocks.domains.mockResolvedValue([
		{ id: "flower", name: "花卉数据域", code: "DATA_PRS", parentId: "root" },
		{ id: "project", name: "项目数据域", code: "DATA_PRJ", parentId: "root" },
	]);
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});
afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});
const render = async (initial: unknown, canMaintain = true) => {
	await act(async () => root.render(<BusinessProcessForm initial={initial} canMaintain={canMaintain} onDone={done} />));
};
const change = async (selector: string, value: string) => {
	const field = container.querySelector(selector) as HTMLInputElement;
	await act(async () => {
		field.value = value;
		Simulate.change(field);
	});
};
const save = async () => {
	await act(async () => container.querySelector("button")?.click());
};

describe("S10DC-97 business process editing", () => {
	it("fills the selected record and updates it without creating or changing identity", async () => {
		await render(original);
		const inputs = container.querySelectorAll("input");
		expect(inputs[0].value).toBe("prj_mgmt");
		expect(inputs[0].disabled).toBe(true);
		expect(inputs[1].value).toBe("项目管理业务");
		expect(container.querySelector("select")?.value).toBe("project");
		expect(container.querySelector("select")?.disabled).toBe(true);
		expect(container.querySelector("textarea")?.value).toBe("原业务定义");
		await change("input:not(:disabled)", "项目管理修订");
		await change("textarea", "");
		await save();
		expect(mocks.update).toHaveBeenCalledWith("project", "prj_mgmt", { name: "项目管理修订", description: undefined });
		expect(mocks.create).not.toHaveBeenCalled();
		expect(done).toHaveBeenCalledWith("业务过程已更新");
	});

	it("resets when selecting another record or returning to creation", async () => {
		await render(original);
		await render({ ...original, processId: "prs_mgmt", domainId: "flower", name: "花卉管理" });
		expect(container.querySelector("input")?.value).toBe("prs_mgmt");
		expect(container.querySelector("select")?.value).toBe("flower");
		await render(null);
		expect(container.querySelector("input")?.value).toBe("");
		expect(container.querySelector("input")?.disabled).toBe(false);
		await change("input", "new_process");
		await change("label:nth-child(2) input", "新业务过程");
		await save();
		expect(mocks.create).toHaveBeenCalledWith("flower", {
			processId: "new_process",
			name: "新业务过程",
			description: undefined,
		});
		expect(mocks.update).not.toHaveBeenCalled();
	});

	it("keeps failed edits available for correction", async () => {
		mocks.update.mockRejectedValue(new Error("保存失败"));
		await render(original);
		await change("input:not(:disabled)", "保留修改");
		await save();
		expect(container.querySelector('[role="alert"]')?.textContent).toBe("业务过程更新失败。");
		expect(container.querySelectorAll("input")[1].value).toBe("保留修改");
		expect(done).not.toHaveBeenCalled();
	});

	it.each([false, true])("prevents readonly and retired updates (maintain=%s)", async (canMaintain) => {
		await render({ ...original, lifecycleStatus: canMaintain ? "RETIRED" : "ACTIVE" }, canMaintain);
		expect(container.querySelector("button")?.disabled).toBe(true);
		await save();
		expect(mocks.update).not.toHaveBeenCalled();
	});
});
