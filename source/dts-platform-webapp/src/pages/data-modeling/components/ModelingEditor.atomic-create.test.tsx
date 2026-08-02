// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CreateDimensionModelCommand } from "@/api/modelSpecApi";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelingEditor } from "./ModelingEditor";

const apiMocks = vi.hoisted(() => ({
	createDimension: vi.fn(),
	createModel: vi.fn(),
	updateModel: vi.fn(),
	getGates: vi.fn(),
	listDefinitions: vi.fn(),
	listStandards: vi.fn(),
	listPlans: vi.fn(),
	getCategories: vi.fn(),
	persistDraft: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", () => ({
	createDimensionModel: apiMocks.createDimension,
	createModelSpec: apiMocks.createModel,
	updateModelSpec: apiMocks.updateModel,
	getModelSpecStageGates: apiMocks.getGates,
}));
vi.mock("@/api/dimensionDefinitionApi", () => ({ listDimensionDefinitions: apiMocks.listDefinitions }));
vi.mock("@/api/modelingStandardsApi", () => ({ listModelFieldStandardOptions: apiMocks.listStandards }));
vi.mock("@/api/warehousePlanApi", () => ({
	listWarehousePlans: apiMocks.listPlans,
	getWarehousePlanCategories: apiMocks.getCategories,
}));
vi.mock("@/features/modeling/operations/dimensionModelDraftSession", () => ({
	persistDimensionModelDraft: apiMocks.persistDraft,
}));
vi.mock("@/hooks/useModuleManageAccess", () => ({ useCatalogMaintainerAccess: () => true }));
vi.mock("@/store/userStore", () => ({ useUserInfo: () => ({ id: "actor-1" }) }));
vi.mock("./ModelingDialogs", () => ({
	MODEL_FIELD_DISPLAY_COLUMNS: [
		["sequence", "序号"],
		["code", "字段名称"],
		["dataType", "类型"],
		["displayName", "字段显示名"],
		["primaryKey", "字段作用"],
		["notNull", "非空"],
		["attributeCode", "维度属性编码"],
		["operation", "操作"],
	],
	ModelingDialogs: () => null,
}));
vi.mock("./WorkspacePage", () => ({
	ActionButton: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
		<button type="button" {...props}>
			{children}
		</button>
	),
	StatusTag: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
}));

const OPERATION_ID = "50000000-0000-4000-8000-000000000001";
const DEFINITION_ID = "60000000-0000-4000-8000-000000000001";
const MODEL_ID = "70000000-0000-4000-8000-000000000001";
const PLAN_ID = "80000000-0000-4000-8000-000000000001";
const DOMAIN_ID = "90000000-0000-4000-8000-000000000001";

const command: CreateDimensionModelCommand = {
	operationId: OPERATION_ID,
	definitionBinding: {
		mode: "EXISTING",
		dimensionDefinitionRef: { dimensionDefinitionId: DEFINITION_ID, revision: 2 },
	},
	modelSpec: {
		planId: PLAN_ID,
		domainId: DOMAIN_ID,
		modelType: "DIMENSION",
		layer: "DWD",
		name: "客户维度",
		description: "统一客户分析口径",
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: null,
		consumptionScenario: null,
		grain: { statement: "一个客户一行", keys: ["customer_code"] },
		factShape: null,
		timeSemantics: null,
		generationStrategy: null,
		dimensionProfile: { hierarchies: [], scdPolicy: { type: "TYPE1" } },
		dataMartId: null,
		variantCode: "DIM_CUSTOMER",
		fields: [
			{
				name: "customer_code",
				displayName: "客户编码",
				dataType: "STRING",
				nullable: false,
				sourceFieldRef: "ods_customer.customer_code",
				role: "KEY",
				securityLevel: "INTERNAL",
				dimensionAttributeCode: "CUSTOMER_CODE",
				redundant: false,
				redundancySourceRef: null,
			},
		],
		sourceRefs: [],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [],
	},
};

const savedModel: CanonicalModelSpecView = {
	...command.modelSpec,
	id: MODEL_ID,
	contractVersion: 2,
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
	dimensionDefinitionRef: { dimensionDefinitionId: DEFINITION_ID, revision: 2 },
	status: "DRAFT",
	revision: 2,
	checksum: "checksum-r2",
	createdAt: "2026-08-02T00:00:00Z",
	updatedAt: "2026-08-02T00:00:00Z",
};

let container: HTMLDivElement;
let root: Root;

const flush = async () => {
	await act(async () => {
		await new Promise((resolve) => setTimeout(resolve, 0));
	});
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	Object.values(apiMocks).forEach((mock) => mock.mockReset());
	apiMocks.listPlans.mockResolvedValue([]);
	apiMocks.listStandards.mockResolvedValue([]);
	apiMocks.getCategories.mockResolvedValue({ value: { domainBindings: [] } });
	apiMocks.listDefinitions.mockResolvedValue([]);
	apiMocks.persistDraft.mockResolvedValue({ requestFingerprint: "sha256", expiresAt: 1 });
	apiMocks.createDimension.mockResolvedValue({
		operationId: OPERATION_ID,
		bindingMode: "EXISTING",
		dimensionDefinitionRevision: { revision: 2 },
		modelSpecRevision: savedModel,
		currentModelSpec: savedModel,
		replayed: false,
	});
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("ModelingEditor atomic dimension create", () => {
	it("submits one composite POST and never falls through to generic create or PUT", async () => {
		const onSaved = vi.fn().mockResolvedValue(undefined);
		await act(async () => {
			root.render(
				<ModelingEditor
					dimensionActorScope="tenant-1|actor-1"
					dimensionOperationId={OPERATION_ID}
					initialDimensionCommand={command}
					onSaved={onSaved}
					selection={{
						type: "dimension-table",
						code: "DIM_CUSTOMER",
						name: "客户维度",
						layer: "公共层",
						domain: DOMAIN_ID,
					}}
				/>,
			);
		});
		await flush();

		const saveButton = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent?.trim() === "保存",
		) as HTMLButtonElement;
		expect(saveButton.disabled).toBe(false);
		act(() => saveButton.click());
		await flush();

		expect(apiMocks.persistDraft).toHaveBeenCalledTimes(1);
		expect(apiMocks.createDimension).toHaveBeenCalledTimes(1);
		expect(apiMocks.createModel).not.toHaveBeenCalled();
		expect(apiMocks.updateModel).not.toHaveBeenCalled();
		expect(onSaved).toHaveBeenCalledWith(savedModel, OPERATION_ID);
	});
});
