function normalizeTagIds(tagIds: string[]): string[] {
	const result: string[] = [];
	const seen = new Set<string>();
	for (const id of tagIds) {
		const value = id.trim();
		if (!value || seen.has(value)) continue;
		seen.add(value);
		result.push(value);
	}
	return result;
}

export function readTagIds(params: URLSearchParams): string[] {
	return normalizeTagIds(params.getAll("tagIds"));
}

export function writeTagIds(params: URLSearchParams, tagIds: string[]): URLSearchParams {
	const next = new URLSearchParams(params);
	next.delete("tagIds");
	for (const id of normalizeTagIds(tagIds)) {
		next.append("tagIds", id);
	}
	return next;
}
