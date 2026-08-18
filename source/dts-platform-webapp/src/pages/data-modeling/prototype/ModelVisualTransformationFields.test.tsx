// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelVisualTransformationFields, visualTransformationInputAliases } from "./ModelVisualTransformationFields";
import { emptyModelDraft, type ModelSpecDraft, type ModelWorkbenchContext } from "./services/modelWorkbenchService";

beforeAll(() => {
	(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

let root: Root | null = null;
let container: HTMLDivElement | null = null;

afterEach(() => {
	if (root) act(() => root?.unmount());
	container?.remove();
	root = null;
	container = null;
});

const context = (): ModelWorkbenchContext =>
	({
		planId: "10000000-0000-0000-0000-000000000001",
		domains: [],
		models: [
			{ id: "30000000-0000-0000-0000-000000000001", name: "预算事实表" },
			{ id: "20000000-0000-0000-0000-000000000001", name: "日期维度表" },
		] as ModelSpecView[],
		dimensions: [],
		standards: [],
		dataMarts: [],
		subjectDomains: [],
		warehouseLayers: [],
		sources: [
			{
				bindingId: "50000000-0000-0000-0000-000000000002",
				displayName: "预算来源 B",
			},
			{
				bindingId: "50000000-0000-0000-0000-000000000001",
				displayName: "预算来源 A",
			},
		] as ModelWorkbenchContext["sources"],
	}) as ModelWorkbenchContext;

const draft = (): ModelSpecDraft => {
	const result = emptyModelDraft("fact", context()) as ModelSpecDraft;
	result.fields = [
		{ name: "record_id", dataType: "varchar", nullable: false, role: "KEY", securityLevel: "INTERNAL" },
		{ name: "amount", dataType: "decimal", nullable: false, role: "MEASURE", securityLevel: "INTERNAL" },
	];
	result.sourceRefs = [
		{
			kind: "TABLE",
			ref: "预算来源 B",
			layer: "ODS",
			role: "PRIMARY",
			alias: null,
			joinType: null,
			joinExpression: null,
			sortOrder: 0,
			sourceBindingId: "50000000-0000-0000-0000-000000000002",
			resolvedVersion: "source-v1",
		},
	];
	result.dimensionRefs = [{ modelSpecId: "20000000-0000-0000-0000-000000000001", revision: 2 }];
	return result;
};

describe("ModelVisualTransformationFields", () => {
	it("uses the same stable input ordering as the dependency snapshot", () => {
		const current = draft();
		current.sourceRefs.push({
			...current.sourceRefs[0],
			ref: "预算来源 A",
			sourceBindingId: "50000000-0000-0000-0000-000000000001",
		});
		current.dependsOn = [{ modelSpecId: "30000000-0000-0000-0000-000000000001", revision: 3 }];

		expect(visualTransformationInputAliases(current, context())).toEqual([
			{ index: 0, label: "预算来源 A", role: "基础来源" },
			{ index: 1, label: "预算来源 B", role: "基础来源" },
			{ index: 2, label: "日期维度表 · r2", role: "维度引用" },
			{ index: 3, label: "预算事实表 · r3", role: "上游模型" },
		]);
	});

	it("creates editable structured mappings without exposing a free SQL control", () => {
		container = document.createElement("div");
		document.body.append(container);
		root = createRoot(container);
		const current = draft();
		const onChange = vi.fn();
		act(() => root?.render(<ModelVisualTransformationFields context={context()} draft={current} onChange={onChange} />));

		expect(container.querySelector('[aria-label="来源字段 record_id"]')).not.toBeNull();
		expect(container.textContent).toContain("不接收自由 SQL");
		expect(container.querySelector('[aria-label*="SQL"]')).toBeNull();
		const autoMap = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("按名称一一映射"),
		);
		act(() => autoMap?.dispatchEvent(new MouseEvent("click", { bubbles: true })));

		expect(onChange).toHaveBeenCalledWith(
			expect.objectContaining({
				fieldMappings: [
					{ sourceField: "src_0.record_id", targetField: "record_id" },
					{ sourceField: "src_0.amount", targetField: "amount" },
				],
			}),
		);
	});

	it("keeps visual transformations read-only after dbt ownership is selected", () => {
		container = document.createElement("div");
		document.body.append(container);
		root = createRoot(container);
		const current = draft();
		current.implementationMode = "DBT_MANAGED";
		act(() => root?.render(<ModelVisualTransformationFields context={context()} draft={current} onChange={vi.fn()} />));

		expect(container.textContent).toContain("当前实现已由 dbt 代码维护");
		expect(container.querySelector('[aria-label="来源字段 record_id"]')).toBeNull();
	});
});
