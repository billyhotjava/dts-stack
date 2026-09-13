# Sprint-32 React Flow 指标语义契约

## 能力陈述

数据工程师和指标负责人在 `dts-metrics-webapp` 里使用 React Flow 搭建“资产 -> 业务对象 -> Join -> 指标 -> DWS/ADS -> 发布消费”的图。图是指标语义配置的主交互面，`dts-metrics` 保存图和指标 DSL，`dts-platform` 提供企业事实源和 dbt 验证/发布网关。

## 平台边界

`dts-platform` 必须继续持有以下事实源：

- 用户、组织、角色、租户、人员密级和当前部门。
- 数据源、密钥、资产目录、字段 schema、生命周期、分类分级、owner。
- asset permission、RLS policy、授权申请、审批、审计、事件。
- dbt 项目目录、artifact 写入、compile/test/build、release gate、release submit。
- BI Dataset、血缘、资产门户注册和下游消费关系。

`dts-metrics` 只保存指标语义事实：

- React Flow graph draft。
- subject/object/join/metric/filter/model/publish node 配置。
- 指标公式 DSL、metric-pack、候选 artifact、版本状态。
- platform contract reference，不保存 platform 内部表主键作为唯一事实。

## dts-platform 需要新增或固化的 API

| API | 类型 | 用途 |
|-----|------|------|
| `GET /api/internal/metrics/source-assets` | 只读 | 返回可用于指标建模的资产、schema、字段、治理元数据和可用 join hint。 |
| `POST /api/internal/asset-permission/check` | 已有/固化 | 校验当前用户是否可读取 source asset 或发布到目标消费面。 |
| `POST /api/internal/v1/asset-permission/policy` | 已有/固化 | 返回 RLS predicate、policy source、predicate hash。 |
| `POST /api/internal/domains/resolve` | 已有/固化 | 解析 subject domain。 |
| `POST /api/internal/glossary/terms/resolve` | 已有/固化 | 解析指标术语。 |
| `POST /api/internal/data-standards/resolve` | 已有/固化 | 解析字段标准。 |
| `POST /api/internal/metrics/model-validation` | 新增 | 接收候选 dbt artifacts，执行 contract precheck + dbt compile/test/build，返回结构化诊断。 |
| `POST /api/etl/dbt/release-gate/check` | 已有/固化 | 对已验证候选模型做发布门禁。 |
| `POST /api/etl/dbt/release/submit` | 已有/固化 | 提交正式发布。 |
| `POST /api/internal/bi/datasets/register` | 新增/固化 | 把发布模型注册为 BI Dataset。 |
| `POST /api/internal/lineage/register` | 新增/固化 | 注册 source -> DWS/ADS -> BI Dataset 血缘。 |
| `POST /api/internal/audit-events` | 已有/固化 | 记录配置、验证、发布、撤销动作。 |

## 模型检测决策

最终检测入口：`dts-platform`。

最终检测引擎：dbt。

调用关系：

```text
dts-metrics-webapp
  -> dts-metrics /api/metrics/graph/preflight
  -> dts-metrics /api/metrics/models/{id}/artifacts
  -> dts-platform /api/internal/metrics/model-validation
       -> asset/schema/permission/RLS/standard/glossary precheck
       -> dbt compile
       -> dbt test/build 或 release gate
       -> structured validation report
```

原因：

- dbt 可以判断 SQL/ref/schema/test 是否真实可编译运行。
- platform 才知道 dbt 项目路径、运行目标、凭据、发布策略、审计、部门、审批和运行证据。
- metrics 直接调用 dbt 会绕过 platform 控制面，破坏权限、审计和发布一致性。

## React Flow 图模型

### 节点

- `sourceAsset`: platform dataset/table/dbt model/BI dataset。
- `businessObject`: 项目、合同、供应商等业务对象。
- `join`: Join 逻辑、基数、fanout 风险、审批要求。
- `dimension`: 维度字段、时间字段、标准字段。
- `metric`: 原子/衍生/复合指标。
- `filter`: 过滤条件、时间周期、RLS 注入点。
- `model`: DWS/ADS 输出表、物化方式、刷新周期。
- `validation`: platform/dbt 验证报告。
- `publish`: 审核、发布、BI Dataset、血缘、消费关系。

### 边

- `uses_asset`: 业务对象引用 platform asset。
- `joins_to`: 对象或资产之间的 join。
- `selects_field`: 指标/维度引用字段。
- `depends_on_metric`: 衍生/复合指标依赖。
- `materializes_to`: 指标组合生成 DWS/ADS。
- `publishes_to`: 模型发布到 BI/大屏/API 消费面。

## 状态机

```text
DRAFT
  -> GRAPH_VALIDATED
  -> CONTRACT_VALIDATED
  -> DBT_VALIDATED
  -> REVIEW_SUBMITTED
  -> APPROVED
  -> PUBLISHED
  -> CONSUMED
```

失败态：

- `GRAPH_BLOCKED`: 图结构不完整。
- `CONTRACT_BLOCKED`: platform 资产/权限/治理契约不通过。
- `DBT_BLOCKED`: dbt compile/test/build 不通过。
- `REVIEW_REJECTED`: 审批拒绝。
- `PUBLISH_FAILED`: release submit 或注册失败。

## 验收口径

- 画布可保存、加载、diff、回滚。
- 每个节点都有 platform contract reference 和 validation status。
- 验证报告能定位到具体节点、边、字段或指标。
- 发布前必须存在 `DBT_VALIDATED` 和审批通过记录。
- 所有发布、撤销、回滚动作写 platform audit。
