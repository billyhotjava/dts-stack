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
			<div className="flex gap-3 flex-wrap items-center justify-between">
				<strong>{t(locale, "builder.summarize")}</strong>
				<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={addAggregation}>
					{t(locale, "builder.addAggregation")}
				</button>
			</div>
			<div className="h-2" />

			{/* group-by section */}
			<div className="text-text-secondary">{t(locale, "builder.groupBy")}</div>
			<div className="h-2" />

			{allFields.length === 0 ? (
				<div className="text-text-secondary">—</div>
			) : (
				<div>
					{Array.from(grouped.entries()).map(([tableName, fields]) => (
						<div key={tableName} className="mb-2">
							<div className="text-text-secondary text-xs mb-1">{tableName}</div>
							<div className="flex flex-wrap gap-1">
								{fields.map((f) => {
									const ref: FieldRef = { fieldId: f.fieldId, joinAlias: f.tableAlias };
									const checked = groupByFields.some((g) => fieldRefEquals(g, ref));
									return (
										<label
											key={fieldRefKey(ref)}
											className="inline-flex items-center px-2 py-0.5 rounded-full text-sm font-medium border border-border-default bg-surface-muted text-text-secondary cursor-pointer select-none"
										>
											<input
												type="checkbox"
												checked={checked}
												onChange={() => toggleGroupBy(ref)}
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
			)}

			<div className="h-3" />

			{/* aggregations section */}
			<div className="flex gap-3 flex-wrap items-center justify-between">
				<div className="text-text-secondary">{t(locale, "builder.aggregations")}</div>
				<div className="text-text-secondary">{aggregations.length ? `${aggregations.length}` : "—"}</div>
			</div>
			<div className="h-2" />

			{aggregations.length === 0 ? <div className="text-text-secondary">—</div> : null}
			{aggregations.map((r) => (
				<div key={r.id} className="flex gap-3 flex-wrap items-center mb-2">
					<select
						className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
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

					<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={() => removeAggregation(r.id)}>
						{t(locale, "builder.remove")}
					</button>
				</div>
			))}
		</div>
	);
}
