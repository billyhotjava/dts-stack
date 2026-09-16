// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
	getModelSpec: vi.fn(),
	getModelDeliveryStatus: vi.fn(),
}));
const qualityRequests = vi.hoisted(() => [] as number[]);

vi.mock("@/api/modelSpecApi", () => ({
	getModelSpec: api.getModelSpec,
	rerunReleaseCandidateGovernanceQuality: vi.fn(),
}));
vi.mock("@/api/modelDeliveryStatusApi", () => ({ getModelDeliveryStatus: api.getModelDeliveryStatus }));
vi.mock("@/api/modelIngestionTargetApi", () => ({ registerModelData: vi.fn() }));
vi.mock("@/pages/data-modeling/prototype/ModelTargetQualityPanel", () => ({
	ModelTargetQualityPanel: ({ openRequest }: { openRequest: number }) => {
		qualityRequests.push(openRequest);
		return <div data-testid="quality" data-open={openRequest} />;
	},
}));
vi.mock("@/pages/data-modeling/prototype/ModelCatalogDeliveryPanel", () => ({ ModelCatalogDeliveryPanel: () => null }));
vi.mock("@/pages/data-modeling/prototype/ModelAnalysisPreparationAction", () => ({
	ModelAnalysisPreparationAction: () => null,
}));
vi.mock("@/pages/data-modeling/prototype/ModelPublishDialog", () => ({ ModelPublishDialog: () => null }));
vi.mock("@/pages/data-modeling/prototype/ModelWorkbenchNavigationGuard", () => ({
	ModelWorkbenchNavigationGuard: () => null,
}));

import { ModelDataOperationsPanel } from "./ModelDataOperationsPanel";

let root: Root;
let container: HTMLDivElement;

const model = { id: "m-1", revision: 2, checksum: "sum", name: "项目明细", modelType: "FACT" };
const delivery = (actionCode: string) => ({
	modelSpecId: "m-1",
	modelRevision: 2,
	modelChecksum: "sum",
	planId: "p-1",
	wizard: [],
	steps: [],
	modelingResult: { state: "SUCCEEDED", targetRelation: "dwd.project" },
	dataPrimaryAction: { code: actionCode, enabled: true },
});

beforeEach(() => {
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
	qualityRequests.length = 0;
	api.getModelSpec.mockResolvedValue(model);
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

const openRequest = () => Number(container.querySelector("[data-testid=quality]")?.getAttribute("data-open"));

it("opens the quality section once when arriving from a finished model", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("CONFIGURE_QUALITY_RULES"));

	await mount({
		focus: "quality",
		returnTo: "/data-modeling/dimensions/workbench?modelSpecId=m-1&step=verification&environment=dev",
	});

	expect(openRequest()).toBe(1);
	const back = Array.from(container.querySelectorAll("a")).find((link) => link.textContent === "返回模型");
	expect(back?.getAttribute("href")).toBe(
		"/data-modeling/dimensions/workbench?modelSpecId=m-1&step=verification&environment=dev",
	);
});

it("explains that assets must be registered before quality configuration", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("REGISTER_DATA_ASSETS"));

	await mount({ focus: "quality" });

	expect(openRequest()).toBe(0);
	expect(container.querySelector("output")?.textContent).toContain("资产登记完成后可配置质量规则");
});

it("keeps the quality section closed without a focus request and hides unsafe return links", async () => {
	api.getModelDeliveryStatus.mockResolvedValue(delivery("CONFIGURE_QUALITY_RULES"));

	await mount({ returnTo: "/governance/rules" });

	expect(openRequest()).toBe(0);
	expect(Array.from(container.querySelectorAll("a")).map((link) => link.textContent)).not.toContain("返回模型");
});
