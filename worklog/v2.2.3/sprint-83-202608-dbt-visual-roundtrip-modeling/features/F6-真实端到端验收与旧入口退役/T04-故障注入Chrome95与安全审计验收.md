# T04：故障注入、Chrome95 与安全审计验收

**优先级**：P1
**状态**：E2E_PENDING
**依赖**：T01～T03

## 目标

集中验证并发、重放、重启、超时、stale、跨租户、恶意 ZIP、物理预览标识符和公共审计，形成对应交付切片的 Go/No-Go。旧入口物理退役独立由 P2/T05 验收。

## 验收矩阵

- **故障**：服务在 inspect/apply/commit/dispatch 各事务边界崩溃后恢复。
- **并发**：相同 idempotency key、相同模型不同草稿、双边 drift、旧 callback。
- **安全**：FX-05、跨租户、无 read/write、密级/脱敏失败；恶意 relation identifier 在数据库调用前拒绝且查询计数为 0；model/implementation/candidate/version/attempt/pipelineRunId/observationAttempt/evidence 任一 pin 篡改返回 EVIDENCE_MISMATCH、0 行、0 查询。
- **浏览器**：Chrome95 深链、上传、三视图、冲突、运行结果四态。
- **审计**：动作字典、stage、actor/IP、correlation、脱敏、重试/DLQ。

## Definition of Done

- [ ] 当前切片适应度函数和对应 IT 子项通过；退役项可保持 P2 DRAFT。
- [ ] GitNexus detect_changes 和独立 Java/TS/DB/security review 无未解决 HIGH/CRITICAL。
- [ ] 未部署/未验证部分明确标为 NO-GO，禁止用源码测试冒充现场验收。
