import type { ModelSpecDraft } from "./modelWorkbenchService";

/** An explicitly configured aggregate output is a measure, not a grouping key. */
export function summaryMeasureFields(
	draft: Pick<ModelSpecDraft, "createKind" | "implementationMode" | "fields" | "aggregations">,
) {
	if (draft.createKind !== "summary" || draft.implementationMode !== "DESIGNER_GENERATED") return draft.fields;
	const targets = new Set(draft.aggregations.map((item) => item.targetField.trim()));
	return draft.fields.map((field) => (targets.has(field.name.trim()) ? { ...field, role: "MEASURE" as const } : field));
}
