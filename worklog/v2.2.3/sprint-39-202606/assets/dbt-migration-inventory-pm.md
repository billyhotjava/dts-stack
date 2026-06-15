# PM 业务包存量 dbt 迁移清单

**日期**: 2026-06-14
**范围**: `services/dts-dbt/models/pm*`
**目标**: 将仍依赖手工 dbt 文件、手工资产登记或缺运行图的模型纳入 Sprint-39 黄金链路。

## 证据来源

- `services/dts-dbt/models/pm_sources.yml`: 已存在 PM ODS source 定义。
- `services/dts-dbt/models/pm_schema.yml`: 已存在 PM DWD/DWS/ADS schema/test 定义。
- `services/dts-dbt/models/pm_sources_v2.yml`, `pm_schema_v2.yml`, `pm_stg_v2.yml`: 存在 v2 source/schema/STG 文件。
- `services/dts-dbt/target/manifest.json`: 存在 dbt manifest，可作为血缘补录输入。

## 迁移清单

| 包 | 资产范围 | 当前状态 | 风险 | 下一步动作 |
|----|----------|----------|------|------------|
| pm | `biz_dwd_quality_issue`, `biz_dwd_quality_measure`, `biz_dws_quality_period_summary`, `biz_ads_quality_kpi` 等质量域模型 | 已有 schema/test 和 source 文件；未确认黄金链路实例、资产登记、血缘快照、运行图绑定 | HIGH | 补资产登记、补血缘、补运行图、人工确认优先级 |
| pm | `biz_dwd_tech_state`, `biz_dwd_tech_state_measure`, `biz_dws_tech_state_period_summary`, `biz_ads_tech_state_kpi` 等技术状态模型 | 已有 schema/test 和 source 文件；未确认黄金链路实例、资产登记、血缘快照、运行图绑定 | HIGH | 补资产登记、补血缘、补运行图、人工确认优先级 |
| pm | `biz_dwd_risk_info`, `biz_dwd_risk_measure`, `biz_dws_risk_period_summary`, `biz_ads_risk_kpi` 等风险模型 | 已有 schema/test 和 source 文件；未确认黄金链路实例、资产登记、血缘快照、运行图绑定 | HIGH | 补资产登记、补血缘、补运行图、人工确认优先级 |
| pm | `biz_dwd_material_info`, `biz_dws_material_period_summary`, `biz_ads_material_kpi` 等物料模型 | 已有 schema/test 和 source 文件；未确认黄金链路实例、资产登记、血缘快照、运行图绑定 | HIGH | 补资产登记、补血缘、补运行图、人工确认优先级 |
| pm | 执行域模型，`pm_schema.yml` 注释标记已迁移到 `project_cockpit_schema.yml` | 文件层已迁移；仍需确认是否已有黄金链路实例和消费资产映射 | MEDIUM | 人工确认迁移归属，避免重复登记 |
| pm_v2 | `pm_sources_v2.yml`, `pm_schema_v2.yml`, `pm_stg_v2.yml` | v2 文件存在；STG 仅允许内部层，不作为业务入口发布 | MEDIUM | 将 ODS source 接入 `GoldenChainOdsDbtSourceContractService`，STG 标记为内部诊断层 |

## 统一迁移动作

1. 对 HIGH 风险模型批量创建黄金链路实例，绑定 source、dbt model、catalog asset、lineage、runtime graph。
2. 用 `GoldenChainOdsDbtSourceContractService` 补齐 ODS -> dbt source 候选配置。
3. 用 `GoldenChainModelReleaseGateService` 重新跑 DWD/DWS/ADS 发布门禁。
4. 用 `manifest.json` 导入或刷新模型血缘，失败项进入 F3 待治理状态。
5. 在消费发布前补 owner、分级分类、质量、血缘和权限证据。

## 结论

PM 包已有 dbt 文件基础，但还不能视为完整黄金链路资产。当前最大风险不是 SQL 缺失，而是 source、资产、血缘、运行图和权限证据没有统一绑定到产品链路。
