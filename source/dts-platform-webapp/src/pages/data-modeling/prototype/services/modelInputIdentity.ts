import type { ModelSpecDraft } from "./modelWorkbenchService";

export const modelInputIds = (draft: ModelSpecDraft): string[] => {
	const chosen =
		draft.implementationInputMode === "PHYSICAL_ASSET"
			? draft.sourceRefs.map((source) => `source:${source.sourceBindingId}`)
			: draft.implementationInputMode === "UPSTREAM_MODEL"
				? draft.dependsOn.map((source) => `model:${source.modelSpecId}`)
				: [];
	const persisted = (draft.authoringImplementationInputs || draft.implementationBase?.inputs || []).flatMap((input) =>
		"sourceBindingId" in input
			? [`source:${input.sourceBindingId}`]
			: "modelSpecId" in input
				? [`model:${input.modelSpecId}`]
				: [],
	);
	return [...persisted.filter((id) => chosen.includes(id)), ...chosen.filter((id) => !persisted.includes(id))];
};

/** Preserve semantic source identity when indices change. Removed references remain explicitly invalid. */
export const reconcileModelInputIdentity = (before: ModelSpecDraft, after: ModelSpecDraft): ModelSpecDraft => {
	const previous = modelInputIds(before);
	const next = modelInputIds(after);
	if (JSON.stringify(previous) === JSON.stringify(next)) return after;
	const remap = (field: string): string => {
		const match = /^src_(\d+)\.([A-Za-z_][A-Za-z0-9_]*)$/.exec(field);
		const index = match ? Number(match[1]) : previous.length === 1 && /^[A-Za-z_][A-Za-z0-9_]*$/.test(field) ? 0 : null;
		if (index === null) return field;
		const replacement = previous[index] ? next.indexOf(previous[index]) : -1;
		return `src_${replacement < 0 ? 2147483647 : replacement}.${match ? match[2] : field}`;
	};
	return {
		...after,
		fieldMappings: after.fieldMappings.map((mapping) => ({ ...mapping, sourceField: remap(mapping.sourceField) })),
		aggregations: after.aggregations.map((aggregation) => ({
			...aggregation,
			sourceField: before.fieldMappings.some((mapping) => mapping.targetField === aggregation.sourceField)
				? aggregation.sourceField
				: remap(aggregation.sourceField),
		})),
		filters: after.filters.map((filter) => ({
			...filter,
			field: before.fieldMappings.some((mapping) => mapping.targetField === filter.field)
				? filter.field
				: remap(filter.field),
		})),
		joins: after.joins.flatMap((join) => {
			const newIndex = previous[join.inputIndex] ? next.indexOf(previous[join.inputIndex]) : -1;
			return newIndex > 0
				? [{ ...join, inputIndex: newIndex, leftField: remap(join.leftField), rightField: remap(join.rightField) }]
				: [];
		}),
	};
};
