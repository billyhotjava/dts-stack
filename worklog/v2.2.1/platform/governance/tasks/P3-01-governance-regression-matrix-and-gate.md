# P3-01 治理专项回归矩阵与发布门禁

`status`: `planned`
`priority`: `P3`

## 目标

建立治理中心专项自动回归（normal/legacy/dev + x86/arm）并接入发布门禁。

## 范围

`worklog/v2.2.1/platform/governance/scripts`、CI 门禁脚本、治理 smoke 用例。

## 子任务

1. 定义治理最小回归集（规则、巡检、问题、合规、指标、码表）。
2. 提供一键脚本产出 summary/trend/raw。
3. 将失败阈值接入发布前 gate。

## 验收标准

- 每次发版可自动出治理回归报告。
- 出现关键失败时 gate 可阻断发布。
- 矩阵结果可长期追踪。

## 风险与回滚

- 风险：脚本维护成本高。
- 回滚：先保留核心 smoke gate。
