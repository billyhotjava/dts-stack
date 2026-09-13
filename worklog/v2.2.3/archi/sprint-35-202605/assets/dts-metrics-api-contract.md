# dts-metrics 前后端 API 契约

## 设计原则

- URL 使用复数资源和 kebab-case。
- `dts-metrics-webapp` 只调用 `dts-metrics` 对外 API；跨服务事实源由 `dts-metrics` 使用 service-auth 调 platform internal API。
- 资产查询必须显式携带 `warehouseLayer`，默认只返回 `DWS,ADS`。
- 错误使用 HTTP status 表达语义；响应体包含稳定 `code`、`message`、`details`。

## dts-metrics 对前端 API

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/metrics/visual-assets` | 查询可视化资产，默认 `layers=DWS,ADS` |
| GET | `/api/metrics/visual-assets/{assetKey}` | 获取单资产 schema、字段、血缘和治理状态 |
| POST | `/api/metrics/graphs` | 创建 graph draft |
| GET | `/api/metrics/graphs/{graphId}` | 读取 graph draft |
| PATCH | `/api/metrics/graphs/{graphId}` | 保存节点、边、布局、选择和配置 |
| POST | `/api/metrics/graphs/{graphId}/preflight` | 本地 graph/DSL 预检 |
| POST | `/api/metrics/models/{modelId}/artifacts` | 生成候选 dbt artifact |
| POST | `/api/metrics/models/{modelId}/validate` | 调 platform model-validation |
| POST | `/api/metrics/models/{modelId}/submit-review` | 提交审批 |
| POST | `/api/metrics/models/{modelId}/publish-dry-run` | 发布前 dry-run |
| POST | `/api/metrics/models/{modelId}/publish` | 通过 platform release submit 发布 |

### `GET /api/metrics/visual-assets`

Query:

```text
layers=DWS,ADS
domainCode=PROJECT
businessObjectCode=CONTRACT
keyword=month
page=0
size=20
includeDrilldown=false
```

Response:

```json
{
  "data": [
    {
      "assetKey": "dbt:model:biz_dws_project_monthly_v2",
      "name": "biz_dws_project_monthly_v2",
      "warehouseLayer": "DWS",
      "domainCode": "PROJECT",
      "businessObjectCode": "PROJECT",
      "grain": ["project_no", "stat_month"],
      "primaryKeys": ["project_no", "stat_month"],
      "timeColumns": ["stat_month"],
      "dimensionColumns": ["project_no", "dept_code"],
      "metricColumns": ["risk_count", "quality_issue_count"],
      "governanceStatus": "READY",
      "lineageStatus": "READY",
      "permissionDecision": "ALLOWED"
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

> **现状对齐（Sprint-35b F4-T02/T03）**：dts-metrics 当前从 platform 通用目录 `GET /catalog/assets-v2`
> 取数（`meta.source = "dts-platform catalog assets-v2"`），而非下表声明的专用 `GET /api/internal/metrics/visual-assets`。
> 后者由 platform 侧按权限/治理/层级返回"可建模资产"，列为**中期跨服务目标**（与 `bi/datasets/register`、`lineage/register`、
> `audit-events` 一并待 platform 就绪后切换）。`permissionDecision` 采**逐资产透传**语义：当平台载荷带该字段时原样返回
> （如 `ALLOWED`，与未来内部端点前向兼容），缺失时回退为 `PLATFORM_FILTERED`（表示"列表已由平台预过滤"，不擅自宣称
> `ALLOWED` 以免越权背书）。

### `POST /api/metrics/graphs/{graphId}/preflight`

Response 必须把失败定位到图元素：

```json
{
  "state": "GRAPH_BLOCKED",
  "diagnostics": [
    {
      "nodeId": "asset-1",
      "edgeId": null,
      "fieldId": "risk_level_code",
      "severity": "ERROR",
      "code": "standard_code_required",
      "message": "维度字段必须使用稳定 standard_code 参与 Join 或过滤"
    }
  ]
}
```

## dts-metrics 调 platform internal API

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/internal/metrics/visual-assets` | platform 根据权限、治理和层级返回可建模资产（**中期目标**；现状临时走 `/catalog/assets-v2`，见上方"现状对齐"） |
| POST | `/api/internal/metrics/asset-contracts/batch` | 批量解析 schema、grain、lineage、governance gaps |
| POST | `/api/internal/asset-permission/check` | 校验 source、preview、publish 权限 |
| POST | `/api/internal/v1/asset-permission/policy` | 获取 RLS/masking policy 和 hash |
| POST | `/api/internal/domains/resolve` | 解析数据域 |
| POST | `/api/internal/glossary/terms/resolve` | 解析术语 |
| POST | `/api/internal/data-standards/resolve` | 解析数据标准 |
| POST | `/api/internal/metrics/model-validation` | 写候选 artifact 并执行 dbt compile/test/build |
| POST | `/api/etl/dbt/release-gate/check` | 发布门禁 |
| POST | `/api/etl/dbt/release/submit` | 正式发布 |
| POST | `/api/internal/bi/datasets/register` | 注册 BI Dataset |
| POST | `/api/internal/lineage/register` | 注册 source -> DWS/ADS -> BI Dataset 血缘 |
| POST | `/api/internal/audit-events` | 记录 graph、验证、发布、撤销、回滚事件 |

## 错误码

| HTTP | code | 场景 |
|------|------|------|
| 400 | `invalid_layer` | ODS/STG 被作为普通可视化入口 |
| 400 | `grain_mismatch` | DWD 生成 DWS 时粒度或主键不兼容 |
| 403 | `asset_permission_denied` | 用户无权读取来源资产或发布目标 |
| 409 | `metric_version_conflict` | 指标 code 或版本冲突 |
| 422 | `graph_validation_failed` | graph/DSL 结构不完整 |
| 422 | `dbt_validation_failed` | dbt compile/test/build 不通过 |
| 503 | `platform_contract_unavailable` | platform internal contract 不可用，禁止生成候选 artifact |

## 前端类型要求

- `WarehouseLayer = "DWD" | "DWS" | "ADS"`；ODS/STG 只能用于 lineage DTO。
- `VisualAssetSummary` 必须包含 `warehouseLayer`、`grain`、`governanceStatus`、`permissionDecision`。
- `GraphNode` 必须包含 `sourceAssetKey`、`warehouseLayer`、`validationState`。
- `ValidationDiagnostic` 必须包含 `nodeId` 或 `edgeId` 中至少一个。
