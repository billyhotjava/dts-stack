# T03：收敛 ReleaseCandidate 与 Materialization

**优先级**：P0
**状态**：PLANNED
**依赖**：T02；F4/T01 DbtExecutionGateway 契约

## 目标

让 ReleaseCandidate 独占构建/审核/发布控制，Materialization 只以 candidate/version/attempt 驱动执行和关系核验。

## 技术设计（Contract-first）

- **输入契约**：candidate commands 携带 `candidateId/version/attempt/expectedVersion/idempotencyKey`；物化命令另含 environment/target/artifactChecksum/profileLeaseId。
- **输出契约**：candidate state、execution handle、pipeline/materialization status、relation evidence refs、CatalogAssetRef。
- **数据流**：Lifecycle release-ready → candidate → independent review/publish → materialization → execution gateway → relation probe → catalog/audit outbox。
- **错误路径**：职责冲突 403；active claim 冲突 409；evidence 不完整 422；UNKNOWN execution 必须对账；relation 不存在不发布资产。
- **复用点**：既有 ReleaseCandidate、pipeline/materialization ledger、profile lease 与 Catalog port。
- **禁止**：facade 自有 candidate 状态；compile 视作物化；自动替 reviewer/operator。

## 影响范围

Candidate command service、materialization orchestrator、relation/catalog adapters。

## 验证

- [ ] candidate/version/attempt/idempotency uniqueness tests。
- [ ] reviewer/operator duty、403、claim conflict tests。
- [ ] dbt success + relation missing 保持失败/待对账。
- [ ] publish 事务产生 domain event + audit outbox。

## Definition of Done

- [ ] 所有发布/物化入口委托同一 candidate/materialization owner。
- [ ] 执行事实可对账且不会被旧 attempt 覆盖。
- [ ] 仅真实 relation 在发布后进入 Catalog。
