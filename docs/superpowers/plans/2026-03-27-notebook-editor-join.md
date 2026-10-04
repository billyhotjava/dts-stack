# Notebook Editor with JOIN Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat QueryBuilder with a step-based Notebook Editor that supports multi-table JOIN queries (up to 3 joins).

**Architecture:** Six collapsible step cards (Data Source → Join → Pick Columns → Filter → Summarize → Sort & Limit). NotebookEditor owns all state, computes a merged field list from source + joined tables, and outputs MBQL with `joins` array. Each step is a focused component consuming shared types and the merged field list.

**Tech Stack:** React 18, TypeScript, existing custom UI lib (not antd), existing analyticsApi client, i18n with zh-CN/en locales.

**Spec:** `docs/superpowers/specs/2026-03-27-notebook-editor-join-design.md`

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `components/query/notebookTypes.ts` | All type definitions (FieldRef, MergedField, JoinConfig, FilterRow, AggregationRow, NotebookState) |
| `components/query/shared/StepCard.tsx` | Collapsible step card shell (title, summary, expand/collapse) |
| `components/query/shared/FieldPicker.tsx` | Unified field selector with optgroup by table, emits FieldRef |
| `components/query/shared/TableSearchPicker.tsx` | Searchable table picker with FK recommendations, 300ms debounce |
| `components/query/steps/DataSourceStep.tsx` | Step 1: data source selection with search |
| `components/query/steps/JoinStep.tsx` | Step 2: JOIN config (up to 3), conditions, alias generation |
| `components/query/steps/PickColumnsStep.tsx` | Step 3: column selection checkboxes |
| `components/query/steps/FilterStep.tsx` | Step 4: filter rows with FieldRef |
| `components/query/steps/SummarizeStep.tsx` | Step 5: aggregation + group by with FieldRef |
| `components/query/steps/SortLimitStep.tsx` | Step 6: order by + limit |
| `components/query/NotebookEditor.tsx` | Main container, state owner, MBQL builder, props interface |

### Modified Files

| File | Change |
|------|--------|
| `api/analyticsApi.ts` | Add `getTableFks(tableId)` method (~5 lines) |
| `i18n.ts` | Add ~40 notebook.* i18n keys for zh-CN and en |
| `pages/CardEditorPage.tsx` | Change import from `QueryBuilder` to `NotebookEditor` (1 line) |

All paths are relative to `source/dts-analytics-webapp/modern/src/`.

---

## Task 1: Type Definitions

**Files:**
- Create: `components/query/notebookTypes.ts`

- [ ] **Step 1: Create notebookTypes.ts with all type definitions**

```typescript
// components/query/notebookTypes.ts

export type FieldRef = {
	fieldId: number;
	joinAlias: string | null;
};

export type MergedField = {
	fieldId: number;
	name: string;
	displayName: string;
	baseType?: string;
	tableAlias: string | null;
	tableName: string;
};

export type JoinType = "left-join" | "inner-join" | "right-join" | "full-join";
export type JoinConditionOp = "=" | "!=" | ">" | ">=" | "<" | "<=";

export type JoinCondition = {
	id: string;
	leftField: FieldRef | null;
	op: JoinConditionOp;
	rightFieldId: number | null;
};

export type JoinConfig = {
	id: string;
	sourceTableId: number | null;
	alias: string;
	strategy: JoinType;
	conditions: JoinCondition[];
	conditionCombine: "and" | "or";
	tableDetail?: import("../../api/analyticsApi").TableDetail;
};

export type FilterOp =
	| "=" | "!=" | ">" | ">=" | "<" | "<="
	| "between" | "in"
	| "is-null" | "not-null"
	| "contains" | "starts-with" | "ends-with"
	| "is-empty" | "not-empty";

export type FilterRow = {
	id: string;
	field: FieldRef | null;
	op: FilterOp;
	value1: string;
	value2: string;
};

export type AggregationOp = "count" | "sum" | "avg" | "min" | "max";

export type AggregationRow = {
	id: string;
	op: AggregationOp;
	field: FieldRef | null;
};

export type NotebookState = {
	sourceTableId: number | null;
	sourceTableDetail?: import("../../api/analyticsApi").TableDetail;
	joins: JoinConfig[];
	selectedFields: FieldRef[];
	filters: FilterRow[];
	aggregations: AggregationRow[];
	groupByFields: FieldRef[];
	orderByKey: string;
	orderByDir: "asc" | "desc";
	limit: number;
};

export function makeId(): string {
	return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function fieldRefKey(ref: FieldRef): string {
	return ref.joinAlias ? `${ref.joinAlias}:${ref.fieldId}` : `${ref.fieldId}`;
}

export function fieldRefEquals(a: FieldRef | null, b: FieldRef | null): boolean {
	if (!a || !b) return false;
	return a.fieldId === b.fieldId && a.joinAlias === b.joinAlias;
}

export function fieldRefToMbql(ref: FieldRef): any[] {
	if (ref.joinAlias) return ["field", ref.fieldId, { "join-alias": ref.joinAlias }];
	return ["field", ref.fieldId, {}];
}

export function parseFieldRefFromMbql(node: unknown): FieldRef | null {
	if (!Array.isArray(node)) return null;
	if (node[0] !== "field") return null;
	const id = Number(node[1]);
	if (!Number.isFinite(id) || id <= 0) return null;
	const opts = node[2];
	const alias = opts && typeof opts === "object" && "join-alias" in opts
		? String((opts as Record<string, unknown>)["join-alias"])
		: null;
	return { fieldId: id, joinAlias: alias };
}
```

- [ ] **Step 2: Verify file compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: no errors related to notebookTypes.ts

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/notebookTypes.ts
git commit -m "feat(notebook): add type definitions for Notebook Editor"
```

---

## Task 2: API — Add getTableFks

**Files:**
- Modify: `api/analyticsApi.ts` (near line 1653, after `getTable`)

- [ ] **Step 1: Add getTableFks method to analyticsApi**

Add after the `getTable` line (~line 1653):

```typescript
	getTableFks: (tableId: string | number) =>
		fetchJson<Array<{ origin_id: number; origin: { id: number; name: string; table_id: number }; destination_id: number; destination: { id: number; name: string; table_id: number } }>>(`/analytics/api/table/${encodeURIComponent(String(tableId))}/fks`),
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/api/analyticsApi.ts
git commit -m "feat(notebook): add getTableFks API client method"
```

---

## Task 3: i18n — Add Notebook Keys

**Files:**
- Modify: `i18n.ts`

- [ ] **Step 1: Add zh-CN keys after the existing `builder.*` block (~line 157)**

Add these entries inside the `"zh-CN"` object, after `"builder.fieldsDisabled"`:

```typescript
		"notebook.step.dataSource": "选择数据",
		"notebook.step.join": "关联",
		"notebook.step.pickColumns": "选择列",
		"notebook.step.filter": "过滤",
		"notebook.step.summarize": "汇总",
		"notebook.step.sortLimit": "排序与限制",
		"notebook.join.addJoin": "添加关联",
		"notebook.join.remaining": "还可添加{n}个",
		"notebook.join.removeJoin": "移除此关联",
		"notebook.join.joinType": "关联类型",
		"notebook.join.leftJoin": "左关联（保留主表全部）",
		"notebook.join.innerJoin": "内关联（仅匹配行）",
		"notebook.join.rightJoin": "右关联（保留关联表全部）",
		"notebook.join.fullJoin": "全关联（保留两表全部）",
		"notebook.join.condition": "关联条件",
		"notebook.join.advancedCondition": "高级条件",
		"notebook.join.addCondition": "添加条件",
		"notebook.join.conditionCombine": "条件组合",
		"notebook.join.selectTable": "选择关联表...",
		"notebook.join.selectField": "选择字段...",
		"notebook.join.incompleteWarning": "请选择关联条件",
		"notebook.source.searchPlaceholder": "搜索表、问题、模型...",
		"notebook.source.tables": "表",
		"notebook.source.savedQuestions": "已保存问题",
		"notebook.source.models": "模型",
		"notebook.source.recommended": "推荐（有外键关系）",
		"notebook.source.otherTables": "其他表",
		"notebook.columns.title": "选择列",
		"notebook.columns.disabled": "已启用汇总：输出列由分组和聚合决定。",
		"notebook.columns.selected": "已选",
		"notebook.columns.selectAll": "全选",
		"notebook.changeSourceConfirm": "更换主表将清空所有关联、过滤和汇总配置，是否继续？",
		"notebook.advanced": "高级",
```

- [ ] **Step 2: Add en keys in the `"en"` object at corresponding position**

```typescript
		"notebook.step.dataSource": "Pick Data",
		"notebook.step.join": "Join",
		"notebook.step.pickColumns": "Pick Columns",
		"notebook.step.filter": "Filter",
		"notebook.step.summarize": "Summarize",
		"notebook.step.sortLimit": "Sort & Limit",
		"notebook.join.addJoin": "Add Join",
		"notebook.join.remaining": "{n} more allowed",
		"notebook.join.removeJoin": "Remove Join",
		"notebook.join.joinType": "Join Type",
		"notebook.join.leftJoin": "Left Join (keep all from source)",
		"notebook.join.innerJoin": "Inner Join (matching rows only)",
		"notebook.join.rightJoin": "Right Join (keep all from joined)",
		"notebook.join.fullJoin": "Full Join (keep all from both)",
		"notebook.join.condition": "Join Condition",
		"notebook.join.advancedCondition": "Advanced Conditions",
		"notebook.join.addCondition": "Add Condition",
		"notebook.join.conditionCombine": "Combine With",
		"notebook.join.selectTable": "Select table to join...",
		"notebook.join.selectField": "Select field...",
		"notebook.join.incompleteWarning": "Please select join condition",
		"notebook.source.searchPlaceholder": "Search tables, questions, models...",
		"notebook.source.tables": "Tables",
		"notebook.source.savedQuestions": "Saved Questions",
		"notebook.source.models": "Models",
		"notebook.source.recommended": "Recommended (FK)",
		"notebook.source.otherTables": "Other Tables",
		"notebook.columns.title": "Pick Columns",
		"notebook.columns.disabled": "Summarize is active: output is determined by group-by and aggregations.",
		"notebook.columns.selected": "Selected",
		"notebook.columns.selectAll": "Select All",
		"notebook.changeSourceConfirm": "Changing the source table will clear all joins, filters, and summaries. Continue?",
		"notebook.advanced": "Advanced",
```

- [ ] **Step 3: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/i18n.ts
git commit -m "feat(notebook): add i18n keys for Notebook Editor (zh-CN + en)"
```

---

## Task 4: Shared Component — StepCard

**Files:**
- Create: `components/query/shared/StepCard.tsx`

- [ ] **Step 1: Create StepCard component**

```tsx
// components/query/shared/StepCard.tsx
import { useState, type ReactNode } from "react";

type Props = {
	title: string;
	summary?: string | null;
	defaultExpanded?: boolean;
	disabled?: boolean;
	warning?: string | null;
	children: ReactNode;
};

export function StepCard({ title, summary, defaultExpanded = false, disabled = false, warning, children }: Props) {
	const [expanded, setExpanded] = useState(defaultExpanded);
	const isCollapsed = !expanded && summary != null;

	return (
		<div
			style={{
				border: warning ? "1px solid var(--color-warning, #E8A735)" : "1px solid var(--color-border, #e0e0e0)",
				borderRadius: "var(--radius-md, 8px)",
				marginBottom: "var(--spacing-sm, 8px)",
				opacity: disabled ? 0.5 : 1,
				pointerEvents: disabled ? "none" : undefined,
			}}
		>
			<div
				onClick={() => { if (summary != null) setExpanded((v) => !v); }}
				style={{
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					padding: "var(--spacing-sm, 8px) var(--spacing-md, 12px)",
					cursor: summary != null ? "pointer" : "default",
					userSelect: "none",
					background: isCollapsed ? "var(--color-bg-secondary, #f8f9fa)" : undefined,
					borderRadius: isCollapsed ? "var(--radius-md, 8px)" : "var(--radius-md, 8px) var(--radius-md, 8px) 0 0",
				}}
			>
				<strong>{title}</strong>
				{isCollapsed && (
					<span style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm, 13px)" }}>
						{summary}
					</span>
				)}
			</div>
			{!isCollapsed && (
				<div style={{ padding: "0 var(--spacing-md, 12px) var(--spacing-md, 12px)" }}>
					{children}
				</div>
			)}
			{warning && (
				<div style={{
					padding: "var(--spacing-xs, 4px) var(--spacing-md, 12px)",
					fontSize: "var(--font-size-sm, 13px)",
					color: "var(--color-warning, #E8A735)",
				}}>
					{warning}
				</div>
			)}
		</div>
	);
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/shared/StepCard.tsx
git commit -m "feat(notebook): add StepCard collapsible step component"
```

---

## Task 5: Shared Component — FieldPicker

**Files:**
- Create: `components/query/shared/FieldPicker.tsx`

- [ ] **Step 1: Create FieldPicker component**

```tsx
// components/query/shared/FieldPicker.tsx
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
			className="input"
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
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/shared/FieldPicker.tsx
git commit -m "feat(notebook): add FieldPicker with optgroup multi-table support"
```

---

## Task 6: Shared Component — TableSearchPicker

**Files:**
- Create: `components/query/shared/TableSearchPicker.tsx`

- [ ] **Step 1: Create TableSearchPicker component**

This component renders a searchable list of tables, grouped by schema, with FK-recommended tables at top. Used by both DataSourceStep and JoinStep.

```tsx
// components/query/shared/TableSearchPicker.tsx
import { useEffect, useMemo, useRef, useState } from "react";
import type { TableSummary } from "../../../api/analyticsApi";
import { t, type Locale } from "../../../i18n";

type FkInfo = {
	originFieldName: string;
	destinationFieldName: string;
	tableId: number;
};

type Props = {
	locale: Locale;
	tables: TableSummary[];
	fkRecommendations?: FkInfo[];
	value: number | null;
	onChange: (tableId: number) => void;
	placeholder?: string;
	disabled?: boolean;
};

export function TableSearchPicker({ locale, tables, fkRecommendations = [], value, onChange, placeholder, disabled }: Props) {
	const [search, setSearch] = useState("");
	const debounceRef = useRef<ReturnType<typeof setTimeout>>();
	const [debouncedSearch, setDebouncedSearch] = useState("");

	useEffect(() => {
		if (debounceRef.current) clearTimeout(debounceRef.current);
		debounceRef.current = setTimeout(() => setDebouncedSearch(search), 300);
		return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
	}, [search]);

	const fkTableIds = useMemo(() => new Set(fkRecommendations.map((f) => f.tableId)), [fkRecommendations]);

	const filtered = useMemo(() => {
		const q = debouncedSearch.toLowerCase().trim();
		if (!q) return tables;
		return tables.filter((t) => {
			const name = (t.display_name || t.name || "").toLowerCase();
			const desc = (t.description || "").toLowerCase();
			const schema = (t.schema || "").toLowerCase();
			return name.includes(q) || desc.includes(q) || schema.includes(q);
		});
	}, [tables, debouncedSearch]);

	const recommended = filtered.filter((t) => fkTableIds.has(t.id));
	const others = filtered.filter((t) => !fkTableIds.has(t.id));

	const schemaGroups = (items: TableSummary[]) => {
		const grouped = new Map<string, TableSummary[]>();
		for (const t of items) {
			const key = t.schema || "";
			const arr = grouped.get(key) ?? [];
			arr.push(t);
			grouped.set(key, arr);
		}
		return grouped;
	};

	const renderTable = (t: TableSummary) => {
		const fk = fkRecommendations.find((f) => f.tableId === t.id);
		const label = t.display_name || t.name || `table:${t.id}`;
		const isSelected = value === t.id;
		return (
			<div
				key={t.id}
				onClick={() => !disabled && onChange(t.id)}
				style={{
					padding: "6px 12px",
					cursor: disabled ? "default" : "pointer",
					background: isSelected ? "var(--color-primary-bg, #EBF5FF)" : undefined,
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
				}}
			>
				<span>{t.schema ? `${t.schema}.` : ""}{label}</span>
				{fk && (
					<span style={{ fontSize: "12px", color: "var(--color-text-tertiary)" }}>
						{fk.originFieldName} → {fk.destinationFieldName}
					</span>
				)}
				{isSelected && <span style={{ color: "var(--color-primary, #3B82F6)" }}>✓</span>}
			</div>
		);
	};

	return (
		<div>
			<input
				className="input"
				type="text"
				value={search}
				onChange={(e) => setSearch(e.target.value)}
				placeholder={placeholder ?? "Search..."}
				disabled={disabled}
				style={{ width: "100%", marginBottom: 8 }}
			/>
			<div style={{ maxHeight: 240, overflowY: "auto", border: "1px solid var(--color-border, #e0e0e0)", borderRadius: "var(--radius-sm, 4px)" }}>
				{recommended.length > 0 && (
					<>
						<div style={{ padding: "4px 12px", fontSize: "12px", fontWeight: 600, color: "var(--color-text-secondary)", background: "var(--color-bg-secondary, #f8f9fa)" }}>
							{t(locale, "notebook.source.recommended")}
						</div>
						{recommended.map(renderTable)}
					</>
				)}
				{others.length > 0 && (
					<>
						{Array.from(schemaGroups(others).entries()).map(([schema, items]) => (
							<div key={schema}>
								{schema && (
									<div style={{ padding: "4px 12px", fontSize: "12px", fontWeight: 600, color: "var(--color-text-secondary)", background: "var(--color-bg-secondary, #f8f9fa)" }}>
										{schema}
									</div>
								)}
								{items.map(renderTable)}
							</div>
						))}
					</>
				)}
				{filtered.length === 0 && (
					<div style={{ padding: "12px", textAlign: "center", color: "var(--color-text-tertiary)" }}>
						—
					</div>
				)}
			</div>
		</div>
	);
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/shared/TableSearchPicker.tsx
git commit -m "feat(notebook): add TableSearchPicker with FK recommendations"
```

---

## Task 7: Step 1 — DataSourceStep

**Files:**
- Create: `components/query/steps/DataSourceStep.tsx`

- [ ] **Step 1: Create DataSourceStep**

This step loads tables for the selected database, shows the TableSearchPicker, and reports the selected table back up. It also loads FK info for the selected table.

```tsx
// components/query/steps/DataSourceStep.tsx
import { useEffect, useState } from "react";
import { analyticsApi, type TableSummary, type TableDetail, type VisibleTable } from "../../../api/analyticsApi";
import { TableSearchPicker } from "../shared/TableSearchPicker";
import { ErrorNotice } from "../../ErrorNotice";
import { t, type Locale } from "../../../i18n";

type LoadState<T> =
	| { state: "idle" }
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type Props = {
	locale: Locale;
	databaseId: number | null;
	tableId: number | null;
	onTableSelected: (tableId: number, detail: TableDetail) => void;
};

export function DataSourceStep({ locale, databaseId, tableId, onTableSelected }: Props) {
	const [tables, setTables] = useState<LoadState<TableSummary[]>>({ state: "idle" });

	useEffect(() => {
		if (!databaseId) { setTables({ state: "idle" }); return; }
		let cancelled = false;
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
						if (typeof it === "number") { ids.add(it); continue; }
						const obj = it as VisibleTable;
						const id = Number(obj?.tableId);
						const dbId = Number(obj?.dbId);
						if (Number.isFinite(id) && id > 0 && (!Number.isFinite(dbId) || dbId === databaseId)) ids.add(id);
					}
				}
				const safe = Array.isArray(tableList) ? tableList : [];
				const filtered = ids.size > 0 ? safe.filter((t) => ids.has(t.id)) : safe;
				setTables({ state: "loaded", value: filtered });
			})
			.catch((e) => { if (!cancelled) setTables({ state: "error", error: e }); });

		return () => { cancelled = true; };
	}, [databaseId]);

	const handleSelect = async (id: number) => {
		try {
			const detail = await analyticsApi.getTable(id);
			onTableSelected(id, detail);
		} catch {
			// error handled by caller
		}
	};

	if (!databaseId) return null;

	return (
		<div>
			{tables.state === "error" && <ErrorNotice locale={locale} error={tables.error} />}
			{tables.state === "loading" && <div className="muted">{t(locale, "loading")}</div>}
			{tables.state === "loaded" && (
				<TableSearchPicker
					locale={locale}
					tables={tables.value}
					value={tableId}
					onChange={handleSelect}
					placeholder={t(locale, "notebook.source.searchPlaceholder")}
				/>
			)}
		</div>
	);
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/DataSourceStep.tsx
git commit -m "feat(notebook): add DataSourceStep with table search"
```

---

## Task 8: Step 2 — JoinStep

**Files:**
- Create: `components/query/steps/JoinStep.tsx`

- [ ] **Step 1: Create JoinStep**

This is the most complex step. It manages up to 3 JoinConfig entries, each with table picker, join type dropdown (with advanced separator), and condition editor (default single-equality + advanced multi-condition toggle).

Below is the complete implementation skeleton. The subagent should read spec Section 4 for full behavior requirements.

```tsx
// components/query/steps/JoinStep.tsx
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type TableSummary, type TableDetail } from "../../../api/analyticsApi";
import type { JoinConfig, JoinCondition, JoinType, JoinConditionOp, MergedField, FieldRef } from "../notebookTypes";
import { makeId, fieldRefKey } from "../notebookTypes";
import { TableSearchPicker } from "../shared/TableSearchPicker";
import { FieldPicker } from "../shared/FieldPicker";
import { t, type Locale } from "../../../i18n";

const MAX_JOINS = 3;

// --- Alias generation with deduplication ---
function generateAlias(tableName: string, existingAliases: string[]): string {
	const base = tableName || "t";
	if (!existingAliases.includes(base)) return base;
	let i = 2;
	while (existingAliases.includes(`${base}_${i}`)) i++;
	return `${base}_${i}`;
}

// --- FK info type ---
type FkInfo = { originFieldName: string; destinationFieldName: string; tableId: number; originFieldId: number; destinationFieldId: number };

// --- JoinCard sub-component (one per join) ---
function JoinCard(props: {
	locale: Locale;
	join: JoinConfig;
	tables: TableSummary[];
	fkRecommendations: FkInfo[];
	sourceFields: MergedField[];  // fields from source table + earlier joins
	onUpdate: (patch: Partial<JoinConfig>) => void;
	onRemove: () => void;
}) {
	const { locale, join, tables, fkRecommendations, sourceFields, onUpdate, onRemove } = props;
	const [advancedMode, setAdvancedMode] = useState(join.conditions.length > 1);

	// Fields from this join's table
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

	// Handle table selection — load detail, auto-fill FK condition
	const handleTableSelect = async (tableId: number) => {
		try {
			const detail = await analyticsApi.getTable(tableId);
			const tableName = detail.name || `t${tableId}`;

			// Check for FK auto-fill
			const fk = fkRecommendations.find((f) => f.tableId === tableId);
			const conditions: JoinCondition[] = fk
				? [{ id: makeId(), leftField: { fieldId: fk.destinationFieldId, joinAlias: null }, op: "=", rightFieldId: fk.originFieldId }]
				: [{ id: makeId(), leftField: null, op: "=", rightFieldId: null }];

			onUpdate({ sourceTableId: tableId, tableDetail: detail, conditions, alias: tableName });
		} catch {
			// error silently — user can retry
		}
	};

	// Join type dropdown with separator
	const joinTypeOptions: { value: JoinType; label: string; advanced?: boolean }[] = [
		{ value: "left-join", label: t(locale, "notebook.join.leftJoin") },
		{ value: "inner-join", label: t(locale, "notebook.join.innerJoin") },
		{ value: "right-join", label: t(locale, "notebook.join.rightJoin"), advanced: true },
		{ value: "full-join", label: t(locale, "notebook.join.fullJoin"), advanced: true },
	];

	// Condition row renderer
	const renderCondition = (cond: JoinCondition, index: number) => (
		<div key={cond.id} style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4 }}>
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
					className="input"
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
				<span style={{ width: 30, textAlign: "center" }}>=</span>
			)}
			<select
				className="input"
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
				<button className="btn" type="button" onClick={() => {
					onUpdate({ conditions: join.conditions.filter((c) => c.id !== cond.id) });
				}}>×</button>
			)}
		</div>
	);

	return (
		<div style={{
			border: "1px solid var(--color-border, #e0e0e0)",
			borderRadius: "var(--radius-sm, 4px)",
			padding: "var(--spacing-sm, 8px) var(--spacing-md, 12px)",
			marginBottom: 8,
		}}>
			{/* Table picker */}
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
					{/* Join type */}
					<div style={{ marginTop: 8 }}>
						<select
							className="input"
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

					{/* Conditions */}
					<div style={{ marginTop: 8 }}>
						<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
							<span className="muted">{t(locale, "notebook.join.condition")}</span>
							<button
								className="btn"
								type="button"
								onClick={() => {
									if (advancedMode) {
										// Collapse to default: keep only first condition (confirm if >1)
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
							<div style={{ marginBottom: 4 }}>
								<select
									className="input"
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
							<button className="btn" type="button" onClick={() => {
								onUpdate({ conditions: [...join.conditions, { id: makeId(), leftField: null, op: "=", rightFieldId: null }] });
							}}>
								{t(locale, "notebook.join.addCondition")}
							</button>
						)}
					</div>
				</>
			)}

			<div style={{ marginTop: 8, textAlign: "right" }}>
				<button className="btn" type="button" onClick={onRemove}>
					{t(locale, "notebook.join.removeJoin")}
				</button>
			</div>
		</div>
	);
}

// --- Main JoinStep component ---
type Props = {
	locale: Locale;
	sourceTableId: number | null;
	tables: TableSummary[];
	joins: JoinConfig[];
	allFieldsBeforeJoins: MergedField[];  // source table fields only
	onJoinsChange: (joins: JoinConfig[]) => void;
};

export function JoinStep({ locale, sourceTableId, tables, joins, allFieldsBeforeJoins, onJoinsChange }: Props) {
	const [fkInfos, setFkInfos] = useState<FkInfo[]>([]);

	// Load FK info when source table changes
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

	// Compute cumulative fields for each join card (source + earlier joins)
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

		// If alias needs generation (table just selected)
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
				className="btn"
				type="button"
				onClick={addJoin}
				disabled={joins.length >= MAX_JOINS || !sourceTableId}
			>
				{t(locale, "notebook.join.addJoin")}
				{remaining > 0 && remaining < MAX_JOINS && (
					<span style={{ marginLeft: 8, fontSize: 12, opacity: 0.7 }}>
						({t(locale, "notebook.join.remaining").replace("{n}", String(remaining))})
					</span>
				)}
			</button>
		</div>
	);
}
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/JoinStep.tsx
git commit -m "feat(notebook): add JoinStep with FK recommendations and advanced conditions"
```

---

## Task 9: Step 3 — PickColumnsStep

**Files:**
- Create: `components/query/steps/PickColumnsStep.tsx`

- [ ] **Step 1: Create PickColumnsStep**

Renders checkboxes for each field in the merged field list. When summarizing is active, shows disabled hint text instead.

```tsx
// components/query/steps/PickColumnsStep.tsx
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

	// Group by table
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
```

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/PickColumnsStep.tsx
git commit -m "feat(notebook): add PickColumnsStep with multi-table field selection"
```

---

## Task 10: Step 4 — FilterStep

**Files:**
- Create: `components/query/steps/FilterStep.tsx`

- [ ] **Step 1: Create FilterStep**

Port the existing filter logic from `QueryBuilder.tsx` (lines 161-196 for MBQL building, lines 661-757 for UI), but replace bare `fieldId` with `FieldRef` and use `FieldPicker` for field selection. Keep the same filter operators, value inputs, and datalist field-values behavior.

Key changes from existing:
- `FilterRow.field: FieldRef | null` instead of `fieldId: number | null`
- Use `FieldPicker` component instead of inline `<select>` with table fields
- `buildMbqlFilter()` uses `fieldRefToMbql()` instead of `["field", fieldId]`

Code: **Port from existing QueryBuilder filter UI (~100 lines).** The subagent should read `QueryBuilder.tsx` lines 116-196 (filter parsing/building) and 661-757 (filter UI) as reference.

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/FilterStep.tsx
git commit -m "feat(notebook): add FilterStep with FieldRef-based multi-table filters"
```

---

## Task 11: Step 5 — SummarizeStep

**Files:**
- Create: `components/query/steps/SummarizeStep.tsx`

- [ ] **Step 1: Create SummarizeStep**

Port the existing summarize logic from `QueryBuilder.tsx` (lines 540-620 for UI), but with `FieldRef`-based group-by and aggregation fields, using `FieldPicker`.

Key changes from existing:
- `AggregationRow.field: FieldRef | null` instead of `fieldId: number | null`
- `groupByFields: FieldRef[]` instead of `groupByFieldIds: number[]`
- Use `FieldPicker` for aggregation field selection
- Group-by checkboxes rendered per table group (from merged field list)

Code: **Port from existing QueryBuilder summarize UI (~80 lines).** The subagent should read `QueryBuilder.tsx` lines 540-620 as reference.

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/SummarizeStep.tsx
git commit -m "feat(notebook): add SummarizeStep with FieldRef-based aggregation"
```

---

## Task 12: Step 6 — SortLimitStep

**Files:**
- Create: `components/query/steps/SortLimitStep.tsx`

- [ ] **Step 1: Create SortLimitStep**

Port the existing sort/limit UI from `QueryBuilder.tsx` (lines 486-536), using `FieldPicker` for sort field selection. When summarized, sort options switch to group-by fields + aggregation result labels.

Code: **Port from existing QueryBuilder sort/limit UI (~50 lines).**

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/steps/SortLimitStep.tsx
git commit -m "feat(notebook): add SortLimitStep"
```

---

## Task 13: NotebookEditor — Main Container

**Files:**
- Create: `components/query/NotebookEditor.tsx`

- [ ] **Step 1: Create NotebookEditor**

This is the main container that:
1. Holds all `NotebookState`
2. Computes `allFields: MergedField[]` from `sourceTableDetail + joins[].tableDetail`
3. Computes `currentDatasetQuery` (MBQL with joins) via `useMemo`
4. Calls `onDatasetQueryChange(mbql)` on state changes
5. Renders 6 StepCards with the step components
6. Handles `applyInitialDatasetQuery()` for loading saved queries (including parsing `joins` array)
7. Handles source table change confirmation

**Props interface (must match existing QueryBuilder):**

```typescript
type Props = {
	databaseId: number | null;
	initialDatasetQuery?: Record<string, unknown> | null;
	onDatasetQueryChange?: (datasetQuery: Record<string, unknown> | null) => void;
};
```

**Key implementation details:**

- `allFields` computed from `sourceTableDetail.fields` (with `tableAlias: null`) + each `join.tableDetail.fields` (with `tableAlias: join.alias`)
- MBQL building: same structure as spec Section 7, using `fieldRefToMbql()` for all field references
- `joins` array in MBQL: map each valid `JoinConfig` to `{ "source-table": id, alias, strategy, condition }`
- `applyInitialDatasetQuery`: parse `joins` array from MBQL, load table details for each join
- When source table changes: `window.confirm()` with i18n message, then reset downstream state

Code: **~200-250 lines.** The subagent should read the spec Sections 7-9 carefully for MBQL format and backward compatibility requirements.

- [ ] **Step 2: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/components/query/NotebookEditor.tsx
git commit -m "feat(notebook): add NotebookEditor main container with MBQL builder"
```

---

## Task 14: Integration — Switch CardEditorPage

**Files:**
- Modify: `pages/CardEditorPage.tsx` (line 16)

- [ ] **Step 1: Change import**

In `CardEditorPage.tsx`, change:

```typescript
import { QueryBuilder } from "../components/query/QueryBuilder";
```

to:

```typescript
import { NotebookEditor } from "../components/query/NotebookEditor";
```

- [ ] **Step 2: Change JSX usage**

On line 402, change:

```tsx
<QueryBuilder
```

to:

```tsx
<NotebookEditor
```

- [ ] **Step 3: Verify compiles**

Run: `cd source/dts-analytics-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`

- [ ] **Step 4: Smoke test**

Run: `cd source/dts-analytics-webapp && npm run build 2>&1 | tail -10`
Expected: build succeeds

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/CardEditorPage.tsx
git commit -m "feat(notebook): switch CardEditorPage to NotebookEditor"
```

---

## Task 15: Manual End-to-End Verification

- [ ] **Step 1: Start dev server and verify**

Run: `cd source/dts-analytics-webapp && npm run dev`

Manual verification checklist:
1. Navigate to /questions/new — see Notebook Editor with 6 steps
2. Select a database and table in Step 1 — steps 2-6 appear
3. Add a JOIN in Step 2 — pick table, verify FK recommendation, set condition
4. Pick columns in Step 3 — verify merged field list shows both tables
5. Add a filter in Step 4 — verify joined table fields available
6. Add aggregation in Step 5 — verify Step 3 becomes disabled
7. Set sort and limit in Step 6
8. Click Run — verify query executes correctly
9. Save the query — reload and verify it loads back correctly
10. Edit an existing saved query with no joins — verify backward compatibility

- [ ] **Step 2: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix(notebook): address issues from E2E verification"
```
