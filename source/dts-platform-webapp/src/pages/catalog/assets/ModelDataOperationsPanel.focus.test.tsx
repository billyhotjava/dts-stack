// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
	getModelSpec: vi.fn(),
	getModelDeliveryStatus: vi.fn(),
	getModelDataRegistrationStatus: vi.fn(),
	registerModelData: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", () => ({ getModelSpec: api.getModelSpec }));
vi.mock("@/api/modelDeliveryStatusApi", () => ({ getModelDeliveryStatus: api.getModelDeliveryStatus }));
vi.mock("@/api/modelIngestionTargetApi", () => ({
	getModelDataRegistrationStatus: api.getModelDataRegistrationStatus,
	registerModelData: api.registerModelData,
}));
vi.mock("@/pages/data-modeling/prototype/ModelCatalogDeliveryPanel", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/pages/data-modeling/prototype/ModelCatalogDeliveryPanel")>()),
	ModelCatalogDeliveryPanel: () => null,
}));
vi.mock("@/pages/data-modeling/prototype/ModelAnalysisPreparationAction", () => ({
	ModelAnalysisPreparationAction: () => null,
}));
vi.mock("@/pages/data-modeling/prototype/ModelWorkbenchNavigationGuard", () => ({
	ModelWorkbenchNavigationGuard: () => null,
}));

import { ModelDataOperationsPanel } from "./ModelDataOperationsPanel";

let root: Root;
let container: HTMLDivElement;

const model = { id: "m-1", revision: 2, checksum: "sum", name: "项目明细", modelType: "FACT" };
const delivery = (actionCode: string, catalogResourceId?: string) => ({
	modelSpecId: "m-1",
	modelRevision: 2,
	modelChecksum: "sum",
	planId: "p-1",
	candidate: { id: "c-1", version: 3 },
	wizard: [],
	steps: catalogResourceId
		? [{ key: "catalog", state: "SUCCEEDED", matchesCurrentTarget: true, resourceId: catalogResourceId, outputs: [] }]
		: [],
	modelingResult: { state: "SUCCEEDED", targetRelation: "dwd.project" },
	dataPrimaryAction: { code: actionCode, enabled: true },
});

beforeEach(() => {
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
	api.getModelSpec.mockResolvedValue(model);
	api.getModelDataRegistrationStatus.mockResolvedValue({ state: "SUCCEEDED", attempts: 1 });
	api.registerModelData.mockResolvedValue({});
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

async function mount(props: { focus?: "quality"; returnTo?: string }) {
	await act(async () => {
		root.render(
			<MemoryRouter>
				<ModelDataOperationsPanel modelSpecId="m-1" environment="dev" {...props} />
			</MemoryRouter>,
		);
	});
}

const links = () => Array.from(container.querySelectorAll("a"));
const button = (label: string) =>
	Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.replace(/\s/g, "") === label);

it("links a registered output to 质量管控 instead of embedding quality rules, with a safe way back", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("CONFIGURE_QUALITY_RULES", "dataset-1"));

	await mount({
		focus: "quality",
		returnTo: "/data-modeling/dimensions/workbench?modelSpecId=m-1&step=verification&environment=dev",
	});

	const quality = links().find((link) => link.textContent === "按表配置质量规则");
	expect(quality?.getAttribute("href")).toBe("/governance/rules/config/tables/dataset-1");
	expect(links().find((link) => link.textContent === "返回模型")?.getAttribute("href")).toBe(
		"/data-modeling/dimensions/workbench?modelSpecId=m-1&step=verification&environment=dev",
	);
	expect(api.getModelDataRegistrationStatus).toHaveBeenCalledWith("m-1", "c-1");
});

it("explains that assets must be registered before quality rules can be configured", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("REGISTER_DATA_ASSETS"));
	api.getModelDataRegistrationStatus.mockResolvedValue({ state: "MISSING", attempts: 0 });

	await mount({ focus: "quality" });

	expect(container.textContent).toContain("资产登记完成后，才能在质量管控中为它配置规则");
	expect(links().map((link) => link.textContent)).not.toContain("按表配置质量规则");
	expect(button("登记数据资产")?.disabled).toBe(false);
});

it("hides unsafe return links", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("CONFIGURE_QUALITY_RULES", "dataset-1"));

	await mount({ returnTo: "/governance/rules" });

	expect(links().map((link) => link.textContent)).not.toContain("返回模型");
});

it("shows a queued registration without offering a manual command", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("NONE"));
	api.getModelDataRegistrationStatus.mockResolvedValue({ state: "PENDING", attempts: 0 });

	await mount({});

	expect(container.textContent).toContain("资产登记排队中");
	expect(button("登记数据资产")).toBeUndefined();
	expect(button("重试登记")).toBeUndefined();
});

it("shows a failed registration with its reason and retries it without rebuilding", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("NONE"));
	api.getModelDataRegistrationStatus.mockResolvedValue({
		state: "FAILED",
		attempts: 5,
		errorCode: "CATALOG_UNAVAILABLE",
		errorMessage: "目录服务不可用",
	});

	await mount({});

	expect(container.textContent).toContain("目录服务不可用（CATALOG_UNAVAILABLE），已尝试 5 次");
	expect(container.textContent).toContain("构建结果不受影响");
	await act(async () => button("重试登记")?.click());
	expect(api.registerModelData).toHaveBeenCalledWith("m-1", {
		candidateId: "c-1",
		candidateVersion: 3,
		modelRevision: 2,
		modelChecksum: "sum",
	});
});

it("keeps the model output readable when the registration status cannot be read", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("CONFIGURE_QUALITY_RULES", "dataset-1"));
	api.getModelDataRegistrationStatus.mockRejectedValue(new Error("boom"));

	await mount({});

	expect(container.textContent).toContain("模型已完成构建");
	expect(container.querySelector('[role="alert"]')).toBeNull();
});
