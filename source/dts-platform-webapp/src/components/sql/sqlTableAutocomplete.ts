import type { TableInfo } from "@/api/sql-workbench";

export type TableAutocompleteContext = {
	start: number;
	end: number;
	query: string;
};

const TABLE_CONTEXT = /\b(?:from|join|update|into)\s+([\w.$]*)$/i;

export const resolveTableAutocompleteContext = (
	sqlText: string,
	cursorPosition: number,
): TableAutocompleteContext | null => {
	const beforeCursor = sqlText.slice(0, cursorPosition);
	const match = beforeCursor.match(TABLE_CONTEXT);
	if (!match) return null;
	const query = match[1] ?? "";
	return {
		start: cursorPosition - query.length,
		end: cursorPosition,
		query: query.toLowerCase(),
	};
};

export const filterTableSuggestions = (tables: TableInfo[], query: string): TableInfo[] => {
	const normalized = query.toLowerCase();
	return tables
		.filter((table) => {
			const qualifiedName = `${table.schema}.${table.name}`.toLowerCase();
			return !normalized || table.name.toLowerCase().includes(normalized) || qualifiedName.includes(normalized);
		})
		.slice(0, 12);
};
