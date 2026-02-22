# P1-03 问题单闭环页面落地

`status`: `done`
`priority`: `P1`

## 目标

落地问题单全流程页面，承接质量失败自动转工单后的人工闭环。

## 范围

`source/dts-platform-webapp/src/pages/governance`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IssueTicketService.java`。

## 子任务

1. 新增问题单列表（状态、优先级、责任人过滤）。
2. 新增问题单详情（处理记录、关闭动作）。
3. 支持从数据集/规则跳转到问题详情。

## 验收标准

- 问题单可创建、更新、追加动作、关闭。
- 可按责任人、部门、状态快速筛选。
- 质量失败记录可定位到对应工单。

## 完成记录

1. 在 `质量管控` 页面新增 `问题单闭环`面板，支持状态筛选与关键词检索。
2. 支持问题单创建、编辑、关闭与追加处理记录。
3. 支持问题单详情抽屉查看处理记录时间线。
4. API 已补齐 `getIssue` 与 `listIssues(params)`，支持按条件拉取与详情查询。

## 风险与回滚

- 风险：权限边界不清导致越权查看。
- 回滚：按部门上下文严格过滤。
