# T02: 语义层 REST API JSON schema

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

定义语义层对前端暴露的 REST API。只有三个核心端点：**元信息查询**、**数据查询**、**虚拟数据集 CRUD**。接口一旦定死，UI 和引擎可以独立演化。

## 交付物

`worklog/v2.2.3/sprint-13-202604/assets/specs/02-semantic-rest-api.md`
+ OpenAPI 3.1 YAML：`assets/specs/02-semantic-rest-api.openapi.yaml`

## API 清单

### 1. `GET /api/semantic/meta`

**返回当前用户可访问的 model / metric / dimension / join 元信息。**

Query params:
- `subject_area` (可选) — 筛选主题域
- `exposed_to_modeler` (可选，默认 true)
- `include_classification_above` (可选) — 只返回密级不高于此值的

Response:
```json
{
  "spec_version": "1",
  "generated_at": "2026-04-22T10:00:00Z",
  "models": [
    {
      "id": "ads_sales_daily",
      "label": "销售日汇总",
      "subject_area": "sales",
      "security_level": "INTERNAL",
      "grain": "order × day",
      "metrics": [
        {
          "id": "ads_sales_daily.revenue",
          "label": "营收",
          "type": "sum",
          "source_column": "revenue_cents",
          "format": { "type": "currency_cny", "scale": 100 },
          "security_level": "INTERNAL"
        }
      ],
      "dimensions": [
        {
          "id": "ads_sales_daily.order_date",
          "label": "下单日期",
          "type": "time",
          "granularities": ["day", "week", "month", "quarter", "year"],
          "security_level": "INTERNAL"
        }
      ],
      "joins": [
        {
          "to": "dim_customer",
          "type": "many_to_one",
          "path": "ads_sales_daily.customer_id = dim_customer.customer_id",
          "approval_required": false
        }
      ]
    }
  ]
}
```

### 2. `POST /api/semantic/query`

**执行一次查询。请求完全声明式，不含 SQL。**

Request body:
```json
{
  "base": "ads_sales_daily",
  "joins": [
    { "to": "dim_customer", "via": "ads_sales_daily.customer_id" }
  ],
  "measures": [
    "ads_sales_daily.revenue",
    "ads_sales_daily.order_count"
  ],
  "dimensions": [
    "dim_customer.region",
    { "id": "ads_sales_daily.order_date", "granularity": "month" }
  ],
  "filters": [
    { "field": "ads_sales_daily.order_date", "op": ">=", "value": "2026-01-01" },
    { "field": "dim_customer.region", "op": "in", "value": ["华东", "华北"] }
  ],
  "derived_metrics": [
    {
      "id": "_custom_1",
      "label": "平均客单",
      "expression": "[ads_sales_daily.revenue] / [ads_sales_daily.order_count]"
    }
  ],
  "order_by": [ { "field": "_custom_1", "direction": "desc" } ],
  "limit": 1000,
  "format": "arrow_ipc"                 // arrow_ipc | json
}
```

Response（JSON 模式）:
```json
{
  "status": "ok",
  "meta": {
    "sql_preview": "SELECT ...",
    "row_count": 42,
    "elapsed_ms": 187,
    "cache_hit": false,
    "security_applied": ["row_level", "classification_filter"]
  },
  "columns": [
    { "id": "region", "label": "地区", "type": "string" },
    { "id": "order_date", "label": "下单月份", "type": "date" },
    { "id": "revenue", "label": "营收", "type": "number", "format": { ... } }
  ],
  "rows": [ [ "华东", "2026-01-01", 1234567 ], ... ]
}
```

Response（Arrow 模式）:
- HTTP header `Content-Type: application/vnd.apache.arrow.stream`
- Body 是 Arrow IPC stream
- `X-Semantic-Meta` header 带 JSON-base64 编码的 meta 块

错误：
- `400` — schema 不合法（字段不存在、filter 类型不匹配）
- `422` — 语义错（密级超限、join 不在白名单、fanout 无法对称聚合）
- `504` — 查询超时（默认 30s）

### 3. 虚拟数据集 CRUD

- `GET  /api/semantic/virtual-datasets?owner=me&workspace=123`
- `GET  /api/semantic/virtual-datasets/:id`
- `POST /api/semantic/virtual-datasets`
- `PUT  /api/semantic/virtual-datasets/:id`
- `DELETE /api/semantic/virtual-datasets/:id`
- `POST /api/semantic/virtual-datasets/:id/promote` — 生成 dbt PR（F6-T04）

VirtualDataset 详细 schema 见 T04。

### 4. 辅助端点

- `POST /api/semantic/query/preview-sql` — 只编译不执行，返回 SQL（供 Card Editor "SQL 预览"使用）
- `POST /api/semantic/query/explain` — 返回执行计划 + fanout 路径分析
- `GET  /api/semantic/graph` — 返回 JoinGraphRegistry 的可视化 JSON（供画布使用）

## 幂等与缓存

- `POST /query` 是只读但 non-idempotent（因为 cache key 包含时间戳类 filter）
- 缓存 key = SHA256(body 归一化 JSON + user_id + user_classification)
- 缓存 TTL 默认 5 分钟，可通过 `cache_hint: "fresh"` 绕过

## 鉴权

- 所有端点走现有 Keycloak JWT
- JWT claims 里的 `security_level`、`dept_id`、`roles` 由 SecurityInjector 消费
- OP_ADMIN 跳过密级检查但审计日志必记

## Open Questions

1. `format: arrow_ipc` 是否本 Sprint 强制？（建议：后端两种都出，前端先用 JSON，Arrow 作为 Sprint-14 开关）
2. 是否支持多 base 的 "UNION ALL" 查询？（建议：不，复杂度爆炸，用户需要 UNION 就让工程师建 dbt view）
3. `derived_metrics` 是否可以引用其他 `derived_metrics`？（建议：允许，且在 ExpressionParser 里做拓扑排序，不超过 3 层）
4. 筛选条件 `op` 支持哪些？建议白名单：`= != > >= < <= in not_in between like is_null is_not_null`。任何 raw SQL 的 op 一律拒绝。

## 影响范围

- 新建 spec markdown + OpenAPI yaml
- 后续 F2 所有 REST 实现以此为准
- 前端 F5 的 API client 以此生成 TS 类型

## 验证

- [ ] OpenAPI yaml 能用 `openapi-generator` 生成 TypeScript client 不报错
- [ ] 3 份 curl 样例（meta / query / virtual-dataset CRUD）附在 spec 中能照抄使用
- [ ] 错误码表（400 / 422 / 504 触发条件）覆盖所有已知 edge case
- [ ] open questions 全部评审 resolution

## 完成标准

- [ ] spec markdown 完成
- [ ] OpenAPI yaml 能 lint 通过（`spectral lint`）
- [ ] 评审 sign-off
