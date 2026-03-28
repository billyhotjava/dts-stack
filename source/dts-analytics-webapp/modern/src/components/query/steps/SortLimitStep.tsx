import type { MergedField, FieldRef, AggregationRow, AggregationOp } from "../notebookTypes";
import { fieldRefKey } from "../notebookTypes";
import { t, type Locale } from "../../../i18n";

type Props = {
	locale: Locale;
	allFields: MergedField[];
	isSummarized: boolean;
	groupByFields: FieldRef[];
	aggregations: AggregationRow[];
	orderByKey: string;
	orderByDir: "asc" | "desc";
	limit: number;
	onOrderByKeyChange: (key: string) => void;
	onOrderByDirChange: (dir: "asc" | "desc") => void;
	onLimitChange: (limit: number) => void;
};

function aggLabel(agg: AggregationRow, allFields: MergedField[]): string {
	const opLabel = agg.op.toUpperCase();
	if (agg.op === "count" && !agg.field) return "COUNT(*)";
	const f = agg.field ? allFields.find((mf) => mf.fieldId === agg.field!.fieldId && mf.tableAlias === agg.field!.joinAlias) : null;
	const fieldName = f ? `${f.tableName}.${f.displayName}` : "?";
	return `${opLabel}(${fieldName})`;
}

export function SortLimitStep({ locale, allFields, isSummarized, groupByFields, aggregations, orderByKey, orderByDir, limit, onOrderByKeyChange, onOrderByDirChange, onLimitChange }: Props) {
	return (
		<div className="flex gap-3 items-end flex-wrap">
			<label className="flex-1 min-w-[200px]">
				<div className="text-text-secondary">{t(locale, "builder.sort")}</div>
				<select
					className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
					value={orderByKey}
					onChange={(e) => onOrderByKeyChange(e.target.value)}
				>
					<option value="">{t(locale, "builder.noSort")}</option>
					{isSummarized ? (
						<>
							{groupByFields.map((ref) => {
								const f = allFields.find((mf) => mf.fieldId === ref.fieldId && mf.tableAlias === ref.joinAlias);
								const key = `field:${fieldRefKey(ref)}`;
								return (
									<option key={key} value={key}>
										{f ? `${f.tableName}.${f.displayName}` : `field:${ref.fieldId}`}
									</option>
								);
							})}
							{aggregations.map((agg, idx) => (
								<option key={`agg:${idx}`} value={`agg:${idx}`}>
									{aggLabel(agg, allFields)}
								</option>
							))}
						</>
					) : (
						allFields.map((f) => {
							const key = `field:${fieldRefKey({ fieldId: f.fieldId, joinAlias: f.tableAlias })}`;
							return (
								<option key={key} value={key}>
									{f.tableName}.{f.displayName}
								</option>
							);
						})
					)}
				</select>
			</label>

			<label className="w-[120px]">
				<div className="text-text-secondary">{t(locale, "builder.direction")}</div>
				<select className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary" value={orderByDir} onChange={(e) => onOrderByDirChange(e.target.value === "desc" ? "desc" : "asc")}>
					<option value="asc">{t(locale, "builder.asc")}</option>
					<option value="desc">{t(locale, "builder.desc")}</option>
				</select>
			</label>

			<label className="w-[120px]">
				<div className="text-text-secondary">{t(locale, "builder.limit")}</div>
				<input
					className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
					type="number"
					min={1}
					max={10000}
					value={limit}
					onChange={(e) => onLimitChange(Number.parseInt(e.target.value, 10) || 200)}
				/>
			</label>
		</div>
	);
}
