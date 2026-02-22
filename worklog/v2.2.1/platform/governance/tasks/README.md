# Platform 数据治理中心任务清单（v2.2.1）

> 依据：`worklog/v2.2.1/platform/governance/report/data-governance-center-gap-analysis.md`
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 阶段总览

| 阶段 | 目标 | 状态 |
|---|---|---|
| P0 | 主链路一致性修复（配置、状态、报表同源） | planned |
| P1 | 治理闭环页面补齐（巡检/合规/问题） | planned |
| P2 | 治理增强（可观测、码表、权限） | planned |
| P3 | 工程化（门禁、契约、性能） | planned |

当前看板：`worklog/v2.2.1/platform/governance/tasks/status-board.md`

## 强制顺序

1. `P0-01-governance-boundary-and-menu-contract.md`
2. `P0-02-quality-scheduler-config-alignment.md`
3. `P0-03-status-enum-unification.md`
4. `P0-04-quality-report-source-unification.md`
5. `P1-01-quality-task-ui-delivery.md`
6. `P1-02-compliance-center-ui-delivery.md`
7. `P1-03-issue-workflow-ui-delivery.md`
8. `P2-01-governance-observability-and-audit-views.md`
9. `P2-02-reference-code-enterprise-import-and-validation.md`
10. `P2-03-governance-permission-matrix-hardening.md`
11. `P3-01-governance-regression-matrix-and-gate.md`
12. `P3-02-governance-domain-model-contract-and-openapi.md`
13. `P3-03-governance-performance-and-data-volume-benchmark.md`

## 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务完工后补：影响文件、测试命令、证据链接。
- 未完成 P0，禁止进入 P1/P2/P3 的开发实施。
