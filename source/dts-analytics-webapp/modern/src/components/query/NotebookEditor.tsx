import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { analyticsApi, type TableDetail, type TableSummary, type VisibleTable } from "../../api/analyticsApi";
import { getEffectiveLocale, t, type Locale } from "../../i18n";
import type {
	JoinConfig,
	JoinCondition,
	FilterRow,
	FilterOp,
	AggregationRow,
	AggregationOp,
	MergedField,
	FieldRef,
} from "./notebookTypes";
import { makeId, fieldRefKey, fieldRefToMbql, parseFieldRefFromMbql } from "./notebookTypes";
import { StepCard } from "./shared/StepCard";
import { DataSourceStep } from "./steps/DataSourceStep";
import { JoinStep } from "./steps/JoinStep";
import { PickColumnsStep } from "./steps/PickColumnsStep";
import { FilterStep } from "./steps/FilterStep";
import { SummarizeStep } from "./steps/SummarizeStep";
import { SortLimitStep } from "./steps/SortLimitStep";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

type Props = {
	databaseId: number | null;
	initialDatasetQuery?: Record<string, unknown> | null;
	onDatasetQueryChange?: (datasetQuery: Record<string, unknown> | null) => void;
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function clampLimit(v: number): number {
	if (!Number.isFinite(v) || v < 1) return 200;
	return Math.min(Math.max(Math.round(v), 1), 10000);
}

function parseNumberOrString(raw: string): number | string {
	const s = raw.trim();
	if (s === "") return s;
	const n = Number(s);
	return Number.isFinite(n) ? n : s;
}

function buildFieldsFromDetail(detail: TableDetail | undefined, tableAlias: string | null, tableName: string): MergedField[] {
	if (!detail?.fields) return [];
	return detail.fields
		.filter((f) => typeof f.id === "number" && f.id > 0)
		.map((f) => ({
			fieldId: f.id,
			name: f.name || "",
			displayName: f.display_name || f.name || `field:${f.id}`,
			baseType: f.base_type,
			tableAlias,
			tableName,
		}));
}

function refBelongsToAlias(ref: FieldRef, alias: string): boolean {
	return ref.joinAlias === alias;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function NotebookEditor({ databaseId, initialDatasetQuery, onDatasetQueryChange }: Props) {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);

	// ---- state ----
	const [sourceTableId, setSourceTableId] = useState<number | null>(null);
	const [sourceTableDetail, setSourceTableDetail] = useState<TableDetail | undefined>(undefined);
	const [joins, setJoins] = useState<JoinConfig[]>([]);
	const [selectedFields, setSelectedFields] = useState<FieldRef[]>([]);
	const [filters, setFilters] = useState<FilterRow[]>([]);
	const [aggregations, setAggregations] = useState<AggregationRow[]>([]);
	const [groupByFields, setGroupByFields] = useState<FieldRef[]>([]);
	const [orderByKey, setOrderByKey] = useState<string>("");
	const [orderByDir, setOrderByDir] = useState<"asc" | "desc">("asc");
	const [limit, setLimit] = useState<number>(200);

	// tables list (for JoinStep)
	const [tables, setTables] = useState<TableSummary[]>([]);

	// guard to prevent looping initial parse
	const didApplyInitial = useRef(false);

	// ---- load tables when databaseId changes ----
	useEffect(() => {
		if (!databaseId) { setTables([]); return; }
		let cancelled = false;
		Promise.all([
			analyticsApi.listVisibleTables().catch(() => null),
			analyticsApi.listTables(databaseId),
		]).then(([rawVisible, tableList]) => {
			if (cancelled) return;
			const ids = new Set<number>();
			if (Array.isArray(rawVisible)) {
				for (const it of rawVisible) {
					if (typeof it === "number") { ids.add(it); continue; }
					const obj = it as VisibleTable;
					const id = Number(obj?.tableId);
					const dbId = Number(obj?.dbId);
					if (Number.isFinite(id) && id > 0 && (!Number.isFinite(dbId) || dbId === databaseId)) ids.add(id);
				}
			}
			const safe = Array.isArray(tableList) ? tableList : [];
			const filtered = ids.size > 0 ? safe.filter((tb) => ids.has(tb.id)) : safe;
			setTables(filtered);
		}).catch(() => { if (!cancelled) setTables([]); });
		return () => { cancelled = true; };
	}, [databaseId]);

	// ---- computed: merged fields ----
	const sourceFields: MergedField[] = useMemo(() => {
		const name = sourceTableDetail?.display_name || sourceTableDetail?.name || "source";
		return buildFieldsFromDetail(sourceTableDetail, null, name);
	}, [sourceTableDetail]);

	const allFields: MergedField[] = useMemo(() => {
		let fields = [...sourceFields];
		for (const j of joins) {
			const name = j.tableDetail?.display_name || j.tableDetail?.name || j.alias;
			fields = fields.concat(buildFieldsFromDetail(j.tableDetail, j.alias, name));
		}
		return fields;
	}, [sourceFields, joins]);

	const isSummarized = aggregations.length > 0 || groupByFields.length > 0;

	// ---- MBQL builder ----
	const currentDatasetQuery = useMemo(() => {
		if (!sourceTableId) return null;

		const query: Record<string, unknown> = { "source-table": sourceTableId };

		// joins
		const validJoins = joins.filter((j) => j.sourceTableId && j.conditions.some((c) => c.leftField && c.rightFieldId));
		if (validJoins.length > 0) {
			query["joins"] = validJoins.map((j) => {
				const conditionMbqls = j.conditions
					.filter((c) => c.leftField && c.rightFieldId)
					.map((c) => [c.op, fieldRefToMbql(c.leftField!), ["field", c.rightFieldId!, { "join-alias": j.alias }]]);

				let condition: unknown;
				if (conditionMbqls.length === 1) {
					condition = conditionMbqls[0];
				} else if (conditionMbqls.length > 1) {
					condition = [j.conditionCombine, ...conditionMbqls];
				}

				return {
					"source-table": j.sourceTableId,
					alias: j.alias,
					strategy: j.strategy,
					condition,
				};
			});
		}

		// fields (only when not summarized)
		if (!isSummarized && selectedFields.length > 0) {
			query["fields"] = selectedFields.map(fieldRefToMbql);
		}

		// filter
		const validFilters = filters.filter((f) => f.field);
		if (validFilters.length > 0) {
			const filterClauses = validFilters.map((f) => {
				const ref = fieldRefToMbql(f.field!);
				const op = f.op;
				if (op === "is-null" || op === "not-null" || op === "is-empty" || op === "not-empty") {
					return [op, ref];
				}
				if (op === "between") {
					return [op, ref, parseNumberOrString(f.value1), parseNumberOrString(f.value2)];
				}
				if (op === "in") {
					const vals = f.value1.split(",").map((s) => parseNumberOrString(s.trim())).filter((v) => v !== "");
					return [op, ref, ...vals];
				}
				return [op, ref, parseNumberOrString(f.value1)];
			});

			if (filterClauses.length === 1) {
				query["filter"] = filterClauses[0];
			} else {
				query["filter"] = ["and", ...filterClauses];
			}
		}

		// aggregation
		if (aggregations.length > 0) {
			query["aggregation"] = aggregations.map((a) => {
				if (a.op === "count" && !a.field) return ["count"];
				if (a.field) return [a.op, fieldRefToMbql(a.field)];
				return ["count"];
			});
		}

		// breakout
		if (groupByFields.length > 0) {
			query["breakout"] = groupByFields.map(fieldRefToMbql);
		}

		// order-by
		if (orderByKey) {
			if (orderByKey.startsWith("field:")) {
				const refStr = orderByKey.slice(6);
				const parts = refStr.split(":");
				let ref: FieldRef;
				if (parts.length === 2) {
					ref = { fieldId: Number(parts[1]), joinAlias: parts[0] };
				} else {
					ref = { fieldId: Number(parts[0]), joinAlias: null };
				}
				if (Number.isFinite(ref.fieldId) && ref.fieldId > 0) {
					query["order-by"] = [[orderByDir, fieldRefToMbql(ref)]];
				}
			} else if (orderByKey.startsWith("agg:")) {
				const idx = Number(orderByKey.slice(4));
				if (Number.isFinite(idx) && idx >= 0 && idx < aggregations.length) {
					query["order-by"] = [[orderByDir, ["aggregation", idx]]];
				}
			}
		}

		// limit — always include
		query["limit"] = clampLimit(limit);

		return { type: "query", database: databaseId, query };
	}, [databaseId, sourceTableId, joins, selectedFields, filters, aggregations, groupByFields, orderByKey, orderByDir, limit, isSummarized]);

	// ---- emit changes ----
	useEffect(() => {
		onDatasetQueryChange?.(currentDatasetQuery);
	}, [currentDatasetQuery, onDatasetQueryChange]);

	// ---- initial dataset query parsing ----
	useEffect(() => {
		if (didApplyInitial.current) return;
		if (!initialDatasetQuery || !databaseId) return;
		const q = initialDatasetQuery.query ?? initialDatasetQuery;
		if (!q || typeof q !== "object") return;
		const query = q as Record<string, unknown>;
		const tableId = Number(query["source-table"]);
		if (!Number.isFinite(tableId) || tableId <= 0) return;

		didApplyInitial.current = true;
		applyInitialDatasetQuery(query, tableId);
	}, [initialDatasetQuery, databaseId]); // eslint-disable-line react-hooks/exhaustive-deps

	async function applyInitialDatasetQuery(query: Record<string, unknown>, tableId: number) {
		// load source table
		try {
			const detail = await analyticsApi.getTable(tableId);
			setSourceTableId(tableId);
			setSourceTableDetail(detail);
		} catch {
			return;
		}

		// parse joins
		const rawJoins = query["joins"];
		if (Array.isArray(rawJoins)) {
			const parsed: JoinConfig[] = [];
			for (const rj of rawJoins) {
				if (!rj || typeof rj !== "object") continue;
				const jObj = rj as Record<string, unknown>;
				const jTableId = Number(jObj["source-table"]);
				if (!Number.isFinite(jTableId) || jTableId <= 0) continue;

				const alias = String(jObj["alias"] || "");
				const strategy = (jObj["strategy"] as JoinConfig["strategy"]) || "left-join";
				const conditionCombine = parseConditionCombine(jObj["condition"]);
				const conditions = parseJoinConditions(jObj["condition"], alias);

				let tableDetail: TableDetail | undefined;
				try {
					tableDetail = await analyticsApi.getTable(jTableId);
				} catch {
					// continue without detail
				}

				parsed.push({
					id: makeId(),
					sourceTableId: jTableId,
					alias,
					strategy,
					conditions: conditions.length > 0 ? conditions : [{ id: makeId(), leftField: null, op: "=", rightFieldId: null }],
					conditionCombine,
					tableDetail,
				});
			}
			setJoins(parsed);
		}

		// parse fields
		const rawFields = query["fields"];
		if (Array.isArray(rawFields)) {
			const refs: FieldRef[] = [];
			for (const rf of rawFields) {
				const ref = parseFieldRefFromMbql(rf);
				if (ref) refs.push(ref);
			}
			setSelectedFields(refs);
		}

		// parse filters
		const rawFilter = query["filter"];
		if (Array.isArray(rawFilter)) {
			const rows = parseFilterRows(rawFilter);
			if (rows.length > 0) setFilters(rows);
		}

		// parse aggregations
		const rawAgg = query["aggregation"];
		if (Array.isArray(rawAgg)) {
			const rows: AggregationRow[] = [];
			for (const ra of rawAgg) {
				if (!Array.isArray(ra)) continue;
				const op = ra[0] as AggregationOp;
				const field = ra.length > 1 ? parseFieldRefFromMbql(ra[1]) : null;
				rows.push({ id: makeId(), op, field });
			}
			if (rows.length > 0) setAggregations(rows);
		}

		// parse breakout / group-by
		const rawBreakout = query["breakout"];
		if (Array.isArray(rawBreakout)) {
			const refs: FieldRef[] = [];
			for (const rb of rawBreakout) {
				const ref = parseFieldRefFromMbql(rb);
				if (ref) refs.push(ref);
			}
			if (refs.length > 0) setGroupByFields(refs);
		}

		// parse order-by
		const rawOrder = query["order-by"];
		if (Array.isArray(rawOrder) && rawOrder.length > 0) {
			const first = rawOrder[0];
			if (Array.isArray(first) && first.length >= 2) {
				const dir = first[0] === "desc" ? "desc" : "asc";
				setOrderByDir(dir as "asc" | "desc");
				if (Array.isArray(first[1]) && first[1][0] === "aggregation") {
					setOrderByKey(`agg:${first[1][1]}`);
				} else {
					const ref = parseFieldRefFromMbql(first[1]);
					if (ref) setOrderByKey(`field:${fieldRefKey(ref)}`);
				}
			}
		}

		// parse limit
		const rawLimit = Number(query["limit"]);
		if (Number.isFinite(rawLimit) && rawLimit > 0) {
			setLimit(clampLimit(rawLimit));
		}
	}

	// ---- source table change handling ----
	const handleSourceTableSelected = useCallback((tableId: number, detail: TableDetail) => {
		if (sourceTableId && sourceTableId !== tableId) {
			if (!window.confirm(t(locale, "notebook.changeSourceConfirm"))) return;
			setJoins([]);
			setFilters([]);
			setAggregations([]);
			setGroupByFields([]);
			setSelectedFields([]);
			setOrderByKey("");
		}
		setSourceTableId(tableId);
		setSourceTableDetail(detail);
		// Default: select first 12 fields of source table
		if (detail.fields) {
			const first12 = detail.fields
				.filter((f) => typeof f.id === "number" && f.id > 0)
				.slice(0, 12)
				.map((f) => ({ fieldId: f.id, joinAlias: null } as FieldRef));
			setSelectedFields(first12);
		}
	}, [sourceTableId, locale]);

	// ---- join deletion cascade ----
	const handleJoinsChange = useCallback((newJoins: JoinConfig[]) => {
		const oldAliases = new Set(joins.map((j) => j.alias));
		const newAliases = new Set(newJoins.map((j) => j.alias));
		const removedAliases: string[] = [];
		for (const a of oldAliases) {
			if (a && !newAliases.has(a)) removedAliases.push(a);
		}

		setJoins(newJoins);

		if (removedAliases.length > 0) {
			const isRemoved = (ref: FieldRef) => ref.joinAlias != null && removedAliases.some((a) => refBelongsToAlias(ref, a));
			setFilters((prev) => prev.filter((f) => !f.field || !isRemoved(f.field)));
			setAggregations((prev) => prev.filter((a) => !a.field || !isRemoved(a.field)));
			setGroupByFields((prev) => prev.filter((ref) => !isRemoved(ref)));
			setSelectedFields((prev) => prev.filter((ref) => !isRemoved(ref)));
		}
	}, [joins]);

	// ---- summaries for collapsed cards ----
	const sourceSummary = sourceTableDetail
		? (sourceTableDetail.display_name || sourceTableDetail.name || `#${sourceTableId}`)
		: null;

	const joinSummary = joins.length > 0
		? `${joins.length} ${t(locale, "notebook.step.join").toLowerCase()}`
		: null;

	const columnsSummary = selectedFields.length > 0
		? `${selectedFields.length} ${t(locale, "notebook.step.pickColumns").toLowerCase()}`
		: null;

	const filterSummary = filters.length > 0
		? `${filters.length} ${t(locale, "notebook.step.filter").toLowerCase()}`
		: null;

	const summarizeSummary = (aggregations.length > 0 || groupByFields.length > 0)
		? `${aggregations.length} agg, ${groupByFields.length} group`
		: null;

	const sortSummary = orderByKey ? `${orderByKey} ${orderByDir}` : null;

	// ---- render ----
	return (
		<div style={{ maxWidth: 960, margin: "0 auto" }}>
			{/* Step 1: Data Source */}
			<StepCard
				title={t(locale, "notebook.step.dataSource")}
				summary={sourceSummary}
				defaultExpanded
			>
				<DataSourceStep
					locale={locale}
					databaseId={databaseId}
					tableId={sourceTableId}
					onTableSelected={handleSourceTableSelected}
				/>
			</StepCard>

			{sourceTableId && (
				<>
					{/* Step 2: Join */}
					<StepCard
						title={t(locale, "notebook.step.join")}
						summary={joinSummary}
					>
						<JoinStep
							locale={locale}
							sourceTableId={sourceTableId}
							tables={tables}
							joins={joins}
							allFieldsBeforeJoins={sourceFields}
							onJoinsChange={handleJoinsChange}
						/>
					</StepCard>

					{/* Step 3: Pick Columns */}
					<StepCard
						title={t(locale, "notebook.step.pickColumns")}
						summary={columnsSummary}
						disabled={isSummarized}
					>
						<PickColumnsStep
							locale={locale}
							allFields={allFields}
							selectedFields={selectedFields}
							onSelectedFieldsChange={setSelectedFields}
							isSummarized={isSummarized}
						/>
					</StepCard>

					{/* Step 4: Filter */}
					<StepCard
						title={t(locale, "notebook.step.filter")}
						summary={filterSummary}
					>
						<FilterStep
							locale={locale}
							allFields={allFields}
							filters={filters}
							onFiltersChange={setFilters}
						/>
					</StepCard>

					{/* Step 5: Summarize */}
					<StepCard
						title={t(locale, "notebook.step.summarize")}
						summary={summarizeSummary}
					>
						<SummarizeStep
							locale={locale}
							allFields={allFields}
							aggregations={aggregations}
							groupByFields={groupByFields}
							onAggregationsChange={setAggregations}
							onGroupByFieldsChange={setGroupByFields}
						/>
					</StepCard>

					{/* Step 6: Sort & Limit */}
					<StepCard
						title={t(locale, "notebook.step.sortLimit")}
						summary={sortSummary}
					>
						<SortLimitStep
							locale={locale}
							allFields={allFields}
							isSummarized={isSummarized}
							groupByFields={groupByFields}
							aggregations={aggregations}
							orderByKey={orderByKey}
							orderByDir={orderByDir}
							limit={limit}
							onOrderByKeyChange={setOrderByKey}
							onOrderByDirChange={setOrderByDir}
							onLimitChange={setLimit}
						/>
					</StepCard>
				</>
			)}
		</div>
	);
}

// ---------------------------------------------------------------------------
// Parse helpers for initial dataset query
// ---------------------------------------------------------------------------

function parseConditionCombine(condition: unknown): "and" | "or" {
	if (!Array.isArray(condition)) return "and";
	if (condition[0] === "or") return "or";
	return "and";
}

function parseJoinConditions(condition: unknown, joinAlias: string): JoinCondition[] {
	if (!Array.isArray(condition)) return [];

	// single condition: ["=", leftRef, rightRef]
	if (typeof condition[0] === "string" && condition[0] !== "and" && condition[0] !== "or") {
		return [parseSingleCondition(condition, joinAlias)].filter(Boolean) as JoinCondition[];
	}

	// compound: ["and"|"or", cond1, cond2, ...]
	if (condition[0] === "and" || condition[0] === "or") {
		const results: JoinCondition[] = [];
		for (let i = 1; i < condition.length; i++) {
			const c = parseSingleCondition(condition[i], joinAlias);
			if (c) results.push(c);
		}
		return results;
	}

	return [];
}

function parseSingleCondition(node: unknown, _joinAlias: string): JoinCondition | null {
	if (!Array.isArray(node) || node.length < 3) return null;
	const op = node[0] as JoinCondition["op"];
	const leftField = parseFieldRefFromMbql(node[1]);
	const rightRef = parseFieldRefFromMbql(node[2]);
	const rightFieldId = rightRef?.fieldId ?? null;
	return { id: makeId(), leftField, op, rightFieldId };
}

function parseFilterRows(raw: unknown): FilterRow[] {
	if (!Array.isArray(raw)) return [];

	// compound: ["and", filter1, filter2, ...]
	if (raw[0] === "and") {
		const rows: FilterRow[] = [];
		for (let i = 1; i < raw.length; i++) {
			const r = parseSingleFilter(raw[i]);
			if (r) rows.push(r);
		}
		return rows;
	}

	// single filter
	const single = parseSingleFilter(raw);
	return single ? [single] : [];
}

function parseSingleFilter(node: unknown): FilterRow | null {
	if (!Array.isArray(node) || node.length < 2) return null;
	const op = node[0] as FilterOp;
	const field = parseFieldRefFromMbql(node[1]);

	if (op === "is-null" || op === "not-null" || op === "is-empty" || op === "not-empty") {
		return { id: makeId(), field, op, value1: "", value2: "" };
	}

	if (op === "between") {
		return { id: makeId(), field, op, value1: String(node[2] ?? ""), value2: String(node[3] ?? "") };
	}

	if (op === "in") {
		const vals = node.slice(2).map(String).join(", ");
		return { id: makeId(), field, op, value1: vals, value2: "" };
	}

	return { id: makeId(), field, op, value1: String(node[2] ?? ""), value2: "" };
}
