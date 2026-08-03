# T04：接入 apply、恢复、结果、retry 与历史

**优先级**：P0
**状态**：CODE_COMPLETE
**依赖**：T03

## 目标

复用现有 apply/retry 台账，把逐项事务、部分成功、刷新恢复和深链结果产品化。

## Contract-first

- **apply 输入**：runId、previewHash、selectedUniqueIds、idempotencyKey。
- **输出**：attemptId/status/summary/items；attempt 只输出 `RUNNING/SUCCESS/PARTIAL/FAILED/BLOCKED`，item 终态只输出 `CREATED/UPDATED/SKIPPED/FAILED/BLOCKED`；成功项含 modelSpecId/revision/implementationRevision/checksum。
- **恢复**：URL 固定 runId；刷新调用 GET run/apply；RUNNING 轮询有界退避。
- **状态代数**：复用统一契约；summary 完整返回 `selected/pending/succeeded/created/updated/skipped/failed/blocked`。`succeeded=created+updated`，`terminal=created+updated+skipped+failed+blocked`，`pending=selected-terminal`；pending>0 只能 RUNNING，终态 pending=0。`handled=succeeded+skipped`，`unresolved=failed+blocked`，`handled>0 && unresolved>0` 必须为 PARTIAL，因此 SKIP+FAILED/BLOCKED 也属于 PARTIAL；状态由服务端持久化，UI/审计不得重算。
- **前向迁移**：存量 attempt `SUCCEEDED→SUCCESS`、item `REPLAYED→SKIPPED`；先迁 item，再从明细重算 summary，恒等式异常即停止并审计，不猜测修复。迁移后持久化/API/审计/UI 均不输出 legacy 值或 `replayed` 计数。
- **逐项失败**：每个 FAILED/BLOCKED 项必须返回 `code/stage/category/message/retryable/recoveryAction/correlationId`；字段或依赖导致失败时附 fieldPath/dependencyUniqueId。完整契约见 [`assets/import-partial-result-contract.md`](../../assets/import-partial-result-contract.md)。
- **UI**：结果页常驻展示模型、动作、状态、失败阶段、安全原因和建议动作；详情抽屉展示 correlationId。不得仅用 toast、汇总数字或“请查看日志”代替失败原因。
- **retry**：只选择 `retryable=true` 且前置条件已满足的 FAILED/BLOCKED 项；成功项不得重放；同 key 同 request 由 `BeginDisposition.REPLAY` 返回原 attempt，同 key 异 request 409。`REPLAY` 只属于请求级幂等响应元数据，不是 item/attempt 状态或 summary 桶。
- **错误路径**：preview 过期 410、hash 漂移 409、权限变更 403；系统异常返回脱敏说明和 correlationId，不返回 SQL、ZIP 条目正文、凭据或堆栈。

## 验证

- [ ] 服务重启后恢复、重复 apply、partial+retry、并发同 key。
- [ ] populated DB 前向迁移覆盖 `SUCCEEDED/REPLAYED`，迁移后 canonical 状态、summary 和 item 明细一致；不存在长期双写或 UI 兼容旧代数。
- [ ] 对每种失败类别断言逐项失败字段完整、计数一致、恢复动作可执行且敏感正文为零。
- [ ] 成功深链进入精确 model/implementation revision 的普通业务可视化；高级 dbt 实现只能在同一模型详情内显式进入。

## Definition of Done

- [ ] 不新建另一套 import history 表；现有 run/attempt/result 是唯一事实源。
- [ ] `BeginDisposition.REPLAY` 仅返回既有 attempt，绝不新增 item、增加 skipped 或改变 summary。
- [ ] PARTIAL 不被任何 API、审计或 UI 映射为 SUCCESS，失败项无需访问服务器日志即可定位下一步动作。
