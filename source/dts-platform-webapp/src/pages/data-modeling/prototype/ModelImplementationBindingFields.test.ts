// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";
import { applyFactTimeFieldSelection } from "./ModelImplementationBindingFields";

const factDraft = (): ModelSpecDraft =>
	({
		fields: [
			{
				name: "snapshot_date",
				displayName: "快照日期",
				dataType: "DATE",
				nullable: false,
				role: "ATTRIBUTE",
			},
		],
		timeSemanticsFields: [],
	}) as ModelSpecDraft;

describe("fact time field selection", () => {
	it("records an explicit selection and synchronizes the field semantic role", () => {
		const selected = applyFactTimeFieldSelection(factDraft(), 0, true);

		expect(selected.fields[0].role).toBe("TIME");
		expect(selected.timeSemanticsFields).toEqual(["snapshot_date"]);
	});

	it("removes the binding without guessing a replacement semantic role", () => {
		const selected = applyFactTimeFieldSelection(factDraft(), 0, true);
		const cleared = applyFactTimeFieldSelection(selected, 0, false);

		expect(cleared.fields[0].role).toBe("TIME");
		expect(cleared.timeSemanticsFields).toEqual([]);
	});
});

describe("time selection preserves the data contract after guidance moves", () => {
	it("changes the semantic role without changing type or requiring selection", () => {
		const original = factDraft();
		const selected = applyFactTimeFieldSelection(original, 0, true);
		expect(selected.fields[0].dataType).toBe(original.fields[0].dataType);
		expect(original.timeSemanticsFields).toEqual([]);
		expect(original.fields[0].role).toBe("ATTRIBUTE");
	});
});
