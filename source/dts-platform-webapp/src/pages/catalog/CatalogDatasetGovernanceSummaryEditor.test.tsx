// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ getDataset: vi.fn(), updateDatasetGovernanceSummary: vi.fn() }));
vi.mock("@/api/platformApi", () => api);

import { CatalogDatasetGovernanceSummaryEditor } from "./CatalogDatasetGovernanceSummaryEditor";

let root: Root;
let container: HTMLDivElement;
const dataset = (id: string, version: number, owner = "原负责人") => ({ id, version, owner, description: `${id}说明` });
const flush = async () => act(async () => Promise.resolve());
const input = (label: string) => container.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)!;
const setInput = async (element: HTMLInputElement, value: string) => {
	await act(async () => {
		Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set?.call(element, value);
		element.dispatchEvent(new Event("input", { bubbles: true }));
		element.dispatchEvent(new Event("change", { bubbles: true }));
	});
};
const render = async (datasetId: string) => {
	await act(async () => root.render(<CatalogDatasetGovernanceSummaryEditor datasetId={datasetId} canMaintain />));
};

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
	vi.restoreAllMocks();
});

describe("CatalogDatasetGovernanceSummaryEditor", () => {
	it("saves with the version loaded for the dataset", async () => {
		api.getDataset.mockResolvedValue(dataset("dataset-a", 7));
		api.updateDatasetGovernanceSummary.mockResolvedValue(dataset("dataset-a", 8, "新负责人"));
		await render("dataset-a");
		await flush();
		await setInput(input("资产负责人"), "新负责人");
		await act(async () => (Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "保存") as HTMLButtonElement).click());
		expect(api.updateDatasetGovernanceSummary).toHaveBeenCalledWith(
			"dataset-a",
			{ owner: "新负责人", description: "dataset-a说明" },
			7,
		);
	});

	it("keeps the edited input after a version conflict", async () => {
		api.getDataset.mockResolvedValue(dataset("dataset-a", 7));
		api.updateDatasetGovernanceSummary.mockRejectedValue({ response: { status: 409 } });
		await render("dataset-a");
		await flush();
		await setInput(input("资产负责人"), "仍要保留");
		await act(async () => (Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "保存") as HTMLButtonElement).click());
		expect(input("资产负责人").value).toBe("仍要保留");
		expect(container.textContent).toContain("资产已被其他操作修改");
	});

	it("does not let a late load for A overwrite the active B dataset", async () => {
		let resolveA!: (value: unknown) => void;
		let resolveB!: (value: unknown) => void;
		api.getDataset.mockImplementation((id: string) => new Promise((resolve) => {
			if (id === "dataset-a") resolveA = resolve;
			else resolveB = resolve;
		}));
		await render("dataset-a");
		await render("dataset-b");
		await act(async () => resolveB(dataset("dataset-b", 3, "B负责人")));
		await flush();
		await act(async () => resolveA(dataset("dataset-a", 2, "A负责人")));
		await flush();
		expect(input("资产负责人").value).toBe("B负责人");
	});
});
