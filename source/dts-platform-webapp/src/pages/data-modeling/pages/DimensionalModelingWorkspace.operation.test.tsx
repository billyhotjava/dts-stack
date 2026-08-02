// @vitest-environment jsdom

import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, useLocation, useNavigate } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CreateDimensionModelCommand } from "@/api/modelSpecApi";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { DataModelingRoute } from "../types";
import { DimensionalModelingWorkspace } from "./DimensionalModelingWorkspace";

const apiMocks = vi.hoisted(() => ({
	getOperation: vi.fn(),
	listModels: vi.fn(),
	getRevision: vi.fn(),
	getDefinition: vi.fn(),
	getDefinitionRevision: vi.fn(),
}));
const draftMocks = vi.hoisted(() => ({ read: vi.fn(), clear: vi.fn() }));
const editorCapture = vi.hoisted(() => ({ props: null as Record<string, unknown> | null }));

vi.mock("@/api/modelSpecApi", () => ({
	getDimensionModelOperation: apiMocks.getOperation,
	getModelSpecRevision: apiMocks.getRevision,
	listModelSpecs: apiMocks.listModels,
}));
vi.mock("@/api/dimensionDefinitionApi", () => ({
	getDimensionDefinition: apiMocks.getDefinition,
	getDimensionDefinitionRevision: apiMocks.getDefinitionRevision,
}));
vi.mock("@/features/modeling/operations/dimensionModelDraftSession", () => ({
	readDimensionModelDraft: draftMocks.read,
	clearDimensionModelDraft: draftMocks.clear,
}));
vi.mock("@/store/userStore", () => ({
	useUserInfo: () => ({ id: "actor-1", username: "tester", email: "tester@example.com" }),
}));
vi.mock("../components/ModelingEditor", () => ({
	ModelingEditor: (props: Record<string, unknown>) => {
		editorCapture.props = props;
		return <div data-testid="modeling-editor">editor</div>;
	},
}));
vi.mock("../components/model-detail/ModelDetailWorkbench", () => ({
	ModelDetailWorkbench: ({ model, onEdit }: { model: CanonicalModelSpecView; onEdit?: () => void }) => (
		<div data-testid="model-detail">
			{model.name}
			{onEdit ? (
				<button data-testid="edit-model" onClick={onEdit} type="button">
					edit
				</button>
			) : null}
		</div>
	),
}));
vi.mock("../components/model-detail/ModelRepresentationState", () => ({
	ModelRepresentationState: ({ state, message }: { state: string; message?: string }) => (
		<div data-testid={`state-${state}`}>{message || state}</div>
	),
}));
vi.mock("../components/DimensionDefinitionTargetView", () => ({
	DimensionDefinitionTargetView: () => <div>dimension target</div>,
}));
vi.mock("../components/ReverseModelingWizard", () => ({ ReverseModelingWizard: () => <div>reverse</div> }));
vi.mock("../components/WorkspacePage", () => ({
	WorkspacePage: ({ children }: { children: ReactElement }) => <main>{children}</main>,
	ActionButton: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
		<button type="button" {...props}>
			{children}
		</button>
	),
}));

const OPERATION_ID = "50000000-0000-4000-8000-000000000001";
const MODEL_ID = "60000000-0000-4000-8000-000000000001";
const OTHER_MODEL_ID = "60000000-0000-4000-8000-000000000002";
const DEFINITION_REF = {
	dimensionDefinitionId: "90000000-0000-4000-8000-000000000001",
	revision: 2,
};

const currentModel: CanonicalModelSpecView = {
	id: MODEL_ID,
	contractVersion: 2,
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
	planId: "70000000-0000-4000-8000-000000000001",
	domainId: "80000000-0000-4000-8000-000000000001",
	modelType: "DIMENSION",
	layer: "DWD",
	name: "客户维度",
	description: null,
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	businessActivityRef: null,
	consumptionScenario: null,
	grain: { statement: "一个客户一行", keys: ["customer_code"] },
	factShape: null,
	timeSemantics: null,
	generationStrategy: null,
	dimensionProfile: { hierarchies: [], scdPolicy: { type: "NONE" } },
	dataMartId: null,
	variantCode: "DIM_CUSTOMER",
	fields: [],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
	dimensionDefinitionRef: DEFINITION_REF,
	status: "DRAFT",
	revision: 2,
	checksum: "checksum-r2",
	createdAt: "2026-08-02T00:00:00Z",
	updatedAt: "2026-08-02T00:00:00Z",
};

const pendingCommand: CreateDimensionModelCommand = {
	operationId: OPERATION_ID,
	definitionBinding: {
		mode: "EXISTING",
		dimensionDefinitionRef: DEFINITION_REF,
	},
	modelSpec: {
		planId: currentModel.planId,
		domainId: currentModel.domainId,
		modelType: "DIMENSION",
		layer: "DWD",
		name: currentModel.name,
		description: currentModel.description,
		implementationMode: "DESIGNER_GENERATED",
		materialization: currentModel.materialization,
		businessActivityRef: null,
		consumptionScenario: null,
		grain: currentModel.grain,
		factShape: null,
		timeSemantics: null,
		generationStrategy: null,
		dimensionProfile: currentModel.dimensionProfile,
		dataMartId: null,
		variantCode: currentModel.variantCode,
		fields: [],
		sourceRefs: [],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [],
	},
};

const route: DataModelingRoute = {
	workspace: "dimensions",
	view: "workbench",
	title: "维度建模",
	description: "维度建模",
};

function LocationProbe() {
	const location = useLocation();
	return <output data-testid="location">{`${location.pathname}${location.search}`}</output>;
}

function NavigationProbe() {
	const navigate = useNavigate();
	return (
		<button
			data-testid="navigate-other-model"
			onClick={() => navigate(`/data-modeling/dimensions/workbench?modelSpecId=${OTHER_MODEL_ID}`)}
			type="button"
		>
			navigate
		</button>
	);
}

let container: HTMLDivElement;
let root: Root;

async function render(initialEntry: string) {
	await act(async () => {
		root.render(
			<MemoryRouter initialEntries={[initialEntry]}>
				<DimensionalModelingWorkspace route={route} />
				<LocationProbe />
				<NavigationProbe />
			</MemoryRouter>,
		);
		await Promise.resolve();
	});
}

async function flush() {
	await act(async () => {
		await new Promise((resolve) => setTimeout(resolve, 0));
	});
}

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	Object.values(apiMocks).forEach((mock) => mock.mockReset());
	Object.values(draftMocks).forEach((mock) => mock.mockReset());
	editorCapture.props = null;
	apiMocks.listModels.mockResolvedValue([]);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("DimensionalModelingWorkspace operation recovery", () => {
	it("resolves an operation before explicit deep links, create intent, or a default model", async () => {
		let resolveOperation!: (value: unknown) => void;
		apiMocks.getOperation.mockReturnValue(
			new Promise((resolve) => {
				resolveOperation = resolve;
			}),
		);
		apiMocks.listModels.mockResolvedValue([{ ...currentModel, id: "default-model", name: "默认模型" }]);
		await render(`/data-modeling/dimensions/workbench?modelSpecId=explicit&create=1&dmOperationId=${OPERATION_ID}`);
		expect(container.querySelector('[data-testid="state-loading"]')).toBeTruthy();
		expect(container.querySelector('[data-testid="location"]')?.textContent).toContain(`dmOperationId=${OPERATION_ID}`);

		resolveOperation({
			operationId: OPERATION_ID,
			bindingMode: "EXISTING",
			dimensionDefinitionRevision: { revision: 2 },
			modelSpecRevision: currentModel,
			currentModelSpec: currentModel,
			replayed: true,
		});
		await flush();

		const location = container.querySelector('[data-testid="location"]')?.textContent || "";
		expect(location).toContain(`modelSpecId=${MODEL_ID}`);
		expect(location).not.toContain("dmOperationId");
		expect(location).not.toContain("create=1");
		expect(draftMocks.clear).toHaveBeenCalledWith(OPERATION_ID);
	});

	it("restores the exact cached command with the same operation after GET 404", async () => {
		apiMocks.getOperation.mockRejectedValue({ response: { status: 404 } });
		draftMocks.read.mockResolvedValue({ kind: "FOUND", command: pendingCommand, requestFingerprint: "sha256" });
		await render(`/data-modeling/dimensions/workbench?dmOperationId=${OPERATION_ID}`);
		await flush();

		expect(container.querySelector('[data-testid="modeling-editor"]')).toBeTruthy();
		expect(editorCapture.props?.initialDimensionCommand).toEqual(pendingCommand);
		expect((editorCapture.props?.selection as { type: string }).type).toBe("dimension-table");
		expect(container.querySelector('[data-testid="location"]')?.textContent).toContain(`dmOperationId=${OPERATION_ID}`);
		expect(draftMocks.clear).not.toHaveBeenCalled();
	});

	it("shows a recovery error instead of auto-posting or opening an empty editor when no draft exists", async () => {
		apiMocks.getOperation.mockRejectedValue({ response: { status: 404 } });
		draftMocks.read.mockResolvedValue({ kind: "MISSING" });
		await render(`/data-modeling/dimensions/workbench?dmOperationId=${OPERATION_ID}`);
		await flush();

		expect(container.textContent).toContain("DIMENSION_MODEL_RECOVERY_MISSING");
		expect(container.querySelector('[data-testid="modeling-editor"]')).toBeNull();
		expect(container.querySelector('[data-testid="location"]')?.textContent).toContain(`dmOperationId=${OPERATION_ID}`);
	});

	it("drops the editor and rejects a stale save callback after browser navigation changes the selected model", async () => {
		const otherModel = { ...currentModel, id: OTHER_MODEL_ID, name: "其他模型", checksum: "checksum-other" };
		apiMocks.listModels.mockResolvedValue([currentModel, otherModel]);
		await render(`/data-modeling/dimensions/workbench?modelSpecId=${MODEL_ID}`);
		await flush();

		act(() => {
			(container.querySelector('[data-testid="edit-model"]') as HTMLButtonElement).click();
		});
		const staleOnSaved = editorCapture.props?.onSaved as (model: CanonicalModelSpecView) => Promise<void>;
		expect(container.querySelector('[data-testid="modeling-editor"]')).toBeTruthy();

		act(() => {
			(container.querySelector('[data-testid="navigate-other-model"]') as HTMLButtonElement).click();
		});
		await flush();

		expect(container.querySelector('[data-testid="modeling-editor"]')).toBeNull();
		expect(container.querySelector('[data-testid="location"]')?.textContent).toContain(`modelSpecId=${OTHER_MODEL_ID}`);
		await expect(staleOnSaved(currentModel)).rejects.toThrow("MODEL_SPEC_STALE_NAVIGATION_RESULT");
		expect(container.querySelector('[data-testid="location"]')?.textContent).toContain(`modelSpecId=${OTHER_MODEL_ID}`);
	});
});
