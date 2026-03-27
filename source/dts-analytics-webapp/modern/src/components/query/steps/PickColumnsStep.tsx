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
		return <div className="muted">{t(locale, "notebook.columns.disabled")}</div>;
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
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
				<span className="muted">{t(locale, "notebook.columns.selected")}: {selectedFields.length}</span>
				<button className="btn" type="button" onClick={selectAll}>{t(locale, "notebook.columns.selectAll")}</button>
			</div>
			{Array.from(grouped.entries()).map(([tableName, fields]) => (
				<div key={tableName} style={{ marginBottom: 8 }}>
					<div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>{tableName}</div>
					<div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
						{fields.map((f) => {
							const ref: FieldRef = { fieldId: f.fieldId, joinAlias: f.tableAlias };
							const checked = selectedFields.some((s) => fieldRefEquals(s, ref));
							return (
								<label key={fieldRefKey(ref)} className="tag" style={{ cursor: "pointer", userSelect: "none" }}>
									<input
										type="checkbox"
										checked={checked}
										onChange={() => toggle(ref)}
										style={{ marginRight: 6 }}
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
