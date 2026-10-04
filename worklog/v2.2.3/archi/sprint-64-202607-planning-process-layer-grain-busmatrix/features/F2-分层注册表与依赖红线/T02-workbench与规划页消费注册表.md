# T02: workbench 与规划页消费注册表

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

分层展示单一事实源化：workbench 分层卡与主题域规划卡都从注册表渲染。

## 技术设计

- 删除 `DataManagementWorkbenchPage.tsx` 的 WAREHOUSE_LAYER_PLAN 常量，改为 map 注册表（title/responsibility/route 映射保留）。
- SubjectAreasPage 规划卡层选择读注册表（当前仍锁定 DWD，展示职责说明）。

## 影响范围

- `DataManagementWorkbenchPage.tsx` / `SubjectAreasPage.tsx` 及契约测试更新

## 验证

- [x] workbench API 优先消费分层注册表，API 不可用时保留显式静态回退；契约回归通过。
