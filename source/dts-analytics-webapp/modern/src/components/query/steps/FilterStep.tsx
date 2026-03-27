import type { FilterRow, FilterOp, MergedField, FieldRef } from "../notebookTypes";
import { makeId } from "../notebookTypes";
import { FieldPicker } from "../shared/FieldPicker";
import { t, type Locale } from "../../../i18n";

type Props = {
	locale: Locale;
	allFields: MergedField[];
	filters: FilterRow[];
	onFiltersChange: (filters: FilterRow[]) => void;
};

function isNoValueOp(op: FilterOp): boolean {
	return op === "is-null" || op === "not-null" || op === "is-empty" || op === "not-empty";
}

export function FilterStep({ locale, allFields, filters, onFiltersChange }: Props) {
	const addFilter = () => {
		const row: FilterRow = { id: makeId(), field: null, op: "=", value1: "", value2: "" };
		onFiltersChange([...filters, row]);
	};

	const removeFilter = (id: string) => {
		onFiltersChange(filters.filter((r) => r.id !== id));
	};

	const updateFilter = (id: string, patch: Partial<FilterRow>) => {
		onFiltersChange(filters.map((r) => (r.id === id ? { ...r, ...patch } : r)));
	};

	return (
		<div>
			<div className="row" style={{ justifyContent: "space-between" }}>
				<strong>{t(locale, "builder.filters")}</strong>
				<button className="btn" type="button" onClick={addFilter}>
					{t(locale, "builder.addFilter")}
				</button>
			</div>
			<div style={{ height: 8 }} />

			{filters.length === 0 ? <div className="muted">—</div> : null}
			{filters.map((r) => (
				<div key={r.id} className="row" style={{ marginBottom: 8 }}>
					<FieldPicker
						fields={allFields}
						value={r.field}
						onChange={(ref: FieldRef | null) => updateFilter(r.id, { field: ref })}
						placeholder={t(locale, "builder.chooseField")}
						style={{ width: 240 }}
					/>
					<select
						className="input"
						style={{ width: 160 }}
						value={r.op}
						onChange={(e) => updateFilter(r.id, { op: e.target.value as FilterOp })}
					>
						<option value="=">=</option>
						<option value="!=">!=</option>
						<option value=">">&gt;</option>
						<option value=">=">&gt;=</option>
						<option value="<">&lt;</option>
						<option value="<=">&lt;=</option>
						<option value="contains">{t(locale, "builder.contains")}</option>
						<option value="starts-with">{t(locale, "builder.startsWith")}</option>
						<option value="ends-with">{t(locale, "builder.endsWith")}</option>
						<option value="in">{t(locale, "builder.in")}</option>
						<option value="between">{t(locale, "builder.between")}</option>
						<option value="is-null">{t(locale, "builder.isNull")}</option>
						<option value="not-null">{t(locale, "builder.notNull")}</option>
						<option value="is-empty">{t(locale, "builder.isEmpty")}</option>
						<option value="not-empty">{t(locale, "builder.notEmpty")}</option>
					</select>

					{isNoValueOp(r.op) ? null : r.op === "between" ? (
						<>
							<input
								className="input"
								style={{ width: 180 }}
								value={r.value1}
								onChange={(e) => updateFilter(r.id, { value1: e.target.value })}
								placeholder={t(locale, "builder.min")}
							/>
							<input
								className="input"
								style={{ width: 180 }}
								value={r.value2}
								onChange={(e) => updateFilter(r.id, { value2: e.target.value })}
								placeholder={t(locale, "builder.max")}
							/>
						</>
					) : (
						<input
							className="input"
							style={{ width: 360 }}
							value={r.value1}
							onChange={(e) => updateFilter(r.id, { value1: e.target.value })}
							placeholder={r.op === "in" ? t(locale, "builder.csvValues") : t(locale, "builder.value")}
						/>
					)}

					<button
						className="btn"
						type="button"
						onClick={() => removeFilter(r.id)}
						disabled={false}
					>
						{t(locale, "builder.remove")}
					</button>
				</div>
			))}
		</div>
	);
}
