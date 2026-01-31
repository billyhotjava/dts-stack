import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type TableDetail, type TableSummary, type VisibleTable } from "../../api/analyticsApi";
import { ErrorNotice } from "../ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../../i18n";

type LoadState<T> =
	| { state: "idle" }
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type FilterOp =
	| "="
	| "!="
	| ">"
	| ">="
	| "<"
	| "<="
	| "between"
	| "in"
	| "is-null"
	| "not-null"
	| "contains"
	| "starts-with"
	| "ends-with"
	| "is-empty"
	| "not-empty";

type FilterRow = {
	id: string;
	fieldId: number | null;
	op: FilterOp;
	value1: string;
	value2: string;
};

type AggregationOp = "count" | "sum" | "avg" | "min" | "max";

type AggregationRow = {
	id: string;
	op: AggregationOp;
	fieldId: number | null;
};

function makeId(): string {
	return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function parseNumberOrString(raw: string): number | string {
	const s = raw.trim();
	if (!s) return "";
	if (/^-?\\d+(\\.\\d+)?$/.test(s)) {
		const n = Number(s);
		if (!Number.isNaN(n)) return n;
	}
	return s;
}

function stringifyValue(v: unknown): string {
	if (v === null || v === undefined) return "";
	if (Array.isArray(v)) return v.map((x) => stringifyValue(x)).filter(Boolean).join(",");
	if (typeof v === "number" || typeof v === "bigint") return String(v);
	if (typeof v === "boolean") return v ? "true" : "false";
	if (typeof v === "string") return v;
	return String(v);
}

function asRecord(v: unknown): Record<string, unknown> | null {
	return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

function parseFieldRef(node: unknown): number | null {
	if (!Array.isArray(node)) return null;
	if (node[0] !== "field") return null;
	const id = Number(node[1]);
	return Number.isFinite(id) && id > 0 ? id : null;
}

function parseAggregationRow(node: unknown): AggregationRow | null {
	if (!Array.isArray(node) || node.length < 1) return null;
	const op = String(node[0] ?? "").toLowerCase();
	if (op !== "count" && op !== "sum" && op !== "avg" && op !== "min" && op !== "max") return null;
	const fieldId = node.length >= 2 ? parseFieldRef(node[1]) : null;
	return { id: makeId(), op: op as AggregationOp, fieldId };
}

function buildAggregationNode(row: AggregationRow): any[] | null {
	if (row.op === "count") {
		return row.fieldId ? ["count", ["field", row.fieldId]] : ["count"];
	}
	if (!row.fieldId) return null;
	return [row.op, ["field", row.fieldId]];
}

function aggregationLabel(row: AggregationRow, fieldsById: Map<number, string>): string {
	const op = row.op;
	if (op === "count" && !row.fieldId) return "Count";
	const fieldName = row.fieldId ? fieldsById.get(row.fieldId) ?? `field:${row.fieldId}` : "?";
	return `${op.toUpperCase()}(${fieldName})`;
}

function parseOrderByKey(target: unknown): string {
	if (!Array.isArray(target) || target.length < 2) return "";
	const kind = String(target[0] ?? "").toLowerCase();
	if (kind === "field") {
		const id = parseFieldRef(target);
		return id ? `field:${id}` : "";
	}
	if (kind === "aggregation") {
		const idx = Number(target[1]);
		return Number.isFinite(idx) && idx >= 0 ? `agg:${idx}` : "";
	}
	return "";
}

function parseMbqlFilter(node: unknown): FilterRow[] {
	if (!Array.isArray(node)) return [];
	const op = String(node[0] ?? "");
	if (op === "and" || op === "or") {
		return node.slice(1).flatMap(parseMbqlFilter);
	}

	const fieldId = parseFieldRef(node[1]);
	if (!fieldId) return [];
	const rowBase: Omit<FilterRow, "id"> = { fieldId, op: (op as FilterOp) || "=", value1: "", value2: "" };

	if (op === "is-null" || op === "not-null" || op === "is-empty" || op === "not-empty") {
		return [{ id: makeId(), ...rowBase }];
	}
	if (op === "between") {
		return [
			{
				id: makeId(),
				...rowBase,
				value1: stringifyValue(node[2]),
				value2: stringifyValue(node[3]),
			},
		];
	}
	if (op === "in") {
		const values = node[2];
		return [
			{
				id: makeId(),
				...rowBase,
				value1: stringifyValue(values),
				value2: "",
			},
		];
	}
	return [
		{
			id: makeId(),
			...rowBase,
			value1: stringifyValue(node[2]),
			value2: "",
		},
	];
}

function buildMbqlFilter(rows: FilterRow[]): any[] | null {
	const clauses: any[] = [];
	for (const r of rows) {
		if (!r.fieldId) continue;
		const fieldRef = ["field", r.fieldId];
		const op = r.op;
		if (op === "is-null" || op === "not-null" || op === "is-empty" || op === "not-empty") {
			clauses.push([op, fieldRef]);
			continue;
		}
		if (op === "between") {
			const min = r.value1.trim();
			const max = r.value2.trim();
			if (!min || !max) continue;
			clauses.push([op, fieldRef, parseNumberOrString(min), parseNumberOrString(max)]);
			continue;
		}
		if (op === "in") {
			const values = r.value1
				.split(",")
				.map((v) => v.trim())
				.filter(Boolean)
				.map(parseNumberOrString);
			if (!values.length) continue;
			clauses.push([op, fieldRef, values]);
			continue;
		}
		const v = r.value1.trim();
		if (!v) continue;
		clauses.push([op, fieldRef, parseNumberOrString(v)]);
	}

	if (!clauses.length) return null;
	if (clauses.length === 1) return clauses[0];
	return ["and", ...clauses];
}

export function QueryBuilder(props: {
	databaseId: number | null;
	initialDatasetQuery?: Record<string, unknown> | null;
	onDatasetQueryChange?: (datasetQuery: Record<string, unknown> | null) => void;
}) {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { databaseId } = props;

	const [tables, setTables] = useState<LoadState<TableSummary[]>>({ state: "idle" });
	const [visibleTableIds, setVisibleTableIds] = useState<LoadState<Set<number>>>({ state: "idle" });
	const [tableId, setTableId] = useState<number | null>(null);
	const [table, setTable] = useState<LoadState<TableDetail>>({ state: "idle" });

	const [selectedFieldIds, setSelectedFieldIds] = useState<number[]>([]);
	const [groupByFieldIds, setGroupByFieldIds] = useState<number[]>([]);
	const [aggregations, setAggregations] = useState<AggregationRow[]>([]);
	const [filters, setFilters] = useState<FilterRow[]>([{ id: makeId(), fieldId: null, op: "=", value1: "", value2: "" }]);
	const [orderByKey, setOrderByKey] = useState<string>("");
	const [orderByDir, setOrderByDir] = useState<"asc" | "desc">("asc");
	const [limit, setLimit] = useState<number>(200);
	const [appliedInitialKey, setAppliedInitialKey] = useState<string>("");
	const [fieldValues, setFieldValues] = useState<Record<number, LoadState<string[]>>>({});

	const applyInitialDatasetQuery = (dq: Record<string, unknown>) => {
		if (dq.type !== "query") return;
		const q = asRecord(dq.query);
		const sourceTable = Number(q?.["source-table"]);
		if (Number.isFinite(sourceTable) && sourceTable > 0) {
			setTableId(sourceTable);
		}
		const rawBreakout = q?.breakout;
		if (Array.isArray(rawBreakout)) {
			const ids = rawBreakout
				.map((f) => parseFieldRef(f))
				.filter((x): x is number => typeof x === "number" && x > 0);
			setGroupByFieldIds(ids);
		}
		const rawAgg = q?.aggregation;
		if (Array.isArray(rawAgg)) {
			const rows = rawAgg.map(parseAggregationRow).filter((r): r is AggregationRow => !!r);
			setAggregations(rows);
		}
		const rawFields = q?.fields;
		if (Array.isArray(rawFields)) {
			const ids = rawFields.map((f) => parseFieldRef(f)).filter((x): x is number => typeof x === "number" && x > 0);
			setSelectedFieldIds(ids);
		}
		const parsedFilters = parseMbqlFilter(q?.filter);
		if (parsedFilters.length) setFilters(parsedFilters);
		const orderBy = q?.["order-by"];
		if (Array.isArray(orderBy) && Array.isArray(orderBy[0])) {
			const dir = String(orderBy[0][0] ?? "asc").toLowerCase() === "desc" ? "desc" : "asc";
			setOrderByDir(dir);
			setOrderByKey(parseOrderByKey(orderBy[0][1]));
		}
		const lim = Number(q?.limit);
		if (Number.isFinite(lim) && lim > 0) setLimit(Math.min(10000, Math.max(1, Math.floor(lim))));
	};

	useEffect(() => {
		setTableId(null);
		setTable({ state: "idle" });
		setSelectedFieldIds([]);
		setGroupByFieldIds([]);
		setAggregations([]);
		setFilters([{ id: makeId(), fieldId: null, op: "=", value1: "", value2: "" }]);
		setOrderByKey("");
		if (!databaseId) {
			setTables({ state: "idle" });
			setVisibleTableIds({ state: "idle" });
			return;
		}
		const dq = props.initialDatasetQuery;
		if (dq && dq.type === "query") {
			applyInitialDatasetQuery(dq);
			setAppliedInitialKey(JSON.stringify({ database: dq.database, query: dq.query }));
		}

		let cancelled = false;
		setVisibleTableIds({ state: "loading" });
		setTables({ state: "loading" });

		Promise.all([
			analyticsApi.listVisibleTables().catch(() => null),
			analyticsApi.listTables(databaseId),
		])
			.then(([rawVisible, tableList]) => {
				if (cancelled) return;
				const ids = new Set<number>();
				if (Array.isArray(rawVisible)) {
					for (const it of rawVisible) {
						if (typeof it === "number" && Number.isFinite(it) && it > 0) {
							ids.add(it);
							continue;
						}
						const obj = it as VisibleTable;
						const id = Number((obj as any)?.tableId);
						const dbId = Number((obj as any)?.dbId);
						if (Number.isFinite(id) && id > 0 && (!Number.isFinite(dbId) || dbId === databaseId)) {
							ids.add(id);
						}
					}
				}
				setVisibleTableIds({ state: "loaded", value: ids });
				const safe = Array.isArray(tableList) ? tableList : [];
				const filtered = ids.size > 0 ? safe.filter((t) => ids.has(t.id)) : safe;
				setTables({ state: "loaded", value: filtered });
			})
			.catch((e) => {
				if (cancelled) return;
				setTables({ state: "error", error: e });
				setVisibleTableIds({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, [databaseId]);

	useEffect(() => {
		if (!databaseId) return;
		if (tableId !== null) return;
		const dq = props.initialDatasetQuery;
		if (!dq || dq.type !== "query") return;
		if (typeof dq.database === "number" && dq.database !== databaseId) return;
		const key = JSON.stringify({ database: dq.database, query: dq.query });
		if (!key || key === appliedInitialKey) return;
		applyInitialDatasetQuery(dq);
		setAppliedInitialKey(key);
	}, [databaseId, tableId, props.initialDatasetQuery, appliedInitialKey]);

	useEffect(() => {
		if (!tableId) {
			setTable({ state: "idle" });
			return;
		}
		let cancelled = false;
		setTable({ state: "loading" });
		analyticsApi
			.getTable(tableId)
			.then((detail) => {
				if (cancelled) return;
				setTable({ state: "loaded", value: detail });
				const allFieldIds = Array.isArray(detail.fields)
					? detail.fields.map((f) => (typeof f.id === "number" ? f.id : 0)).filter((id) => id > 0)
					: [];
				setSelectedFieldIds((prev) => {
					if (prev.length) return prev;
					return allFieldIds.slice(0, 12);
				});
			})
			.catch((e) => {
				if (cancelled) return;
				setTable({ state: "error", error: e });
			});
		return () => {
			cancelled = true;
		};
	}, [tableId]);

	const tableFields = table.state === "loaded" && Array.isArray(table.value.fields) ? table.value.fields : [];
	const fieldLabelById = useMemo(() => {
		const m = new Map<number, string>();
		for (const f of tableFields) {
			if (typeof f.id !== "number") continue;
			m.set(f.id, f.display_name || f.name || `field:${f.id}`);
		}
		return m;
	}, [tableFields]);

	const isSummarized = aggregations.length > 0 || groupByFieldIds.length > 0;

	const currentDatasetQuery = useMemo(() => {
		if (!databaseId || !tableId) return null;
		const mbql: any = { "source-table": tableId };

		const isSummarized = aggregations.length > 0 || groupByFieldIds.length > 0;
		if (isSummarized) {
			if (groupByFieldIds.length) mbql.breakout = groupByFieldIds.map((id) => ["field", id]);
			const aggNodes = aggregations.map(buildAggregationNode).filter((n): n is any[] => Array.isArray(n));
			if (aggNodes.length) mbql.aggregation = aggNodes;
		} else {
			const fields = selectedFieldIds.map((id) => ["field", id]);
			if (fields.length) mbql.fields = fields;
		}
		const filter = buildMbqlFilter(filters);
		if (filter) mbql.filter = filter;
		if (orderByKey) {
			if (orderByKey.startsWith("field:")) {
				const id = Number(orderByKey.slice("field:".length));
				if (Number.isFinite(id) && id > 0) mbql["order-by"] = [[orderByDir, ["field", id]]];
			} else if (orderByKey.startsWith("agg:")) {
				const idx = Number(orderByKey.slice("agg:".length));
				if (Number.isFinite(idx) && idx >= 0) mbql["order-by"] = [[orderByDir, ["aggregation", idx]]];
			}
		}
		if (Number.isFinite(limit) && limit > 0) mbql.limit = Math.min(10000, Math.max(1, Math.floor(limit)));
		return { database: databaseId, type: "query", query: mbql } as Record<string, unknown>;
	}, [databaseId, tableId, selectedFieldIds, groupByFieldIds, aggregations, filters, orderByKey, orderByDir, limit]);

	useEffect(() => {
		props.onDatasetQueryChange?.(currentDatasetQuery);
	}, [currentDatasetQuery, props]);

	const toggleField = (id: number) => {
		setSelectedFieldIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
	};

	const toggleGroupByField = (id: number) => {
		setGroupByFieldIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
	};

	const addAggregation = () => {
		setAggregations((prev) => [...prev, { id: makeId(), op: "count", fieldId: null }]);
	};

	const updateAggregation = (id: string, patch: Partial<AggregationRow>) => {
		setAggregations((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
	};

	const removeAggregation = (id: string) => {
		setAggregations((prev) => prev.filter((r) => r.id !== id));
	};

	const addFilter = () => {
		setFilters((prev) => [...prev, { id: makeId(), fieldId: null, op: "=", value1: "", value2: "" }]);
	};

	const updateFilter = (id: string, patch: Partial<FilterRow>) => {
		setFilters((prev) => prev.map((r) => (r.id === id ? { ...r, ...patch } : r)));
	};

	const removeFilter = (id: string) => {
		setFilters((prev) => prev.filter((r) => r.id !== id));
	};

	const ensureFieldValues = async (fieldId: number) => {
		if (!fieldId) return;
		const existing = fieldValues[fieldId];
		if (existing?.state === "loaded" || existing?.state === "loading") return;
		setFieldValues((prev) => ({ ...prev, [fieldId]: { state: "loading" } }));
		try {
			const res = await analyticsApi.getFieldValues(fieldId);
			const raw = Array.isArray(res?.values) ? res.values : [];
			const normalized = raw
				.map((v) => (v === null || v === undefined ? "" : String(v)))
				.map((s) => s.trim())
				.filter(Boolean)
				.slice(0, 200);
			setFieldValues((prev) => ({ ...prev, [fieldId]: { state: "loaded", value: normalized } }));
		} catch (e) {
			setFieldValues((prev) => ({ ...prev, [fieldId]: { state: "error", error: e } }));
		}
	};

	return (
		<div className="card">
			<div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
				<strong>{t(locale, "questions.builder")}</strong>
			</div>
			<div style={{ height: 12 }} />

			{tables.state === "error" && <ErrorNotice locale={locale} error={tables.error} />}
			{visibleTableIds.state === "error" && <ErrorNotice locale={locale} error={visibleTableIds.error} />}
			{table.state === "error" && <ErrorNotice locale={locale} error={table.error} />}

			<div className="row">
				<label style={{ width: 420 }}>
					<div className="muted">{t(locale, "builder.table")}</div>
					<select
						className="input"
						value={tableId ?? ""}
						onChange={(e) => setTableId(Number.parseInt(e.target.value, 10) || null)}
						disabled={!databaseId || tables.state !== "loaded"}
					>
						<option value="" disabled>
							{tables.state === "loading" ? t(locale, "loading") : t(locale, "builder.chooseTable")}
						</option>
						{tables.state === "loaded" &&
							tables.value.map((tb) => (
								<option key={tb.id} value={tb.id}>
									{tb.schema ? `${tb.schema}.` : ""}
									{tb.display_name || tb.name || `table:${tb.id}`}
								</option>
							))}
					</select>
				</label>

				<label style={{ width: 180 }}>
					<div className="muted">{t(locale, "builder.limit")}</div>
					<input
						className="input"
						type="number"
						min={1}
						max={10000}
						value={limit}
						onChange={(e) => setLimit(Number.parseInt(e.target.value, 10) || 200)}
					/>
				</label>

				<label style={{ width: 220 }}>
					<div className="muted">{t(locale, "builder.sort")}</div>
					<select
						className="input"
						value={orderByKey}
						onChange={(e) => setOrderByKey(e.target.value)}
						disabled={table.state !== "loaded"}
					>
						<option value="">{t(locale, "builder.noSort")}</option>
						{isSummarized ? (
							<>
								{groupByFieldIds.map((id) => (
									<option key={`field:${id}`} value={`field:${id}`}>
										{fieldLabelById.get(id) ?? `field:${id}`}
									</option>
								))}
								{aggregations.map((agg, idx) => (
									<option key={`agg:${idx}`} value={`agg:${idx}`}>
										{aggregationLabel(agg, fieldLabelById)}
									</option>
								))}
							</>
						) : (
							tableFields.map((f) => (
								<option key={String(f.id)} value={`field:${String(f.id)}`}>
									{f.display_name || f.name || `field:${f.id}`}
								</option>
							))
						)}
					</select>
				</label>
				<label style={{ width: 140 }}>
					<div className="muted">{t(locale, "builder.direction")}</div>
					<select className="input" value={orderByDir} onChange={(e) => setOrderByDir(e.target.value === "desc" ? "desc" : "asc")}>
						<option value="asc">{t(locale, "builder.asc")}</option>
						<option value="desc">{t(locale, "builder.desc")}</option>
					</select>
				</label>
			</div>

			<div style={{ height: 12 }} />

			<div>
				<div className="row" style={{ justifyContent: "space-between" }}>
					<strong>{t(locale, "builder.summarize")}</strong>
					<button className="btn" type="button" onClick={addAggregation} disabled={table.state !== "loaded"}>
						{t(locale, "builder.addAggregation")}
					</button>
				</div>
				<div style={{ height: 8 }} />

				{table.state !== "loaded" ? (
					<div className="muted">—</div>
				) : (
					<>
						<div className="muted">{t(locale, "builder.groupBy")}</div>
						<div style={{ height: 8 }} />
						<div className="row" style={{ alignItems: "flex-start" }}>
							{tableFields.map((f) => {
								const id = typeof f.id === "number" ? f.id : 0;
								if (!id) return null;
								const checked = groupByFieldIds.includes(id);
								return (
									<label key={String(id)} className="tag" style={{ cursor: "pointer", userSelect: "none" }}>
										<input
											type="checkbox"
											checked={checked}
											onChange={() => toggleGroupByField(id)}
											style={{ marginRight: 6 }}
										/>
										{f.display_name || f.name || `field:${id}`}
									</label>
								);
							})}
						</div>

						<div style={{ height: 12 }} />

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
									disabled={table.state !== "loaded"}
								>
									<option value="count">{t(locale, "builder.agg.count")}</option>
									<option value="sum">{t(locale, "builder.agg.sum")}</option>
									<option value="avg">{t(locale, "builder.agg.avg")}</option>
									<option value="min">{t(locale, "builder.agg.min")}</option>
									<option value="max">{t(locale, "builder.agg.max")}</option>
								</select>

								<select
									className="input"
									style={{ width: 360 }}
									value={r.fieldId ?? ""}
									onChange={(e) => updateAggregation(r.id, { fieldId: Number.parseInt(e.target.value, 10) || null })}
									disabled={table.state !== "loaded"}
								>
									<option value="">{t(locale, "builder.agg.rows")}</option>
									{tableFields.map((f) => (
										<option key={String(f.id)} value={String(f.id)}>
											{f.display_name || f.name || `field:${f.id}`}
										</option>
									))}
								</select>

								<button className="btn" type="button" onClick={() => removeAggregation(r.id)}>
									{t(locale, "builder.remove")}
								</button>
							</div>
						))}
					</>
				)}
			</div>

			<div style={{ height: 12 }} />

			<div>
				<div className="row" style={{ justifyContent: "space-between" }}>
					<strong>{t(locale, "builder.fields")}</strong>
					<div className="muted">
						{t(locale, "builder.selected")}: {selectedFieldIds.length}
					</div>
				</div>
				<div style={{ height: 8 }} />
				{table.state !== "loaded" ? (
					<div className="muted">—</div>
				) : (
					<div className="row" style={{ alignItems: "flex-start" }}>
						{tableFields.map((f) => {
							const id = typeof f.id === "number" ? f.id : 0;
							if (!id) return null;
							const checked = selectedFieldIds.includes(id);
							return (
								<label key={String(id)} className="tag" style={{ cursor: "pointer", userSelect: "none" }}>
									<input
										type="checkbox"
										checked={checked}
										onChange={() => toggleField(id)}
										disabled={isSummarized}
										style={{ marginRight: 6 }}
									/>
									{f.display_name || f.name || `field:${id}`}
								</label>
							);
						})}
					</div>
				)}
				{isSummarized ? <div className="muted" style={{ marginTop: 8 }}>{t(locale, "builder.fieldsDisabled")}</div> : null}
			</div>

			<div style={{ height: 12 }} />

			<div>
				<div className="row" style={{ justifyContent: "space-between" }}>
					<strong>{t(locale, "builder.filters")}</strong>
					<button className="btn" type="button" onClick={addFilter} disabled={table.state !== "loaded"}>
						{t(locale, "builder.addFilter")}
					</button>
				</div>
				<div style={{ height: 8 }} />

				{filters.length === 0 ? <div className="muted">—</div> : null}
				{filters.map((r) => (
					<div key={r.id} className="row" style={{ marginBottom: 8 }}>
						<select
							className="input"
							style={{ width: 240 }}
							value={r.fieldId ?? ""}
							onChange={(e) => updateFilter(r.id, { fieldId: Number.parseInt(e.target.value, 10) || null })}
							disabled={table.state !== "loaded"}
						>
							<option value="">{t(locale, "builder.chooseField")}</option>
							{tableFields.map((f) => (
								<option key={String(f.id)} value={String(f.id)}>
									{f.display_name || f.name || `field:${f.id}`}
								</option>
							))}
						</select>
						<select
							className="input"
							style={{ width: 160 }}
							value={r.op}
							onChange={(e) => updateFilter(r.id, { op: e.target.value as FilterOp })}
							disabled={table.state !== "loaded"}
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

						{r.op === "is-null" || r.op === "not-null" || r.op === "is-empty" || r.op === "not-empty" ? null : r.op === "between" ? (
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
							<>
								<input
									className="input"
									style={{ width: 360 }}
									value={r.value1}
									list={r.fieldId ? `field-values-${r.fieldId}` : undefined}
									onFocus={() => {
										if (r.fieldId) void ensureFieldValues(r.fieldId);
									}}
									onChange={(e) => updateFilter(r.id, { value1: e.target.value })}
									placeholder={r.op === "in" ? t(locale, "builder.csvValues") : t(locale, "builder.value")}
								/>
								{r.fieldId ? (
									<datalist id={`field-values-${r.fieldId}`}>
										{(() => {
											const cached = fieldValues[r.fieldId];
											if (!cached || cached.state !== "loaded") return null;
											return cached.value.map((v: string) => <option key={v} value={v} />);
										})()}
									</datalist>
								) : null}
							</>
						)}

						<button className="btn" type="button" onClick={() => removeFilter(r.id)} disabled={filters.length <= 1}>
							{t(locale, "builder.remove")}
						</button>
					</div>
				))}
			</div>

			<div style={{ height: 12 }} />
		</div>
	);
}
