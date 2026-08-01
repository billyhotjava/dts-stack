# T04：故障注入、Chrome95、审计核对与旧入口退役

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01～T03

## 目标

最后集中验证并发、重放、重启、超时、stale、跨租户、恶意 ZIP 和旧入口退役，形成 Sprint Go/No-Go。

## 验收矩阵

- **故障**：服务在 inspect/apply/commit/dispatch 各事务边界崩溃后恢复。
- **并发**：相同 idempotency key、相同模型不同草稿、双边 drift、旧 callback。
- **安全**：FX-05、跨租户、无 read/write、密级/脱敏失败。
- **浏览器**：Chrome95 深链、上传、三视图、冲突、运行结果四态。
- **审计**：动作字典、stage、actor/IP、correlation、脱敏、重试/DLQ。
- **退役**：建模 UI caller=0；旧脚本/重复 parser/共享文件写按批准矩阵物理删除。

## Definition of Done

- [ ] 所有适应度函数与 IT-01～08 通过。
- [ ] GitNexus detect_changes 和独立 Java/TS/DB/security review 无未解决 HIGH/CRITICAL。
- [ ] 未部署/未验证部分明确标为 NO-GO，禁止用源码测试冒充现场验收。
