# P1-01 接入变更流程化（审批闭环）

`status`: `done`
`priority`: `P1`

## 目标

把接入变更记录从“登记台账”升级为“流程治理”。

## 范围

- 前端：`source/dts-platform-webapp/src/pages/foundation/AccessChangesPage.tsx`
- 后端：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
- 服务：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskChangeLogService.java`

## 子任务

1. 增加状态流转接口：`PENDING -> APPROVAL -> DONE/REJECTED`。
2. 增加责任人、审批意见、处理时间字段。
3. 前端增加审批动作与筛选维度。
4. 所有状态变更写入审计。

## 验收标准

- 一条变更可完整走完审批流程。
- 可按状态/责任人/风险等级筛选。
- 审批动作可追溯。

## 风险与回滚

- 风险：流程过重影响操作效率。
- 回滚：保留“快速登记”路径，审批可按策略启用。

## 实现进展（2026-02-14）

- 后端新增流转接口：`POST /api/ingestion/tasks/changes/{id}/transition`
- 新增流转动作：`SUBMIT`、`APPROVE`、`REJECT`
- 新增字段：`assignee`、`approvalComment`、`handledAt`、`handledBy`
- 列表查询已支持按 `assignee` 过滤
- 前端“接入变更记录”页已新增：
  - 责任人筛选
  - 状态流转按钮（提交审批/通过/驳回）
  - 流转弹窗（责任人、审批意见）
  - 责任人/处理时间/审批意见展示
