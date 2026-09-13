# Sprint-23 执行计划

## 阶段 0 - 现状基线与身份审计

- 盘点 OpenMetadata 当前 table FQN、entity id、database service、schema、table 和 test case 数据。
- 盘点 DTS `catalog_dataset`、`catalog_table_schema`、`catalog_column_schema`、血缘和质量运行表的存量关系。
- 输出 FQN 规则差异报告：service/database/schema/table 的真实来源和不一致样例。
- 确认 forbidden database / 历史污染资产处理规则。

**退出条件**: 有一份可复现的映射基线，能说明哪些资产可自动匹配、哪些需要人工确认。

## 阶段 1 - OpenMetadata cache 层

- 建立 `om_asset_cache`、`om_column_cache`、`om_lineage_cache`。
- 实现 OpenMetadata tables / columns / lineage / quality summary 同步服务。
- 支持增量刷新、手动刷新、失败状态、lastSyncedAt 和 raw_json 留痕。
- 支持 OpenMetadata 不可用时读取最近 cache。

**退出条件**: 不改前端的情况下，后端能同步并查询 OM 技术资产快照。

## 阶段 2 - DTS 治理扩展与迁移

- 建立 `catalog_asset_extension` 和 `catalog_asset_mapping`。
- 将现有 `catalog_dataset` 的密级、部门、生命周期、主题域、负责人迁移为 extension。
- 保留 legacy dataset id 到 OM asset 的映射。
- 对未匹配资产标记 `UNMATCHED` / `MANUAL_REVIEW`，不静默合并。

**退出条件**: 存量治理属性不丢失，OM 资产能挂载 DTS 治理扩展。

## 阶段 3 - 聚合 API 与权限模型

- 新增资产聚合列表 API，主查询来自 OM cache，left join DTS extension。
- 权限过滤继续走 DTS access checker 和部门可见性。
- 新增资产详情 API，返回技术详情、治理扩展、映射状态和同步状态。
- 兼容旧 `/catalog/datasets` 和 `/catalog/datasets/{id}` 路径。

**退出条件**: 前端可以用统一 API 获取 OM 主资产列表，并保持 DTS 权限边界。

## 阶段 4 - 数据资产门户重构

- 资产列表改为展示 OM 主资产 + DTS 治理状态。
- 增加待治理筛选：待认领、待定级、待归域、映射异常。
- 资产详情展示字段、profile、tags、owner、domain、质量摘要、血缘入口和 DTS 治理属性。
- 支持从资产详情发起认领、定级、归域和生命周期更新。

**退出条件**: 用户能把 OpenMetadata 资产当作 DTS 数据资产主入口使用。

## 阶段 5 - 血缘与质量身份统一

- 血缘查询以 `om_entity_id` / `fqn` 解析技术资产，不再依赖各页面独立拼 FQN。
- 同步 OpenMetadata table/column lineage 到 cache。
- DTS 本地接入任务、运行批次、dbt、指标、报表节点作为 overlay 叠加。
- 质量报告统一展示 DTS 治理运行和 OM test case，标注来源和更新时间。

**退出条件**: 资产、字段、质量、血缘共用同一身份映射，前端不再出现“资产存在但质量/血缘找不到”的割裂状态。

## 阶段 6 - 发布门禁与回滚

- 编写迁移前检查、迁移执行、回滚脚本和数据校验 SQL。
- 补齐单测、集成测试和 smoke 脚本。
- 归档验收证据：同步结果、映射报告、资产门户截图、血缘图、质量页、OpenMetadata 不可用降级。
- 明确 feature flag：允许回退到旧 `catalog_dataset` 主路径。

**退出条件**: dev/app/legacy 至少一种环境完成 E2E，回滚路径经过验证。

## Sprint 看板规则

- 任何涉及资产身份的改动必须说明 `om_entity_id`、`fqn`、`dataset_id` 的映射关系。
- 任何读 OpenMetadata 的页面改造必须优先读 cache，不直接增加前端实时 OM 依赖。
- 任何血缘边必须标注 `source` 和 `edgeType`，避免重复或误导。
- 所有迁移任务必须有 dry-run 输出和回滚说明。
- 发现历史污染数据时新增 `MANUAL_REVIEW`，不自动删除。
