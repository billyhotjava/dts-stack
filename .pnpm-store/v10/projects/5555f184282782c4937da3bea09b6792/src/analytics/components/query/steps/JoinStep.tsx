// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type TableSummary, type TableDetail } from "../../../api/analyticsApi";
import type { JoinConfig, JoinCondition, JoinType, JoinConditionOp, MergedField, FieldRef } from "../notebookTypes";
import { makeId, fieldRefKey } from "../notebookTypes";
import { TableSearchPicker } from "../shared/TableSearchPicker";
import { FieldPicker } from "../shared/FieldPicker";
import { t, type Locale } from "../../../i18n";

const MAX_JOINS = 3;

function generateAlias(tableName: string, existingAliases: string[]): string {
	const base = tableName || "t";
	if (!existingAliases.includes(base)) return base;
	let i = 2;
	while (existingAliases.includes(`${base}_${i}`)) i++;
	return `${base}_${i}`;
}

type FkInfo = { originFieldName: string; destinationFieldName: string; tableId: number; originFieldId: number; destinationFieldId: number };

function JoinCard(props: {
	locale: Locale;
	join: JoinConfig;
	tables: TableSummary[];
	fkRecommendations: FkInfo[];
	sourceFields: MergedField[];
	onUpdate: (patch: Partial<JoinConfig>) => void;
	onRemove: () => void;
}) {
	const { locale, join, tables, fkRecommendations, sourceFields, onUpdate, onRemove } = props;
	const [advancedMode, setAdvancedMode] = useState(join.conditions.length > 1);

	const joinFields: MergedField[] = useMemo(() => {
		if (!join.tableDetail?.fields) return [];
		return join.tableDetail.fields
			.filter((f) => typeof f.id === "number" && f.id > 0)
			.map((f) => ({
				fieldId: f.id,
				name: f.name || "",
				displayName: f.display_name || f.name || `field:${f.id}`,
				baseType: f.base_type,
				tableAlias: join.alias,
				tableName: join.tableDetail?.display_name || join.tableDetail?.name || join.alias,
			}));
	}, [join.tableDetail, join.alias]);

	const handleTableSelect = async (tableId: number) => {
		try {
			const detail = await analyticsApi.getTable(tableId);
			const tableName = detail.name || `t${tableId}`;
			const fk = fkRecommendations.find((f) => f.tableId === tableId);
			const conditions: JoinCondition[] = fk
				? [{ id: makeId(), leftField: { fieldId: fk.destinationFieldId, joinAlias: null }, op: "=", rightFieldId: fk.originFieldId }]
				: [{ id: makeId(), leftField: null, op: "=", rightFieldId: null }];
			onUpdate({ sourceTableId: tableId, tableDetail: detail, conditions, alias: tableName });
		} catch {
			// error silently
		}
	};

	const joinTypeOptions: { value: JoinType; label: string; advanced?: boolean }[] = [
		{ value: "left-join", label: t(locale, "notebook.join.leftJoin") },
		{ value: "inner-join", label: t(locale, "notebook.join.innerJoin") },
		{ value: "right-join", label: t(locale, "notebook.join.rightJoin"), advanced: true },
		{ value: "full-join", label: t(locale, "notebook.join.fullJoin"), advanced: true },
	];

	const renderCondition = (cond: JoinCondition, index: number) => (
		<div key={cond.id} className="flex gap-2 items-center mb-1">
			<FieldPicker
				fields={sourceFields}
				value={cond.leftField}
				onChange={(ref) => {
					const updated = [...join.conditions];
					updated[index] = { ...cond, leftField: ref };
					onUpdate({ conditions: updated });
				}}
				placeholder={t(locale, "notebook.join.selectField")}
				style={{ width: 200 }}
			/>
			{advancedMode ? (
				<select
					className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
					style={{ width: 80 }}
					value={cond.op}
					onChange={(e) => {
						const updated = [...join.conditions];
						updated[index] = { ...cond, op: e.target.value as JoinConditionOp };
						onUpdate({ conditions: updated });
					}}
				>
					{(["=", "!=", ">", ">=", "<", "<="] as JoinConditionOp[]).map((op) => (
						<option key={op} value={op}>{op}</option>
					))}
				</select>
			) : (
				<span className="w-[30px] text-center">=</span>
			)}
			<select
				className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
				style={{ width: 200 }}
				value={cond.rightFieldId ?? ""}
				onChange={(e) => {
					const updated = [...join.conditions];
					updated[index] = { ...cond, rightFieldId: Number(e.target.value) || null };
					onUpdate({ conditions: updated });
				}}
			>
				<option value="">{t(locale, "notebook.join.selectField")}</option>
				{joinFields.map((f) => (
					<option key={f.fieldId} value={f.fieldId}>{f.displayName}</option>
				))}
			</select>
			{advancedMode && join.conditions.length > 1 && (
				<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={() => {
					onUpdate({ conditions: join.conditions.filter((c) => c.id !== cond.id) });
				}}>×</button>
			)}
		</div>
	);

	return (
		<div className="border border-border-default rounded-sm px-3 py-2 mb-2">
			<TableSearchPicker
				locale={locale}
				tables={tables}
				fkRecommendations={fkRecommendations}
				value={join.sourceTableId}
				onChange={handleTableSelect}
				placeholder={t(locale, "notebook.join.selectTable")}
			/>

			{join.sourceTableId && (
				<>
					<div className="mt-2">
						<select
							className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
							value={join.strategy}
							onChange={(e) => onUpdate({ strategy: e.target.value as JoinType })}
							style={{ width: 300 }}
						>
							{joinTypeOptions.filter((o) => !o.advanced).map((o) => (
								<option key={o.value} value={o.value}>{o.label}</option>
							))}
							<option disabled>── {t(locale, "notebook.advanced")} ──</option>
							{joinTypeOptions.filter((o) => o.advanced).map((o) => (
								<option key={o.value} value={o.value}>{o.label}</option>
							))}
						</select>
					</div>

					<div className="mt-2">
						<div className="flex justify-between items-center mb-1">
							<span className="text-text-secondary">{t(locale, "notebook.join.condition")}</span>
							<button
								className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer"
								type="button"
								onClick={() => {
									if (advancedMode) {
										if (join.conditions.length > 1 && !window.confirm(t(locale, "notebook.changeSourceConfirm"))) return;
										onUpdate({ conditions: [join.conditions[0]], conditionCombine: "and" });
										setAdvancedMode(false);
									} else {
										setAdvancedMode(true);
									}
								}}
							>
								{advancedMode ? t(locale, "notebook.join.condition") : t(locale, "notebook.join.advancedCondition")}
							</button>
						</div>

						{advancedMode && (
							<div className="mb-1">
								<select
									className="w-full box-border px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary"
									value={join.conditionCombine}
									onChange={(e) => onUpdate({ conditionCombine: e.target.value as "and" | "or" })}
									style={{ width: 100 }}
								>
									<option value="and">AND</option>
									<option value="or">OR</option>
								</select>
							</div>
						)}

						{join.conditions.map((c, i) => renderCondition(c, i))}

						{advancedMode && (
							<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={() => {
								onUpdate({ conditions: [...join.conditions, { id: makeId(), leftField: null, op: "=", rightFieldId: null }] });
							}}>
								{t(locale, "notebook.join.addCondition")}
							</button>
						)}
					</div>
				</>
			)}

			<div className="mt-2 text-right">
				<button className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer" type="button" onClick={onRemove}>
					{t(locale, "notebook.join.removeJoin")}
				</button>
			</div>
		</div>
	);
}

type Props = {
	locale: Locale;
	sourceTableId: number | null;
	tables: TableSummary[];
	joins: JoinConfig[];
	allFieldsBeforeJoins: MergedField[];
	onJoinsChange: (joins: JoinConfig[]) => void;
};

export function JoinStep({ locale, sourceTableId, tables, joins, allFieldsBeforeJoins, onJoinsChange }: Props) {
	const [fkInfos, setFkInfos] = useState<FkInfo[]>([]);

	useEffect(() => {
		if (!sourceTableId) { setFkInfos([]); return; }
		let cancelled = false;
		analyticsApi.getTableFks(sourceTableId)
			.then((fks) => {
				if (cancelled) return;
				setFkInfos(
					fks.map((fk) => ({
						originFieldName: fk.origin?.name ?? `field:${fk.origin_id}`,
						destinationFieldName: fk.destination?.name ?? `field:${fk.destination_id}`,
						tableId: fk.origin?.table_id ?? 0,
						originFieldId: fk.origin_id,
						destinationFieldId: fk.destination_id,
					})).filter((f) => f.tableId > 0)
				);
			})
			.catch(() => { if (!cancelled) setFkInfos([]); });
		return () => { cancelled = true; };
	}, [sourceTableId]);

	const fieldsForJoinIndex = (index: number): MergedField[] => {
		let fields = [...allFieldsBeforeJoins];
		for (let i = 0; i < index; i++) {
			const j = joins[i];
			if (j.tableDetail?.fields) {
				fields = fields.concat(
					j.tableDetail.fields
						.filter((f) => typeof f.id === "number" && f.id > 0)
						.map((f) => ({
							fieldId: f.id,
							name: f.name || "",
							displayName: f.display_name || f.name || `field:${f.id}`,
							baseType: f.base_type,
							tableAlias: j.alias,
							tableName: j.tableDetail?.display_name || j.tableDetail?.name || j.alias,
						}))
				);
			}
		}
		return fields;
	};

	const addJoin = () => {
		if (joins.length >= MAX_JOINS) return;
		const newJoin: JoinConfig = {
			id: makeId(),
			sourceTableId: null,
			alias: "",
			strategy: "left-join",
			conditions: [{ id: makeId(), leftField: null, op: "=", rightFieldId: null }],
			conditionCombine: "and",
		};
		onJoinsChange([...joins, newJoin]);
	};

	const updateJoin = (index: number, patch: Partial<JoinConfig>) => {
		const updated = [...joins];
		const current = updated[index];
		const merged = { ...current, ...patch };

		if (patch.sourceTableId && patch.tableDetail) {
			const existingAliases = joins.filter((_, i) => i !== index).map((j) => j.alias);
			merged.alias = generateAlias(patch.tableDetail.name || `t${patch.sourceTableId}`, existingAliases);
		}

		updated[index] = merged;
		onJoinsChange(updated);
	};

	const removeJoin = (index: number) => {
		onJoinsChange(joins.filter((_, i) => i !== index));
	};

	const remaining = MAX_JOINS - joins.length;

	return (
		<div>
			{joins.map((join, i) => (
				<JoinCard
					key={join.id}
					locale={locale}
					join={join}
					tables={tables}
					fkRecommendations={fkInfos}
					sourceFields={fieldsForJoinIndex(i)}
					onUpdate={(patch) => updateJoin(i, patch)}
					onRemove={() => removeJoin(i)}
				/>
			))}

			<button
				className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-sm border border-border-default bg-surface-card text-text-primary font-medium cursor-pointer"
				type="button"
				onClick={addJoin}
				disabled={joins.length >= MAX_JOINS || !sourceTableId}
			>
				{t(locale, "notebook.join.addJoin")}
				{remaining > 0 && remaining < MAX_JOINS && (
					<span className="ml-2 text-xs opacity-70">
						({t(locale, "notebook.join.remaining").replace("{n}", String(remaining))})
					</span>
				)}
			</button>
		</div>
	);
}
