# T05: WorkbenchFilterBar 装配

**优先级**: P0
**状态**: READY
**依赖**: T01, T02, T03, T04

## 目标

把 DeptSelect / BizDomainSelect / TimeRangeSelect 三个子组件装到 sticky 顶栏里，并以统一的 `WorkbenchFilterState` 对外暴露，F4 / F5 的数据层消费。

## 技术设计

### 统一状态类型

```ts
// src/pages/workbench/components/WorkbenchFilterBar.tsx（顶部 export）

import type { TimeRange } from "./TimeRangeSelect";

export interface WorkbenchFilterState {
  scope: "MINE" | "DEPT" | "ALL";
  deptCode: string | null;  // scope=DEPT/ALL 下钻时非空
  bizDomain: string | null; // null = ALL
  timeRange: TimeRange;
  bizDomainAvailable: boolean;
}
```

`scope` 推导规则（前端本地）：

- `INST_LEADER` 且部门选 `ALL` → `scope=ALL, deptCode=null`
- `INST_LEADER` 且选具体部门 → `scope=ALL, deptCode=<selected>` （下钻查看某部门，语义仍是 ALL）
- `DEPT_LEADER` → `scope=DEPT, deptCode=<self>`
- `EMP` → `scope=MINE, deptCode=null`

> 前端做这层推导只是为了**构造请求参数**；后端还会再做一次降级校验（F1/T07），前后端一致。

### 组件骨架

```tsx
import { useCallback, useMemo, useState } from "react";
import { Space } from "antd";
import { DeptSelect } from "./DeptSelect";
import { BizDomainSelect } from "./BizDomainSelect";
import { TimeRangeSelect, type TimeRange } from "./TimeRangeSelect";
import { useWorkbenchRole } from "../hooks/useWorkbenchRole";
import { auditLog } from "@/utils/audit";

export interface WorkbenchFilterBarProps {
  value: WorkbenchFilterState;
  onChange: (next: WorkbenchFilterState) => void;
}

export function WorkbenchFilterBar({ value, onChange }: WorkbenchFilterBarProps) {
  const roleInfo = useWorkbenchRole();

  const handleDeptChange = useCallback((deptSelected: string | "ALL") => {
    const next: WorkbenchFilterState = {
      ...value,
      scope:
        roleInfo.isInstLeader ? "ALL" :
        roleInfo.isDeptLeader ? "DEPT" :
        "MINE",
      deptCode:
        roleInfo.isInstLeader ? (deptSelected === "ALL" ? null : deptSelected) :
        roleInfo.isDeptLeader ? roleInfo.deptCode :
        null,
    };
    auditLog("WORKBENCH_FILTER_CHANGE", { dim: "dept", value: deptSelected, role: roleInfo.role });
    onChange(next);
  }, [roleInfo, value, onChange]);

  const handleDomainChange = useCallback((domain: string | "ALL") => {
    const next = { ...value, bizDomain: domain === "ALL" ? null : domain };
    auditLog("WORKBENCH_FILTER_CHANGE", { dim: "bizDomain", value: domain, role: roleInfo.role });
    onChange(next);
  }, [roleInfo.role, value, onChange]);

  const handleTimeChange = useCallback((t: TimeRange) => {
    const next = { ...value, timeRange: t };
    auditLog("WORKBENCH_FILTER_CHANGE", { dim: "timeRange", value: t, role: roleInfo.role });
    onChange(next);
  }, [roleInfo.role, value, onChange]);

  const handleBizAvailability = useCallback((available: boolean) => {
    if (available === value.bizDomainAvailable) return;
    onChange({ ...value, bizDomainAvailable: available, bizDomain: available ? value.bizDomain : null });
  }, [value, onChange]);

  return (
    <div style={{ position: "sticky", top: 0, zIndex: 10, background: "#fff", padding: "12px 16px", borderBottom: "1px solid #f0f0f0" }}>
      <Space size="middle" wrap>
        <DeptSelect
          value={roleInfo.isInstLeader ? (value.deptCode ?? "ALL") : roleInfo.deptCode}
          onChange={handleDeptChange}
        />
        <BizDomainSelect
          value={value.bizDomain ?? "ALL"}
          onChange={handleDomainChange}
          onAvailabilityChange={handleBizAvailability}
        />
        <TimeRangeSelect value={value.timeRange} onChange={handleTimeChange} />
      </Space>
    </div>
  );
}

export function initialFilterState(roleInfo: ReturnType<typeof useWorkbenchRole>): WorkbenchFilterState {
  return {
    scope: roleInfo.isInstLeader ? "ALL" : roleInfo.isDeptLeader ? "DEPT" : "MINE",
    deptCode: roleInfo.isInstLeader ? null : roleInfo.deptCode,
    bizDomain: null,
    timeRange: "MONTH",
    bizDomainAvailable: false, // BizDomainSelect 挂载后会更新
  };
}
```

### `auditLog` helper

若项目暂无现成 helper，在 `src/utils/audit.ts` 新建最小桩：

```ts
export function auditLog(event: string, payload: Record<string, unknown>): void {
  // 真实实现应通过现有 auditService 发送；先桩住方便埋点联调
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.debug(`[audit] ${event}`, payload);
  }
  // TODO(sprint-15/F6/T01): 替换为 auditService.record
}
```

F6/T01 会接入真实上报。

## 影响范围

- 新增：`src/pages/workbench/components/WorkbenchFilterBar.tsx`
- 新增（若无）：`src/utils/audit.ts`

## 验证

- [ ] 单测：
  - `initialFilterState_for_EMP_returns_scope_MINE()`
  - `initialFilterState_for_DEPT_LEADER_returns_scope_DEPT_with_self_dept()`
  - `initialFilterState_for_INST_LEADER_returns_scope_ALL_null_dept()`
  - `INST_LEADER_selecting_dept_sets_scope_ALL_with_deptCode()`
  - `DEPT_LEADER_dept_change_ignored_self_dept_kept()`
  - `bizDomain_availability_false_clears_selected_bizDomain()`
  - `audit_event_emitted_on_each_filter_change()`
- [ ] 手测：顶栏粘顶；三下拉切换触发 `onChange`；切时间段 bizDomain 选择保留；业务域 API 挂后整个下拉消失，其余两个继续工作。

## 完成标准

- [ ] `WorkbenchFilterState` 对外完整、可序列化（方便 F5 据此调用 `workbenchService.leaderOverview`）。
- [ ] sticky 样式在 1440 宽度下不遮挡页面主内容。
- [ ] 单测覆盖所有分支（尤其角色组合 × 业务域可用性）。
