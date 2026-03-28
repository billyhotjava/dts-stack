import type { FieldRef, MergedField } from "../notebookTypes";
import { fieldRefKey } from "../notebookTypes";

type Props = {
	fields: MergedField[];
	value: FieldRef | null;
	onChange: (ref: FieldRef | null) => void;
	placeholder?: string;
	disabled?: boolean;
	style?: React.CSSProperties;
};

export function FieldPicker({ fields, value, onChange, placeholder, disabled, style }: Props) {
	const grouped = new Map<string, MergedField[]>();
	for (const f of fields) {
		const key = f.tableName;
		const arr = grouped.get(key) ?? [];
		arr.push(f);
		grouped.set(key, arr);
	}

	const currentKey = value ? fieldRefKey(value) : "";

	return (
		<select
			className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
			style={style}
			value={currentKey}
			disabled={disabled}
			onChange={(e) => {
				const v = e.target.value;
				if (!v) { onChange(null); return; }
				const match = fields.find((f) => fieldRefKey({ fieldId: f.fieldId, joinAlias: f.tableAlias }) === v);
				if (match) onChange({ fieldId: match.fieldId, joinAlias: match.tableAlias });
				else onChange(null);
			}}
		>
			<option value="">{placeholder ?? "..."}</option>
			{Array.from(grouped.entries()).map(([tableName, tableFields]) => (
				<optgroup key={tableName} label={tableName}>
					{tableFields.map((f) => {
						const key = fieldRefKey({ fieldId: f.fieldId, joinAlias: f.tableAlias });
						return (
							<option key={key} value={key}>
								{f.tableName}.{f.displayName}
							</option>
						);
					})}
				</optgroup>
			))}
		</select>
	);
}
