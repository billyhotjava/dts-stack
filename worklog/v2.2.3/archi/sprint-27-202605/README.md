# Sprint-27: ELT 与指标可视化企业级补强

**时间**: 2026-05
**状态**: IN_PROGRESS
**类型**: Product Capability / Audit Consistency / Event Foundation
**目标**: 在不提前固化客户审批、端到端权限和脱敏模式的前提下，优先补齐 ELT 可视化、指标可视化和审计一致性；Kafka 仅作为可选基础设施与事件底座预留，不进入核心业务依赖。

## 背景

架构审查确认 `dts-platform`、`dts-ingestion`、`dts-analytics` 已具备数据平台基础能力，但企业级差距集中在统一审计、事件化、观测、发布治理以及跨模块可视化闭环。客户侧审批模式、端到端权限和脱敏策略仍未最终确定，本 Sprint 不强行收敛安全策略，只保留扩展口和审计证据字段。

补充调研确认：`v2.3.0` 当前只是引入 Kafka 容器级基础设施，没有承载实际业务事件。因此本 Sprint 对 Kafka 采取保守策略：不直接 merge 业务代码，不把功能依赖 Kafka，只做事件契约、outbox 设计和可开关 smoke。

## 架构原则

- **可视化优先**: 先让 ELT 链路、指标运行、质量状态、血缘影响对用户可见、可解释、可验收。
- **审计先统一**: 权限和脱敏策略未定时，不先做强策略闭环；但所有关键操作必须具备一致审计语义。
- **Kafka 不进关键路径**: Kafka 容器可纳入环境，但业务功能必须在 Kafka 未启用时正常工作。
- **Outbox 优先**: 事件发布采用 outbox 思路，先保证事件可补发、可追踪，再逐步接 Kafka。
- **聚合 API 优先**: UI 页面只消费 platform Sprint-27 聚合 API，避免前端直接拼接跨域接口导致空数据不可解释。
- **策略接口预留**: 端到端权限、脱敏、审批只定义接口、字段、枚举和审计上下文，不固化客户规则。
- **小步验收**: 每个 feature 都要有页面/API/脚本级验收，不以“架构预研完成”作为交付。

## Feature 列表

| Feature | 优先级 | 状态 | 目标 |
|---------|--------|------|------|
| F1-ELT可视化控制台 | P0 | IN_PROGRESS | 已切入 `/api/platform/sprint27/elt-console` 聚合 API，展示采集、dbt、质量、血缘、发布状态的一体化链路 |
| F2-指标可视化运营台 | P0 | IN_PROGRESS | 已切入 `/api/platform/sprint27/metric-operations` 聚合 API，展示指标定义、运行、质量、订阅、消费和异常趋势 |
| F3-审计一致性与证据链 | P0 | IN_PROGRESS | 已切入 `/api/platform/sprint27/audit-evidence` 聚合 API，统一 platform、ingestion、analytics 关键操作审计语义 |
| F4-事件观测与策略接口预留 | P1 | IN_PROGRESS | 已切入 `/api/platform/sprint27/events-console` 聚合 API，Kafka 仅可选接入；指标、语义模型、dbt 关键动作开始写入 outbox |
| F5-发布治理与验收 | P1 | IN_PROGRESS | 已切入 `/api/platform/sprint27/release-governance` 聚合 API，建立 ELT/指标发布前检查、回归脚本和证据归档 |

**统计**: PLANNED=0, IN_PROGRESS=5, DONE=0, BLOCKED=0

## 架构基线

UI 重构前先遵守 [Sprint-27 架构基线设计](./architecture-baseline.md)：`admin` 管身份组织，`platform` 管平台控制面和聚合 API，`ingestion` 管采集执行，`analytics` 管 BI/分析运行，`platform-webapp` 作为统一业务入口。Kafka 只作为可选基础设施，不进入本 Sprint 主链路。

## 非目标

- 不在本 Sprint 固化客户最终审批流。
- 不强制实现端到端权限和全出口脱敏。
- 不直接 merge `v2.3.0` Kafka 编译产物或依赖缺失源码的 jar。
- 不让 Kafka 成为 ELT/指标可视化的启动前置条件。
- 不重写 ingestion、dbt、analytics 的核心执行引擎。
- 不引入新的一套工作流引擎替代现有审批/发布能力。

## Kafka 采用策略

本 Sprint 只接受以下 Kafka 相关改造：

1. 环境层允许引入 Kafka/Kafka UI 容器，并默认可关闭。
2. 代码层只定义事件 envelope、topic 命名、outbox 表和事件发布接口。
3. 审计事件可先写库，Kafka 仅作为后续异步外送通道。
4. 业务服务调用不直接依赖 KafkaTemplate。
5. smoke 只验证 Kafka 可选启停，不影响主流程。

候选 topic:

| Topic | 用途 | 本 Sprint 状态 |
|-------|------|----------------|
| `dts.audit.events` | 审计事件外送 | 只定义契约，可选发布 |
| `dts.ingestion.execution-events` | 采集执行状态 | 只定义契约 |
| `dts.metric.run-events` | 指标运行状态 | 只定义契约 |
| `dts.lineage.change-events` | 血缘变更 | 只定义契约 |
| `dts.release.events` | 发布治理状态 | 只定义契约 |

## 验收标准

- ELT 页面可以按任务/资产看到采集、dbt、质量、血缘、发布状态。
- 指标页面可以按指标看到定义、运行、异常、消费和质量状态。
- platform、ingestion、analytics 的关键操作审计字段口径一致。
- Kafka 未启用时，核心页面、API、smoke 均可通过。
- 事件契约和 outbox 设计文档完整，后续可无破坏接入 Kafka。
- Sprint-27 smoke 脚本能沉淀到 `it/evidence`。
- Sprint-27 页面空数据必须返回 `sources` 状态，能区分 `READY`、`EMPTY` 和 `ERROR`。
- 语义建模菜单必须具备后端诊断入口，覆盖指标工作台、主题域映射、业务对象 JOIN、指标可视化配置、DWS/ADS 数据集、审核发布和血缘、模型运行监控，能区分 `READY`、`PARTIAL` 和 `EMPTY`。
