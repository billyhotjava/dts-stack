# T04: 删除前端 workbenchService.ts 收藏类型与方法

**优先级**: P0
**状态**: READY
**依赖**: 无（与 T02/T03 并行安全）

## 目标

`source/dts-platform-webapp/src/api/services/workbenchService.ts` 删除收藏相关类型与方法。

## 技术设计

### 删除

- `WorkbenchFavorite` type
- `WorkbenchFavoriteUpsert` type
- `favorites()` / `createFavorite(...)` / `updateFavorite(...)` / `deleteFavorite(...)` 方法

### 保留

- `WorkbenchOverview` / `WorkbenchTodoItem`（暂保留；F5 最终是否保留 Todo 端点视情况）
- `overview()` / `todos()`（本 sprint 不动）

### 删除后文件预期

```ts
import apiClient from "../apiClient";

export type WorkbenchOverview = {
  generatedAt?: string;
  myAssets?: number;
  todayNewAssets?: number;
};

export type WorkbenchTodoItem = {
  type: string;
  title?: string;
  status?: string;
  createdAt?: string;
  taskId?: string;
  requestId?: string;
  datasetId?: string;
  message?: string;
  requester?: string;
};

export default {
  overview: () => apiClient.get<WorkbenchOverview>({ url: "/workbench/overview" }),
  todos: () => apiClient.get<WorkbenchTodoItem[]>({ url: "/workbench/todos" }),
};
```

### 补充：本 task 顺便加入 leaderOverview 方法？

**不**。`leaderOverview` 的类型与方法属于 F3/F5 范畴（前端消费后端新端点），在那边统一加，避免本清理 task 职责漂移。

## 影响范围

- `source/dts-platform-webapp/src/api/services/workbenchService.ts`

## 验证

- [ ] `pnpm tsc --noEmit` 通过（注意：`workbench/index.tsx` 会因引用 `WorkbenchFavorite` 报错 → 由 T05 一并修复；建议 T04 + T05 合并 commit）。
- [ ] `rg "WorkbenchFavorite|createFavorite\(|updateFavorite\(|deleteFavorite\(|favorites\(" source/dts-platform-webapp/src/` 只剩 `workbench/index.tsx` 命中（T05 处理）。

## 完成标准

- [ ] `workbenchService.ts` 无 favorite-related 符号。
- [ ] 与 T05 合并 commit 后，`pnpm tsc --noEmit` 通过。
