// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MetricEditor, reconcileMetricBusinessProcessContext, resolveMetricCatalogSelection } from "./MetricsPage";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("MetricEditor stable business context", () => {
	it("writes the business-process row UUID and exposes version-pinned model selectors", async () => {
		const onChange = vi.fn();
		await act(async () =>
			root.render(
				<MetricEditor
					businessCategories={[
						{
							id: "category-1",
							code: "finance",
							name: "财务",
							owner: "",
							description: "",
							parentId: null,
							parentCode: null,
						},
					]}
					codeLocked={false}
					dataDomains={[
						{
							id: "domain-1",
							code: "budget",
							name: "预算域",
							owner: "",
							description: "",
							parentId: "category-1",
							parentCode: "finance",
						},
					]}
					onChange={onChange}
					metricModels={[
						{
							contractVersion: 2,
							compatibilityMode: "CANONICAL",
							id: "model-1",
							name: "预算事实表",
							status: "PUBLISHED",
							revision: 7,
							modelType: "FACT",
							layer: "DWD",
							fields: [
								{ name: "amount", displayName: "预算金额", dataType: "DECIMAL", nullable: false, role: "MEASURE" },
							],
						} as never,
					]}
					processes={[
						{
							id: "process-row-1",
							version: 1,
							processId: "budget_execution",
							domainId: "domain-1",
							name: "预算执行",
							sourceType: "MANUAL",
							confirmed: true,
							lifecycleStatus: "ACTIVE",
						},
						{
							id: "process-row-2",
							version: 1,
							processId: "budget_adjustment",
							domainId: "domain-1",
							name: "预算调整",
							sourceType: "MANUAL",
							confirmed: true,
							lifecycleStatus: "ACTIVE",
						},
					]}
					values={{
						code: "BUDGET_AMOUNT",
						name: "预算金额",
						metricType: "ATOMIC",
						businessCategoryId: "category-1",
						dataDomainId: "domain-1",
						measureField: "amount",
						sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION", sourceId: "model-1", sourceVersion: "r7" }],
					}}
				/>,
			),
		);

		const processSelect = Array.from(container.querySelectorAll("select")).find((item) =>
			item.querySelector('option[value="process-row-1"]'),
		);
		expect(processSelect).toBeDefined();
		await act(async () => {
			const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")?.set;
			setter?.call(processSelect, "process-row-1");
			processSelect?.dispatchEvent(new Event("change", { bubbles: true }));
		});

		expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ businessProcessId: "process-row-1" }));
		expect(container.querySelector<HTMLSelectElement>('select[aria-label="业务分类"]')?.value).toBe("category-1");
		expect(container.querySelector<HTMLSelectElement>('select[aria-label="来源模型"]')?.value).toBe("model-1@r7");
		expect(container.querySelector<HTMLSelectElement>('select[aria-label="度量字段"]')?.value).toBe("amount");
		expect(container.textContent).toContain("预算事实表（DWD · r7）");
	});
});

describe("metric catalog table selection", () => {
	const rows = [
		{ id: "metric-1", code: "BUDGET_AMOUNT", name: "预算金额", domain: "finance" },
		{ id: "metric-2", code: "BUDGET_RATE", name: "预算执行率", domain: "finance" },
	] as never;

	it("keeps the table visible until the user chooses a row", () => {
		expect(resolveMetricCatalogSelection(rows, "")).toBeNull();
		expect(resolveMetricCatalogSelection(rows, "missing")).toBeNull();
	});

	it("opens the exact indicator requested by a deep link", () => {
		expect(resolveMetricCatalogSelection(rows, "metric-2")).toMatchObject({
			id: "metric-2",
			code: "BUDGET_RATE",
			name: "预算执行率",
		});
	});
});

describe("metric business-process context reconciliation", () => {
	it("preserves the common process inherited from derived indicator dependencies", () => {
		const values = {
			metricType: "DERIVED",
			businessProcessId: "process-row-1",
		};

		expect(reconcileMetricBusinessProcessContext(values, [])).toBe(values);
	});
});
