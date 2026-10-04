import type { ModelSpecDraft } from "./modelWorkbenchService";

/**
 * An explicitly configured aggregate output is a measure, not a grouping key.
 * Aggregations only exist for visually authored implementations, which stay visual after the
 * first commit switches the model to DBT_MANAGED; hand-written dbt models carry none, and a
 * code-authoritative draft keeps its visual settings only as a stale read-only reference.
 */
export function summaryMeasureFields(
	draft: Pick<ModelSpecDraft, "createKind" | "fields" | "aggregations"> & { codeAuthoritative?: boolean },
) {
	if (draft.createKind !== "summary" || draft.codeAuthoritative || !draft.aggregations.length) return draft.fields;
	const targets = new Set(draft.aggregations.map((item) => item.targetField.trim()));
	return draft.fields.map((field) => (targets.has(field.name.trim()) ? { ...field, role: "MEASURE" as const } : field));
}
