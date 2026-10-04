# T05: lifecycle 状态映射对齐既有数据

**优先级**: P0
**状态**: DONE
**依赖**: F1/T06

## 目标

`CodeAssetGrantWriter` 通过 `lifecycleForStatus` / `lifecycleForModel` 把 service 层的 status 翻译成资产生命周期。但当前实现把任何不在白名单的 status 都打成 `PENDING_GOVERNANCE`，对历史数据会造成大量"虚假治理缺口"。

## 背景

`IndicatorService.lifecycleForStatus`：
```java
if (STATUS_PUBLISHED.equalsIgnoreCase(status)) return "ACTIVE";
if (STATUS_ARCHIVED.equalsIgnoreCase(status)) return "ARCHIVED";
if (STATUS_DEPRECATED.equalsIgnoreCase(status)) return "DEPRECATED";
return "PENDING_GOVERNANCE";
```

`ModelingSqlModelService.lifecycleForModel`：
```java
if (Boolean.FALSE.equals(model.getEnabled())) return "ARCHIVED";
if (STATUS_ACTIVE.equalsIgnoreCase(model.getStatus())) return "ACTIVE";
return "PENDING_GOVERNANCE";
```

但实际生产数据里：
- `GovIndicatorDefinition.status` 包含 `DRAFT / PENDING_APPROVAL / APPROVED / PUBLISHED / ARCHIVED / DEPRECATED` 六种
- `ModelingSqlModel.status` 还有 `DRAFT / TESTING / PROMOTED` 等中间态

把这些都打成 `PENDING_GOVERNANCE` 会让"治理缺口"视图大量误报，掩盖真实问题。

## 技术设计

1. 抽出 `CodeAssetLifecycleMapper.fromIndicatorStatus(...)` / `fromModelingSqlModelStatus(...)` 公共方法，集中映射：
   ```
   DRAFT, PENDING_APPROVAL, APPROVED -> DRAFT_GOVERNANCE
   PUBLISHED, ACTIVE, PROMOTED       -> ACTIVE
   TESTING                           -> TESTING
   ARCHIVED                          -> ARCHIVED
   DEPRECATED                        -> DEPRECATED
   <unknown / null>                  -> PENDING_GOVERNANCE  (真正的治理缺口)
   ```
2. 在 changelog 加 `asset_lifecycle_status` 枚举更新（如该枚举存在数据库约束）。
3. 一次性 backfill：对存量 indicator / model 重新 sync grant 与 ownership，避免历史数据停留在错误状态。Backfill 走独立 SQL 脚本或 `OnApplicationReady` 一次性任务（带开关）。
4. 「治理缺口」视图重新校准：原来命中 `PENDING_GOVERNANCE` 数量预期下降一个量级，更新仪表板阈值。

## 当前状态（2026-05-17）

- 已新增 `CodeAssetLifecycleMapper`，集中处理 Indicator / ModelingSqlModel / ApiService / DataStandard / Glossary 的 code asset lifecycle 映射。
- `DRAFT / PENDING_APPROVAL / APPROVED` 映射为 `DRAFT_GOVERNANCE`，`TESTING` 映射为 `TESTING`，`PROMOTED / PUBLISHED / ACTIVE` 映射为 `ACTIVE`。
- `CatalogAssetLifecycleStatus` 与 platform capability 已加入 `DRAFT_GOVERNANCE`、`TESTING`。
- DataStandard writer 已按 `ACTIVE -> ACTIVE`、`DEPRECATED -> DEPRECATED`、`RETIRED/ARCHIVED -> ARCHIVED` 做基础映射。
- Glossary writer 已改用公共 mapper，`APPROVED -> DRAFT_GOVERNANCE`、`PUBLISHED/ACTIVE/PROMOTED -> ACTIVE`、`DEPRECATED -> DEPRECATED`、`ARCHIVED/RETIRED/DISABLED -> ARCHIVED`。
- 历史 backfill 已通过 Liquibase `20260517_03_code_asset_lifecycle_backfill.xml` 接入，覆盖 `GovIndicatorDefinition / ModelingSqlModel / ModelingGlossaryTerm / SvcApi`。
- 仪表板基线查询已写入 `it/evidence/lifecycle-mapping-2026-05-17.md`，真实数量随部署后数据重新计算。

## 影响范围

- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CodeAssetLifecycleMapper.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- 新增 changelog（枚举值扩展）
- 新增 backfill 脚本 `source/dts-platform/src/main/resources/scripts/backfill-code-asset-lifecycle.sql`
- 治理缺口仪表板（仪表板 repo，本任务只列依赖）

## 验证

- [x] `CodeAssetLifecycleMapperTest` 覆盖主要 status 映射
- [x] `CodeAssetLifecycleMapperTest` 覆盖 published / pending approval / testing / retired 等状态
- [x] Liquibase backfill 脚本覆盖存量 grant reason lifecycle 回填
- [x] 仪表板治理缺口基线查询写入 evidence

## 完成标准

- [x] 公共 mapper 抽出。
- [x] 现有 ACTIVE / DRAFT / TESTING / DEPRECATED status 都不会被误打成 `PENDING_GOVERNANCE`。
- [x] backfill 完成，仪表板基线更新。
