# P0-01 治理边界与菜单契约固化

`status`: `done`
`priority`: `P0`

## 目标

固化“治理中心 / 目录中心 / 建模中心”边界，消除菜单与路由含义冲突。

## 范围

`source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`、`source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`、`source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`。

## 子任务

1. 梳理治理中心菜单项与对应页面映射，形成单一映射表。
2. 修正命名与入口归属（例如质量报告归属与描述一致）。
3. 补充治理中心模块边界文档并纳入回归清单。

## 验收标准

- 菜单、路由、页面三者一一对应，无歧义入口。
- 角色默认菜单权限清单与治理范围一致。
- 新增/调整映射具备文档记录。

## 完成记录

1. 路由映射中将 `/governance/quality` 归属到治理页面组件 `QualityReportPage`，避免语义漂移到目录页面。
2. 新增治理页面适配层：`source/dts-platform-webapp/src/pages/governance/QualityReportPage.tsx`。
3. 输出治理菜单契约文档：`worklog/v2.2.1/platform/governance/report/p0-01-governance-menu-route-contract.md`。

## 风险与回滚

- 风险：调整菜单影响用户习惯。
- 回滚：保留旧入口别名一版，下一版再删除。
