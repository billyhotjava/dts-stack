# T02：重接 ReleaseCandidate/Materialization 调用

**优先级**：P0
**状态**：PLANNED
**依赖**：T01；F2/T03

## 目标

把候选构建、物化和操作运行全部重接到 DbtExecutionGateway，移除对旧 `/etl/dbt/run` 和执行实现细节的建模依赖。

## 技术设计（Contract-first）

- **输入契约**：已授权的 candidate/version/attempt 和 MaterializationCommand；artifact checksum/profile lease/target 完整。
- **输出契约**：execution handle 绑定 canonical pipeline/materialization run；状态由 gateway query/reconcile 投影。
- **数据流**：candidate command → materialization transaction/open run → domain/audit outbox → gateway submit → handle persist → async reconcile。
- **错误路径**：submit 确认前本地失败可安全重试；已发送未确认进入 UNKNOWN；禁止直接创建新 attempt 覆盖。
- **复用点**：既有 ReleaseCandidate、pipeline run、profile lease、relation observation。
- **禁止**：resource 调旧 ETL route；传 Docker 参数；将 Airflow 状态直接写 candidate 不做版本校验。

## 影响范围

candidate/materialization orchestration、pipeline run correlation、旧 route client caller。

## 验证

- [ ] candidate→gateway request 字段完整、无 secret。
- [ ] transaction/outbox/submit 崩溃点故障注入。
- [ ] UNKNOWN/retry 不产生第二 execution。
- [ ] old `/etl/dbt/run` modeling caller=0。

## Definition of Done

- [ ] 所有建模执行路径使用同一 gateway。
- [ ] pipeline/candidate/execution 三者可按 correlationId 对账。
- [ ] 旧执行入口可进入 F5 物理删除批次。
