import type { AggregationRow, AggregationOp, MergedField, FieldRef } from "../notebookTypes";
import { makeId, fieldRefKey, fieldRefEquals } from "../notebookTypes";
import { FieldPicker } from "../shared/FieldPicker";
import { t, type Locale } from "../../../i18n";

type Props = {
	locale: Locale;
	allFields: MergedField[];
	aggregations: AggregationRow[];
	groupByFields: FieldRef[];
	onAggregationsChange: (rows: AggregationRow[]) => void;
	onGroupByFieldsChange: (fields: FieldRef[]) => void;
};

export function SummarizeStep({
	locale,
	allFields,
	aggregations,
	groupByFields,
	onAggregationsChange,
	onGroupByFieldsChange,
}: Props) {
	// --- group-by helpers ---
	const toggleGroupBy = (ref: FieldRef) => {
		const exists = groupByFields.some((g) => fieldRefEquals(g, ref));
		if (exists) {
			onGroupByFieldsChange(groupByFields.filter((g) => !fieldRefEquals(g, ref)));
		} else {
			onGroupByFieldsChange([...groupByFields, ref]);
		}
	};

	// --- aggregation helpers ---
	const addAggregation = () => {
		const row: AggregationRow = { id: makeId(), op: "count", field: null };
		onAggregationsChange([...aggregations, row]);
	};

	const updateAggregation = (id: string, patch: Partial<Pick<AggregationRow, "op" | "field">>) => {
		onAggregationsChange(aggregations.map((r) => (r.id === id ? { ...r, ...patch } : r)));
	};

	const removeAggregation = (id: string) => {
		onAggregationsChange(aggregations.filter((r) => r.id !== id));
	};

	// group allFields by table name for the group-by checkboxes
	const grouped = new Map<string, MergedField[]>();
	for (const f of allFields) {
		const arr = grouped.get(f.tableName) ?? [];
		arr.push(f);
		grouped.set(f.tableName, arr);
	}

	return (
		<div>
			{/* header */}
			<div className="row" style={{ justifyContent: "space-between" }}>
				<strong>{t(locale, "builder.summarize")}</strong>
				<button className="btn" type="button" onClick={addAggregation}>
					{t(locale, "builder.addAggregation")}
				</button>
			</div>
			<div style={{ height: 8 }} />

			{/* group-by section */}
			<div className="muted">{t(locale, "builder.groupBy")}</div>
			<div style={{ height: 8 }} />

			{allFields.length === 0 ? (
				<div className="muted">—</div>
			) : (
				<div>
					{Array.from(grouped.entries()).map(([tableName, fields]) => (
						<div key={tableName} style={{ marginBottom: 8 }}>
							<div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>{tableName}</div>
							<div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
								{fields.map((f) => {
									const ref: FieldRef = { fieldId: f.fieldId, joinAlias: f.tableAlias };
									const checked = groupByFields.some((g) => fieldRefEquals(g, ref));
									return (
										<label
											key={fieldRefKey(ref)}
											className="tag"
											style={{ cursor: "pointer", userSelect: "none" }}
										>
											<input
												type="checkbox"
												checked={checked}
												onChange={() => toggleGroupBy(ref)}
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
			)}

			<div style={{ height: 12 }} />

			{/* aggregations section */}
			<div className="row" style={{ justifyContent: "space-between" }}>
				<div className="muted">{t(locale, "builder.aggregations")}</div>
				<div className="muted">{aggregations.length ? `${aggregations.length}` : "—"}</div>
			</div>
			<div style={{ height: 8 }} />

			{aggregations.length === 0 ? <div className="muted">—</div> : null}
			{aggregations.map((r) => (
				<div key={r.id} className="row" style={{ marginBottom: 8 }}>
					<select
						className="input"
						style={{ width: 200 }}
						value={r.op}
						onChange={(e) => updateAggregation(r.id, { op: e.target.value as AggregationOp })}
					>
						<option value="count">{t(locale, "builder.agg.count")}</option>
						<option value="sum">{t(locale, "builder.agg.sum")}</option>
						<option value="avg">{t(locale, "builder.agg.avg")}</option>
						<option value="min">{t(locale, "builder.agg.min")}</option>
						<option value="max">{t(locale, "builder.agg.max")}</option>
					</select>

					<FieldPicker
						fields={allFields}
						value={r.field}
						onChange={(ref) => updateAggregation(r.id, { field: ref })}
						placeholder={t(locale, "builder.agg.rows")}
						style={{ width: 360 }}
					/>

					<button className="btn" type="button" onClick={() => removeAggregation(r.id)}>
						{t(locale, "builder.remove")}
					</button>
				</div>
			))}
		</div>
	);
}
