# T01: KpiRow 组件（三角色差异化）

**优先级**: P0
**状态**: READY
**依赖**: F3/T05, F1/T03

## 目标

`KpiRow` 按角色渲染不同数量与语义的 KPI 卡：

| 角色 | 卡数 | 卡片语义 |
|---|---|---|
| EMP | 3 | 我常用的报表 / 本期访问（个人） / 我常用的资产 |
| DEPT_LEADER | 3 | 本部门报表 / 本期访问（部门） / 部门核心资产（S1+S2） |
| INST_LEADER | 4 | 所内报表 / 本期访问（全所） / 数据资产 / 核心资产 S1 |

文案中的"本期"根据 `timeRange` 动态替换（本月 / 本季 / 本年）。

## 技术设计

### Props

```ts
import type { WorkbenchFilterState } from "./WorkbenchFilterBar";
import type { LeaderOverviewKpis } from "@/api/services/workbenchService"; // F5/T... 前置：workbenchService 扩展类型

export interface KpiRowProps {
  role: "EMP" | "DEPT_LEADER" | "INST_LEADER";
  filter: WorkbenchFilterState;
  kpis: LeaderOverviewKpis;
  loading: boolean;
}
```

### `workbenchService` 类型扩展前置

此 task 开始前需要 `workbenchService.ts` 已经有对应类型（由本 task 引入也行，放在 F5/T04 统一引入亦可）：

```ts
export interface LeaderOverviewKpis {
  reportsTotal: number;
  reportsNewInPeriod: number;
  visitsInPeriod: number;
  visitsMoM: number | null;
  assetsTotal: number;
  assetsNewInPeriod: number;
  assetsS1: number;
  assetsS1Ratio: number | null;
}

export interface LeaderOverviewResponse {
  generatedAt: string;
  scope: "MINE" | "DEPT" | "ALL";
  effectiveDeptCode: string | null;
  timeRange: "MONTH" | "QUARTER" | "YEAR";
  kpis: LeaderOverviewKpis;
  topReports: Array<{ id: string; title: string; visits: number; bizDomain: string | null; classification: string; lastVisitedAt: string | null }>;
  topAssets: Array<{ id: string; name: string; classification: string; updatedAt: string; bizDomain: string | null }>;
  domainMatrix: Array<{ domain: string; domainName: string; visits: number }>;
}

export function leaderOverview(params: { scope: string; deptCode?: string | null; bizDomain?: string | null; timeRange: string }) {
  return apiClient.get<LeaderOverviewResponse>({
    url: "/workbench/leader-overview",
    params: {
      scope: params.scope,
      deptCode: params.deptCode ?? undefined,
      bizDomain: params.bizDomain ?? undefined,
      timeRange: params.timeRange,
    },
  });
}
```

> **若此时 F5/T04 尚未执行**：本 task 在 `workbenchService.ts` 新增类型与方法；F5/T04 只需引用，不重复定义。

### 组件

```tsx
// src/pages/workbench/components/KpiRow.tsx
import { Card, Col, Row, Skeleton, Statistic, Tag } from "antd";
import { timeRangeLabel } from "./TimeRangeSelect";
import type { WorkbenchFilterState } from "./WorkbenchFilterBar";
import type { LeaderOverviewKpis } from "@/api/services/workbenchService";

export interface KpiRowProps {
  role: "EMP" | "DEPT_LEADER" | "INST_LEADER";
  filter: WorkbenchFilterState;
  kpis: LeaderOverviewKpis | null;
  loading: boolean;
}

export function KpiRow({ role, filter, kpis, loading }: KpiRowProps) {
  const cards = buildCards(role, filter, kpis);
  const span = role === "INST_LEADER" ? 6 : 8;

  return (
    <Row gutter={16}>
      {cards.map((c) => (
        <Col key={c.key} xs={24} sm={12} md={span}>
          <Card>
            {loading || !kpis ? (
              <Skeleton active paragraph={{ rows: 1 }} />
            ) : (
              <>
                <Statistic title={c.title} value={c.value} suffix={c.suffix} />
                {c.secondary && <div style={{ marginTop: 8, color: "#8c8c8c", fontSize: 12 }}>{c.secondary}</div>}
              </>
            )}
          </Card>
        </Col>
      ))}
    </Row>
  );
}

function buildCards(
  role: KpiRowProps["role"],
  filter: WorkbenchFilterState,
  kpis: LeaderOverviewKpis | null
) {
  const periodLabel = timeRangeLabel(filter.timeRange); // 本月 / 本季 / 本年
  if (!kpis) return placeholders(role);

  if (role === "EMP") {
    return [
      { key: "myReports", title: "我常用的报表", value: kpis.reportsTotal, secondary: "近 30 天访问过" },
      { key: "myVisits", title: `${periodLabel}访问`, value: kpis.visitsInPeriod, secondary: undefined },
      { key: "myAssets", title: "我常用的资产", value: kpis.assetsTotal, secondary: "近 30 天访问过" },
    ];
  }

  if (role === "DEPT_LEADER") {
    return [
      { key: "deptReports", title: "本部门报表", value: kpis.reportsTotal, secondary: `${periodLabel}新发布 ${kpis.reportsNewInPeriod}` },
      { key: "deptVisits", title: `${periodLabel}访问`, value: kpis.visitsInPeriod, secondary: formatMoM(kpis.visitsMoM) },
      { key: "deptCoreAssets", title: "部门核心资产", value: kpis.assetsS1S2, secondary: "S1 + S2 总数" },
    ];
  }

  // INST_LEADER
  return [
    { key: "instReports", title: "所内报表", value: kpis.reportsTotal, secondary: `${periodLabel}新发布 ${kpis.reportsNewInPeriod}` },
    { key: "instVisits", title: `${periodLabel}访问`, value: kpis.visitsInPeriod, secondary: formatMoM(kpis.visitsMoM) },
    { key: "instAssets", title: "数据资产", value: kpis.assetsTotal, secondary: `${periodLabel}新增 ${kpis.assetsNewInPeriod}` },
    { key: "instS1", title: "核心资产（S1）", value: kpis.assetsS1, secondary: formatRatio(kpis.assetsS1Ratio) },
  ];
}

function formatMoM(mom: number | null | undefined): string | undefined {
  if (mom === null || mom === undefined) return undefined;
  const sign = mom > 0 ? "↑" : mom < 0 ? "↓" : "";
  const color = mom > 0 ? "#52c41a" : mom < 0 ? "#ff4d4f" : "#8c8c8c";
  const pct = Math.abs(mom * 100).toFixed(1);
  return `环比${sign} ${pct}%`; // 颜色由 T02 应用（包一层 span style）
}

function formatRatio(r: number | null | undefined): string | undefined {
  if (r === null || r === undefined) return undefined;
  return `占比 ${(r * 100).toFixed(1)}%`;
}

function placeholders(role: KpiRowProps["role"]) {
  const count = role === "INST_LEADER" ? 4 : 3;
  return Array.from({ length: count }, (_, i) => ({ key: `ph-${i}`, title: "", value: 0, secondary: undefined }));
}
```

T02 会把 `secondary` 的颜色化渲染做完整（此处先文本）。

### 注意

- 员工 KPI 的 `"本期"` 对"我常用的报表"这类**静态口径**字段无意义——不套用 periodLabel。仅 `"本期访问"` 会随时间切换。
- 部门领导 KPI "部门核心资产 = S1+S2"：后端目前 KPI 只返回 `assetsS1`。**本 sprint 简化**：直接用 `assetsS1` 展示（文案改为"S1 资产"），或让 F1/T03 多返一个 `assetsS1S2` 字段。**决策**：让 F1/T03 加 `assetsS1S2` 字段（零成本），UI 用它渲染部门领导卡。

## 影响范围

- 新增：`src/pages/workbench/components/KpiRow.tsx`
- 修改：`src/api/services/workbenchService.ts`（新增类型 + `leaderOverview` 方法；若 F5/T04 已做则跳过）
- 修改：后端 `LeaderOverviewResponse.Kpis` 增加 `assetsS1S2: long` 字段（F1/T03 在本 task 拉动时补）

## 验证

- [ ] 单测：
  - `renders_3_cards_for_EMP()`
  - `renders_3_cards_for_DEPT_LEADER()`
  - `renders_4_cards_for_INST_LEADER()`
  - `period_label_follows_timeRange()` × 3（MONTH/QUARTER/YEAR）
  - `shows_skeleton_when_loading_or_kpis_null()`
- [ ] 快照测试：三角色各一张快照，避免样式漂移。

## 完成标准

- [ ] 三种角色下卡片数量、title、value 字段都正确映射。
- [ ] 加载中显示 Skeleton，不显示 0 这种假数据。
- [ ] periodLabel 文案与 `TimeRangeSelect` 选择同步。
