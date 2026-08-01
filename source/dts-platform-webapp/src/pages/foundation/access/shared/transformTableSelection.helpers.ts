export type TableSelectionMode = "all" | "manual";

export type ResolvedTableSelection = {
	selection: TableSelectionMode;
	includeTables: string[];
	excludeTables: string[];
};

export type TableMapping = {
	source: string;
	target: string;
};

const flattenTableEntries = (value: unknown): unknown[] =>
	Array.isArray(value) ? value.flatMap((item) => flattenTableEntries(item)) : [value];

export const parseTableEntries = (value: unknown): string[] => {
	const seen = new Set<string>();
	const entries: string[] = [];
	for (const raw of flattenTableEntries(value)) {
		for (const part of String(raw ?? "").split(/[\r\n,]+/)) {
			const table = part.trim();
			if (!table || seen.has(table)) continue;
			seen.add(table);
			entries.push(table);
		}
	}
	return entries;
};

const stripTableSchema = (table: string) => (table.includes(".") ? table.split(".").pop() || table : table);

const sourceAlignedTables = (sourceTables: string[], writerTables: string[]) => {
	if (!sourceTables.length || sourceTables.length !== writerTables.length) {
		return false;
	}
	return sourceTables.every((source, index) => {
		const writer = writerTables[index];
		return (
			source.toLowerCase() === writer.toLowerCase() ||
			stripTableSchema(source).toLowerCase() === stripTableSchema(writer).toLowerCase()
		);
	});
};

const applyTablePrefix = (tables: string[], prefix?: unknown) => {
	const normalizedPrefix = String(prefix ?? "").trim();
	return tables.map((table) => `${normalizedPrefix}${stripTableSchema(table)}`);
};

export const resolveWriterTables = ({
	sourceTables,
	explicitTables,
	existingTables,
	prefix,
}: {
	sourceTables?: unknown;
	explicitTables?: unknown;
	existingTables?: unknown;
	prefix?: unknown;
}): string[] => {
	const sources = parseTableEntries(sourceTables);
	if (!sources.length) return [];
	const explicit = parseTableEntries(explicitTables);
	if (explicit.length) {
		return sourceAlignedTables(sources, explicit) ? applyTablePrefix(sources, prefix) : explicit;
	}
	const existing = parseTableEntries(existingTables);
	if (!existing.length || sourceAlignedTables(sources, existing)) {
		return applyTablePrefix(sources, prefix);
	}
	return existing;
};

export const buildTableMapping = (sourceTables?: unknown, targetTables?: unknown): TableMapping[] => {
	const sources = parseTableEntries(sourceTables);
	const targets = parseTableEntries(targetTables);
	const mappedSources = targets.length ? sources.slice(0, targets.length) : sources;
	return mappedSources.map((source, index) => ({
		source,
		target: targets[index] || stripTableSchema(source),
	}));
};

export const resolveTableSelection = ({
	mode,
	selectedTables,
	fallbackTables,
	excludeTables,
}: {
	mode?: unknown;
	selectedTables?: unknown;
	fallbackTables?: unknown;
	excludeTables?: unknown;
}): ResolvedTableSelection => {
	const selection: TableSelectionMode = mode === "manual" ? "manual" : "all";
	if (selection === "all") {
		return {
			selection,
			includeTables: [],
			excludeTables: parseTableEntries(excludeTables),
		};
	}
	const canonicalTables = parseTableEntries(selectedTables);
	return {
		selection,
		includeTables: canonicalTables.length ? canonicalTables : parseTableEntries(fallbackTables),
		excludeTables: [],
	};
};
