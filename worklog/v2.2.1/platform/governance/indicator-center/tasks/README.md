# Platform 数据治理-指标中心任务清单（v2.2.1）

> 范围：`数据治理中心 / 指标中心`，并包含其依赖的最小资产门户底座能力。  
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 阶段总览

| 阶段 | 目标 | 状态 |
|---|---|---|
| P0 | 指标中心可用性与底座依赖补齐 | done |
| P1 | 指标建模与发布闭环完善 | done |
| P2 | 指标中心工程化与可观测 | done |
| P3 | 回归门禁与上线验收 | done |

## 强制顺序

1. `P0-01-asset-baseline-for-indicators.md`
2. `P0-02-indicator-list-filter-and-search.md`
3. `P0-03-indicator-validation-preview-entries.md`
4. `P1-01-indicator-version-history.md`
5. `P1-02-indicator-reference-management.md`
6. `P1-03-indicator-publish-consistency-workflow.md`
7. `P2-01-indicator-observability-panel.md`
8. `P2-02-indicator-performance-baseline.md`
9. `P3-01-indicator-e2e-regression-gate.md`

## 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务完工后补：影响文件、验证命令、证据（日志/截图/报告）。
- 未完成 P0 不进入 P1/P2/P3 的代码实现。
