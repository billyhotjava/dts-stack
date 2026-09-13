# Resolver Failure Report

**范围**: Sprint-31B F1/T05  
**接口**: `GET /api/internal/catalog/asset-resolution-failures?since=2026-05-17T00:00:00Z&limit=100`  
**权限**: `ROLE_SERVICE_INTERNAL`

## 目标

`CatalogAssetIdentityResolver.resolveIdentity(ref)` 解析不到资产时不再静默返回。失败会写入 `catalog_asset_resolution_failure`，供 platform / metrics / ops 排查 asset ref 为什么没有进入统一资产事实源。

## 字段

| 字段 | 含义 | 排查价值 |
|---|---|---|
| `ref` | 调用方提交的原始资产引用 | 定位 manifest / code writer / legacy 调用方 |
| `requested_at` | 失败发生时间 | 对齐发布、预览、导入运行日志 |
| `caller` | 调用方或 resolver 默认调用点 | 区分 metrics preview、publish gate、平台补偿任务 |
| `type_hint_guess` | 从 `type:value` 或 `type.value` 推断的类型前缀 | 判断是否写错资产类型 |
| `reason` | 失败原因 | 决定修复动作 |

## reason 处理

| reason | 常见触发 | Owner | 修复动作 |
|---|---|---|---|
| `UNKNOWN_TYPE_HINT` | `unknown_asset:xxx`、合作方包引用了未开放 asset type | dts-metrics / 合作方包维护者 | 改成允许的 `DATASET / DBT_MODEL / BI_DATASET / SEMANTIC_MODEL / METRIC / GLOSSARY_TERM` 或 platform 支持的新 code asset 类型 |
| `TYPE_REPOSITORY_MISS` | 类型合法，但 repository 找不到对应 code / id / FQN | dts-platform asset owner | 检查对应 writer 是否写入 `asset_ownership` / `asset_grant`，必要时跑迁移或补 seed |
| `LEGACY_FORMAT_NOT_RECOGNIZED` | 空 ref、坏的 `urn:uuid:`、无法识别的历史格式 | 调用方 owner | 改为 scoped dataset key、UUID、OpenMetadata FQN 或明确 code asset ref |
| `AMBIGUOUS_MATCH` | 同一短 code 同时命中多个候选 | 平台治理 owner | 使用全限定 ref，或清理重复 code |

## 常见模式

1. `gov_indicator:xxx` 失败  
   检查 `GovIndicatorDefinition.code` 是否存在，并确认 `CodeAssetGrantWriter` 在指标保存/发布链路已执行。

2. `modeling_sql_model:xxx` 失败  
   检查 `ModelingSqlModel.name` / `alias` 是否存在；新模型如果只在 dbt 文件中存在，需要先注册成 platform modeling asset。

3. `glossary.xxx` 或 `glossary:xxx` 失败  
   检查术语是否 ACTIVE，且 metric-pack dependencies 中是否显式声明 `GLOSSARY_TERM`。

4. `tenant:.../env:.../source:.../schema:.../table:...` 失败  
   检查 key 是否包含 `tenant/env/dialect/source/schema/table` 六段，避免多租户或多环境撞 key。

## 当前证据

- 新表: `catalog_asset_resolution_failure`
- 新内部接口: `/api/internal/catalog/asset-resolution-failures`
- 聚焦测试:
  - `CatalogAssetIdentityResolverTest.recordsFailureAuditForUnknownTypeHint`
  - `CatalogAssetIdentityResolutionAuditServiceTest`
  - `CatalogAssetResolutionFailureResourceTest`
