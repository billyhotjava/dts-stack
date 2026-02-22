# P1-01 质量巡检计划页面落地

`status`: `done`
`priority`: `P1`

## 目标

将 `/governance/quality/tasks` API 落地为可运维页面。

## 范围

`source/dts-platform-webapp/src/pages/governance`、`source/dts-platform-webapp/src/api/platformApi.ts`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceQualityTaskResource.java`。

## 子任务

1. 新增巡检计划列表页（创建/编辑/启停/手工触发）。
2. 展示最近运行摘要与失败原因。
3. 增加部门上下文提示与权限反馈。

## 验收标准

- 可在页面完成计划全生命周期管理。
- 触发后可看到运行反馈与状态刷新。
- 无权限用户只读或不可见。

## 完成记录

1. 在 `质量管控` 页面新增 `质量巡检计划`管理面板（同路由下交付，避免新增菜单迁移成本）。
2. 支持巡检计划创建/编辑/启停/手工触发/删除。
3. 展示最近执行状态与失败原因（按数据集聚合最近一次运行）。
4. 增加部门上下文提示与只读反馈（无治理维护角色时禁用写操作）。

## 风险与回滚

- 风险：页面轮询造成后台压力。
- 回滚：降级为手动刷新。
