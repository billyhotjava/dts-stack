// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { DataModelingRoute } from "../types";

const mocks = vi.hoisted(() => ({
	loadPlanningProjection: vi.fn(),
	createWarehouseLayer: vi.fn(),
	deleteWarehouseLayer: vi.fn(),
	deleteBusinessProcessApi: vi.fn(),
	createDataMart: vi.fn(),
	confirmDataMart: vi.fn(),
	retireDataMart: vi.fn(),
	updateDataMart: vi.fn(),
	createSubjectDomain: vi.fn(),
	confirmSubjectDomain: vi.fn(),
	retireSubjectDomain: vi.fn(),
	updateSubjectDomain: vi.fn(),
	listPlanningCatalogDomains: vi.fn(),
	loadPlanningContextPolicy: vi.fn(),
	listDataMarts: vi.fn(),
	normalizeModelingRequestFailure: vi.fn(),
	canMaintain: false,
	architectureCanMaintain: true,
}));

vi.mock("@/store/userStore", () => ({ useUserInfo: () => ({ id: "user-1" }) }));
vi.mock("@/pages/data-architecture/useArchitectureDictionaryWriteAccess", () => ({
	useArchitectureDictionaryWriteAccess: () => mocks.architectureCanMaintain,
}));
vi.mock("@/api/warehouseLayerApi", () => ({
	createWarehouseLayer: mocks.createWarehouseLayer,
	deleteWarehouseLayer: mocks.deleteWarehouseLayer,
}));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	createBusinessProcessApi: vi.fn(),
	deleteBusinessProcessApi: mocks.deleteBusinessProcessApi,
	listBusinessProcessesApi: vi.fn(),
}));
vi.mock("@/api/dataMartApi", () => ({
	createDataMart: mocks.createDataMart,
	confirmDataMart: mocks.confirmDataMart,
	retireDataMart: mocks.retireDataMart,
	updateDataMart: mocks.updateDataMart,
	listDataMarts: mocks.listDataMarts,
}));
vi.mock("@/api/subjectDomainApi", () => ({
	createSubjectDomain: mocks.createSubjectDomain,
	confirmSubjectDomain: mocks.confirmSubjectDomain,
	retireSubjectDomain: mocks.retireSubjectDomain,
	updateSubjectDomain: mocks.updateSubjectDomain,
	listSubjectDomains: vi.fn(),
}));
vi.mock("./services/planningCatalogDomainService", () => ({
	listPlanningCatalogDomains: mocks.listPlanningCatalogDomains,
	createPlanningCatalogDomain: vi.fn(),
	updatePlanningCatalogDomain: vi.fn(),
	deletePlanningCatalogDomain: vi.fn(),
}));
vi.mock("./services/planningProjectionService", () => ({
	loadPlanningProjection: mocks.loadPlanningProjection,
	normalizeModelingRequestFailure: mocks.normalizeModelingRequestFailure,
}));
vi.mock("./services/planningContextPolicyService", () => ({
	loadPlanningContextPolicy: mocks.loadPlanningContextPolicy,
}));
vi.mock("./useDataModelingMenuGrant", () => ({ useDataModelingMenuGrant: () => mocks.canMaintain }));
vi.mock("./PlanningPolicyForm", () => ({ PlanningPolicyForm: () => <section>建模策略已接入权威策略</section> }));

import { PlanningPage } from "./PlanningPage";

let container: HTMLDivElement;
let root: Root;

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

beforeEach(() => {
	mocks.canMaintain = false;
	mocks.architectureCanMaintain = true;
	mocks.loadPlanningContextPolicy.mockResolvedValue({
		policy: {
			businessCategoryMode: "SINGLE_DEFAULT",
			defaultBusinessCategoryId: "category-1",
			businessProcessMode: "AUTO_SELECT_SINGLE",
		},
	});
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

async function renderPlanning(route: DataModelingRoute) {
	await act(async () =>
		root.render(
			<MemoryRouter>
				<PlanningPage route={route} />
			</MemoryRouter>,
		),
	);
}

describe("PlanningPage", () => {
	const layerRoute = (): DataModelingRoute => ({
		workspace: "planning",
		view: "layers",
		title: "数仓分层",
		description: "维护全局共享的自定义数仓分层。",
	});
	const builtinDwd = {
		code: "DWD",
		name: "明细事实 / 维度层",
		systemLayerCode: "DWD",
		layerGroup: "COMMON",
		modelTypes: ["DIMENSION", "FACT"],
		kind: "DETAIL",
		responsibility: "业务明细",
		namingPrefixes: ["dwd_"],
		optional: false,
		builtin: true,
		deletable: false,
		disabledReason: "平台内置分层不可删除",
	};
	const customFinDetail = {
		code: "FIN_DETAIL",
		name: "财务明细层",
		systemLayerCode: "DWD",
		layerGroup: "COMMON",
		modelTypes: ["DIMENSION", "FACT"],
		kind: "DETAIL",
		responsibility: "财务域明细",
		namingPrefixes: ["fin_dwd_"],
		optional: false,
		builtin: false,
		deletable: true,
		disabledReason: null,
	};

	it("renders modeling strategy without a duplicate warehouse-planning sidebar", async () => {
		mocks.loadPlanningProjection.mockResolvedValue({ headers: [], rows: [], readOnlyReason: null });
		const route: DataModelingRoute = {
			workspace: "planning",
			view: "system",
			title: "建模策略",
			description: "配置当前建模计划的默认业务分类、业务过程选择方式和交付策略。",
		};

		await renderPlanning(route);

		expect(container.textContent).toContain("建模策略");
		expect(container.textContent).not.toContain("建模空间");
		expect(container.textContent).not.toContain("平台数仓规划");
		expect(container.querySelector('nav[aria-label="数仓规划目录"]')).toBeNull();
		expect(container.textContent).toContain("建模策略已接入权威策略");
	});

	it("uses the platform menu as the only warehouse-planning navigation", async () => {
		mocks.loadPlanningProjection.mockResolvedValue({ headers: [], rows: [], readOnlyReason: null });
		const route: DataModelingRoute = {
			workspace: "planning",
			view: "business-domains",
			title: "业务分类与数据域",
			description: "维护平台全局业务分类和数据域。",
		};

		await act(async () =>
			root.render(
				<MemoryRouter>
					<PlanningPage route={route} surface="architecture" />
				</MemoryRouter>,
			),
		);

		expect(container.textContent).toContain("数仓规划 / 平台规划");
		expect(container.textContent).not.toContain("平台数仓规划");
		expect(container.querySelector('nav[aria-label="数仓规划目录"]')).toBeNull();
		expect(container.querySelectorAll("a")).toHaveLength(0);
		expect(
			[...container.querySelectorAll("button")].find((button) => button.textContent?.includes("新建业务分类"))
				?.disabled,
		).toBe(false);
	});

	it("renders layer rows with built-in protection and opens the create drawer", async () => {
		mocks.canMaintain = true;
		mocks.loadPlanningProjection.mockResolvedValue({
			headers: ["分层编码", "分层名称", "分层归属"],
			rows: [
				{ id: "DWD", cells: ["DWD", "明细事实 / 维度层", "公共层"], source: builtinDwd },
				{ id: "FIN_DETAIL", cells: ["FIN_DETAIL", "财务明细层", "公共层"], source: customFinDetail },
			],
			readOnlyReason: null,
		});

		await renderPlanning(layerRoute());

		for (const label of ["分层编码", "分层名称", "分层归属"]) {
			expect(container.textContent).toContain(label);
		}
		expect(container.textContent).toContain("新建数仓分层");
		expect(container.textContent).toContain("财务明细层");
		expect(container.textContent).toContain("内置");

		await act(async () => {
			(
				[...container.querySelectorAll("button")].find((button) =>
					button.textContent?.includes("新建数仓分层"),
				) as HTMLButtonElement
			).click();
		});

		const codeInput = [...container.querySelectorAll("input")].find(
			(input) => input.placeholder === "例如：FIN_DETAIL",
		);
		expect(codeInput).toBeDefined();
	});

	it("creates a custom layer from the drawer with normalized values and refreshes", async () => {
		mocks.canMaintain = true;
		mocks.createWarehouseLayer.mockResolvedValue(customFinDetail);
		mocks.normalizeModelingRequestFailure.mockReturnValue({ message: "失败" });
		mocks.loadPlanningProjection.mockResolvedValue({
			headers: ["分层编码"],
			rows: [],
			readOnlyReason: null,
		});

		await renderPlanning(layerRoute());

		await act(async () => {
			(
				[...container.querySelectorAll("button")].find((button) =>
					button.textContent?.includes("新建数仓分层"),
				) as HTMLButtonElement
			).click();
		});

		const setReactInputValue = (input: HTMLInputElement, value: string) => {
			const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
			setter?.call(input, value);
			input.dispatchEvent(new Event("input", { bubbles: true }));
		};
		const codeInput = [...container.querySelectorAll("input")].find(
			(input) => input.placeholder === "例如：FIN_DETAIL",
		);
		const nameInput = [...container.querySelectorAll("input")].find(
			(input) => input.placeholder === "例如：财务明细层",
		);
		const prefixInput = [...container.querySelectorAll("input")].find(
			(input) => input.placeholder === "例如：fin_dwd_",
		);
		await act(async () => {
			setReactInputValue(codeInput!, "fin_detail");
			setReactInputValue(nameInput!, "财务明细层");
			setReactInputValue(prefixInput!, "FIN_DWD_");
		});
		await act(async () => {
			(
				[...container.querySelectorAll(".dmx-drawer button")].find((button) =>
					button.textContent?.includes("新建数仓分层"),
				) as HTMLButtonElement
			).click();
		});

		expect(mocks.createWarehouseLayer).toHaveBeenCalledWith({
			code: "FIN_DETAIL",
			name: "财务明细层",
			systemLayerCode: "DWD",
			description: undefined,
			namingPrefix: "fin_dwd_",
		});
		expect(mocks.loadPlanningProjection).toHaveBeenCalledTimes(2);
	});

	it("deletes a custom layer through the row action after confirmation", async () => {
		mocks.canMaintain = true;
		mocks.deleteWarehouseLayer.mockResolvedValue({});
		mocks.loadPlanningProjection.mockResolvedValue({
			headers: ["分层编码"],
			rows: [{ id: "FIN_DETAIL", cells: ["FIN_DETAIL"], source: customFinDetail }],
			readOnlyReason: null,
		});

		await renderPlanning(layerRoute());

		const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
		const deleteButton = [...container.querySelectorAll("button")].find((button) =>
			button.textContent?.includes("删除"),
		) as HTMLButtonElement;
		expect(deleteButton).toBeDefined();
		await act(async () => deleteButton.click());
		expect(mocks.deleteWarehouseLayer).not.toHaveBeenCalled();
		confirm.mockReturnValue(true);
		await act(async () => deleteButton.click());
		expect(mocks.deleteWarehouseLayer).toHaveBeenCalledWith("FIN_DETAIL");
		expect(mocks.loadPlanningProjection).toHaveBeenCalledTimes(2);
		confirm.mockRestore();
	});

	it("keeps rows intact and shows a stable error when an in-use delete is rejected", async () => {
		mocks.canMaintain = true;
		mocks.deleteWarehouseLayer.mockRejectedValue({
			response: { data: { code: "WAREHOUSE_LAYER_IN_USE", message: "存在活动模型引用该分层" } },
		});
		mocks.normalizeModelingRequestFailure.mockReturnValue({ message: "数仓分层删除失败：存在活动模型引用该分层" });
		mocks.loadPlanningProjection.mockResolvedValue({
			headers: ["分层编码"],
			rows: [{ id: "FIN_DETAIL", cells: ["FIN_DETAIL"], source: customFinDetail }],
			readOnlyReason: null,
		});

		await renderPlanning(layerRoute());
		vi.spyOn(window, "confirm").mockReturnValue(true);
		const deleteButton = [...container.querySelectorAll("button")].find((button) =>
			button.textContent?.includes("删除"),
		) as HTMLButtonElement;
		await act(async () => deleteButton.click());

		expect(container.textContent).toContain("数仓分层删除失败：存在活动模型引用该分层");
		expect(container.textContent).toContain("FIN_DETAIL");
		expect(mocks.loadPlanningProjection).toHaveBeenCalledTimes(1);
	});

	it("disables create and row actions without maintain permission", async () => {
		mocks.canMaintain = false;
		mocks.loadPlanningProjection.mockResolvedValue({
			headers: ["分层编码"],
			rows: [{ id: "FIN_DETAIL", cells: ["FIN_DETAIL"], source: customFinDetail }],
			readOnlyReason: null,
		});

		await renderPlanning(layerRoute());

		const createButton = [...container.querySelectorAll("button")].find((button) =>
			button.textContent?.includes("新建数仓分层"),
		) as HTMLButtonElement;
		expect(createButton.disabled).toBe(true);
		const deleteButton = [...container.querySelectorAll("button")].find((button) =>
			button.textContent?.includes("删除"),
		) as HTMLButtonElement;
		expect(deleteButton.disabled).toBe(true);
	});
});
