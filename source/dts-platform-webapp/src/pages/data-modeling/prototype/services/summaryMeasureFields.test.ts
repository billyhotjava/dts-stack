// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
	emptyModelDraft,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
	modelDraftToUpdateCommand,
} from "./modelWorkbenchService";
import { summaryMeasureFields } from "./summaryMeasureFields";

const newSummary = () =>
	emptyModelDraft("summary", {
		domains: [],
		implementationCapabilities: {
			inputModesByModelType: { SUMMARY: ["UPSTREAM_MODEL"] },
			loadStrategies: ["FULL"],
			materializationsByLoadStrategy: { FULL: ["table"] },
		},
	} as ModelWorkbenchContext) as ModelSpecDraft;

describe("summary aggregate measures", () => {
	it.each(["SUM", "COUNT", "AVG", "MIN", "MAX"] as const)(
		"saves %s output as measure while preserving grouping fields",
		(functionName) => {
			const draft = newSummary();
			draft.implementationMode = "DESIGNER_GENERATED";
			draft.fields = [
				{ name: "dept", dataType: "varchar", nullable: false, role: "KEY" },
				{ name: "total", dataType: "decimal", nullable: true, role: "ATTRIBUTE" },
			];
			draft.aggregations = [
				{ targetField: "total", sourceField: "src_0.amount", function: functionName, distinct: false },
			];
			const saved = modelDraftToUpdateCommand(draft);
			expect(saved.fields.map((field) => field.role)).toEqual(["KEY", "MEASURE"]);
			expect(saved.grain.keys).toEqual(["dept"]);
			expect(draft.fields[1].role).toBe("ATTRIBUTE");
		},
	);

	it("removes an aggregate target from logical grain keys", () => {
		const draft = newSummary();
		draft.implementationMode = "DESIGNER_GENERATED";
		draft.fields = [{ name: "count", dataType: "bigint", nullable: false, role: "KEY" }];
		draft.aggregations = [{ targetField: "count", sourceField: "src_0.id", function: "COUNT", distinct: false }];
		expect(modelDraftToUpdateCommand(draft).grain.keys).toEqual([]);
	});

	it("does not invent measures from numeric types or rewrite external models", () => {
		const draft = newSummary();
		draft.implementationMode = "DESIGNER_GENERATED";
		draft.fields = [{ name: "year", dataType: "integer", nullable: true, role: "ATTRIBUTE" }];
		expect(summaryMeasureFields(draft)[0].role).toBe("ATTRIBUTE");
		draft.aggregations = [{ targetField: "year", sourceField: "src_0.year", function: "MAX", distinct: false }];
		draft.implementationMode = "DBT_MANAGED";
		expect(summaryMeasureFields(draft)[0].role).toBe("ATTRIBUTE");
		draft.implementationMode = "DESIGNER_GENERATED";
		draft.createKind = "fact";
		expect(summaryMeasureFields(draft)[0].role).toBe("ATTRIBUTE");
	});
});
