# 黄金链路状态机

**Sprint**: Sprint-31
**Feature**: F1/T01
**状态**: DONE

## 状态

```text
SOURCE
  -> ODS_READY
  -> DBT_READY
  -> CATALOG_READY
  -> SEMANTIC_READY
  -> CONSUMABLE_READY
```

## 状态定义

| 状态 | 含义 | 必要证据 |
|---|---|---|
| `SOURCE` | 数据源、文件或 API 已登记，凭据由 platform 管理 | data source id、连接测试、密级/归属 |
| `ODS_READY` | 原始数据已具备 ODS 落地契约或预检结果 | ODS schema/table、字段映射、预检结果 |
| `DBT_READY` | dbt STG/DWD/DWS/ADS 模型可编译、测试和构建 | dbt compile/test/build、schema.yml、run_results |
| `CATALOG_READY` | 模型资产已进入 platform 资产事实源 | asset contract、schema contract、governance gaps、lineage evidence |
| `SEMANTIC_READY` | 指标/语义候选可引用资产并通过审核 | metric model、formula DSL、permission check、review status |
| `CONSUMABLE_READY` | BI Dataset / 大屏 / API 可被授权用户消费 | asset_grant、classification、发布记录、审计 |

## API 契约

`GET /api/capabilities` 和 `GET /api/internal/capabilities` 暴露 `goldenPath`：

```json
{
  "contractVersion": "2026-05-sprint31",
  "states": [
    "SOURCE",
    "ODS_READY",
    "DBT_READY",
    "CATALOG_READY",
    "SEMANTIC_READY",
    "CONSUMABLE_READY"
  ],
  "primaryRoute": "JDBC first, file/API capability-gated"
}
```

## 约束

- JDBC 是本 Sprint 的优先验收主线。
- 文件/API 先通过能力矩阵明确可用边界，再逐步补齐产品化。
- 所有后续 Feature 的验收必须能映射到上述状态。
