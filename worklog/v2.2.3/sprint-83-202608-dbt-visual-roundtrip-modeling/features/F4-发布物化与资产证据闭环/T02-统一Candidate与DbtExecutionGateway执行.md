# T02：统一 ReleaseCandidate 与 DbtExecutionGateway 执行

**优先级**：P1
**状态**：CODE_COMPLETE
**依赖**：T01、F0/T05

## 目标

把 DESIGNER_GENERATED 和 DBT_MANAGED 实施版本的构建、审核、发布和取消纳入既有 candidate/version/attempt 与 profile lease，不允许任何编辑器直接 run。现有“发布与物化”流程负责选择已固定的实现修订和物化策略。

## Contract-first

- **输入**：固定 model/implementation/artifact/source checksum 的 candidate command。
- **UI 选择**：
  - DESIGNER_GENERATED：显示“可视化生成（系统通过 dbt 物化）”，可选受 adapter 支持的 `table/view/incremental` 等策略；SQL 制品保持隐藏。
  - DBT_MANAGED：显示“高级/外部 dbt 实现”及固定 revision/checksum；materialization 来自已验证实现，修改必须先产生新 Implementation Revision。
  - 若当前只有 dbt runtime，不展示虚假的多引擎下拉框。
- **输出**：candidateId/version/attempt(int)、ExecutionHandle、pipelineRunId(UUID)、relation observationAttempt(int) 与状态投影；三类 attempt/run 身份不得混用。
- **错误路径**：active claim 冲突、profile lease 无效、executor UNKNOWN、callback mismatch、取消状态冲突均 fail-closed。
- **禁止**：前端或 import service 调 `/etl/dbt/run`、Docker 或 Airflow REST。

## 验证

- [ ] 幂等 submit/cancel/query、超时/UNKNOWN、旧 attempt callback。
- [ ] 高级 dbt 草稿未 commit 不能进入 candidate；普通可视化未形成固定生成式实现同样不能进入 candidate。
- [ ] 物化对话框不能改变 ownership，也不展示 SQL/Jinja 正文。

## Definition of Done

- [ ] Airflow/dbt 仍是执行事实，platform 不建第二 scheduler/run owner。
