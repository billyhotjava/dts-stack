# T04：接入 apply、恢复、结果、retry 与历史

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T03

## 目标

复用现有 apply/retry 台账，把逐项事务、部分成功、刷新恢复和深链结果产品化。

## Contract-first

- **apply 输入**：runId、previewHash、selectedUniqueIds、idempotencyKey。
- **输出**：attemptId/status/summary/items；成功项含 modelSpecId/revision/implementationRevision/checksum。
- **恢复**：URL 固定 runId；刷新调用 GET run/apply；RUNNING 轮询有界退避。
- **retry**：仅失败/阻断后已满足条件的项；同 key 同 request replay，同 key 异 request 409。
- **错误路径**：preview 过期 410、hash 漂移 409、权限变更 403、部分失败明确 PARTIAL，不显示全局成功。

## 验证

- [ ] 服务重启后恢复、重复 apply、partial+retry、并发同 key。
- [ ] 成功深链进入精确 model/implementation revision。

## Definition of Done

- [ ] 不新建另一套 import history 表；现有 run/attempt/result 是唯一事实源。
