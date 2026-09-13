# Semantic 到 Metrics 历史映射报告

**Sprint**: Sprint-31A
**Feature**: F6/T02
**状态**: DONE

## 目标

盘点 platform 内 `semantic_*` 历史数据如何迁移到后续独立 `dts-metrics` 的 `metric_*` 领域模型，并明确 Sprint-31A 不执行生产迁移。

## 映射矩阵

| platform 历史表 / 能力 | dts-metrics 目标 | 迁移策略 | platform 保留内容 |
|---|---|---|---|
| `semantic_subject_domain` | `metric_subject_domain` | dry-run 后迁移 code/name/governance_domain_id | 一个 Sprint 的兼容读代理 |
| `semantic_business_object` | `metric_business_object` | 迁移业务对象、主键、主题域关系 | 兼容查询和弃用日志 |
| `semantic_object_table_mapping` | `metric_object_asset_ref` | 改为引用 platform `asset contract` 中的 `assetKey/grantAssetId` | 禁止新增绕过 platform 的表引用 |
| `semantic_dimension` | `metric_dimension` | 迁移维度定义、语义类型、来源字段 | 只读兼容 |
| `semantic_metric` | `metric_definition` | 迁移指标口径、公式 JSON、单位、格式、负责人 | 只读兼容 |
| `semantic_model` | `metric_model` | 迁移 DWS/ADS/BI Dataset 候选模型元数据 | 发布仍走 platform/dbt gate |
| `semantic_model_dimension` | `metric_model_dimension` | 迁移模型维度关系 | 只读兼容 |
| `semantic_model_metric` | `metric_model_metric` | 迁移模型指标关系 | 只读兼容 |
| `semantic_generated_artifact` | `metric_artifact` | 迁移生成物记录，不直接信任为已发布 | 发布证据重新通过 platform 校验 |
| `semantic_model_review_log` | `metric_review_event` | 迁移审核历史 | platform audit 保留最终审计事实 |
| `semantic_model_run` | `metric_run` | 迁移运行历史，外部 run id 保留 | 平台运行观测可读取汇总 |
| `gov_indicator_definition` | `metric_definition` or `GOV_INDICATOR` compatibility asset | 先登记为治理指标资产并输出迁移候选，生产迁移需人工确认口径 | platform 保留治理视图和历史版本 |
| `gov_indicator_template` | `metric_pack` template or `GOV_INDICATOR_TEMPLATE` compatibility asset | 可转为 metric-pack 模板，但不自动发布 | platform 保留模板管理和导入 |
| `gov_indicator_run` | `metric_run_event` | 运行事件回送 platform audit/observability 后再迁移 | platform 保留历史观测 |
| `gov_indicator_subscription` | `metric_notification_channel` | 迁移为指标订阅/告警通道候选 | platform 保留订阅历史 |
| `data_standard` / `metadata_standard` | `DATA_STANDARD` / `METADATA_STANDARD` assets | 不迁入 dts-metrics，只作为指标和字段的 glossary/standard 依赖 | platform 继续作为标准事实源 |
| `modeling_glossary_term` | `GLOSSARY_TERM` asset + metric term binding | 指标必须绑定术语；术语审批仍复用 platform 现有能力 | platform 保留术语事实源 |

## 资产和权限映射

- 所有来源表、DWD/DWS/ADS、BI Dataset 引用必须转为 platform `asset contract`。
- `dts-metrics` 不能直接保存 platform 内部 `catalog_dataset.id` 作为唯一事实；必须保存 `assetType + assetKey` 或 `grantAssetType + grantAssetId`。
- 预览、发布、BI 注册前必须调用 platform asset permission check。
- 历史缺失密级或资产映射的数据只能进入待治理或待确认，不自动发布。
- 旧 Gov 指标和新 metric-pack 在一个 Sprint 兼容窗口内允许并存，但对外读取必须优先暴露 platform 资产身份和迁移状态，避免形成两套指标事实源。

## 迁移阶段

| 阶段 | 动作 | 结果 |
|---|---|---|
| Sprint-31A | 输出映射报告和 API 兼容矩阵 | 明确边界 |
| Sprint-31 | 主链路只依赖 platform 资产事实源 | 不再扩散 platform semantic 写入 |
| Sprint-32 | dts-metrics 执行 dry-run 和最小迁移 | 生成 metric_* 候选 |
| 后续 | 生产迁移和回滚脚本 | 单独验收 |

## 风险

- 历史 `semantic_object_table_mapping` 可能存的是表名而非稳定资产键，需要用 Catalog dry-run 补映射。
- 公式 JSON 可能包含旧 SQL 片段，迁移时必须重新校验受控 DSL。
- 已生成 artifact 不代表已通过当前发布门禁，不能直接视为可用 DWS/ADS。
