import type { FieldRef, MergedField } from "../notebookTypes";
import { fieldRefKey, fieldRefEquals } from "../notebookTypes";
import { t, type Locale } from "../../../i18n";

type Props = {
	locale: Locale;
	allFields: MergedField[];
	selectedFields: FieldRef[];
	onSelectedFieldsChange: (fields: FieldRef[]) => void;
	isSummarized: boolean;
};

export function PickColumnsStep({ locale, allFields, selectedFields, onSelectedFieldsChange, isSummarized }: Props) {
	if (isSummarized) {
		return <div className="text-text-secondary">{t(locale, "notebook.columns.disabled")}</div>;
	}

	const toggle = (ref: FieldRef) => {
		const exists = selectedFields.some((s) => fieldRefEquals(s, ref));
		if (exists) {
			onSelectedFieldsChange(selectedFields.filter((s) => !fieldRefEquals(s, ref)));
		} else {
			onSelectedFieldsChange([...selectedFields, ref]);
		}
	};

	const selectAll = () => {
		onSelectedFieldsChange(allFields.map((f) => ({ fieldId: f.fieldId, joinAlias: f.tableAlias })));
	};

	const grouped = new Map<string, MergedField[]>();
	for (const f of allFields) {
		const arr = grouped.get(f.tableName) ?? [];
		arr.push(f);
		grouped.set(f.tableName, arr);
	}

	return (
		<div>
			<div className="flex justify-between items-center mb-2">
				<span className="text-text-secondary">{t(locale, "notebook.columns.selected")}: {selectedFields.length}</span>
				<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={selectAll}>{t(locale, "notebook.columns.selectAll")}</button>
			</div>
			{Array.from(grouped.entries()).map(([tableName, fields]) => (
				<div key={tableName} className="mb-2">
					<div className="text-text-secondary text-xs mb-1">{tableName}</div>
					<div className="flex flex-wrap gap-1">
						{fields.map((f) => {
							const ref: FieldRef = { fieldId: f.fieldId, joinAlias: f.tableAlias };
							const checked = selectedFields.some((s) => fieldRefEquals(s, ref));
							return (
								<label key={fieldRefKey(ref)} className="inline-flex items-center px-2 py-0.5 rounded-full text-sm font-medium border border-border-default bg-surface-muted text-text-secondary cursor-pointer select-none">
									<input
										type="checkbox"
										checked={checked}
										onChange={() => toggle(ref)}
										className="mr-1.5"
									/>
									{f.displayName}
								</label>
							);
						})}
					</div>
				</div>
			))}
		</div>
	);
}
