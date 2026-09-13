# F1: 中心职责与菜单收敛

**优先级**: P0
**状态**: DONE
**目标**: 固定数据接入中心、数据开发中心、数据治理中心、语义与指标中心、资产门户的职责边界，清理重复菜单和重复指标入口。

## 背景

当前指标能力出现在多个中心，客户无法判断应该在治理中心、开发中心还是语义建模页完成指标设计。Sprint-25 先从菜单和路由层收敛，让产品 IA 先稳定。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 盘点 `portal-menu-seed.json` 中数据接入、数据开发、数据治理、资产门户、语义指标相关菜单 |
| T02 | DONE | 将 `语义与指标中心` 固定为根菜单，与数据开发中心同级 |
| T03 | DONE | 移除治理中心中的指标主入口，治理中心只保留标准、质量、安全、责任能力 |
| T04 | DONE | 移除开发中心中的指标语义建模主入口，开发中心聚焦 SQL/dbt/调度 |
| T05 | DONE | 将 `血缘视图` 命名收敛为 `血缘与影响分析` |
| T06 | DONE | 更新 `role-menu-defaults.json`，保证菜单权限和新路由一致 |
| T07 | DONE | 保留旧路由兼容，但菜单不再暴露旧路径 |

## 验收标准

- `语义与指标中心` 是指标相关唯一主入口。
- 数据治理中心不再出现独立指标中心菜单。
- 数据开发中心不再出现业务指标配置菜单。
- 血缘全局入口名称表达“影响分析”，不是单纯图谱视图。
- 菜单 JSON 通过 `jq empty`。

## 涉及文件

- `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`
- `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`
