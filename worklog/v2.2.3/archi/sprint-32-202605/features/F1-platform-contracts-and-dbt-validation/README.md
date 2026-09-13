# F1: platform 契约与 dbt 验证网关

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

明确 `dts-metrics` 构建 React Flow 指标语义图时必须依赖的 platform 事实源，并补齐面向候选模型的 platform/dbt 验证网关。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | platform 能力契约清单 | P0 | IN_PROGRESS | - |
| T02 | source asset/schema/field 查询契约 | P0 | READY | T01 |
| T03 | 权限、RLS 与治理解析契约 | P0 | READY | T01 |
| T04 | metrics model validation gateway | P0 | READY | T02,T03 |
| T05 | 审计、审批和 capability 回传 | P1 | READY | T04 |

## 完成标准

- [ ] `dts-metrics` 不直接读取 platform 内部表。
- [ ] React Flow 节点池来自 platform 稳定资产/字段契约。
- [ ] 模型检测入口是 platform API，platform 内部调用 dbt。
- [ ] 验证报告能映射回 graph node/edge/field/metric。
