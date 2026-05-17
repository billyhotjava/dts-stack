# T05: lifecycle 状态映射对齐既有数据

**优先级**: P0
**状态**: READY
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

## 影响范围

- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CodeAssetLifecycleMapper.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- 新增 changelog（枚举值扩展）
- 新增 backfill 脚本 `source/dts-platform/src/main/resources/scripts/backfill-code-asset-lifecycle.sql`
- 治理缺口仪表板（仪表板 repo，本任务只列依赖）

## 验证

- [ ] `CodeAssetLifecycleMapperTest` 覆盖所有 status 映射
- [ ] `IndicatorServiceTest.lifecycle_publishedMapsToActive`
- [ ] `IndicatorServiceTest.lifecycle_pendingApprovalMapsToDraftGovernance_NotPendingGovernance`
- [ ] 仪表板治理缺口数量在 backfill 前后对比

## 完成标准

- [ ] 公共 mapper 抽出。
- [ ] 现有 ACTIVE / DRAFT / TESTING / DEPRECATED status 都不会被误打成 `PENDING_GOVERNANCE`。
- [ ] backfill 完成，仪表板基线更新。
