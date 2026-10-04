# SQL IDE F5: Chart / Pivot / Plan / 二次查询 / Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 BottomPanel 从单一 Result tab 升级为 5 tab 切换器（Results / Chart / Pivot / Query Plan / Log）。Chart 用 ECharts 快速可视化、Pivot 用 tanstack-table 客户端透视、Plan 用 React Flow 树形展示 EXPLAIN 输出、二次查询通过 PostgreSQL 临时表（DuckDB 作为可选优化）、Log 显示重写前后 SQL + 执行 metadata。

**Architecture:** 后端新增 `SqlPlanService.explain(sql, engine, datasourceId)` 委托给 `SecuritySqlRewriter.guard()` 后调 `EXPLAIN (FORMAT JSON)`（Trino/PG）或纯文本（Hive），统一为 `PlanNode` 树。新增 `SqlSubqueryService` 使用 PostgreSQL 临时表 (`CREATE TEMP TABLE result_<id>`) 作为二次查询数据源（30min TTL）；DuckDB 实现作为 followup。前端 BottomPanel 改为 AntD Tabs，5 个面板各自实现，使用 useTabStore.gridState（已存在）记忆当前 active 子 tab。

**Tech Stack:** echarts@6 + echarts-for-react@3（项目已有）+ @xyflow/react@12（项目已有）+ @tanstack/react-table（F4 已加）+ Spring Boot；PostgreSQL `CREATE TEMP TABLE` 临时表机制。

---

## Spec Reference

- Sprint README: `worklog/v2.2.3/sprint-11-202604/README.md`
- Feature README: `worklog/v2.2.3/sprint-11-202604/features/F5-Chart-Pivot-Plan-二次查询/README.md`
- Tasks: T21–T25

## Dependencies

- **F1+F2+F3+F4 完成**
- echarts + @xyflow/react 已在 package.json
- T16 chunk storage / T17 ResultGrid / T18 useSqlExecution / T20 SqlIdeAuditActions 全部就位
- ExecEngine 当前是 `TRINO | HIVE` —— 需要扩展到 `POSTGRESQL` 以便 PG explain 能跑

## File Structure

**Backend（新增）：**

| 路径 | 职责 |
|---|---|
| `service/sql/SqlPlanServiceImpl.java` | 实现 SqlPlanService 接口（T01 占位现填）：3 个引擎 EXPLAIN |
| `service/sql/dto/PlanNodeDto.java` | record(id, operator, table, estimatedRows, estimatedCost, attributes Map, children List) |
| `service/sql/dto/PlanResultDto.java` | record(root PlanNodeDto, rawText, engine, explainTimeMs) |
| `service/sql/dto/ExplainRequest.java` | record(sql, engine, datasourceId, catalog) |
| `service/sql/SqlSubqueryService.java` + `Impl.java` | createTempView(executionId) → name；executeOnView(name, sql) |
| `service/sql/dto/TempViewDto.java` | record(viewName, executionId, rowCount, expiresAt) |
| `service/scheduling/SqlSubqueryViewCleaner.java` | @Scheduled 30 min 清理 |
| `web/rest/sql/SqlIdePlanController.java` | `POST /api/sql/v2/explain` |
| `web/rest/sql/SqlIdeSubqueryController.java` | `POST /api/sql/v2/temp-views` 创建 + `POST /api/sql/v2/temp-views/{name}/query` 执行 + `DELETE` 销毁 |
| `service/sql/dto/QueryLogDto.java` | record(executionId, originalSql, rewrittenSql, status, startedAt, finishedAt, rowCount, elapsedMs, errorMessage, scanRows, bytesProcessed) |

**Backend（修改）：**

| 路径 | 改动 |
|---|---|
| `service/sql/SqlPlanService.java` | T01 留下的空接口填充签名 |
| `web/rest/sql/SqlIdeExecutionController.java` | 追加 `GET /{id}/log` 返回 QueryLogDto |
| `domain/explore/ExecEnums.java` | ExecEngine 增加 POSTGRESQL |
| `pom.xml` | （可选）DuckDB 作为 future improvement，不在本 Sprint 引入 |

**Frontend（新增）：**

| 路径 | 职责 |
|---|---|
| `api/sqlIdePlan.ts` | postExplain |
| `api/sqlIdeSubquery.ts` | createTempView/executeOnView/deleteTempView |
| `api/sqlIdeLog.ts` | getExecutionLog |
| `result/BottomTabs.tsx` | AntD Tabs 容器，包含 5 个 panel slots |
| `result/ResultChart.tsx` | ECharts 图表选择 + 渲染 |
| `result/chartConfig.ts` | 自动推荐图表类型 + 默认配置生成 |
| `result/__tests__/chartConfig.test.ts` | TDD 推荐逻辑 |
| `result/ResultPivot.tsx` | tanstack-table 行/列/值拖放透视 |
| `result/QueryPlanView.tsx` | React Flow 树形 |
| `result/planLayout.ts` | 树→ React Flow nodes/edges 布局算法 |
| `result/__tests__/planLayout.test.ts` | TDD 布局 |
| `result/SubQueryButton.tsx` | "Query This Result" 按钮 + 流程 |
| `result/LogPanel.tsx` | Log 面板（含 rewrite 前后 SQL toggle） |

**Frontend（修改）：**

| 路径 | 改动 |
|---|---|
| `tabs/types.ts` | TabState 新增 `subqueryViewName: string \| null` |
| `tabs/useTabStore.ts` | openTab 默认 subqueryViewName: null |
| `SqlIde.tsx` | BottomPanel 内 Result 区替换为 BottomTabs（5 个 child panels） |

---

## Task 1 — T21: ResultChart (ECharts)

**Files:**
- Create: `result/chartConfig.ts` + `__tests__/chartConfig.test.ts`
- Create: `result/ResultChart.tsx`
- Create: `api/sqlIdePlan.ts` (placeholder for cross-import — actually used in T23; create empty for now or in T23)
- Modify: `tabs/types.ts` + `useTabStore.ts` (extend GridColumnState with `chartConfig` field — or store in separate slot)

> **Decision**: chart config is per-tab; reuse `gridState` slot is wrong (mixing concerns). Add a sibling `bottomTab: { active: "results"|"chart"|"pivot"|"plan"|"log", chartConfig: ChartConfig | null }` to TabState. For T21 only add `chartConfig`; the active-tab field arrives in the next subtask integration step.

### - [ ] Step 1.1: TDD chartConfig

`src/components/sql-ide/result/__tests__/chartConfig.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { recommendChart, type ChartConfig } from "../chartConfig";

describe("recommendChart", () => {
  it("returns line chart for 1 time + 1 number column", () => {
    const cfg = recommendChart([
      { name: "ts", dataType: "TIMESTAMP", nullable: false },
      { name: "value", dataType: "DOUBLE", nullable: false },
    ]);
    expect(cfg.type).toBe("line");
    expect(cfg.xAxis).toBe("ts");
    expect(cfg.yAxis).toEqual(["value"]);
  });

  it("returns bar chart for low-cardinality string + number", () => {
    const cfg = recommendChart([
      { name: "category", dataType: "VARCHAR", nullable: false },
      { name: "amount", dataType: "BIGINT", nullable: false },
    ]);
    expect(cfg.type).toBe("bar");
    expect(cfg.xAxis).toBe("category");
    expect(cfg.yAxis).toEqual(["amount"]);
  });

  it("returns scatter for two numeric columns", () => {
    const cfg = recommendChart([
      { name: "x", dataType: "DOUBLE", nullable: false },
      { name: "y", dataType: "DOUBLE", nullable: false },
    ]);
    expect(cfg.type).toBe("scatter");
  });

  it("falls back to bar for one column", () => {
    const cfg = recommendChart([
      { name: "x", dataType: "VARCHAR", nullable: false },
    ]);
    expect(cfg.type).toBe("bar");
  });

  it("falls back to bar for empty columns", () => {
    const cfg = recommendChart([]);
    expect(cfg.type).toBe("bar");
    expect(cfg.xAxis).toBe("");
    expect(cfg.yAxis).toEqual([]);
  });
});
```

### - [ ] Step 1.2: Run vitest → FAIL

```bash
cd source/dts-platform-webapp
pnpm vitest run src/components/sql-ide/result/__tests__/chartConfig.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 1.3: Implement chartConfig

`src/components/sql-ide/result/chartConfig.ts`:

```typescript
import type { ColumnMeta } from "../api/sqlIdeExecution";

export type ChartType = "bar" | "line" | "pie" | "scatter" | "area";

export interface ChartConfig {
  type: ChartType;
  xAxis: string;
  yAxis: string[];
  groupBy: string | null;
}

const NUMERIC_TYPES = /^(BIGINT|INT|INTEGER|SMALLINT|TINYINT|DOUBLE|FLOAT|REAL|DECIMAL|NUMERIC)$/i;
const TIME_TYPES = /^(TIMESTAMP|DATE|TIME|DATETIME)/i;

function isNumeric(col: ColumnMeta): boolean {
  return NUMERIC_TYPES.test(col.dataType ?? "");
}

function isTime(col: ColumnMeta): boolean {
  return TIME_TYPES.test(col.dataType ?? "");
}

export function recommendChart(columns: ColumnMeta[]): ChartConfig {
  if (columns.length === 0) {
    return { type: "bar", xAxis: "", yAxis: [], groupBy: null };
  }
  const timeCol = columns.find(isTime);
  const numericCols = columns.filter(isNumeric);
  const stringCols = columns.filter((c) => !isNumeric(c) && !isTime(c));

  if (timeCol && numericCols.length >= 1) {
    return { type: "line", xAxis: timeCol.name, yAxis: [numericCols[0].name], groupBy: null };
  }
  if (numericCols.length >= 2) {
    return {
      type: "scatter",
      xAxis: numericCols[0].name,
      yAxis: [numericCols[1].name],
      groupBy: null,
    };
  }
  if (stringCols.length >= 1 && numericCols.length >= 1) {
    return {
      type: "bar",
      xAxis: stringCols[0].name,
      yAxis: [numericCols[0].name],
      groupBy: null,
    };
  }
  return { type: "bar", xAxis: columns[0].name, yAxis: [], groupBy: null };
}

export function buildEChartsOption(
  cfg: ChartConfig,
  rows: Array<Record<string, unknown>>,
): Record<string, unknown> {
  if (!cfg.xAxis || cfg.yAxis.length === 0) {
    return { title: { text: "请选择 X / Y 轴", left: "center", top: "middle" } };
  }
  const xData = rows.map((r) => r[cfg.xAxis]);
  const series = cfg.yAxis.map((y) => ({
    name: y,
    type: cfg.type === "area" ? "line" : cfg.type,
    data: rows.map((r) => r[y]),
    areaStyle: cfg.type === "area" ? {} : undefined,
  }));
  if (cfg.type === "pie") {
    return {
      tooltip: { trigger: "item" },
      series: [
        {
          type: "pie",
          radius: "55%",
          data: rows.map((r) => ({ name: String(r[cfg.xAxis]), value: r[cfg.yAxis[0]] })),
        },
      ],
    };
  }
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: xData },
    yAxis: { type: "value" },
    series,
    legend: { top: 4 },
    grid: { top: 32, bottom: 32, left: 48, right: 16 },
  };
}
```

### - [ ] Step 1.4: vitest PASS

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/chartConfig.test.ts
```

**Expected:** 5/5 PASS.

### - [ ] Step 1.5: ResultChart component

`src/components/sql-ide/result/ResultChart.tsx`:

```tsx
import { useQuery } from "@tanstack/react-query";
import { Empty, Select, Spin } from "antd";
import EChartsReact from "echarts-for-react";
import { type FC, useMemo, useState } from "react";
import { getExecutionMeta, getExecutionPage } from "../api/sqlIdeExecution";
import {
  type ChartConfig,
  type ChartType,
  buildEChartsOption,
  recommendChart,
} from "./chartConfig";

export interface ResultChartProps {
  executionId: string;
}

const CHART_TYPE_OPTIONS: Array<{ value: ChartType; label: string }> = [
  { value: "bar", label: "柱状图" },
  { value: "line", label: "折线图" },
  { value: "area", label: "面积图" },
  { value: "pie", label: "饼图" },
  { value: "scatter", label: "散点图" },
];

export const ResultChart: FC<ResultChartProps> = ({ executionId }) => {
  const { data: meta, isLoading: metaLoading } = useQuery({
    queryKey: ["sqlide", "execution", "meta", executionId],
    queryFn: () => getExecutionMeta(executionId),
    staleTime: 60_000,
  });
  const { data: page, isLoading: pageLoading } = useQuery({
    queryKey: ["sqlide", "execution", "chart-page", executionId],
    queryFn: () => getExecutionPage(executionId, 1, 5000),
    enabled: !!meta,
    staleTime: 60_000,
  });

  const initial = useMemo(() => (meta ? recommendChart(meta.columns) : null), [meta]);
  const [config, setConfig] = useState<ChartConfig | null>(null);
  const effectiveConfig = config ?? initial;

  if (metaLoading || pageLoading) {
    return <div style={{ padding: 24, textAlign: "center" }}><Spin /></div>;
  }
  if (!meta || !page || meta.columns.length === 0) {
    return <Empty description="无数据" />;
  }
  if (!effectiveConfig) return <Empty description="正在准备图表配置" />;

  const option = buildEChartsOption(effectiveConfig, page.rows);
  const colOptions = meta.columns.map((c) => ({ value: c.name, label: c.name }));

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ flex: 1, minWidth: 0, padding: 8 }}>
        <EChartsReact option={option} style={{ height: "100%", minHeight: 240 }} notMerge lazyUpdate />
      </div>
      <div style={{ width: 220, padding: "8px 12px", borderLeft: "1px solid var(--ant-color-border-secondary)", overflow: "auto" }}>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}>图表类型</div>
          <Select size="small" style={{ width: "100%" }} value={effectiveConfig.type}
            options={CHART_TYPE_OPTIONS}
            onChange={(v) => setConfig({ ...effectiveConfig, type: v })} />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}>X 轴</div>
          <Select size="small" style={{ width: "100%" }} value={effectiveConfig.xAxis}
            options={colOptions} onChange={(v) => setConfig({ ...effectiveConfig, xAxis: v })} />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}>Y 轴</div>
          <Select size="small" style={{ width: "100%" }} mode="multiple" value={effectiveConfig.yAxis}
            options={colOptions} onChange={(v) => setConfig({ ...effectiveConfig, yAxis: v })} />
        </div>
      </div>
    </div>
  );
};
```

### - [ ] Step 1.6: Verify

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 61 + 5 = **66 PASS** (61 prior + 5 chartConfig).

### - [ ] Step 1.7: Commit

```bash
git add source/dts-platform-webapp/src/components/sql-ide/result/chartConfig.ts \
        source/dts-platform-webapp/src/components/sql-ide/result/__tests__/chartConfig.test.ts \
        source/dts-platform-webapp/src/components/sql-ide/result/ResultChart.tsx
git commit -m "$(cat <<'EOF'
feat(F5/T21): ResultChart with ECharts + auto-recommend type

Sprint-11 F5 T21: chartConfig.ts (TDD: 5 tests) inspects column types
to recommend bar/line/pie/scatter/area; buildEChartsOption builds the
ECharts option from config + rows. ResultChart fetches meta + first
5000 rows via React Query, renders EChartsReact + a right-side
config panel (type/xAxis/yAxis dropdowns).

Save-to-dashboard deferred to F5 followup; this lands the chart UI.
EOF
)"
```

---

## Task 2 — T22: ResultPivot

**Files:**
- Create: `result/ResultPivot.tsx`

### - [ ] Step 2.1: ResultPivot component

`src/components/sql-ide/result/ResultPivot.tsx`:

Pure client-side aggregation (no new dep). Use `useMemo` to compute the pivot table.

```tsx
import { useQuery } from "@tanstack/react-query";
import { Empty, Select, Spin } from "antd";
import { type FC, useMemo, useState } from "react";
import { getExecutionMeta, getExecutionPage } from "../api/sqlIdeExecution";

export interface ResultPivotProps {
  executionId: string;
}

type AggFn = "SUM" | "COUNT" | "AVG" | "MIN" | "MAX" | "DISTINCT_COUNT";

const AGG_OPTIONS: Array<{ value: AggFn; label: string }> = [
  { value: "SUM", label: "SUM" },
  { value: "COUNT", label: "COUNT" },
  { value: "AVG", label: "AVG" },
  { value: "MIN", label: "MIN" },
  { value: "MAX", label: "MAX" },
  { value: "DISTINCT_COUNT", label: "DISTINCT COUNT" },
];

const MAX_ROWS = 10_000;

function aggregate(values: unknown[], fn: AggFn): number | string {
  const nums = values.filter((v) => typeof v === "number") as number[];
  switch (fn) {
    case "SUM":
      return nums.reduce((a, b) => a + b, 0);
    case "COUNT":
      return values.filter((v) => v !== null && v !== undefined).length;
    case "AVG":
      return nums.length === 0 ? 0 : nums.reduce((a, b) => a + b, 0) / nums.length;
    case "MIN":
      return nums.length === 0 ? "" : Math.min(...nums);
    case "MAX":
      return nums.length === 0 ? "" : Math.max(...nums);
    case "DISTINCT_COUNT":
      return new Set(values).size;
  }
}

export const ResultPivot: FC<ResultPivotProps> = ({ executionId }) => {
  const { data: meta, isLoading: metaLoading } = useQuery({
    queryKey: ["sqlide", "execution", "meta", executionId],
    queryFn: () => getExecutionMeta(executionId),
    staleTime: 60_000,
  });
  const { data: page, isLoading: pageLoading } = useQuery({
    queryKey: ["sqlide", "execution", "pivot-page", executionId],
    queryFn: () => getExecutionPage(executionId, 1, MAX_ROWS),
    enabled: !!meta,
    staleTime: 60_000,
  });

  const [rowField, setRowField] = useState<string | null>(null);
  const [colField, setColField] = useState<string | null>(null);
  const [valueField, setValueField] = useState<string | null>(null);
  const [aggFn, setAggFn] = useState<AggFn>("SUM");

  const tooLarge = (page?.total ?? 0) > MAX_ROWS;

  const pivot = useMemo(() => {
    if (!page || !rowField || !valueField) return null;
    const rowKeys = new Set<string>();
    const colKeys = new Set<string>();
    const cell = new Map<string, Map<string, unknown[]>>();

    for (const r of page.rows) {
      const rk = String(r[rowField] ?? "");
      const ck = colField ? String(r[colField] ?? "") : "TOTAL";
      const v = r[valueField];
      rowKeys.add(rk);
      colKeys.add(ck);
      if (!cell.has(rk)) cell.set(rk, new Map());
      const inner = cell.get(rk)!;
      if (!inner.has(ck)) inner.set(ck, []);
      inner.get(ck)!.push(v);
    }
    const sortedRows = [...rowKeys].sort();
    const sortedCols = [...colKeys].sort();
    return { rows: sortedRows, cols: sortedCols, cell };
  }, [page, rowField, colField, valueField]);

  if (metaLoading || pageLoading) {
    return <div style={{ padding: 24, textAlign: "center" }}><Spin /></div>;
  }
  if (!meta || !page) return <Empty description="无数据" />;
  if (tooLarge) {
    return <Empty description={`数据量 ${page.total} 行 > ${MAX_ROWS}，请在 SQL 中先聚合`} />;
  }

  const colOptions = meta.columns.map((c) => ({ value: c.name, label: c.name }));

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ flex: 1, padding: 8, overflow: "auto" }}>
        {pivot ? (
          <table style={{ borderCollapse: "collapse", fontSize: 12 }}>
            <thead>
              <tr>
                <th style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{rowField}</th>
                {pivot.cols.map((c) => (
                  <th key={c} style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pivot.rows.map((rk) => (
                <tr key={rk}>
                  <td style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{rk}</td>
                  {pivot.cols.map((ck) => {
                    const vals = pivot.cell.get(rk)?.get(ck) ?? [];
                    return (
                      <td key={ck} style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)", textAlign: "right" }}>
                        {String(aggregate(vals, aggFn))}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty description="请配置行/列/值字段" />
        )}
      </div>
      <div style={{ width: 220, padding: "8px 12px", borderLeft: "1px solid var(--ant-color-border-secondary)" }}>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>行</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={rowField ?? undefined} onChange={setRowField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>列（可选）</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={colField ?? undefined} onChange={setColField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>值</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={valueField ?? undefined} onChange={setValueField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>聚合</div>
          <Select size="small" style={{ width: "100%" }} options={AGG_OPTIONS} value={aggFn} onChange={setAggFn} />
        </div>
      </div>
    </div>
  );
};
```

### - [ ] Step 2.2: Verify

```bash
pnpm tsc --noEmit
pnpm vitest run src/components/sql-ide
```

**Expected:** tsc clean, 66/66 PASS still (no new tests).

### - [ ] Step 2.3: Commit

```bash
git add source/dts-platform-webapp/src/components/sql-ide/result/ResultPivot.tsx
git commit -m "$(cat <<'EOF'
feat(F5/T22): client-side ResultPivot with 6 aggregation functions

Sprint-11 F5 T22: ResultPivot does in-memory pivot of up to 10k rows
using row/col/value field selectors and 6 aggregation functions
(SUM/COUNT/AVG/MIN/MAX/DISTINCT_COUNT). Larger result sets prompt
the user to pre-aggregate in SQL. No new dependencies.
EOF
)"
```

---

## Task 3 — T23: Query Plan (后端 EXPLAIN + 前端 React Flow)

**Files:**
- Modify: `domain/explore/ExecEnums.java` (add POSTGRESQL)
- Modify: `service/sql/SqlPlanService.java` (interface signatures)
- Create: `service/sql/SqlPlanServiceImpl.java`
- Create: `service/sql/dto/{ExplainRequest, PlanNodeDto, PlanResultDto}.java`
- Create: `web/rest/sql/SqlIdePlanController.java`
- Create: `src/test/java/.../web/rest/sql/SqlIdePlanControllerIT.java`
- Create: `src/components/sql-ide/api/sqlIdePlan.ts`
- Create: `src/components/sql-ide/result/planLayout.ts` + `__tests__/planLayout.test.ts`
- Create: `src/components/sql-ide/result/QueryPlanView.tsx`

### - [ ] Step 3.1: Add POSTGRESQL to ExecEngine

In `domain/explore/ExecEnums.java`:

```java
public enum ExecEngine { TRINO, HIVE, POSTGRESQL }
```

Verify nothing else breaks (compilation will catch switch-exhaustiveness issues).

### - [ ] Step 3.2: DTOs

`service/sql/dto/ExplainRequest.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
import java.util.UUID;
public record ExplainRequest(String sql, String engine, UUID datasourceId, String catalog) {}
```

`service/sql/dto/PlanNodeDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
import java.util.List;
import java.util.Map;
public record PlanNodeDto(
    String id,
    String operator,
    String table,
    Double estimatedRows,
    Double estimatedCost,
    Map<String, String> attributes,
    List<PlanNodeDto> children
) {}
```

`service/sql/dto/PlanResultDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
public record PlanResultDto(
    PlanNodeDto root,
    String rawText,
    String engine,
    long explainTimeMs
) {}
```

### - [ ] Step 3.3: Fill SqlPlanService interface

Replace `service/sql/SqlPlanService.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;

public interface SqlPlanService {
    PlanResultDto explain(ExplainRequest req);
}
```

### - [ ] Step 3.4: Implement SqlPlanServiceImpl

`service/sql/SqlPlanServiceImpl.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanNodeDto;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SqlPlanServiceImpl implements SqlPlanService {

    private final QueryGateway queryGateway;
    private final SecuritySqlRewriter rewriter;
    private final ObjectMapper objectMapper;

    public SqlPlanServiceImpl(QueryGateway queryGateway, SecuritySqlRewriter rewriter, ObjectMapper objectMapper) {
        this.queryGateway = queryGateway;
        this.rewriter = rewriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public PlanResultDto explain(ExplainRequest req) {
        long t0 = System.currentTimeMillis();
        String guarded = rewriter.guard(req.sql(), null);  // adapt: pass principal/level if rewriter requires
        String engine = req.engine() == null ? "trino" : req.engine().toLowerCase();
        String explainSql;
        switch (engine) {
            case "trino" -> explainSql = "EXPLAIN (FORMAT JSON) " + guarded;
            case "postgresql", "postgres" -> explainSql = "EXPLAIN (FORMAT JSON, ANALYZE false) " + guarded;
            default -> explainSql = "EXPLAIN " + guarded;  // hive
        }

        Map<String, Object> raw;
        try {
            raw = queryGateway.execute(explainSql, req.datasourceId(), req.catalog());
        } catch (Exception e) {
            // Surface error as a single-node plan
            PlanNodeDto err = new PlanNodeDto("err", "ExplainError", null, null, null,
                Map.of("error", e.getMessage() == null ? "unknown" : e.getMessage()), List.of());
            return new PlanResultDto(err, e.getMessage(), engine, System.currentTimeMillis() - t0);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) raw.getOrDefault("rows", List.of());
        String rawText = rows.stream()
            .map(r -> r.values().stream().findFirst().map(String::valueOf).orElse(""))
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        PlanNodeDto root;
        if (engine.equals("trino") || engine.startsWith("postgres")) {
            root = parseJsonPlan(rawText, engine);
        } else {
            root = parseHiveTextPlan(rawText);
        }
        return new PlanResultDto(root, rawText, engine, System.currentTimeMillis() - t0);
    }

    private PlanNodeDto parseJsonPlan(String rawText, String engine) {
        try {
            String trimmed = rawText.trim();
            // PG returns "[ { ... } ]"; Trino returns nested object — both parsable
            JsonNode tree = objectMapper.readTree(trimmed);
            JsonNode planRoot = tree.isArray() && tree.size() > 0 ? tree.get(0) : tree;
            // PG nests under "Plan"; Trino under root.
            JsonNode actual = planRoot.has("Plan") ? planRoot.get("Plan") : planRoot;
            return jsonToNode(actual, "n0");
        } catch (Exception e) {
            return new PlanNodeDto("n0", "ParseError", null, null, null,
                Map.of("error", String.valueOf(e.getMessage())), List.of());
        }
    }

    private PlanNodeDto jsonToNode(JsonNode node, String id) {
        String operator = node.has("Node Type") ? node.get("Node Type").asText() :
                          node.has("operator") ? node.get("operator").asText() :
                          node.has("name") ? node.get("name").asText() : "Operator";
        String table = node.has("Relation Name") ? node.get("Relation Name").asText() :
                       node.has("table") ? node.get("table").asText() : null;
        Double estRows = node.has("Plan Rows") ? node.get("Plan Rows").asDouble() :
                         node.has("estimatedRows") ? node.get("estimatedRows").asDouble() : null;
        Double estCost = node.has("Total Cost") ? node.get("Total Cost").asDouble() :
                         node.has("estimatedCost") ? node.get("estimatedCost").asDouble() : null;
        Map<String, String> attrs = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(f -> {
            if (List.of("Plans", "children", "Plan").contains(f)) return;
            JsonNode v = node.get(f);
            if (v.isValueNode()) attrs.put(f, v.asText());
        });
        List<PlanNodeDto> children = new ArrayList<>();
        JsonNode kids = node.has("Plans") ? node.get("Plans") :
                        node.has("children") ? node.get("children") : null;
        if (kids != null && kids.isArray()) {
            int i = 0;
            for (JsonNode k : kids) children.add(jsonToNode(k, id + "." + (i++)));
        }
        return new PlanNodeDto(id, operator, table, estRows, estCost, attrs, children);
    }

    private PlanNodeDto parseHiveTextPlan(String rawText) {
        // Hive's EXPLAIN is multi-section text. Capture the whole thing as a single attribute.
        return new PlanNodeDto("n0", "HivePlan", null, null, null,
            Map.of("text", rawText), List.of());
    }
}
```

> **Note**: `QueryGateway.execute(sql, datasourceId, catalog)` signature may differ — verify in HiveQueryGateway. If only 1-arg or accepts a request DTO, adapt. Simpler approach: directly grab a JDBC connection via `DataSource` injection if QueryGateway.execute is too restrictive. Implementer should adapt without breaking spec.

### - [ ] Step 3.5: Controller + IT

`web/rest/sql/SqlIdePlanController.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlPlanService;
import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdePlanController {

    private final SqlPlanService planService;
    private final AuditService auditService;

    public SqlIdePlanController(SqlPlanService planService, AuditService auditService) {
        this.planService = planService;
        this.auditService = auditService;
    }

    @PostMapping("/explain")
    public ApiResponse<PlanResultDto> explain(@RequestBody ExplainRequest req) {
        PlanResultDto result = planService.explain(req);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", req.engine());
        payload.put("sqlHash", Integer.toHexString(req.sql() == null ? 0 : req.sql().hashCode()));
        payload.put("actionCode", SqlIdeAuditActions.SQL_PLAN_VIEW);
        auditService.record(
            "READ", "sql.ide.plan", "sql.explain",
            "sqlHash:" + payload.get("sqlHash"), "SUCCESS", payload
        );
        return ApiResponses.ok(result);
    }
}
```

IT `SqlIdePlanControllerIT.java`:
```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdePlanControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void explainReturnsRootNodeEvenOnError() throws Exception {
        Map<String, Object> body = Map.of(
            "sql", "SELECT 1",
            "engine", "TRINO",
            "datasourceId", "00000000-0000-0000-0000-000000000000",
            "catalog", "mock"
        );
        mvc.perform(post("/api/sql/v2/explain").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.root.operator").exists());
    }
}
```

### - [ ] Step 3.6: TDD planLayout (frontend)

`src/components/sql-ide/result/__tests__/planLayout.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { layoutPlanNodes, type PlanNode } from "../planLayout";

const sample: PlanNode = {
  id: "n0",
  operator: "Project",
  table: null,
  estimatedRows: 100,
  estimatedCost: 5,
  attributes: {},
  children: [
    { id: "n0.0", operator: "TableScan", table: "users", estimatedRows: 100, estimatedCost: 1, attributes: {}, children: [] },
    { id: "n0.1", operator: "TableScan", table: "orders", estimatedRows: 100, estimatedCost: 1, attributes: {}, children: [] },
  ],
};

describe("layoutPlanNodes", () => {
  it("returns one rf-node per plan node + edges from parent to child", () => {
    const { nodes, edges } = layoutPlanNodes(sample);
    expect(nodes).toHaveLength(3);
    expect(edges).toHaveLength(2);
    expect(edges.map((e) => `${e.source}->${e.target}`).sort()).toEqual([
      "n0->n0.0",
      "n0->n0.1",
    ]);
  });

  it("positions children below parent with horizontal spread", () => {
    const { nodes } = layoutPlanNodes(sample);
    const root = nodes.find((n) => n.id === "n0")!;
    const c0 = nodes.find((n) => n.id === "n0.0")!;
    const c1 = nodes.find((n) => n.id === "n0.1")!;
    expect(c0.position.y).toBeGreaterThan(root.position.y);
    expect(c1.position.x).toBeGreaterThan(c0.position.x);
  });

  it("handles empty children", () => {
    const leaf: PlanNode = { id: "n", operator: "Leaf", table: null, estimatedRows: null, estimatedCost: null, attributes: {}, children: [] };
    const { nodes, edges } = layoutPlanNodes(leaf);
    expect(nodes).toHaveLength(1);
    expect(edges).toHaveLength(0);
  });
});
```

### - [ ] Step 3.7: Run vitest → FAIL

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/planLayout.test.ts
```

**Expected:** FAIL — module not found.

### - [ ] Step 3.8: Implement planLayout

`src/components/sql-ide/result/planLayout.ts`:

```typescript
export interface PlanNode {
  id: string;
  operator: string;
  table: string | null;
  estimatedRows: number | null;
  estimatedCost: number | null;
  attributes: Record<string, string>;
  children: PlanNode[];
}

export interface RFNode {
  id: string;
  position: { x: number; y: number };
  data: { node: PlanNode };
  type?: string;
}

export interface RFEdge {
  id: string;
  source: string;
  target: string;
}

const NODE_WIDTH = 200;
const NODE_HEIGHT = 70;
const VERTICAL_GAP = 100;
const HORIZONTAL_GAP = 30;

interface SubtreeBox {
  width: number;
  nodes: RFNode[];
  edges: RFEdge[];
  rootX: number;
}

function layoutSubtree(node: PlanNode, depth: number): SubtreeBox {
  if (node.children.length === 0) {
    const rf: RFNode = {
      id: node.id,
      position: { x: 0, y: depth * (NODE_HEIGHT + VERTICAL_GAP) },
      data: { node },
    };
    return { width: NODE_WIDTH, nodes: [rf], edges: [], rootX: NODE_WIDTH / 2 };
  }
  const childBoxes = node.children.map((c) => layoutSubtree(c, depth + 1));
  const totalWidth = childBoxes.reduce((sum, b) => sum + b.width, 0) + HORIZONTAL_GAP * (childBoxes.length - 1);
  const allNodes: RFNode[] = [];
  const allEdges: RFEdge[] = [];
  let xCursor = 0;
  for (const box of childBoxes) {
    for (const n of box.nodes) {
      allNodes.push({ ...n, position: { x: n.position.x + xCursor, y: n.position.y } });
    }
    for (const e of box.edges) allEdges.push(e);
    xCursor += box.width + HORIZONTAL_GAP;
  }
  // Root at horizontal center of children
  const rootX = totalWidth / 2;
  const rootRF: RFNode = {
    id: node.id,
    position: { x: rootX - NODE_WIDTH / 2, y: depth * (NODE_HEIGHT + VERTICAL_GAP) },
    data: { node },
  };
  allNodes.push(rootRF);
  for (const child of node.children) {
    allEdges.push({ id: `${node.id}->${child.id}`, source: node.id, target: child.id });
  }
  return { width: Math.max(totalWidth, NODE_WIDTH), nodes: allNodes, edges: allEdges, rootX };
}

export function layoutPlanNodes(root: PlanNode): { nodes: RFNode[]; edges: RFEdge[] } {
  const box = layoutSubtree(root, 0);
  return { nodes: box.nodes, edges: box.edges };
}
```

### - [ ] Step 3.9: vitest PASS

```bash
pnpm vitest run src/components/sql-ide/result/__tests__/planLayout.test.ts
```

**Expected:** 3/3 PASS.

### - [ ] Step 3.10: API client + QueryPlanView

`src/components/sql-ide/api/sqlIdePlan.ts`:

```typescript
import apiClient from "@/api/apiClient";
import type { PlanNode } from "../result/planLayout";

export interface PlanResult {
  root: PlanNode;
  rawText: string;
  engine: string;
  explainTimeMs: number;
}

export interface ExplainPayload {
  sql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
}

export async function postExplain(payload: ExplainPayload): Promise<PlanResult> {
  return apiClient.post<PlanResult>({ url: "/api/sql/v2/explain", data: payload });
}
```

`src/components/sql-ide/result/QueryPlanView.tsx`:

```tsx
import { ReactFlow, Background, Controls, type Node, type Edge } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Button, Spin, Tabs, message } from "antd";
import { type FC, useCallback, useMemo, useState } from "react";
import { postExplain, type PlanResult } from "../api/sqlIdePlan";
import { layoutPlanNodes, type PlanNode } from "./planLayout";

export interface QueryPlanViewProps {
  sql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
}

export const QueryPlanView: FC<QueryPlanViewProps> = ({ sql, engine, datasourceId, catalog }) => {
  const [result, setResult] = useState<PlanResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleExplain = useCallback(async () => {
    if (!sql.trim()) {
      void message.warning("请先输入 SQL");
      return;
    }
    setLoading(true);
    try {
      const r = await postExplain({ sql, engine, datasourceId, catalog });
      setResult(r);
    } catch (err) {
      void message.error("EXPLAIN 失败");
    } finally {
      setLoading(false);
    }
  }, [sql, engine, datasourceId, catalog]);

  const flow = useMemo(() => {
    if (!result) return { nodes: [] as Node[], edges: [] as Edge[] };
    const { nodes, edges } = layoutPlanNodes(result.root);
    return {
      nodes: nodes.map<Node>((n) => ({
        id: n.id,
        position: n.position,
        data: { label: nodeLabel(n.data.node) },
        style: { padding: 6, fontSize: 11, border: `1px solid ${costColor(n.data.node.estimatedCost)}` },
      })),
      edges: edges.map<Edge>((e) => ({ id: e.id, source: e.source, target: e.target })),
    };
  }, [result]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ padding: 6, borderBottom: "1px solid var(--ant-color-border-secondary)" }}>
        <Button size="small" type="primary" onClick={handleExplain} loading={loading}>
          运行 EXPLAIN
        </Button>
        {result && (
          <span style={{ marginLeft: 12, fontSize: 11, color: "var(--ant-color-text-secondary)" }}>
            {result.engine} · {result.explainTimeMs} ms
          </span>
        )}
      </div>
      <div style={{ flex: 1, minHeight: 0 }}>
        {loading ? (
          <div style={{ textAlign: "center", padding: 24 }}><Spin /></div>
        ) : result ? (
          <Tabs
            defaultActiveKey="tree"
            items={[
              {
                key: "tree",
                label: "Tree",
                children: (
                  <div style={{ height: "100%", minHeight: 280 }}>
                    <ReactFlow nodes={flow.nodes} edges={flow.edges} fitView>
                      <Background />
                      <Controls />
                    </ReactFlow>
                  </div>
                ),
              },
              {
                key: "raw",
                label: "Raw",
                children: (
                  <pre style={{ padding: 12, fontSize: 11, overflow: "auto", height: "100%" }}>
                    {result.rawText}
                  </pre>
                ),
              },
            ]}
          />
        ) : (
          <div style={{ padding: 24, color: "var(--ant-color-text-tertiary)" }}>
            点击"运行 EXPLAIN"分析查询计划
          </div>
        )}
      </div>
    </div>
  );
};

function nodeLabel(n: PlanNode): string {
  const cost = n.estimatedCost != null ? ` · cost=${n.estimatedCost.toFixed(0)}` : "";
  const rows = n.estimatedRows != null ? ` · rows=${n.estimatedRows.toFixed(0)}` : "";
  const tbl = n.table ? `\n${n.table}` : "";
  return `${n.operator}${cost}${rows}${tbl}`;
}

function costColor(cost: number | null | undefined): string {
  if (cost == null) return "var(--ant-color-border)";
  if (cost > 10000) return "var(--ant-color-error)";
  if (cost > 1000) return "var(--ant-color-warning)";
  return "var(--ant-color-success)";
}
```

### - [ ] Step 3.11: Verification

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** Backend 24 IT PASS (23 prior + 1 new SqlIdePlanControllerIT). Frontend 66 + 3 = **69 PASS**.

### - [ ] Step 3.12: Commit

```bash
git commit -m "$(cat <<'EOF'
feat(F5/T23): EXPLAIN endpoint + React Flow plan tree

Sprint-11 F5 T23: SqlPlanService runs EXPLAIN (FORMAT JSON) for Trino/
PostgreSQL and plain EXPLAIN for Hive, parses to a unified PlanNodeDto
tree. POST /api/sql/v2/explain audits SQL_PLAN_VIEW with sqlHash.
ExecEngine gains POSTGRESQL.

Frontend: planLayout (TDD: 3 tests) places plan nodes top-down with
horizontal spread; cost-color borders highlight expensive operators.
QueryPlanView toggles Tree (React Flow) vs Raw (text) tabs.
EOF
)"
```

---

## Task 4 — T24: 二次查询 (PostgreSQL temp table)

> **Strategy**: Use PostgreSQL `CREATE TEMP TABLE` (session-scoped) as the simpler, deployment-safe path. DuckDB JNI is genuinely risky for prod; defer it as F5 followup. Spec accepted this fallback (plan §4.5 risk note).

**Files:**
- Modify: `service/sql/SqlResultStreamService.java` (add `Stream<Map<String,Object>> streamRange` already exists — reuse)
- Create: `service/sql/SqlSubqueryService.java` interface + `Impl.java`
- Create: `service/sql/dto/{TempViewDto, SubqueryRequest}.java`
- Create: `web/rest/sql/SqlIdeSubqueryController.java`
- Create: `service/scheduling/SqlSubqueryViewCleaner.java`
- Modify: `tabs/types.ts` + `useTabStore.ts` (add subqueryViewName field)
- Create: `src/components/sql-ide/api/sqlIdeSubquery.ts`
- Create: `src/components/sql-ide/result/SubQueryButton.tsx`
- Create: `src/test/java/.../web/rest/sql/SqlIdeSubqueryControllerIT.java`

### - [ ] Step 4.1: Backend interface

`service/sql/SqlSubqueryService.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import java.util.Map;
import java.util.UUID;

public interface SqlSubqueryService {
    /** Creates a session-scoped temp table from execution result; returns the temp table name. */
    TempViewDto createTempView(UUID executionId);
    /** Executes a SELECT against a previously created temp view. */
    Map<String, Object> executeOnView(String viewName, String sql);
    void dropView(String viewName);
    int cleanupExpired();
}
```

### - [ ] Step 4.2: DTOs

`service/sql/dto/TempViewDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
import java.time.Instant;
import java.util.UUID;
public record TempViewDto(String viewName, UUID executionId, long rowCount, Instant expiresAt) {}
```

`service/sql/dto/SubqueryRequest.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
public record SubqueryRequest(String sql) {}
```

### - [ ] Step 4.3: Implementation (uses platform PG datasource)

`service/sql/SqlSubqueryServiceImpl.java`:

```java
package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlSubqueryServiceImpl implements SqlSubqueryService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_VIEW_ROWS = 100_000;

    private final DataSource dataSource;
    private final SqlResultStreamService streamService;
    /** name → expiry */
    private final Map<String, Instant> views = new ConcurrentHashMap<>();

    public SqlSubqueryServiceImpl(DataSource dataSource, SqlResultStreamService streamService) {
        this.dataSource = dataSource;
        this.streamService = streamService;
    }

    @Override
    @Transactional
    public TempViewDto createTempView(UUID executionId) {
        String name = "sqlide_view_" + executionId.toString().replace("-", "").substring(0, 16);
        long count = 0;
        try (Connection conn = dataSource.getConnection()) {
            // Use unlogged real table (not temp) because session is per-request in HikariCP — temp tables die.
            // Mark with prefix for cleanup.
            try (PreparedStatement drop = conn.prepareStatement("DROP TABLE IF EXISTS " + name)) {
                drop.execute();
            }
            // Create from first row's keys
            var iter = streamService.streamRange(executionId, 0, MAX_VIEW_ROWS).iterator();
            if (!iter.hasNext()) {
                views.put(name, Instant.now().plus(TTL));
                return new TempViewDto(name, executionId, 0, Instant.now().plus(TTL));
            }
            Map<String, Object> firstRow = iter.next();
            List<String> cols = new ArrayList<>(firstRow.keySet());
            StringBuilder ddl = new StringBuilder("CREATE UNLOGGED TABLE " + name + " (");
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) ddl.append(", ");
                ddl.append('"').append(cols.get(i).replace("\"", "")).append("\" text");
            }
            ddl.append(")");
            try (PreparedStatement create = conn.prepareStatement(ddl.toString())) {
                create.execute();
            }
            // Insert rows in batch
            String placeholders = String.join(",", cols.stream().map(c -> "?").toList());
            String insertSql = "INSERT INTO " + name + " VALUES (" + placeholders + ")";
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                count = insertOne(ins, firstRow, cols);
                while (iter.hasNext()) {
                    count += insertOne(ins, iter.next(), cols);
                }
                ins.executeBatch();
            }
            views.put(name, Instant.now().plus(TTL));
            return new TempViewDto(name, executionId, count, Instant.now().plus(TTL));
        } catch (SQLException e) {
            throw new RuntimeException("temp view create failed", e);
        }
    }

    private long insertOne(PreparedStatement ps, Map<String, Object> row, List<String> cols) throws SQLException {
        for (int i = 0; i < cols.size(); i++) {
            Object v = row.get(cols.get(i));
            ps.setObject(i + 1, v == null ? null : String.valueOf(v));
        }
        ps.addBatch();
        return 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> executeOnView(String viewName, String sql) {
        if (!views.containsKey(viewName)) {
            throw new RuntimeException("view not found or expired");
        }
        // Whitelist viewName — enforce sqlide_view_ prefix
        if (!viewName.startsWith("sqlide_view_")) {
            throw new RuntimeException("invalid view name");
        }
        // Substitute literal placeholder if user wrote SELECT * FROM <view> — they should
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String> headers = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) headers.add(md.getColumnLabel(i));
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) r.put(headers.get(i - 1), rs.getObject(i));
                    rows.add(r);
                    if (rows.size() >= MAX_VIEW_ROWS) break;
                }
                return Map.of("columns", headers, "rows", rows);
            }
        } catch (SQLException e) {
            throw new RuntimeException("subquery failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void dropView(String viewName) {
        if (!viewName.startsWith("sqlide_view_")) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DROP TABLE IF EXISTS " + viewName)) {
            ps.execute();
            views.remove(viewName);
        } catch (SQLException ignored) { /* best effort */ }
    }

    @Override
    @Transactional
    public int cleanupExpired() {
        Instant now = Instant.now();
        int dropped = 0;
        for (var entry : views.entrySet()) {
            if (entry.getValue().isBefore(now)) {
                dropView(entry.getKey());
                dropped++;
            }
        }
        return dropped;
    }
}
```

### - [ ] Step 4.4: Cleanup scheduler

`service/scheduling/SqlSubqueryViewCleaner.java`:
```java
package com.yuzhi.dts.platform.service.scheduling;

import com.yuzhi.dts.platform.service.sql.SqlSubqueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SqlSubqueryViewCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(SqlSubqueryViewCleaner.class);
    private final SqlSubqueryService service;
    public SqlSubqueryViewCleaner(SqlSubqueryService service) { this.service = service; }

    @Scheduled(fixedDelayString = "PT5M")  // every 5 minutes
    public void cleanup() {
        int dropped = service.cleanupExpired();
        if (dropped > 0) LOG.info("[subquery-cleaner] dropped {} expired views", dropped);
    }
}
```

### - [ ] Step 4.5: Controller + IT

`web/rest/sql/SqlIdeSubqueryController.java`:

```java
package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlSubqueryService;
import com.yuzhi.dts.platform.service.sql.dto.SubqueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2/temp-views")
public class SqlIdeSubqueryController {

    private final SqlSubqueryService service;
    private final AuditService auditService;

    public SqlIdeSubqueryController(SqlSubqueryService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    public ApiResponse<TempViewDto> create(@RequestParam("executionId") UUID executionId) {
        TempViewDto v = service.createTempView(executionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", v.viewName());
        payload.put("rowCount", v.rowCount());
        payload.put("actionCode", SqlIdeAuditActions.SQL_TEMP_VIEW_CREATE);
        auditService.record("CREATE", "sql.ide.temp_view", "sql.execution",
            executionId.toString(), "SUCCESS", payload);
        return ApiResponses.ok(v);
    }

    @PostMapping("/{name}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String name, @RequestBody SubqueryRequest req) {
        Map<String, Object> result = service.executeOnView(name, req.sql());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", name);
        payload.put("sqlHash", Integer.toHexString(req.sql() == null ? 0 : req.sql().hashCode()));
        payload.put("actionCode", SqlIdeAuditActions.SQL_SUBQUERY_EXECUTE);
        auditService.record("EXECUTE", "sql.ide.subquery", "sql.temp_view",
            name, "SUCCESS", payload);
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        service.dropView(name);
        return ApiResponses.ok(null);
    }
}
```

IT: minimal — ensure POST /temp-views returns 200 for an unknown executionId (which will return empty view).

`SqlIdeSubqueryControllerIT.java`:
```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser(username = "alice")
class SqlIdeSubqueryControllerIT {

    @Autowired private MockMvc mvc;

    @Test
    void createTempViewForUnknownExecutionReturns200WithEmptyView() throws Exception {
        mvc.perform(post("/api/sql/v2/temp-views").param("executionId", UUID.randomUUID().toString()))
            .andExpect(status().isOk());
    }
}
```

### - [ ] Step 4.6: Frontend types + API

In `tabs/types.ts`, add to TabState:
```typescript
subqueryViewName: string | null;
```
In `useTabStore.ts`'s `openTab`, `fromDto`, `loadLocal` (migration!), add default `subqueryViewName: null`.

`api/sqlIdeSubquery.ts`:
```typescript
import apiClient from "@/api/apiClient";

export interface TempView {
  viewName: string;
  executionId: string;
  rowCount: number;
  expiresAt: string;
}

export async function createTempView(executionId: string): Promise<TempView> {
  return apiClient.post<TempView>({
    url: `/api/sql/v2/temp-views?executionId=${encodeURIComponent(executionId)}`,
  });
}

export async function deleteTempView(viewName: string): Promise<void> {
  await apiClient.delete<void>({ url: `/api/sql/v2/temp-views/${encodeURIComponent(viewName)}` });
}
```

### - [ ] Step 4.7: SubQueryButton component

`result/SubQueryButton.tsx`:

```tsx
import { Button, message } from "antd";
import { type FC, useState } from "react";
import { createTempView } from "../api/sqlIdeSubquery";
import { useTabStore } from "../tabs/useTabStore";

export interface SubQueryButtonProps {
  executionId: string;
}

export const SubQueryButton: FC<SubQueryButtonProps> = ({ executionId }) => {
  const [loading, setLoading] = useState(false);
  const openTab = useTabStore((s) => s.openTab);
  const updateTab = useTabStore((s) => s.updateTab);

  const handleQueryThis = async () => {
    setLoading(true);
    try {
      const v = await createTempView(executionId);
      const newId = openTab({
        title: `Query Result · ${executionId.slice(0, 8)}`,
        sqlText: `SELECT * FROM ${v.viewName} LIMIT 100`,
      });
      updateTab(newId, { subqueryViewName: v.viewName });
      void message.success(`已创建临时视图 ${v.viewName}（${v.rowCount} 行）`);
    } catch (err) {
      void message.error("创建临时视图失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button size="small" loading={loading} onClick={handleQueryThis}>
      Query This Result
    </Button>
  );
};
```

### - [ ] Step 4.8: Verification

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** Backend 25 IT PASS (24 prior + 1 new), frontend 69 PASS still.

### - [ ] Step 4.9: Commit

```bash
git commit -m "$(cat <<'EOF'
feat(F5/T24): subquery via PostgreSQL UNLOGGED temp tables

Sprint-11 F5 T24: SqlSubqueryService creates an UNLOGGED Postgres
table per execution result (named sqlide_view_<short-id>), inserts
up to 100k rows, lets users SELECT against it via POST /temp-views/
{name}/query. 30-min TTL with @Scheduled cleaner every 5 min. UNLOGGED
is used because Hikari connection pool means each request gets a
different session — true session-scoped temp tables die.

Frontend: SubQueryButton in BottomPanel header creates the view and
opens a new Tab pre-filled with SELECT *. TabState gains
subqueryViewName for tracking.

DuckDB-based variant deferred to F5 followup (JNI deployment risk).
EOF
)"
```

---

## Task 5 — T25: Log Panel

**Files:**
- Modify: `web/rest/sql/SqlIdeExecutionController.java` (add `GET /{id}/log`)
- Create: `service/sql/dto/QueryLogDto.java`
- Modify: `service/sql/SqlResultStreamService.java` + `Impl.java` (add `getLog(executionId)` method)
- Create: `src/components/sql-ide/api/sqlIdeLog.ts`
- Create: `src/components/sql-ide/result/LogPanel.tsx`

### - [ ] Step 5.1: DTO

`service/sql/dto/QueryLogDto.java`:
```java
package com.yuzhi.dts.platform.service.sql.dto;
import java.time.Instant;
import java.util.UUID;
public record QueryLogDto(
    UUID executionId,
    String originalSql,
    String rewrittenSql,
    String status,
    Instant startedAt,
    Instant finishedAt,
    Long rowCount,
    Long elapsedMs,
    String errorMessage,
    Long bytesProcessed
) {}
```

### - [ ] Step 5.2: Service method

In `SqlResultStreamService`:
```java
QueryLogDto getLog(UUID executionId);
```

In Impl:
```java
@Override
public QueryLogDto getLog(UUID executionId) {
    QueryExecution e = executionRepository.findById(executionId)
        .orElseThrow(() -> new EntityNotFoundException("execution not found"));
    return new QueryLogDto(
        e.getId(),
        e.getSqlText(),
        e.getSqlText(),  // TODO F5 followup: store rewritten separately on QueryExecution; for now original = rewritten
        e.getStatus() == null ? null : e.getStatus().name(),
        e.getStartedAt(),
        e.getFinishedAt(),
        e.getRowCount(),
        e.getElapsedMs(),
        e.getErrorMessage(),
        e.getBytesProcessed()
    );
}
```

### - [ ] Step 5.3: Endpoint

In `SqlIdeExecutionController.java`, add:
```java
@GetMapping("/{id}/log")
public ApiResponse<QueryLogDto> log(@PathVariable UUID id) {
    return ApiResponses.ok(streamService.getLog(id));
}
```

### - [ ] Step 5.4: Frontend API + LogPanel

`api/sqlIdeLog.ts`:
```typescript
import apiClient from "@/api/apiClient";
export interface QueryLog {
  executionId: string;
  originalSql: string | null;
  rewrittenSql: string | null;
  status: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  rowCount: number | null;
  elapsedMs: number | null;
  errorMessage: string | null;
  bytesProcessed: number | null;
}
export async function getExecutionLog(executionId: string): Promise<QueryLog> {
  return apiClient.get<QueryLog>({ url: `/api/sql/v2/executions/${executionId}/log` });
}
```

`result/LogPanel.tsx`:
```tsx
import { useQuery } from "@tanstack/react-query";
import { Empty, Spin, Switch } from "antd";
import { type FC, useState } from "react";
import { getExecutionLog } from "../api/sqlIdeLog";

export interface LogPanelProps {
  executionId: string;
  showAdvanced?: boolean;  // mode === "advanced" gates rewritten SQL
}

export const LogPanel: FC<LogPanelProps> = ({ executionId, showAdvanced = false }) => {
  const [showRewritten, setShowRewritten] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ["sqlide", "execution", "log", executionId],
    queryFn: () => getExecutionLog(executionId),
    staleTime: 30_000,
  });

  if (isLoading) return <div style={{ padding: 24, textAlign: "center" }}><Spin /></div>;
  if (!data) return <Empty description="无日志" />;

  const lines: string[] = [];
  if (data.startedAt) lines.push(`[${data.startedAt}] STARTED`);
  if (data.finishedAt) lines.push(`[${data.finishedAt}] FINISHED status=${data.status}`);
  if (data.rowCount != null) lines.push(`Rows: ${data.rowCount}`);
  if (data.elapsedMs != null) lines.push(`Elapsed: ${data.elapsedMs} ms`);
  if (data.bytesProcessed != null) lines.push(`Bytes processed: ${data.bytesProcessed}`);
  if (data.errorMessage) lines.push(`Error: ${data.errorMessage}`);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", padding: 8, fontFamily: "monospace", fontSize: 11 }}>
      <div style={{ marginBottom: 8, color: "var(--ant-color-text-secondary)" }}>
        {lines.map((l, i) => <div key={i}>{l}</div>)}
      </div>
      <div style={{ marginBottom: 8 }}>
        <span style={{ marginRight: 8, color: "var(--ant-color-text-secondary)" }}>Original SQL:</span>
      </div>
      <pre style={{ background: "var(--ant-color-bg-elevated)", padding: 8, borderRadius: 4, marginBottom: 8 }}>
        {data.originalSql ?? "(empty)"}
      </pre>
      {showAdvanced && (
        <>
          <div style={{ marginBottom: 4 }}>
            <Switch size="small" checked={showRewritten} onChange={setShowRewritten} />
            <span style={{ marginLeft: 8, color: "var(--ant-color-text-secondary)" }}>显示重写后 SQL</span>
          </div>
          {showRewritten && (
            <pre style={{ background: "var(--ant-color-bg-elevated)", padding: 8, borderRadius: 4 }}>
              {data.rewrittenSql ?? "(same as original)"}
            </pre>
          )}
        </>
      )}
    </div>
  );
};
```

### - [ ] Step 5.5: BottomTabs container + SqlIde wiring

`result/BottomTabs.tsx`:

```tsx
import { Tabs } from "antd";
import { type FC } from "react";
import { LogPanel } from "./LogPanel";
import { QueryPlanView } from "./QueryPlanView";
import { ResultChart } from "./ResultChart";
import { ResultGrid } from "./ResultGrid";
import { ResultPivot } from "./ResultPivot";

export interface BottomTabsProps {
  executionId: string;
  sql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
  gridState: any;  // import GridColumnState from columnState
  onGridStateChange: (next: any) => void;
}

export const BottomTabs: FC<BottomTabsProps> = ({
  executionId, sql, engine, datasourceId, catalog, gridState, onGridStateChange,
}) => (
  <Tabs
    defaultActiveKey="results"
    style={{ height: "100%" }}
    items={[
      { key: "results", label: "Results", children: <ResultGrid executionId={executionId} gridState={gridState} onGridStateChange={onGridStateChange} /> },
      { key: "chart", label: "Chart", children: <ResultChart executionId={executionId} /> },
      { key: "pivot", label: "Pivot", children: <ResultPivot executionId={executionId} /> },
      { key: "plan", label: "Query Plan", children: <QueryPlanView sql={sql} engine={engine} datasourceId={datasourceId} catalog={catalog} /> },
      { key: "log", label: "Log", children: <LogPanel executionId={executionId} showAdvanced={false} /> },
    ]}
  />
);
```

In `SqlIde.tsx`, replace the existing `<ResultGrid>` and SubQueryButton mounting:
- BottomPanel children: header (status + cancel + ExportMenu + new SubQueryButton when applicable) + `<BottomTabs>` filling the rest

Add SubQueryButton import; render conditionally next to ExportMenu when activeTab.lastExecutionId.

### - [ ] Step 5.6: Verification

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile && ./mvnw -q test -Dtest='SqlIde*'
cd ../dts-platform-webapp && pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide
```

**Expected:** Backend 25 IT PASS (no new IT for T25). Frontend 69 PASS.

### - [ ] Step 5.7: Commit

```bash
git commit -m "$(cat <<'EOF'
feat(F5/T25): LogPanel + BottomTabs (5-tab Result/Chart/Pivot/Plan/Log)

Sprint-11 F5 T25: GET /api/sql/v2/executions/{id}/log returns
QueryLogDto with original/rewritten SQL + status + timing +
bytesProcessed + error. Frontend LogPanel renders metadata + SQL
in monospace; rewritten SQL gated behind showAdvanced toggle (mode
"advanced" — F6 will wire it).

BottomTabs container replaces single Result panel with 5-tab
switcher: Results / Chart / Pivot / Query Plan / Log. SubQueryButton
mounted in BottomPanel header next to ExportMenu.

Note: rewrittenSql currently same as originalSql until QueryExecution
gets a dedicated rewritten_sql column (F5 followup).
EOF
)"
```

---

## Final Verification

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
./mvnw test -Dtest='SqlIde*' 2>&1 | grep -E "Tests run:" | tail
cd ../dts-platform-webapp
pnpm tsc --noEmit && pnpm vitest run src/components/sql-ide 2>&1 | tail
```

**Expected:** Backend 25+ IT PASS. Frontend **69 PASS** (61 prior + 5 chartConfig + 3 planLayout).

---

## Notes for the Executing Engineer

1. **DuckDB deferred**: T24's plan is PostgreSQL UNLOGGED tables. DuckDB JNI is risky for prod (alpine glibc, deployment story). Track DuckDB as F5 followup.

2. **rewrittenSql storage**: T25 currently returns `originalSql` for both fields because `QueryExecution` doesn't have a separate `rewritten_sql` column. Add column + populate in T16's chunk-write path as F5 followup.

3. **Plan parser robustness**: `SqlPlanServiceImpl.jsonToNode` handles the common shapes for Trino EXPLAIN (FORMAT JSON) and PostgreSQL EXPLAIN (FORMAT JSON). Hive's text-mode plan goes into a single attribute. If Trino's actual JSON nests differently in the production version, adapter may need tweaks.

4. **TempView name length**: 16 hex chars + `sqlide_view_` prefix = 28 chars, within PG identifier limit (63). Multiple sessions may collide; UUID prefix is unique enough for first cut.

5. **Missing `gridState` shape import** in `BottomTabs.tsx`: replace `any` with `import type { GridColumnState } from "./columnState"`.

6. **QueryGateway.execute signature**: `SqlPlanServiceImpl` calls `queryGateway.execute(sql, datasourceId, catalog)`. If the gateway has a different shape (request DTO etc.), adapt before opus-review.

7. **Audit constants**: T20's `SqlIdeAuditActions` has `SQL_PLAN_VIEW`, `SQL_TEMP_VIEW_CREATE`, `SQL_SUBQUERY_EXECUTE` already defined — F5 wires them in for the first time.

8. **Subagent model**: implementer sonnet, spec reviewer sonnet, code quality reviewer **opus**.
