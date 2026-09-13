# dts-metrics ELT 分层可视化 PRD

## CAPABILITY

数据工程师、指标负责人和 BI 设计人员可以在 `dts-metrics-webapp` 中选择已治理的数据仓库资产，默认从 DWS/ADS 开始构建指标、业务对象、Join、筛选、模型和发布链路；当缺少可复用 DWS 时，用户可以在受控高级流程里选择 DWD 明细资产生成新的 DWS 候选模型，再经过 platform/dbt 验证后进入可视化消费。

## CONSTRAINTS

- platform Catalog 是资产事实源，`dts-metrics` 只保存 graph、metric DSL、候选 artifact、版本和发布引用。
- `warehouseLayer` 是强字段，至少支持 `ODS`、`STG`、`DWD`、`DWS`、`ADS`。
- DWS 是默认建模入口；ADS 是已有消费资产复用或发布输出；DWD 是高级生成入口。
- DWD 进入建模前必须满足：字段 contract 存在、主键/粒度明确、维度标准码稳定、字段级血缘存在、治理缺口非阻断、RLS/masking 可解析。
- ODS/STG 不进入普通指标可视化，只能作为 lineage 和诊断信息展示。
- dbt 是模型正确性执行引擎，但入口必须是 platform validation/release gateway。
- `dts-metrics` 不保存 platform 用户、角色、数据源密码、RLS 策略事实或 dbt 运行凭据。

## IMPLEMENTATION CONTRACT

### Actors

| Actor | 权限边界 |
|-------|----------|
| 数据工程师 | 可选择 DWD 生成候选 DWS，可查看验证报告和 lineage 缺口 |
| 指标负责人 | 默认从 DWS/ADS 选择业务对象、维度和指标，维护口径和版本 |
| BI 设计人员 | 复用已发布 DWS/ADS/BI Dataset，不创建明细层新模型 |
| 平台管理员 | 管理 asset permission、RLS/masking、审批、审计和 dbt release gate |

### Surfaces

- `dts-metrics-webapp`: `/metrics/semantic/assets`、`/metrics/semantic/objects`、`/metrics/semantic/metrics`、`/metrics/semantic/models`、`/metrics/semantic/publish`。
- `dts-metrics`: graph draft、metric DSL、artifact preview、publish dry-run、metric-pack import。
- `dts-platform`: visual asset query、asset contract、permission/RLS/masking、governance gaps、model validation、release submit、BI/lineage register、audit。
- `services/dts-dbt`: dbt models and artifacts only through platform gateway.

### States

```text
ASSET_SELECTED
  -> GRAPH_DRAFTED
  -> GRAPH_PREFLIGHTED
  -> CONTRACT_VALIDATED
  -> DBT_VALIDATED
  -> REVIEW_SUBMITTED
  -> APPROVED
  -> PUBLISHED
  -> CONSUMED
```

失败态：

- `LAYER_BLOCKED`: 选择了 ODS/STG 或未治理 DWD 作为普通可视化入口。
- `GRAIN_BLOCKED`: DWD 到 DWS 的粒度、主键或 Join 规则不成立。
- `CONTRACT_BLOCKED`: schema、权限、治理、RLS/masking 或 lineage 不通过。
- `DBT_BLOCKED`: compile/test/build/release gate 不通过。
- `PUBLISH_BLOCKED`: 审批、审计、BI Dataset 或 lineage 注册失败。

### Interfaces / Data Implications

- visual asset DTO 必须包含 `assetKey`、`warehouseLayer`、`domainCode`、`businessObjectCode`、`grain`、`primaryKeys`、`timeColumns`、`metricColumns`、`dimensionColumns`、`governanceStatus`、`lineageStatus`、`permissionDecision`。
- graph draft 节点必须携带 `sourceAssetKey` 和 `warehouseLayer`，避免丢失分层语义。
- DWD 生成 DWS 候选 artifact 时必须生成 dbt SQL、schema.yml、metric/exposure docs、lineage hint 和 validation trace id。
- 指标维度枚举必须优先输出稳定 ASCII `standard_code`，中文 label 只用于展示。

## NON-GOALS

- 不让业务用户直接把 ODS/STG 拖入指标画布。
- 不从 ADS 反向生成新的标准指标口径，ADS 只作为消费复用或现有看板资产。
- 不把 dbt manifest 解析逻辑复制进 `dts-metrics`。
- 不实现任意 SQL 指标编辑器；高级 SQL 只能作为工程师受控模式并走 platform/dbt gate。

## OPEN QUESTIONS

- 客户审批模式未完全确定，F5 仅保留审批扩展点；审计必须先统一。
- 不同项目的 DWS 命名和粒度是否全部满足可自动识别，若不满足需允许 platform 手工补充 asset contract。
- 旧 `semantic_*` 数据是否在 Sprint-35 执行真实迁移，默认仍是 dry-run 和兼容代理。

## HANDOFF

该能力已经足够进入实施计划。下一步按 Sprint-35 的 F2-F5 执行：先 API 契约，再前端层级体验，再后端 graph/artifact/gateway，最后安全和 IT 准入。
