export const hasDuplicateModelFieldNames = (value: unknown): boolean => {
	if (!Array.isArray(value)) return false;
	const names = new Set<string>();
	for (const field of value) {
		if (!field || typeof field !== "object" || Array.isArray(field)) continue;
		const name = typeof (field as { name?: unknown }).name === "string" ? (field as { name: string }).name.trim() : "";
		if (!name) continue;
		if (names.has(name)) return true;
		names.add(name);
	}
	return false;
};

export const parseModelFieldNames = (value: string | null | undefined): string[] => {
	const seen = new Set<string>();
	return (value || "")
		.split(/[,，\n]/)
		.map((item) => item.trim())
		.filter((item) => Boolean(item) && !seen.has(item) && Boolean(seen.add(item)));
};

const normalizedNames = (values: readonly string[]): Set<string> =>
	new Set(values.map((value) => value.trim()).filter(Boolean));

export const isProtectedModelFieldName = (
	fieldName: string | null | undefined,
	persistedFieldNames: readonly string[],
	grainKeysText: string | null | undefined,
): boolean => {
	const normalized = fieldName?.trim();
	if (!normalized) return false;
	if (normalizedNames(persistedFieldNames).has(normalized)) return true;
	return new Set(parseModelFieldNames(grainKeysText)).has(normalized);
};
