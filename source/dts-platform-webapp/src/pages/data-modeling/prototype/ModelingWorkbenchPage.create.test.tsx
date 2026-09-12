// @vitest-environment jsdom
import { act, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { DataModelingRoute } from "../types";
import type { ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import type { ModelWorkbenchCatalogListProps } from "./ModelWorkbenchCatalogList";
import type { ModelWorkbenchContext } from "./services/modelWorkbenchService";

const mocks = vi.hoisted(() => ({ loadContext: vi.fn() }));
vi.mock("./services/modelWorkbenchService", async (importOriginal) => ({
	...(await importOriginal<typeof import("./services/modelWorkbenchService")>()),
	loadModelWorkbenchContext: mocks.loadContext,
}));
vi.mock("@/store/userStore", () => ({ useUserInfo: () => ({ id: "test-owner" }) }));
vi.mock("./useModelingAccess", () => ({
	useModelingAuthorization: () => ({
		isPending: false,
		isError: false,
		data: {
			canModel: true,
			canSelectDepartment: false,
			departmentCode: "dept-test",
			departments: [{ code: "dept-test", name: "测试部门" }],
		},
	}),
	useModelAccess: (ids: string[]) => ({
		isError: false,
		data: Object.fromEntries(ids.map((id) => [id, { canEdit: true, canManage: true }])),
	}),
}));
vi.mock("./ModelAccessDrawer", () => ({ ModelAccessDrawer: () => null }));
vi.mock("./useDataModelingMenuGrant", () => ({ useDataModelingMenuGrant: () => true }));
vi.mock("./useModelDeliveryStatus", () => ({ useModelDeliveryStatus: () => ({ data: null, loading: false }) }));
vi.mock("./useModelAuthoringSession", () => ({ useModelAuthoringSession: () => ({}) }));
vi.mock("./useConceptDimensionWorkflow", () => ({ useConceptDimensionWorkflow: () => ({}) }));
vi.mock("./useCatalogActions", () => ({ useCatalogActions: () => ({}) }));
vi.mock("./AdvancedDbtWorkspace", () => ({ AdvancedDbtWorkspace: () => null }));
vi.mock("./ModelWorkbenchDialog", () => ({ ModelWorkbenchDialog: () => null }));
vi.mock("./ConceptDimensionRecordDialog", () => ({ ConceptDimensionRecordDialog: () => null }));
vi.mock("./ModelPublishDialog", () => ({ ModelPublishDialog: () => null }));
vi.mock("./ModelWizardFrame", () => ({ ModelWizardFrame: ({ children }: { children: ReactNode }) => children }));
vi.mock("./ModelingWorkbenchEditor", () => ({
	ModelingWorkbenchEditor: ({ draft, definitionOnly }: ModelingWorkbenchEditorProps) => (
		<section data-model-kind={draft.createKind} data-definition={String(definitionOnly)}>
			{draft.domainId}
		</section>
	),
}));
vi.mock("./ModelWorkbenchCatalogList", async () => {
	const { ModelWorkbenchCreateMenu } = await import("./ModelWorkbenchCreateMenu");
	return {
		ModelWorkbenchCatalogList: ({ onCreate, domains, detailsReady = true }: ModelWorkbenchCatalogListProps) => (
			<ModelWorkbenchCreateMenu categoryRoots={domains} saving={!detailsReady} onCreate={onCreate} />
		),
	};
});

import { ModelingWorkbenchPage } from "./ModelingWorkbenchPage";

const context: ModelWorkbenchContext = {
	planId: "plan-test",
	domains: [],
	models: [],
	dimensions: [],
	standards: [],
	dataMarts: [],
	subjectDomains: [],
	warehouseLayers: [],
	sources: [],
	implementationCapabilities: {
		adapter: "postgres",
		inputModesByModelType: {
			DIMENSION: ["PHYSICAL_ASSET", "GENERATED"],
			FACT: ["PHYSICAL_ASSET", "UPSTREAM_MODEL"],
			SUMMARY: ["UPSTREAM_MODEL"],
			APPLICATION: ["UPSTREAM_MODEL"],
		},
		loadStrategies: ["FULL"],
		materializationsByLoadStrategy: { FULL: ["table"] },
		settingKeys: [],
		partitionFieldsSupported: false,
		incrementalKeyRequired: true,
	},
};
let container: HTMLDivElement;
let root: Root;
beforeEach(() => {
	Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
	mocks.loadContext.mockReset().mockResolvedValue(context);
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

it.each([
	["创建明细表", "fact"],
	["创建汇总表", "summary"],
	["创建维度表", "dimension-table"],
	["创建应用表", "application"],
])("%s keeps its new draft when the wizard query changes", async (label, kind) => {
	const router = createMemoryRouter(
		[{ path: "/model", element: <ModelingWorkbenchPage route={{ description: "模型测试" } as DataModelingRoute} /> }],
		{ initialEntries: ["/model"] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	const button = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label);
	expect(button).toBeDefined();
	await act(async () => button!.click());
	expect(router.state.location.search).toBe("?step=definition&view=visual");
	expect(container.querySelector("[data-model-kind]")?.getAttribute("data-model-kind")).toBe(kind);
	expect(container.querySelector('[data-definition="true"]')).not.toBeNull();
	expect(mocks.loadContext).toHaveBeenCalledOnce();
	await act(async () => router.navigate("/model?step=definition&view=visual&environment=test"));
	expect(container.querySelector("[data-model-kind]")?.getAttribute("data-model-kind")).toBe(kind);
	expect(mocks.loadContext).toHaveBeenCalledOnce();
});

it("reloads an existing dimension deep link and clears it when returning to the list", async () => {
	mocks.loadContext.mockResolvedValue({
		...context,
		dimensions: [
			{
				id: "dimension-existing",
				domainId: "domain-existing",
				revision: 1,
				name: "已有维度",
				definition: "维度定义",
				reuseScope: "DOMAIN",
				attributes: [],
				status: "DRAFT",
			},
		],
	});
	const router = createMemoryRouter(
		[{ path: "/model", element: <ModelingWorkbenchPage route={{ description: "模型测试" } as DataModelingRoute} /> }],
		{ initialEntries: ["/model"] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	await act(async () => router.navigate("/model?dimensionDefinitionId=dimension-existing"));
	expect(container.querySelector('[data-model-kind="dimension"]')).not.toBeNull();
	expect(container.textContent).toContain("已有维度");
	await act(async () => router.navigate("/model"));
	expect(container.querySelector("[data-model-kind]")).toBeNull();
	expect(container.textContent).toContain("创建明细表");
});

it.each([1, 0, 2])("uses a child domain, never the selected category id (%s children)", async (count) => {
	const domains = [
		{ id: "category-id", code: "CATEGORY", name: "业务分类", parentCode: null },
		...Array.from({ length: count }, (_, i) => ({
			id: `domain-${i}`,
			code: `DOMAIN_${i}`,
			name: `数据域${i}`,
			parentCode: "CATEGORY",
		})),
	];
	mocks.loadContext.mockResolvedValue({ ...context, domains });
	const router = createMemoryRouter(
		[{ path: "/model", element: <ModelingWorkbenchPage route={{ description: "测试" } as DataModelingRoute} /> }],
		{ initialEntries: ["/model"] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	const category = container.querySelector<HTMLSelectElement>('select[aria-label="请选择业务分类"]')!;
	await act(async () => {
		category.value = "category-id";
		category.dispatchEvent(new Event("change", { bubbles: true }));
	});
	await act(async () =>
		Array.from(container.querySelectorAll("button"))
			.find((button) => button.textContent === "创建明细表")!
			.click(),
	);
	expect(container.querySelector("[data-model-kind]")?.textContent).toBe(count === 1 ? "domain-0" : "");
});

it("shows the catalog before editor options arrive and then enables creation", async () => {
	let finish!: (value: ModelWorkbenchContext) => void;
	mocks.loadContext.mockImplementation((ready) => {
		ready({ domains: [], models: [], dimensions: [] });
		return new Promise((resolve) => {
			finish = resolve;
		});
	});
	const router = createMemoryRouter(
		[{ path: "/model", element: <ModelingWorkbenchPage route={{ description: "模型测试" } as DataModelingRoute} /> }],
		{ initialEntries: ["/model"] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	const button = () =>
		Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "创建明细表");
	expect(button()).toBeDefined();
	expect(button()?.disabled).toBe(true);
	await act(async () => finish(context));
	expect(button()?.disabled).toBe(false);
});
