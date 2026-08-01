# T02：统一 ReleaseCandidate 与 DbtExecutionGateway 执行

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01

## 目标

把 DBT_MANAGED 实施版本的构建、审核、发布和取消纳入既有 candidate/version/attempt 与 profile lease，不允许编辑器直接 run。

## Contract-first

- **输入**：固定 model/implementation/artifact/source checksum 的 candidate command。
- **输出**：candidateId/version/attempt、ExecutionHandle、pipeline run、状态投影。
- **错误路径**：active claim 冲突、profile lease 无效、executor UNKNOWN、callback mismatch、取消状态冲突均 fail-closed。
- **禁止**：前端或 import service 调 `/etl/dbt/run`、Docker 或 Airflow REST。

## 验证

- [ ] 幂等 submit/cancel/query、超时/UNKNOWN、旧 attempt callback。
- [ ] SQL 草稿未 commit 不能进入 candidate。

## Definition of Done

- [ ] Airflow/dbt 仍是执行事实，platform 不建第二 scheduler/run owner。
