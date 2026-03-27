# Notebook Editor with JOIN Support — Design Spec

**Date:** 2026-03-27
**Status:** Draft
**Scope:** dts-analytics-webapp query builder refactoring

## 1. Overview

Refactor the flat-layout `QueryBuilder.tsx` into a **Notebook Editor** with collapsible step cards, adding multi-table JOIN support. The backend (`MbqlToSqlService.java`) already fully supports MBQL joins — this is a frontend-only change.

### Goals

- Replace the flat query builder with a guided, step-based Notebook Editor
- Add visual JOIN configuration (up to 3 joins per query)
- Integrate data source search into the query building flow
- Maintain backward compatibility with existing saved queries (MBQL without joins)

### Non-Goals

- Changes to the existing SearchPage (remains as global navigation search)
- Changes to SQL mode
- Changes to visualization/chart components
- Backend modifications

## 2. Notebook Step Flow

Six collapsible step cards, rendered top to bottom:

| Step | Title | Required | Description |
|------|-------|----------|-------------|
| 1 | Select Data | Yes | Pick source table, saved question, or model |
| 2 | Join | No | Configure up to 3 table joins |
| 3 | Pick Columns | No | Select which columns to return (disabled when summarizing) |
| 4 | Filter | No | Add WHERE conditions |
| 5 | Summarize | No | Aggregations + GROUP BY |
| 6 | Sort & Limit | No | ORDER BY + LIMIT |

### Step Behavior

- Each step is a collapsible card (`StepCard` component)
- Completed steps collapse to a single summary line
- Click summary to re-expand for editing
- Steps 2-6 appear only after Step 1 is completed
- Data flows top-down: changes in earlier steps may invalidate later steps

### Known Limitations

- **Self-joins:** The same table can be joined multiple times. Aliases are disambiguated (see Section 4).
- **Single ORDER BY:** Only one sort column supported. Multi-column sorting is deferred to a future iteration.
- **Join targets are physical tables only:** Saved questions and models can be used as the primary data source (Step 1), but join targets (Step 2) are limited to physical tables in the same database.

## 3. Step 1: Data Source Selector

### Data Source Types

| Type | Label (zh/en) | API |
|------|---------------|-----|
| Tables | 表 / Tables | `GET /analytics/api/table?db_id=` |
| Saved Questions | 已保存问题 / Saved Questions | `GET /analytics/api/search?q=` |
| Models | 模型 / Models | `GET /analytics/api/platform/visible-tables` |

### Search Integration

- Built-in search input with 300ms debounce
- Searches table name, display_name, and description
- Results grouped by schema, with FK-related tables promoted to top
- Replaces the data discovery aspect of the standalone SearchPage

### Interactions

- Selecting a table loads its `TableDetail` (fields) and collapses Step 1 to summary
- Changing the source table clears all downstream state (joins, filters, etc.) with a confirmation prompt

## 4. Step 2: JOIN Configuration

### Constraints

- Maximum 3 joins per query
- "Add Join" button shows remaining count (e.g., "2 more allowed")
- Button disabled when limit reached

### Join Card Layout

Each join is rendered as a card within Step 2 containing:

1. **Table Picker** — reuses `TableSearchPicker` from Step 1
2. **Join Type Selector** — dropdown with progressive disclosure
3. **Join Condition** — default mode (single equality) or advanced mode

### Table Selection with FK Recommendations

- Tables with foreign key relationships to the source table appear first under "Recommended"
- FK mapping shown inline (e.g., `customer_id → id`)
- All other tables available below under "Other Tables"
- When a recommended table is selected, join condition auto-fills from FK metadata

**FK API:** `GET /analytics/api/table/{tableId}/fks` — returns an array of FK relationships. Response shape:

```json
[
  {
    "origin_id": 45,        // FK field ID in source table
    "origin": { "id": 45, "name": "customer_id", "table_id": 10 },
    "destination_id": 15,   // PK field ID in target table
    "destination": { "id": 15, "name": "id", "table_id": 5 }
  }
]
```

A new `analyticsApi.getTableFks(tableId)` method must be added to the frontend API client. This endpoint already exists in the backend — no backend changes needed.

### Alias Generation

Join aliases are generated from the table name with deduplication:
- First join of table `orders` → alias `"orders"`
- Second join of same table → alias `"orders_2"`
- Third → `"orders_3"`

Aliases are **immutable once assigned** to avoid invalidating downstream field references in filters, aggregations, and sort. Deleting a join removes all downstream references that use its alias.

### Join Types

| Type | Default/Advanced | Label (zh) | Description |
|------|-----------------|------------|-------------|
| LEFT JOIN | Default | 左关联 (保留主表全部) | Keep all rows from source table |
| INNER JOIN | Default | 内关联 (仅匹配行) | Only matching rows |
| RIGHT JOIN | Advanced | 右关联 (保留关联表全部) | Keep all rows from joined table |
| FULL JOIN | Advanced | 全关联 (保留两表全部) | Keep all rows from both tables |

Dropdown shows LEFT/INNER above a separator line, RIGHT/FULL below with "Advanced" label.

### Join Conditions — Default Mode

Single equality condition:

```
[source_table.field ▾]  =  [joined_table.field ▾]
```

- Left dropdown: source table fields (+ fields from earlier joins)
- Right dropdown: current joined table fields
- Operator fixed to `=`

### Join Conditions — Advanced Mode

Activated by clicking "Advanced Conditions" toggle. Supports:

- Multiple conditions combined with AND or OR
- Operators: `=`, `!=`, `>`, `>=`, `<`, `<=`
- "Add Condition" button to add more rows

```
Combine: [AND ▾]
[customers.id ▾]     [= ▾]  [orders.customer_id ▾]
[customers.region ▾] [= ▾]  [orders.region ▾]       [×]
[+ Add Condition]
```

Switching from advanced back to default keeps only the first condition (with confirmation).

### Collapsed Summary

After configuration, each join collapses to: `LEFT JOIN orders ON id = customer_id`

## 5. Step 3: Pick Columns

When not summarizing (Step 5 has no aggregations), users can select which columns to include in the output. Uses the merged field list from all tables.

- Default: first 12 fields of the source table are selected (same as existing behavior)
- Joined table fields are unselected by default — user opts in
- When Step 5 has aggregations, this step is disabled (greyed out with hint text) — output columns are determined by GROUP BY + aggregation results
- MBQL output: `"fields": [["field", id, {}], ["field", id, {"join-alias": "xxx"}], ...]`

## 6. Steps 4-6: Filter, Summarize, Sort & Limit

### Merged Field List

All steps share a unified field list built from the source table plus all configured joins:

- Format: `tableName.fieldName`
- Grouped by table using `<optgroup>` in dropdowns
- Source table fields first, then joined tables in order of addition
- Implemented as shared `FieldPicker` component
- `FieldPicker` emits a `FieldRef` (not a bare `fieldId`) so downstream steps always know which table a field belongs to

### Step 4: Filter

Existing filter logic with updated field reference model:
- Field dropdown uses merged field list via `FieldPicker`
- `FilterRow` is updated to use `FieldRef` instead of bare `fieldId` (see Section 8 type definitions)
- MBQL serialization: source table fields → `["field", id, {}]`, joined fields → `["field", id, {"join-alias": "xxx"}]`
- Filter combination remains AND-only (same as existing behavior)

### Step 5: Summarize

Existing aggregation + group-by logic with updated field reference model:
- `AggregationRow` updated to use `FieldRef` instead of bare `fieldId`
- `groupByFieldIds` changed to `FieldRef[]`
- Field dropdown uses merged field list for both aggregation targets and group-by fields

### Step 6: Sort & Limit

Same as existing sort/limit logic. Changes:
- Sort field dropdown uses merged field list (or aggregation results when summarized)

## 6. MBQL Output Format

### Example: Query with JOIN

```json
{
  "database": 1,
  "type": "query",
  "query": {
    "source-table": 5,
    "joins": [
      {
        "source-table": 10,
        "alias": "orders",
        "strategy": "left-join",
        "condition": ["=",
          ["field", 15, {}],
          ["field", 45, {"join-alias": "orders"}]
        ]
      }
    ],
    "filter": ["and",
      ["=", ["field", 20, {}], "active"],
      [">", ["field", 45, {"join-alias": "orders"}], 100]
    ],
    "aggregation": [["count"], ["sum", ["field", 46, {"join-alias": "orders"}]]],
    "breakout": [["field", 21, {}]],
    "order-by": [["asc", ["field", 21, {}]]],
    "limit": 200
  }
}
```

### Field Reference Format

- Source table field: `["field", fieldId, {}]` or `["field", fieldId]`
- Joined table field: `["field", fieldId, {"join-alias": "aliasName"}]`

### Join Strategy Values

Map to backend `MbqlToSqlService` expected values:
- `"left-join"` → `LEFT JOIN`
- `"inner-join"` → `INNER JOIN`
- `"right-join"` → `RIGHT JOIN`
- `"full-join"` → `FULL OUTER JOIN`

### Advanced Condition MBQL

```json
{
  "condition": ["and",
    ["=", ["field", 15, {}], ["field", 45, {"join-alias": "orders"}]],
    ["=", ["field", 16, {}], ["field", 46, {"join-alias": "orders"}]]
  ]
}
```

## 7. Component Architecture

### File Structure

```
components/query/
├── NotebookEditor.tsx        — Main container, state owner
├── steps/
│   ├── DataSourceStep.tsx    — Step 1: data source with search
│   ├── JoinStep.tsx          — Step 2: join configuration
│   ├── PickColumnsStep.tsx   — Step 3: column selection
│   ├── FilterStep.tsx        — Step 4: filters
│   ├── SummarizeStep.tsx     — Step 5: aggregations + group by
│   └── SortLimitStep.tsx     — Step 6: order by + limit
├── shared/
│   ├── FieldPicker.tsx       — Unified field selector with optgroup
│   ├── TableSearchPicker.tsx — Searchable table picker (Steps 1 & 2)
│   └── StepCard.tsx          — Collapsible step card shell
└── notebookTypes.ts          — Type definitions
```

### Type Definitions

```typescript
// notebookTypes.ts

// --- Field References ---

/** Identifies a field across tables. joinAlias=null means source table. */
type FieldRef = {
  fieldId: number;
  joinAlias: string | null;
};

/** A field from the merged field list (source + all joins). */
type MergedField = {
  fieldId: number;
  name: string;
  displayName: string;
  baseType?: string;
  tableAlias: string | null;  // null = source table
  tableName: string;
};

// --- JOIN Types ---

type JoinType = "left-join" | "inner-join" | "right-join" | "full-join";
type JoinConditionOp = "=" | "!=" | ">" | ">=" | "<" | "<=";

type JoinCondition = {
  id: string;
  leftFieldId: number | null;
  op: JoinConditionOp;
  rightFieldId: number | null;
};

type JoinConfig = {
  id: string;
  sourceTableId: number | null;
  alias: string;              // generated from table name, immutable (see Section 4)
  strategy: JoinType;
  conditions: JoinCondition[];
  conditionCombine: "and" | "or";
  tableDetail?: TableDetail;
};

// --- Filter & Aggregation (updated for multi-table) ---

type FilterOp =
  | "=" | "!=" | ">" | ">=" | "<" | "<="
  | "between" | "in"
  | "is-null" | "not-null"
  | "contains" | "starts-with" | "ends-with"
  | "is-empty" | "not-empty";

type FilterRow = {
  id: string;
  field: FieldRef | null;     // was: fieldId: number | null
  op: FilterOp;
  value1: string;
  value2: string;
};

type AggregationOp = "count" | "sum" | "avg" | "min" | "max";

type AggregationRow = {
  id: string;
  op: AggregationOp;
  field: FieldRef | null;     // was: fieldId: number | null
};

// --- Top-level State ---

type NotebookState = {
  sourceTableId: number | null;
  sourceTableDetail?: TableDetail;
  joins: JoinConfig[];                // max 3
  selectedFields: FieldRef[];         // Step 3: Pick Columns
  filters: FilterRow[];               // Step 4: Filter
  aggregations: AggregationRow[];     // Step 5: Summarize
  groupByFields: FieldRef[];          // Step 5: GROUP BY
  orderByKey: string;                 // Step 6: Sort
  orderByDir: "asc" | "desc";
  limit: number;                      // Step 6: Limit
};
```

### State Flow

```
NotebookEditor (state owner)
  ├─ Computes allFields: MergedField[] from sourceTableDetail + joins[].tableDetail
  ├─ Computes currentDatasetQuery: MBQL from full state
  ├─ Calls onDatasetQueryChange(mbql) on state changes
  │
  ├─ DataSourceStep    → sets sourceTableId, loads sourceTableDetail
  ├─ JoinStep          → modifies joins[], loads join tableDetails
  ├─ PickColumnsStep   → modifies selectedFields[] (uses allFields, disabled when summarizing)
  ├─ FilterStep        → modifies filters[] (uses allFields, emits FieldRef per filter)
  ├─ SummarizeStep     → modifies aggregations[], groupByFields[] (uses allFields, emits FieldRef)
  └─ SortLimitStep     → modifies orderByKey, orderByDir, limit (uses allFields)
```

### Integration Point

```tsx
// CardEditorPage.tsx — no changes needed to this file
<NotebookEditor
  databaseId={dbId}
  initialDatasetQuery={dq}
  onDatasetQueryChange={setDatasetQuery}
/>
```

Same props interface as existing `QueryBuilder`. Old component preserved for cleanup later.

## 9. Backward Compatibility

- Existing saved queries (MBQL without `joins`) load correctly — `joins` defaults to `[]`
- `NotebookEditor.applyInitialDatasetQuery()` parses the `joins` array if present
- Existing `fields` arrays in MBQL are parsed into `selectedFields: FieldRef[]` (with `joinAlias: null`)
- Field references without `join-alias` metadata continue to work as source table fields
- Old `FilterRow` (bare `fieldId`) is migrated on parse: `fieldId` → `{ fieldId, joinAlias: null }`

## 10. Interaction Details

| Scenario | Behavior |
|----------|----------|
| Source table selected | Step 1 collapses to summary, Steps 2-5 appear |
| Source table changed | Confirmation prompt, then clear all joins/filters/etc. |
| Join deleted | Remove that join's field references from filters/summarize/sort |
| 3rd join added | "Add Join" button disabled |
| Advanced join type expanded | Separator + RIGHT/FULL shown with brief descriptions |
| Advanced conditions expanded | Multi-condition editor appears, first condition preserved |
| Advanced conditions collapsed | Keep only first condition (confirmation if >1 exists) |

## 11. Error Handling

| Error | Handling |
|-------|----------|
| Table load failure | ErrorNotice inside the step card, other steps unaffected |
| Incomplete join condition | Join excluded from MBQL output, card shows warning border |
| Join table selected but no condition | Warning hint: "Please select join condition" |

## 12. Internationalization

All user-facing strings support zh-CN and en locales. New i18n keys:

```
notebook.step.dataSource / notebook.step.join / notebook.step.filter
notebook.step.summarize / notebook.step.sortLimit
notebook.join.addJoin / notebook.join.remaining
notebook.join.removeJoin / notebook.join.joinType
notebook.join.leftJoin / notebook.join.innerJoin
notebook.join.rightJoin / notebook.join.fullJoin
notebook.join.condition / notebook.join.advancedCondition
notebook.join.addCondition / notebook.join.conditionCombine
notebook.source.searchPlaceholder / notebook.source.tables
notebook.source.savedQuestions / notebook.source.models
notebook.source.recommended / notebook.source.otherTables
```
