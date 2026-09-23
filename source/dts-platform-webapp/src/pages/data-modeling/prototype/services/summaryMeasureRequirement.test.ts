// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
	emptyModelDraft,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
	validateModelDraftInput,
} from "./modelWorkbenchService";

const context = {
	domains: [],
	implementationCapabilities: {
		inputModesByModelType: { SUMMARY: ["UPSTREAM_MODEL"] },
		loadStrategies: ["FULL"],
		materializationsByLoadStrategy: { FULL: ["table"] },
	},
} as unknown as ModelWorkbenchContext;

/** A summary whose processing only copies/deduplicates upstream rows, as reported in S10DC-111. */
const dedupOnlySummary = (): ModelSpecDraft => {
	const draft = emptyModelDraft("summary", context) as ModelSpecDraft;
	draft.implementationMode = "DESIGNER_GENERATED";
	draft.implementationInputMode = "UPSTREAM_MODEL";
	draft.dependsOn = [{ modelSpecId: "60000000-0000-0000-0000-000000000001", revision: 3 }];
	draft.fields = [
		{ name: "risk_no", dataType: "text", nullable: false, role: "KEY" },
		{ name: "risk_level", dataType: "text", nullable: true, role: "ATTRIBUTE" },
	];
	draft.fieldMappings = [
		{ sourceField: "risk_no", targetField: "risk_no" },
		{ sourceField: "risk_level", targetField: "risk_level" },
	];
	draft.deduplicateBy = ["risk_no"];
	draft.groupBy = [];
	draft.aggregations = [];
	return draft;
};

const SUMMARY_GUIDANCE = /汇总表需要配置分组和至少一个聚合.*明细表（DWD）/;

describe("summary measure requirement at processing save", () => {
	it("explains how to fix a summary that neither aggregates nor declares a measure", () => {
		expect(validateModelDraftInput(dedupOnlySummary(), context.implementationCapabilities).transformations).toMatch(
			SUMMARY_GUIDANCE,
		);
	});

	it("accepts a summary with a grouped aggregation", () => {
		const draft = dedupOnlySummary();
		draft.deduplicateBy = [];
		draft.groupBy = ["risk_level"];
		draft.fields = [
			{ name: "risk_level", dataType: "text", nullable: true, role: "KEY" },
			{ name: "risk_count", dataType: "numeric", nullable: false, role: "ATTRIBUTE" },
		];
		draft.fieldMappings = [{ sourceField: "risk_level", targetField: "risk_level" }];
		draft.aggregations = [{ targetField: "risk_count", sourceField: "risk_no", function: "COUNT", distinct: false }];
		expect(validateModelDraftInput(draft, context.implementationCapabilities).transformations ?? "").not.toMatch(
			SUMMARY_GUIDANCE,
		);
	});

	it("accepts a summary whose author marked a measure field explicitly", () => {
		const draft = dedupOnlySummary();
		draft.fields = [...draft.fields.slice(0, 1), { ...draft.fields[1], role: "MEASURE" }];
		expect(validateModelDraftInput(draft, context.implementationCapabilities).transformations ?? "").not.toMatch(
			SUMMARY_GUIDANCE,
		);
	});

	it("does not block the first design save before processing is configured", () => {
		const draft = dedupOnlySummary();
		draft.fieldMappings = [];
		draft.deduplicateBy = [];
		expect(validateModelDraftInput(draft, context.implementationCapabilities).transformations ?? "").not.toMatch(
			SUMMARY_GUIDANCE,
		);
	});

	it("leaves code-authoritative summaries to the field roles", () => {
		const draft = { ...dedupOnlySummary(), codeAuthoritative: true };
		expect(validateModelDraftInput(draft, context.implementationCapabilities).transformations ?? "").not.toMatch(
			SUMMARY_GUIDANCE,
		);
	});
});
