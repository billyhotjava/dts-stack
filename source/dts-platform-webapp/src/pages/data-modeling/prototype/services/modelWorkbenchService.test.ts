// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelDraft } from "./modelWorkbenchService";
import { prepareModelDraftForSave, validateModelDraftInput } from "./modelWorkbenchService";

const validDimensionDraft = (): ModelDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "",
	domainId: "domain-1",
	name: "预算科目维度表",
	description: "",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [
		{
			name: "account_code",
			displayName: "科目编码",
			dataType: "STRING",
			nullable: false,
			role: "KEY",
			dimensionAttributeCode: "ACCOUNT_CODE",
		},
	],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
});

describe("model workbench draft preparation", () => {
	it("derives an empty dimension-table grain from the selected definition", () => {
		expect(
			prepareModelDraftForSave(validDimensionDraft(), [
				{ id: "dimension-1", name: "预算科目" } as DimensionDefinitionView,
			]),
		).toMatchObject({ grainStatement: "一个预算科目一行" });
	});

	it("preserves an explicitly entered dimension-table grain", () => {
		const existing = validDimensionDraft();
		existing.grainStatement = "一个科目版本一行";

		expect(prepareModelDraftForSave(existing, [])).toMatchObject({ grainStatement: "一个科目版本一行" });
	});
});

describe("model workbench draft validation", () => {
	it("reports invalid physical names and duplicate trimmed field names", () => {
		const invalid = validDimensionDraft();
		invalid.physicalName = "Bad-Name";
		invalid.fields.push({ ...invalid.fields[0], name: " account_code ", displayName: "重复字段" });

		expect(validateModelDraftInput(invalid)).toMatchObject({
			physicalName: "表名只能使用小写字母、数字和下划线，且必须以字母开头",
			fields: "字段名称不能重复",
		});
	});

	it("enforces prototype-specific dimension draft requirements", () => {
		const invalid = validDimensionDraft();
		invalid.domainId = "";
		invalid.dimensionDefinitionId = "";
		invalid.name = "dimension_table";
		invalid.fields = [{ ...invalid.fields[0], name: "", dataType: "", dimensionAttributeCode: "invalid-code" }];

		expect(validateModelDraftInput(invalid)).toMatchObject({
			domainId: "请选择数据域",
			dimensionDefinitionId: "请选择一个维度",
			name: "请填写中文表名",
			fields: "请补齐字段名称和数据类型",
		});
	});

	it("requires grain for non-dimension drafts", () => {
		const draft = validDimensionDraft();
		draft.createKind = "fact";
		draft.grainStatement = "";

		expect(validateModelDraftInput(draft)).toMatchObject({ grainStatement: "请填写模型粒度" });
	});
});
