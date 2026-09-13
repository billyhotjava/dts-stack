# T01: useWorkbenchRole hook

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

新建 `src/pages/workbench/hooks/useWorkbenchRole.ts`，从 `userStore` 推导出：

```ts
type WorkbenchRole = "EMP" | "DEPT_LEADER" | "INST_LEADER";

interface WorkbenchRoleInfo {
  role: WorkbenchRole;
  deptCode: string | null;
  isInstLeader: boolean;
  isDeptLeader: boolean;
  isEmp: boolean;
  roleLabel: string; // 中文展示用
}

export function useWorkbenchRole(): WorkbenchRoleInfo;
```

## 技术设计

### 角色识别规则

优先级从高到低：`INST_LEADER` > `DEPT_LEADER` > `EMP`。

```ts
const INST_LEADER_CODES = new Set(["ROLE_INST_LEADER", "INST_LEADER"]);
const DEPT_LEADER_CODES = new Set([
  "ROLE_DEPT_LEADER", "DEPT_LEADER",
  "ROLE_SUB_INST_LEADER", "SUB_INST_LEADER",
  "ROLE_FIN_MANAGER", "FIN_MANAGER",
]);

function resolveRole(roles: string[]): WorkbenchRole {
  const set = new Set(roles.map((r) => String(r ?? "").toUpperCase()));
  if ([...INST_LEADER_CODES].some((code) => set.has(code))) return "INST_LEADER";
  if ([...DEPT_LEADER_CODES].some((code) => set.has(code))) return "DEPT_LEADER";
  return "EMP";
}
```

### 部门 code 获取

`userStore` 现有的 `userInfo` 未暴露 `deptCode` 字段。参照 `store/userStore.ts:163` 的 `normalizeToStringArray` 与其他字段提取模式，扩展：

```ts
// src/store/userStore.ts（小幅扩展，不破坏 API）
// 在 state.userInfo 结构上补 deptCode?: string
// 从 adaptedUser.attributes?.department / adaptedUser.dept_code 推断
```

> **若 userStore 改动代价较大**：临时 helper `getCurrentUserDept()` 直接读 `localStorage`/`sessionStorage` 上的 keycloak token，parse JWT 取 `dept_code` claim。与后端 T07 的 claim 名保持一致。

### 完整实现

```ts
import { useMemo } from "react";
import { useUserInfo, useUserRoles } from "@/store/userStore";

const INST_LEADER_CODES = new Set(["ROLE_INST_LEADER", "INST_LEADER"]);
const DEPT_LEADER_CODES = new Set([
  "ROLE_DEPT_LEADER", "DEPT_LEADER",
  "ROLE_SUB_INST_LEADER", "SUB_INST_LEADER",
  "ROLE_FIN_MANAGER", "FIN_MANAGER",
]);

export type WorkbenchRole = "EMP" | "DEPT_LEADER" | "INST_LEADER";

export interface WorkbenchRoleInfo {
  role: WorkbenchRole;
  deptCode: string | null;
  isInstLeader: boolean;
  isDeptLeader: boolean;
  isEmp: boolean;
  roleLabel: string;
}

function resolveRole(roles: readonly string[]): WorkbenchRole {
  const set = new Set(roles.map((r) => String(r ?? "").toUpperCase()));
  for (const code of INST_LEADER_CODES) if (set.has(code)) return "INST_LEADER";
  for (const code of DEPT_LEADER_CODES) if (set.has(code)) return "DEPT_LEADER";
  return "EMP";
}

function resolveDeptCode(userInfo: unknown): string | null {
  if (!userInfo || typeof userInfo !== "object") return null;
  const info = userInfo as Record<string, unknown>;
  if (typeof info.deptCode === "string" && info.deptCode.trim()) {
    return info.deptCode.trim();
  }
  const attrs = info.attributes;
  if (attrs && typeof attrs === "object") {
    const attrMap = attrs as Record<string, unknown>;
    const dept = attrMap.department ?? attrMap.dept_code;
    if (Array.isArray(dept) && typeof dept[0] === "string") return dept[0];
    if (typeof dept === "string" && dept.trim()) return dept.trim();
  }
  return null;
}

export function useWorkbenchRole(): WorkbenchRoleInfo {
  const userInfo = useUserInfo();
  const rawRoles = useUserRoles();

  return useMemo(() => {
    const roles = Array.isArray(rawRoles) ? (rawRoles as string[]) : [];
    const role = resolveRole(roles);
    const deptCode = resolveDeptCode(userInfo);
    return {
      role,
      deptCode,
      isInstLeader: role === "INST_LEADER",
      isDeptLeader: role === "DEPT_LEADER",
      isEmp: role === "EMP",
      roleLabel: role === "INST_LEADER" ? "所领导" : role === "DEPT_LEADER" ? "部门领导" : "员工",
    };
  }, [userInfo, rawRoles]);
}
```

## 影响范围

- 新增：`source/dts-platform-webapp/src/pages/workbench/hooks/useWorkbenchRole.ts`
- 可选修改：`source/dts-platform-webapp/src/store/userStore.ts`（若需扩展 `deptCode` 字段到 `userInfo`；建议仅补字段不改现有结构）

## 验证

- [ ] 单测 `useWorkbenchRole.test.ts`（使用 `@testing-library/react` + mock `useUserInfo` / `useUserRoles`）：
  - `identifies_INST_LEADER_from_roles()`
  - `identifies_DEPT_LEADER_from_roles()`
  - `falls_back_to_EMP_when_no_matching_role()`
  - `empty_roles_array_returns_EMP()`
  - `prefers_INST_LEADER_over_DEPT_LEADER_when_both_present()`
  - `extracts_deptCode_from_attributes_department()`
  - `returns_null_deptCode_when_missing()`

## 完成标准

- [ ] hook 返回的 `role` 严格为 `"EMP" | "DEPT_LEADER" | "INST_LEADER"` 三值之一，永不 undefined。
- [ ] `roleLabel` 中文化文案一致（员工 / 部门领导 / 所领导）。
- [ ] 单测全部通过，覆盖率 100%（hook 自身体量很小，应容易达到）。
