# T02：收敛 StageGate 与 Lifecycle 单一状态机

**优先级**：P0
**状态**：PLANNED
**依赖**：T01；F3/T02 QualityEvidence 契约

## 目标

让 StageGate 对明确 revision 生成可校验决定，并由 Lifecycle 独占状态迁移。

## 技术设计（Contract-first）

- **输入契约**：`StageGateRequest {planId,modelSpecId,revision,targetStage,actor,correlationId}`。
- **输出契约**：`StageGateDecision {PASS|BLOCKED,evidenceRefs,violations,evaluatedAt,checksum}`。
- **证据**：plan/revision、CatalogAssetKey、standard、quality ruleVersion/binding/run、permission、artifact；全部固定版本/ID。
- **状态迁移**：Lifecycle 接收 command + decision checksum + expectedVersion；过期/篡改 decision 重新评估。
- **错误路径**：证据缺失/过期/跨租户/质量非终态返回 422 violations；CAS 冲突 409/412；越权 403。
- **禁止**：controller/service/repository 直接更新状态；页面传 `passed=true`；选择最新质量 run 而不固定 ID。

## 影响范围

StageGate 聚合、Lifecycle service/state machine、canonical resource adapter。

## 验证

- [ ] 每个 targetStage 的 pass/block 矩阵。
- [ ] evidence checksum tamper/stale/latest-drift tests。
- [ ] 20 并发 transition 仅一个成功，无 lost update。
- [ ] SQL 计数 ≤8、无 N+1。

## Definition of Done

- [ ] 状态只能通过 state machine 推进。
- [ ] 每次决定可追溯到明确 revision 和 evidence IDs。
- [ ] 失败路径返回结构化 violations 并写分类审计。
