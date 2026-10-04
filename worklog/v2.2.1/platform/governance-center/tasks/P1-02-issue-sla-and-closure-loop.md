# P1-02 问题工单 SLA 与闭环

`status`: `done`
`priority`: `P1`

## 目标

把质量异常到工单处理闭环打通，支持 SLA 追踪。

## 后端实施点

1. 工单新增 SLA 截止时间、逾期标记、处理时长字段。
2. 质量运行失败自动关联/创建工单策略。
3. 提供工单状态流转校验（Open -> Processing -> Resolved -> Closed）。

## 前端实施点

1. 工单列表新增 SLA、逾期、高优先级筛选。
2. 工单详情显示关联规则与运行日志。
3. 关闭工单必须填写处理结论。

## 验收标准

- 异常到工单可追溯。
- 可按 SLA 统计逾期率与平均处理时长。

## 本轮进展

1. 问题单 API 增强（后端）：
   - `GET /api/governance/issues` 新增筛选参数：`priority`、`overdue`。
   - 新增 `GET /api/governance/issues/metrics?days=30`，输出逾期率与平均处理时长等 SLA 指标。
2. 工单状态流转校验（后端）：
   - 状态规范化为：`OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED`。
   - 禁止跨阶段跳转；`close` 仅允许从 `RESOLVED` 进入 `CLOSED`。
   - `RESOLVED/CLOSED` 必填处理结论（resolution）。
3. SLA 字段与时长（后端）：
   - `IssueTicketUpsertRequest` 支持 `dueAt`、`resolution`。
   - 工单创建时若未指定 `dueAt`，按严重性/优先级自动推导默认 SLA 截止时间。
   - DTO 增加 `overdue`、`overdueDurationMs`、`handlingDurationMs`。
4. 工单闭环界面（前端）：
   - 问题单列表新增筛选：状态、优先级、逾期。
   - 列表新增 SLA 列（截止时间+逾期状态）。
   - 关闭按钮仅在 `RESOLVED` 状态可用；关闭时强制输入处理结论。
   - 详情页新增关联质量运行信息（规则ID、运行状态、运行指标）。
