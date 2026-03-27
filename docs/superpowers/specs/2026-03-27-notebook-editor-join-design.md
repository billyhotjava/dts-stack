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

Five collapsible step cards, rendered top to bottom:

| Step | Title | Required | Description |
|------|-------|----------|-------------|
| 1 | Select Data | Yes | Pick source table, saved question, or model |
| 2 | Join | No | Configure up to 3 table joins |
| 3 | Filter | No | Add WHERE conditions |
| 4 | Summarize | No | Aggregations + GROUP BY |
| 5 | Sort & Limit | No | ORDER BY + LIMIT |

### Step Behavior

- Each step is a collapsible card (`StepCard` component)
- Completed steps collapse to a single summary line
- Click summary to re-expand for editing
- Steps 2-5 appear only after Step 1 is completed
- Data flows top-down: changes in earlier steps may invalidate later steps

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

After configuration, each join collapses to: `🔗 LEFT JOIN orders ON id = customer_id`

## 5. Steps 3-5: Filter, Summarize, Sort & Limit

### Merged Field List

All steps share a unified field list built from the source table plus all configured joins:

- Format: `tableName.fieldName`
- Grouped by table using `<optgroup>` in dropdowns
- Source table fields first, then joined tables in order of addition
- Implemented as shared `FieldPicker` component

### Step 3: Filter

Same as existing filter logic. Changes:
- Field dropdown uses merged field list
- MBQL field references include `{"join-alias": "xxx"}` for joined table fields

### Step 4: Summarize

Same as existing aggregation + group-by logic. Changes:
- Field dropdown uses merged field list for both aggregation targets and group-by fields

### Step 5: Sort & Limit

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
│   ├── FilterStep.tsx        — Step 3: filters
│   ├── SummarizeStep.tsx     — Step 4: aggregations + group by
│   └── SortLimitStep.tsx     — Step 5: order by + limit
├── shared/
│   ├── FieldPicker.tsx       — Unified field selector with optgroup
│   ├── TableSearchPicker.tsx — Searchable table picker (Steps 1 & 2)
│   └── StepCard.tsx          — Collapsible step card shell
└── notebookTypes.ts          — Type definitions
```

### Type Definitions

```typescript
// notebookTypes.ts

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
  alias: string;
  strategy: JoinType;
  conditions: JoinCondition[];
  conditionCombine: "and" | "or";
  tableDetail?: TableDetail;
};

type MergedField = {
  fieldId: number;
  name: string;
  displayName: string;
  baseType?: string;
  tableAlias: string | null;  // null = source table
  tableName: string;
};

type NotebookState = {
  sourceTableId: number | null;
  sourceTableDetail?: TableDetail;
  joins: JoinConfig[];
  filters: FilterRow[];
  aggregations: AggregationRow[];
  groupByFieldIds: FieldRef[];
  orderByKey: string;
  orderByDir: "asc" | "desc";
  limit: number;
};

type FieldRef = {
  fieldId: number;
  joinAlias: string | null;
};
```

### State Flow

```
NotebookEditor (state owner)
  ├─ Computes allFields: MergedField[] from sourceTableDetail + joins[].tableDetail
  ├─ Computes currentDatasetQuery: MBQL from full state
  ├─ Calls onDatasetQueryChange(mbql) on state changes
  │
  ├─ DataSourceStep → sets sourceTableId, loads sourceTableDetail
  ├─ JoinStep → modifies joins[], loads join tableDetails
  ├─ FilterStep → modifies filters[] (uses allFields)
  ├─ SummarizeStep → modifies aggregations[], groupByFieldIds (uses allFields)
  └─ SortLimitStep → modifies orderByKey, orderByDir, limit (uses allFields)
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

## 8. Backward Compatibility

- Existing saved queries (MBQL without `joins`) load correctly — `joins` defaults to `[]`
- `NotebookEditor.applyInitialDatasetQuery()` parses the `joins` array if present
- Field references without `join-alias` metadata continue to work as source table fields

## 9. Interaction Details

| Scenario | Behavior |
|----------|----------|
| Source table selected | Step 1 collapses to summary, Steps 2-5 appear |
| Source table changed | Confirmation prompt, then clear all joins/filters/etc. |
| Join deleted | Remove that join's field references from filters/summarize/sort |
| 3rd join added | "Add Join" button disabled |
| Advanced join type expanded | Separator + RIGHT/FULL shown with brief descriptions |
| Advanced conditions expanded | Multi-condition editor appears, first condition preserved |
| Advanced conditions collapsed | Keep only first condition (confirmation if >1 exists) |

## 10. Error Handling

| Error | Handling |
|-------|----------|
| Table load failure | ErrorNotice inside the step card, other steps unaffected |
| Incomplete join condition | Join excluded from MBQL output, card shows warning border |
| Join table selected but no condition | Warning hint: "Please select join condition" |

## 11. Internationalization

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
