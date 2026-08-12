// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	getWarehousePlanSources: vi.fn(),
	saveWarehousePlanSources: vi.fn(),
	listCatalogAssetsV2: vi.fn(),
	listTablesByDataset: vi.fn(),
}));

vi.mock("@/api/warehousePlanApi", () => ({
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
			root.render(<ModelSourceInventoryDialog onClose={vi.fn()} onSourcesChanged={onSourcesChanged} planId="plan-1" />);
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
});
