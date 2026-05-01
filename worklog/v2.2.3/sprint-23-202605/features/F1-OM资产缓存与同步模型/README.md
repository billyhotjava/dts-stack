# F1: OpenMetadata 资产缓存与同步模型

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

建立 DTS 本地 OpenMetadata cache，让数据资产门户以 OpenMetadata 技术资产为主数据，但不把页面可用性绑定到实时 OpenMetadata API。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `om_asset_cache` / `om_column_cache` / `om_lineage_cache` Liquibase 模型 | P0 | DONE | - |
| T02 | OpenMetadata table list/detail 同步服务 | P0 | DONE | T01 |
| T03 | column/profile/raw_json 同步与 hash 变更检测 | P0 | DONE | T02 |
| T04 | lineage/test summary 同步入口 | P0 | IN_PROGRESS | T02 |
| T05 | 同步状态、失败原因、手动刷新和 forbidden database 过滤 | P0 | DONE | T02-T04 |

## 完成标准

- [x] 可从 OpenMetadata 同步 table、column、owner、domain、tags、profile 到本地 cache。
- [x] cache 记录 `om_entity_id`、`fqn`、service、database、schema、table 和 `last_synced_at`。
- [x] OpenMetadata 不可用时，已有 cache 可继续查询。
- [x] 同步失败有状态和错误摘要，不表现为空白成功。
- [x] forbidden database / 历史污染资产不会进入默认资产门户。
