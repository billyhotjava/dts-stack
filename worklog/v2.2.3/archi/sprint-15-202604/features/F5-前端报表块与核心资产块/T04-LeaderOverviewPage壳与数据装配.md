# T04: LeaderOverviewPage 壳 + 数据装配

**优先级**: P0
**状态**: READY
**依赖**: F3/T05, F4/T01, F4/T03, T01, T02, T03

## 目标

`LeaderOverviewPage` 组装所有子块，负责 `workbenchService.leaderOverview` 拉数 + `filterState` 状态管理 + refetch 触发。

## 技术设计

### workbenchService.ts 扩展（若 F4/T01 未做）

```ts
// src/api/services/workbenchService.ts 追加

export interface LeaderOverviewKpis {
  reportsTotal: number;
  reportsNewInPeriod: number;
  visitsInPeriod: number;
  visitsMoM: number | null;
  assetsTotal: number;
  assetsNewInPeriod: number;
  assetsS1: number;
  assetsS1Ratio: number | null;
  assetsS1S2: number; // F4/T01 拉动后端新增
}

export interface LeaderOverviewTopReport {
  id: string;
  title: string;
  visits: number;
  bizDomain: string | null;
  classification: string;
  lastVisitedAt: string | null;
}

export interface LeaderOverviewTopAsset {
  id: string;
  name: string;
  classification: string;
  updatedAt: string;
  bizDomain: string | null;
}

export interface LeaderOverviewDomainCell {
  domain: string;
  domainName: string;
  visits: number;
}

export interface LeaderOverviewResponse {
  generatedAt: string;
  scope: "MINE" | "DEPT" | "ALL";
  effectiveDeptCode: string | null;
  timeRange: "MONTH" | "QUARTER" | "YEAR";
  kpis: LeaderOverviewKpis;
  topReports: LeaderOverviewTopReport[];
  topAssets: LeaderOverviewTopAsset[];
  domainMatrix: LeaderOverviewDomainCell[];
}

export default {
  overview: () => apiClient.get<WorkbenchOverview>({ url: "/workbench/overview" }),
  todos: () => apiClient.get<WorkbenchTodoItem[]>({ url: "/workbench/todos" }),
  leaderOverview: (params: {
    scope: "MINE" | "DEPT" | "ALL";
    deptCode?: string | null;
    bizDomain?: string | null;
    timeRange: "MONTH" | "QUARTER" | "YEAR";
  }) => apiClient.get<LeaderOverviewResponse>({
    url: "/workbench/leader-overview",
    params: {
      scope: params.scope,
      deptCode: params.deptCode ?? undefined,
      bizDomain: params.bizDomain ?? undefined,
      timeRange: params.timeRange,
    },
  }),
};
```

### LeaderOverviewPage.tsx

```tsx
// src/pages/workbench/LeaderOverviewPage.tsx
import { Alert, Button, Col, Row, Space } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import workbenchService, { type LeaderOverviewResponse } from "@/api/services/workbenchService";
import { useWorkbenchRole } from "./hooks/useWorkbenchRole";
import { WorkbenchFilterBar, initialFilterState, type WorkbenchFilterState } from "./components/WorkbenchFilterBar";
import { KpiRow } from "./components/KpiRow";
import { DomainMatrix } from "./components/DomainMatrix";
import { TopReportsBlock } from "./components/TopReportsBlock";
import { CoreAssetsBlock } from "./components/CoreAssetsBlock";
import { ScreenStrip } from "./components/ScreenStrip";
import { auditLog } from "@/utils/audit";

export function LeaderOverviewPage() {
  const roleInfo = useWorkbenchRole();
  const [filter, setFilter] = useState<WorkbenchFilterState>(() => initialFilterState(roleInfo));
  const [data, setData] = useState<LeaderOverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // 页面进入埋点（一次）
  useEffect(() => {
    auditLog("WORKBENCH_OVERVIEW_VIEW", { role: roleInfo.role, deptCode: roleInfo.deptCode });
  }, [roleInfo.role, roleInfo.deptCode]);

  // 角色变化时（登录切换等）重置 filter 默认值
  useEffect(() => {
    setFilter(initialFilterState(roleInfo));
  }, [roleInfo.role, roleInfo.deptCode]);

  // 拉取聚合数据
  const fetchData = useCallback(async (f: WorkbenchFilterState) => {
    setLoading(true);
    setError(null);
    try {
      const resp = await workbenchService.leaderOverview({
        scope: f.scope,
        deptCode: f.deptCode,
        bizDomain: f.bizDomain,
        timeRange: f.timeRange,
      });
      setData(resp);
    } catch (ex) {
      setError(ex instanceof Error ? ex : new Error(String(ex)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData(filter);
  }, [filter, fetchData]);

  const handleRetry = () => fetchData(filter);

  const showMatrix = roleInfo.isInstLeader && filter.bizDomainAvailable && (data?.domainMatrix?.length ?? 0) > 0;

  return (
    <div>
      <WorkbenchFilterBar value={filter} onChange={setFilter} />

      <div style={{ padding: "16px" }}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <ScreenStrip />

          {error && (
            <Alert
              type="error"
              message="暂时拿不到数据"
              description="请稍后重试。如问题持续，请联系管理员。"
              action={<Button size="small" onClick={handleRetry}>重试</Button>}
              showIcon
            />
          )}

          <KpiRow role={roleInfo.role} filter={filter} kpis={data?.kpis ?? null} loading={loading} />

          {roleInfo.isInstLeader && (
            <DomainMatrix
              visible={showMatrix}
              cells={data?.domainMatrix ?? []}
              activeDomain={filter.bizDomain}
              onSelect={(domain) => setFilter((prev) => ({ ...prev, bizDomain: domain }))}
            />
          )}

          <Row gutter={16}>
            <Col xs={24} md={16}>
              <TopReportsBlock role={roleInfo.role} items={data?.topReports ?? []} loading={loading} />
            </Col>
            <Col xs={24} md={8}>
              <CoreAssetsBlock role={roleInfo.role} items={data?.topAssets ?? []} loading={loading} />
            </Col>
          </Row>
        </Space>
      </div>
    </div>
  );
}
```

### refetch 防抖考虑

当前实现下，用户快速切换 filter（如连续点多个色块）会连续触发 fetch。若后端聚合较重，需要简单防抖：

```ts
// useMemo 缓存 filter JSON → useEffect 依赖
const filterKey = useMemo(() => JSON.stringify(filter), [filter]);
useEffect(() => {
  const id = setTimeout(() => fetchData(filter), 150);
  return () => clearTimeout(id);
}, [filterKey, fetchData]);
```

把 `filter` 依赖替换为 `filterKey`，加 150ms 去抖。本 task 实现这个简单去抖。

## 影响范围

- 新增：`src/pages/workbench/LeaderOverviewPage.tsx`
- 修改：`src/api/services/workbenchService.ts`（新增类型与 `leaderOverview` 方法；若 F4/T01 已做则跳过）

## 验证

- [ ] 集成测试（RTL + msw，mock `/api/workbench/leader-overview`）：
  - `fetches_on_mount_with_default_filter()`
  - `refetches_when_filter_changes()`
  - `debounces_rapid_filter_changes()`
  - `shows_error_alert_on_api_failure()`
  - `error_retry_refetches()`
  - `matrix_hidden_for_non_INST_LEADER()`
  - `matrix_hidden_when_bizDomain_unavailable()`
- [ ] 手测：所领导登录 → 整页出现 → 切时间 → 切部门 → 切业务域 → 点色块；观察 KPI / TOP 同步变化。

## 完成标准

- [ ] Mount 时触发一次 fetch；filter 变更触发去抖后的 refetch。
- [ ] 错误态 `Alert` 有重试按钮，点击触发重新拉数。
- [ ] 单测覆盖 `fetch × filter × error` 主路径。
- [ ] 类型严格：所有子组件 props 与 `LeaderOverviewResponse` 字段一一对齐，零 any。
