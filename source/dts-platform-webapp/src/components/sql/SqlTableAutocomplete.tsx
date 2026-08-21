import type { FC } from "react";
import type { TableInfo } from "@/api/sql-workbench";
import { filterTableSuggestions, resolveTableAutocompleteContext } from "./sqlTableAutocomplete";

type Props = {
	sqlText: string;
	cursorPosition: number;
	tables: TableInfo[];
	onSelect: (table: TableInfo, context: { start: number; end: number }) => void;
};

export const SqlTableAutocomplete: FC<Props> = ({ sqlText, cursorPosition, tables, onSelect }) => {
	const context = resolveTableAutocompleteContext(sqlText, cursorPosition);
	if (!context || tables.length === 0) return null;
	const suggestions = filterTableSuggestions(tables, context.query);
	if (suggestions.length === 0) return null;

	return (
		<div
			aria-label="表名自动提示"
			className="absolute left-10 top-8 z-20 max-h-52 w-80 overflow-y-auto rounded border bg-popover p-1 text-xs shadow-lg"
		>
			<div className="px-2 py-1 text-muted-foreground">当前数据源表名</div>
			{suggestions.map((table) => (
				<button
					className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left hover:bg-muted"
					key={`${table.schema}.${table.name}`}
					onMouseDown={(event) => {
						event.preventDefault();
						onSelect(table, context);
					}}
					type="button"
				>
					<span className="truncate font-mono">{table.schema}.{table.name}</span>
					<span className="ml-auto text-muted-foreground">表</span>
				</button>
			))}
		</div>
	);
};
