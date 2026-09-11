import type { ModelInputFieldSource } from "@/api/modelInputInspectionApi";

export type VisualFieldMapping = { sourceField: string; targetField: string };

/** `alias.field` when exactly one input exposes a field with this name; ambiguous or missing names stay unmapped. */
export const uniqueSourceField = (fieldName: string, sources: ModelInputFieldSource[]): string | null => {
	const matches = sources.filter((source) => source.fields.some((candidate) => candidate.name === fieldName));
	return matches.length === 1 ? `${matches[0].alias}.${fieldName}` : null;
};

/** Fills only unmapped target fields by unique name match; returns null when nothing would change. */
export const defaultNameMappings = (
	fieldNames: string[],
	sources: ModelInputFieldSource[],
	mappings: VisualFieldMapping[],
): VisualFieldMapping[] | null => {
	const additions = fieldNames.flatMap((targetField) => {
		if (mappings.some((mapping) => mapping.targetField === targetField)) return [];
		const sourceField = uniqueSourceField(targetField, sources);
		return sourceField ? [{ sourceField, targetField }] : [];
	});
	return additions.length ? [...mappings, ...additions] : null;
};
