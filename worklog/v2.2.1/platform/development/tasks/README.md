# Platform 数据开发中心任务清单（v2.2.1）

> 依据：`worklog/v2.2.1/platform/development/report/data-development-center-gap-analysis.md`
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 阶段总览

| 阶段 | 目标 | 状态 |
|---|---|---|
| P0 | 主流程收敛与安全面治理 | in-progress |
| P1 | 生产可用能力补齐（异步、脚本、编排） | planned |
| P2 | 治理与工程化增强（质量、产物、语义） | planned |
| P3 | 商业化体验对齐（GitOps/多环境/运营指标） | planned |

当前执行看板：`worklog/v2.2.1/platform/development/tasks/status-board.md`

## 强制顺序

1. `P0-01-modeling-mainflow-convergence.md`
2. `P0-02-legacy-api-surface-retire.md`
3. `P0-03-dbt-run-minimal-parameterization.md`
4. `P1-01-sql-workbench-async-result-paging.md`
5. `P1-02-script-studio-mvp.md`
6. `P1-03-orchestration-observable-entry.md`
7. `P2-01-dbt-devops-capability-pack.md`
8. `P2-02-model-quality-baseline.md`
9. `P2-03-semantic-contract-alignment.md`
10. `P3-01-gitops-ci-gate.md`
11. `P3-02-multi-env-multi-arch-regression.md`
12. `P3-03-dev-center-ops-metrics.md`

## 文件索引

- `P0-01-modeling-mainflow-convergence.md`
- `P0-02-legacy-api-surface-retire.md`
- `P0-03-dbt-run-minimal-parameterization.md`
- `P1-01-sql-workbench-async-result-paging.md`
- `P1-02-script-studio-mvp.md`
- `P1-03-orchestration-observable-entry.md`
- `P2-01-dbt-devops-capability-pack.md`
- `P2-02-model-quality-baseline.md`
- `P2-03-semantic-contract-alignment.md`
- `P3-01-gitops-ci-gate.md`
- `P3-02-multi-env-multi-arch-regression.md`
- `P3-03-dev-center-ops-metrics.md`

## 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务完工后补：代码路径、测试命令、截图/日志证据。
- 未完成 P0 禁止进入 P1/P2/P3 的代码实施。
