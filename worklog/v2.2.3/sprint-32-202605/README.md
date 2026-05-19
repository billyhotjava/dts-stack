# Sprint-32: React Flow 指标与语义工作台（202605）

**时间**: 2026-05
**状态**: IN_PROGRESS
**类型**: Productization / Implementation（dts-metrics-webapp + dts-metrics + dts-platform + dbt gateway）
**目标**: 在已完成 `dts-metrics` 独立服务骨架、平台契约和基础指标包校验后，把指标与语义中心升级为基于 React Flow 的可视化工作台：业务对象、数据资产、Join、指标、筛选、DWS/ADS、发布和消费全部在同一张可验证图上完成。

## 结论

1. `dts-metrics` 是指标与语义产品事实源，负责 React Flow 画布、指标 DSL、metric-pack、候选 DWS/ADS artifact、版本和状态机。
2. `dts-platform` 是企业控制面事实源，必须提供资产、字段、权限、RLS、术语、数据标准、审批、审计、dbt 发布网关、BI Dataset 注册和血缘注册。
3. 模型检测不能由前端或 `dts-metrics` 直接调用 dbt。`dts-metrics` 先做本地 DSL/图校验，再调用 `dts-platform` 的模型验证/发布网关；`dts-platform` 负责用 dbt compile/test/build/release gate 做权威验证并写审计、审批和发布记录。
4. dbt 是最终 SQL/model 正确性的执行引擎，但调用入口必须封装在 `dts-platform`，因为 dbt 项目目录、凭据、发布策略、运行证据和审计都属于 platform 控制面。

## dts-platform 必须提供的能力

| 类别 | platform 能力 | dts-metrics 使用方式 |
|------|---------------|----------------------|
| 资产目录 | dataset/table/column/schema/classification/owner/lifecycle 查询 | React Flow 节点池、字段树、来源资产约束 |
| 治理解析 | domain、glossary term、data standard、metric code 冲突解析 | 指标建模、metric-pack 导入、发布前校验 |
| 权限与策略 | asset permission check、RLS policy resolve、人员/部门/密级上下文 | 预览、生成、验证、发布前全部强制校验 |
| dbt 网关 | artifact 写入、compile/test/build、release-gate、release-submit、运行证据 | 模型检测与发布唯一入口 |
| 审批审计 | review workflow、audit-events、outbox/event、发布记录 | 指标草稿、审核、发布、撤销、回滚 |
| 消费注册 | BI Dataset、血缘、资产门户、下游 lock/consumer 关系 | 发布后进入 BI/大屏/API 消费 |
| capability | metrics service、dbt gateway、BI/lineage 可用性 | 前端展示可用状态和降级提示 |

## 检测链路

```text
React Flow draft
  -> dts-metrics graph/DSL preflight
       结构完整性、节点类型、Join 基数、fanout 风险、指标依赖拓扑、字段引用、公式安全
  -> dts-platform contract precheck
       资产存在、字段存在、权限/RLS、术语/标准、审批策略、consumer lock
  -> dts-platform dbt validation gateway
       写候选 artifact -> dbt compile -> dbt test/build -> release gate -> 返回验证报告
  -> dts-platform publish gateway
       审核通过后 release submit、BI Dataset 注册、血缘注册、审计和运行记录
```

**调用原则**: `dts-metrics` 不直接持有 dbt 目录和运行凭据，也不绕过 platform 调 dbt。`dts-platform` 可以继续复用现有 `/api/etl/dbt/release-gate/check`、`/api/etl/dbt/release/submit`，但 Sprint-32 需要补一个面向 metrics 的“候选模型验证”聚合契约，返回 compile/test/build 的结构化诊断。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | platform 契约与 dbt 验证网关 | P0 | 5 | IN_PROGRESS | Sprint-31A, Sprint-31 |
| F2 | React Flow 语义图画布 | P0 | 5 | READY | F1 |
| F3 | 指标公式与口径设计器 | P0 | 5 | READY | F1, F2 |
| F4 | DWS/ADS 模型编排与 artifact 生成 | P0 | 5 | READY | F1, F2, F3 |
| F5 | 验证、发布、血缘与消费闭环 | P0 | 5 | READY | F1-F4 |
| F6 | 兼容迁移、IT 与回滚 | P0 | 4 | READY | F1-F5 |

**统计**: READY=28, IN_PROGRESS=1, DONE=0, BLOCKED=0

## 已完成基线

上一轮 Sprint-32 已交付：

- `dts-metrics` 服务骨架、Dockerfile、Compose 默认部署和健康检查。
- platform service-auth、asset permission、RLS policy、domain/glossary/data-standard resolver、dbt release gate 的基础调用。
- metric-pack v0.1 校验、DSL SQL 候选 artifact、示例行业包、迁移 dry-run。
- `/metrics/**` 前端入口已从 platform-webapp 转到 metrics 服务。

这些能力是本轮 React Flow 产品化的基础，不作为新功能重复实现。

## 非目标

- 不把 IAM、资产目录、权限、审批、审计和 dbt 执行迁入 `dts-metrics`。
- 不让 `dts-metrics-webapp` 直接访问 platform 内部表或 dbt 文件系统。
- 不接受合作方任意 SQL 作为默认模型定义；高级 SQL 只作为工程师受控模式，必须走 platform/dbt 验证。
- 不在 Sprint-32 实现完整 cost-based optimizer、cube 缓存、GraphQL/OData、向量指标搜索。

## 完成标准

- [ ] React Flow 画布可以创建业务对象、资产、Join、指标、筛选、DWS/ADS、发布节点，并保存为可重放 graph draft。
- [ ] 所有节点都能映射到 platform 提供的资产/字段/权限/治理契约，不出现本地孤立事实源。
- [ ] 指标公式支持原子、衍生、复合、时间周期和过滤条件，并能输出受控 DSL。
- [ ] DWS/ADS 候选模型能生成 dbt SQL、schema.yml、exposure/metric 文档和 lineage hint。
- [ ] 模型检测走 `dts-platform` 聚合 API，platform 内部调用 dbt compile/test/build/release gate，并返回结构化诊断。
- [ ] 发布动作由 platform 完成审核、release submit、BI Dataset 注册、血缘注册、审计和运行记录。
- [ ] 旧 `/api/semantic/**` 和旧页面入口有明确兼容、迁移或弃用提示。

## 验证策略

- `source/dts-metrics-webapp`: source contract、React typecheck、Vite build、Playwright 画布交互 smoke。
- `source/dts-metrics`: graph/DSL/artifact/pack 单元测试，platform contract client mock 测试。
- `source/dts-platform`: metrics validation gateway、dbt release gate、权限/RLS、审计/审批 focused tests。
- 集成: React Flow draft -> metrics preflight -> platform contract precheck -> dbt validation -> publish dry-run -> BI/lineage registration dry-run。

## 相关材料

- 服务拆分设计: `worklog/v2.2.3/sprint-32-202605/assets/dts-metrics-service-design.md`
- React Flow 边界说明: `worklog/v2.2.3/sprint-32-202605/assets/react-flow-metrics-contract.md`
- 集成测试计划: `worklog/v2.2.3/sprint-32-202605/it/README.md`
