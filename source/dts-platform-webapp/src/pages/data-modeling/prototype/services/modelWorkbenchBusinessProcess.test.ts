// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { emptyModelDraft, type ModelSpecDraft, validateModelDraftInput } from "./modelWorkbenchService";

describe("FACT business-process context", () => {
	it("starts empty and blocks a FACT draft until a stable process is resolved", () => {
		const draft = emptyModelDraft("fact", {
			planId: "plan-1",
			domains: [{ id: "domain-1", code: "PROJECT", name: "项目域", parentCode: "INSTITUTE" }],
			models: [],
			dimensions: [],
			standards: [],
			warehouseLayers: [{ code: "DWD", name: "明细层", systemLayerCode: "DWD", builtin: true }],
			sources: [],
		}) as ModelSpecDraft;

		expect(draft.businessProcessId).toBe("");
		expect(validateModelDraftInput(draft).businessProcessId).toBe("请选择业务过程");

		draft.businessProcessId = "00000000-0000-0000-0000-000000000087";
		expect(validateModelDraftInput(draft).businessProcessId).toBeUndefined();
	});

	it("does not require a process for dimensions, summaries or application models", () => {
		for (const kind of ["dimension-table", "summary", "application"] as const) {
			const draft = emptyModelDraft(kind, {
				planId: "plan-1",
				domains: [{ id: "domain-1", code: "PROJECT", name: "项目域", parentCode: "INSTITUTE" }],
				models: [],
				dimensions: [],
				standards: [],
				warehouseLayers: [],
				sources: [],
			}) as ModelSpecDraft;
			expect(validateModelDraftInput(draft).businessProcessId).toBeUndefined();
		}
	});
});
