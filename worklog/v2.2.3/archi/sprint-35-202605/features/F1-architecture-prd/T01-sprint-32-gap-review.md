# T01: Sprint-32 差距评审与继承边界

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

评审 Sprint-32 已有 React Flow、服务拆分、metric-pack、dbt gate 方案，明确哪些决策继承、哪些必须重构到 Sprint-35。

## 技术设计

- 继承 `dts-metrics` 与 `dts-platform` 的事实源边界。
- 继承 platform/dbt validation gateway 是唯一权威检测入口。
- 将 Sprint-32 中分散的权限、RLS、审计、发布约束整理成 Sprint-35 review gates。
- 输出缺陷表：分层入口不清、feature 顺序不适合实施、API DTO 不完整、安全评审缺口。

## 影响范围

- `worklog/v2.2.3/sprint-32-202605/README.md`
- `worklog/v2.2.3/sprint-32-202605/assets/*.md`
- `worklog/v2.2.3/sprint-35-202605/assets/sprint-32-review.md`

## 验证

- [ ] Review 文档至少列出 P0/P1 findings、保留决策和 Sprint-35 新硬约束。
- [ ] 每个 finding 都映射到 Sprint-35 的 feature 或 review gate。

## 完成标准

- [ ] Sprint-32 不被直接覆盖。
- [ ] Sprint-35 的范围能解释为什么从 DWS/ADS 默认入口重构。
