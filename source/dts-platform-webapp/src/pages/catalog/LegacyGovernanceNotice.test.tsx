// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { listDomains, updateDataset, successMessage } = vi.hoisted(() => ({
	listDomains: vi.fn(),
	updateDataset: vi.fn(),
	successMessage: vi.fn(),
}));

vi.mock("@/api/platformApi", () => ({
	getCatalogAssetV2Lineage: vi.fn(),
	getCatalogLineageImpact: vi.fn(),
	getDatasetFields: vi.fn(),
	getDatasetGovernanceHealth: vi.fn(),
	getDatasetIndicatorDeps: vi.fn(),
	listDomains,
	syncCatalogAssetV2Lineage: vi.fn(),
	updateDataset,
}));

vi.mock("@/components/lineage", () => ({
	LineageGraph: () => null,
}));

vi.mock("@/components/table", () => ({
	CompactTable: () => null,
}));

vi.mock("@/routes/hooks", () => ({
	useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const Descriptions = ({ children }: any) => React.createElement("dl", null, children);
	Descriptions.Item = ({ children, label }: any) =>
		React.createElement("div", null, React.createElement("dt", null, label), React.createElement("dd", null, children));
	return {
		Alert: ({ message, description }: any) =>
			React.createElement("div", { role: "alert" }, React.createElement("strong", null, message), description),
		Button: ({ children, disabled, loading, onClick }: any) =>
			React.createElement("button", { type: "button", disabled: disabled || loading, onClick }, children),
		Descriptions,
		message: { success: successMessage },
		Select: ({ disabled, onChange, options = [], value, "aria-label": ariaLabel }: any) =>
			React.createElement(
				"select",
				{
					"aria-label": ariaLabel,
					disabled,
					value: value || "",
					onChange: (event: any) => onChange(event.currentTarget.value),
				},
				[
					React.createElement("option", { key: "", value: "" }, "请选择主题域"),
					...options.map((option: any) =>
						React.createElement("option", { key: option.value, value: option.value }, option.label),
					),
				],
			),
		Spin: () => React.createElement("span", null, "加载中"),
		Tag: ({ children }: any) => React.createElement("span", null, children),
	};
});

async function flush() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function renderAndFlush(ui: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flush();
	return {
		container,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

const DATASET = {
	id: "dataset-1",
	name: "ods_test0521_dev",
	domainId: null,
	classification: "INTERNAL",
	warehouseLayer: "ODS",
	owner: "biadmin",
	ownerDept: "",
	enabled: true,
	editable: true,
	type: "POSTGRESQL",
	hiveDatabase: "public",
	hiveTable: "ods_test0521_dev",
};

describe("LegacyGovernanceNotice", () => {
	beforeEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
		listDomains.mockResolvedValue({
			content: [
				{ id: "domain-finance", name: "财务域", code: "S10-FIN" },
				{ id: "domain-project", name: "项目管理域", code: "S10-PM" },
			],
		});
		updateDataset.mockResolvedValue(DATASET);
	});

	afterEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("lets an editable legacy asset select and save its subject domain", async () => {
		const { LegacyGovernanceNotice } = await import("./DatasetDetailSupportTabs");
		const LegacyGovernanceEditor = LegacyGovernanceNotice as any;
		const onChanged = vi.fn();
		const { container, unmount } = await renderAndFlush(
			<LegacyGovernanceEditor dataset={DATASET} onChanged={onChanged} />,
		);

		const select = container.querySelector("select[aria-label='设置主题域']") as HTMLSelectElement;
		expect(select).not.toBeNull();
		expect(Array.from(select.options).map((option) => option.textContent)).toContain("财务域");

		await act(async () => {
			select.value = "domain-finance";
			select.dispatchEvent(new Event("change", { bubbles: true }));
		});
		const save = Array.from(container.querySelectorAll("button")).find((button) => button.textContent === "保存主题域");
		expect(save).toBeDefined();
		await act(async () => save?.click());
		await flush();

		expect(updateDataset).toHaveBeenCalledWith(
			"dataset-1",
			expect.objectContaining({
				name: "ods_test0521_dev",
				domain: { id: "domain-finance" },
				classification: "INTERNAL",
				warehouseLayer: "ODS",
				enabled: true,
			}),
		);
		expect(onChanged).toHaveBeenCalledWith(
			expect.objectContaining({
				domainId: "domain-finance",
				domainName: "财务域",
			}),
		);
		expect(successMessage).toHaveBeenCalledWith("主题域已保存");
		unmount();
	});
});
