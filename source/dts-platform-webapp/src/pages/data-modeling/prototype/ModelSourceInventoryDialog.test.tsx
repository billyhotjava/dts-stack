// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	createWarehousePlan: vi.fn(),
	getWarehousePlanSources: vi.fn(),
	saveWarehousePlanSources: vi.fn(),
	listCatalogAssetsV2: vi.fn(),
	listTablesByDataset: vi.fn(),
}));

vi.mock("@/api/warehousePlanApi", () => ({
	createWarehousePlan: mocks.createWarehousePlan,
	getWarehousePlanSources: mocks.getWarehousePlanSources,
	saveWarehousePlanSources: mocks.saveWarehousePlanSources,
}));

vi.mock("@/api/platformApi", () => ({
	listCatalogAssetsV2: mocks.listCatalogAssetsV2,
	listTablesByDataset: mocks.listTablesByDataset,
}));

import { ModelSourceInventoryDialog } from "./ModelSourceInventoryDialog";

let container: HTMLDivElement;
let root: Root;

const confirmedSource = {
	bindingId: "binding-1",
	sourceType: "CATALOG_TABLE" as const,
	locator: { assetId: "asset-1" },
	sourceId: "asset-1",
	confirmationStatus: "CONFIRMED" as const,
	displayName: "ODS 项目计划",
	confirmedVersion: "schema-v1",
	resolvedVersion: "schema-v1",
	resolutionStatus: "AVAILABLE" as const,
	freshness: "CURRENT" as const,
};

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	mocks.getWarehousePlanSources.mockResolvedValue({
		bindings: [],
		readiness: "DRAFT",
		issues: [],
		version: 1,
		etag: "sources:1",
		checkedAt: "2026-08-12T00:00:00Z",
	});
	mocks.createWarehousePlan.mockResolvedValue({
		planId: "plan-created",
		plan: { id: "plan-created" },
		version: 1,
		etag: "plan-head:1",
		initialSourceBindings: [],
		nextAction: "CONFIRM_SOURCE",
		replayed: false,
	});
	mocks.listCatalogAssetsV2.mockResolvedValue({
		content: [
			{
				legacyDatasetId: "dataset-1",
				displayName: "PJM ODS 数据集",
			},
		],
		page: 0,
		size: 200,
		total: 1,
	});
	mocks.listTablesByDataset.mockResolvedValue({
		content: [{ id: "asset-1", name: "ODS 项目计划" }],
	});
	mocks.saveWarehousePlanSources.mockResolvedValue({
		bindings: [confirmedSource],
		readiness: "READY",
		issues: [],
		version: 2,
		etag: "sources:2",
		checkedAt: "2026-08-12T00:01:00Z",
	});
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

describe("ModelSourceInventoryDialog", () => {
	it("registers and confirms a catalog table through the warehouse source command boundary", async () => {
		const onSourcesChanged = vi.fn();
		await act(async () => {
			root.render(
				<ModelSourceInventoryDialog
					onClose={vi.fn()}
					onPlanCreated={vi.fn()}
					onSourcesChanged={onSourcesChanged}
					planId="plan-1"
				/>,
			);
		});
		await act(async () => undefined);

		expect(container.textContent).toContain("维护物理来源");
		const datasetSelect = container.querySelector<HTMLSelectElement>('select[aria-label="来源数据集"]');
		expect(datasetSelect).not.toBeNull();
		await act(async () => {
			if (!datasetSelect) return;
			datasetSelect.value = "dataset-1";
			datasetSelect.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await act(async () => undefined);
		const assetSelect = container.querySelector<HTMLSelectElement>('select[aria-label="目录资产"]');
		expect(assetSelect).not.toBeNull();
		await act(async () => {
			if (!assetSelect) return;
			assetSelect.value = "asset-1";
			assetSelect.dispatchEvent(new Event("change", { bubbles: true }));
		});
		const register = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("登记并确认"),
		);
		expect(register).toBeDefined();
		await act(async () => {
			(register as HTMLButtonElement).click();
		});
		await act(async () => undefined);

		expect(mocks.saveWarehousePlanSources).toHaveBeenCalledWith("plan-1", 1, [
			{
				sourceType: "CATALOG_TABLE",
				locator: { assetId: "asset-1" },
				confirmationStatus: "CONFIRMED",
				exclusionReason: null,
			},
		]);
		expect(onSourcesChanged).toHaveBeenCalledWith([confirmedSource]);
	});

	it("creates the default modeling context when the first physical source is registered", async () => {
		const candidateSource = {
			...confirmedSource,
			confirmationStatus: "CANDIDATE" as const,
			confirmedVersion: null,
			resolvedVersion: "schema-v1",
			freshness: "UNKNOWN" as const,
		};
		mocks.getWarehousePlanSources.mockResolvedValueOnce({
			bindings: [candidateSource],
			readiness: "DRAFT",
			issues: [],
			version: 1,
			etag: "sources:1",
			checkedAt: "2026-09-03T00:00:00Z",
		});
		const onPlanCreated = vi.fn();
		const onSourcesChanged = vi.fn();
		await act(async () => {
			root.render(
				<ModelSourceInventoryDialog
					onClose={vi.fn()}
					onPlanCreated={onPlanCreated}
					onSourcesChanged={onSourcesChanged}
					planId=""
				/>,
			);
		});
		await act(async () => undefined);

		const datasetSelect = container.querySelector<HTMLSelectElement>('select[aria-label="来源数据集"]');
		await act(async () => {
			if (!datasetSelect) return;
			datasetSelect.value = "dataset-1";
			datasetSelect.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await act(async () => undefined);
		const assetSelect = container.querySelector<HTMLSelectElement>('select[aria-label="目录资产"]');
		await act(async () => {
			if (!assetSelect) return;
			assetSelect.value = "asset-1";
			assetSelect.dispatchEvent(new Event("change", { bubbles: true }));
		});
		const register = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("登记并确认"),
		);
		await act(async () => {
			(register as HTMLButtonElement).click();
		});
		await act(async () => undefined);

		expect(mocks.createWarehousePlan).toHaveBeenCalledWith(
			expect.objectContaining({
				name: "默认建模上下文",
				onboardingMode: "ASSET_FIRST",
				initialSourceRefs: [{ sourceType: "CATALOG_TABLE", sourceId: "asset-1" }],
			}),
		);
		expect(onPlanCreated).toHaveBeenCalledWith("plan-created");
		expect(mocks.saveWarehousePlanSources).toHaveBeenCalledWith("plan-created", 1, [
			{
				bindingId: "binding-1",
				confirmationStatus: "CONFIRMED",
				exclusionReason: null,
				action: "CONFIRM",
			},
		]);
		expect(onSourcesChanged).toHaveBeenCalledWith([confirmedSource]);
	});

	it("reconfirms a stale source with the observed versions advertised by the server", async () => {
		const staleSource = {
			...confirmedSource,
			confirmedVersion: "schema-v1",
			resolvedVersion: "schema-v2",
			currentVersion: "schema-v2",
			freshness: "STALE" as const,
			changeImpact: "COMPATIBLE" as const,
			diffSummary: { added: 0, removed: 1, changed: 0 },
			changes: [{ field: "legacy_column", kind: "FIELD_REMOVED", impact: "COMPATIBLE" }],
			allowedActions: ["RECONFIRM" as const, "EXCLUDE" as const],
			reasonCode: "SOURCE_DRIFT_COMPATIBLE",
			statusSummary: "来源结构已更新，当前模型未引用受影响字段",
		};
		mocks.getWarehousePlanSources.mockResolvedValueOnce({
			bindings: [staleSource],
			readiness: "BLOCKED",
			issues: [{ code: "SOURCE_STALE", message: "The source changed", field: "bindings[0]" }],
			version: 7,
			etag: "sources:7",
			checkedAt: "2026-08-15T00:00:00Z",
		});
		mocks.saveWarehousePlanSources.mockResolvedValueOnce({
			bindings: [confirmedSource],
			readiness: "READY",
			issues: [],
			version: 8,
			etag: "sources:8",
			checkedAt: "2026-08-15T00:01:00Z",
		});
		const onSourcesChanged = vi.fn();
		await act(async () => {
			root.render(
				<ModelSourceInventoryDialog
					onClose={vi.fn()}
					onPlanCreated={vi.fn()}
					onSourcesChanged={onSourcesChanged}
					planId="plan-1"
				/>,
			);
		});
		await act(async () => undefined);

		expect(container.textContent).toContain("来源结构已更新，当前模型未引用受影响字段");
		expect(container.textContent).toContain("+0 / -1 / ~0");
		const reconfirm = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("采用新结构"),
		);
		expect(reconfirm).toBeDefined();
		await act(async () => {
			(reconfirm as HTMLButtonElement).click();
		});
		await act(async () => undefined);

		expect(mocks.saveWarehousePlanSources).toHaveBeenCalledWith("plan-1", 7, [
			{
				bindingId: "binding-1",
				confirmationStatus: "CONFIRMED",
				exclusionReason: null,
				action: "RECONFIRM",
				expectedConfirmedVersion: "schema-v1",
				expectedCurrentVersion: "schema-v2",
			},
		]);
		expect(onSourcesChanged).toHaveBeenCalledWith([confirmedSource]);
	});
});
