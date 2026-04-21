# T04: 虚拟数据集 JSON schema + Arrow 返回约定

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标

固化两个持久化/传输合约：
- **VirtualDataset JSON schema** — 用户画布的声明式持久化格式
- **Arrow IPC 返回约定** — 前后端列式数据传输协议

这两个契约定了，UI 和后端的交互面就稳了。

## 交付物

`worklog/v2.2.3/sprint-13-202604/assets/specs/04-virtual-dataset-and-arrow.md`
+ JSON schema 文件：`assets/specs/04-virtual-dataset.schema.json`

## 一、VirtualDataset 持久化 schema

### 1. 顶层结构

```json
{
  "id": "vds_a1b2c3",
  "spec_version": "1",
  "owner": { "type": "user", "id": "u1001" },
  "workspace_id": "ws_sales",
  "name": "华东区销售 × 客户分层",
  "description": "...",
  "created_at": "2026-04-22T10:00:00Z",
  "updated_at": "2026-04-22T10:05:00Z",
  "state": "draft",                  // draft | shared | promoted_pending | promoted
  "security_level": "INTERNAL",      // 自动推导 = max(参与表)
  "base": "ads_sales_daily",
  "joins": [
    {
      "id": "j1",
      "to": "dim_customer",
      "via": "ads_sales_daily.customer_id",
      "type": "many_to_one"
    }
  ],
  "derived_metrics": [
    {
      "id": "dm1",
      "label": "付费率",
      "expression": "[付费订单数] / [总订单数]",
      "format": { "type": "percent" }
    }
  ],
  "exposed_fields": {                // 用户在画布上显式"暴露"给后续 Card 的
    "measures": ["ads_sales_daily.revenue", "dm1"],
    "dimensions": ["dim_customer.region", "ads_sales_daily.order_date"]
  },
  "default_filters": [               // 画布级 filter（每次查询都加）
    { "field": "ads_sales_daily.order_date", "op": ">=", "value": "2026-01-01" }
  ],
  "canvas_layout": {                 // UI 画布坐标，纯视觉
    "nodes": [
      { "model": "ads_sales_daily", "x": 100, "y": 200 },
      { "model": "dim_customer",    "x": 400, "y": 200 }
    ]
  },
  "usage_stats": {                   // F6 维护
    "last_queried_at": "2026-04-22T12:00:00Z",
    "query_count_30d": 42,
    "referencing_card_ids": ["card_001", "card_002"]
  }
}
```

### 2. 关键字段约定

- `id` 格式 `vds_<12位 base36>`
- `state` 状态机：`draft` → `shared` → `promoted_pending` → `promoted`
- `base` 必须是 dbt manifest 里的 `model.name`
- `joins[].to` 必须是 `base` 通过 JoinGraphRegistry 可达的 model
- `derived_metrics[].expression` 必须通过 F4 ExpressionParser 校验
- `canvas_layout` 对后端查询无意义，仅存不校验

### 3. 存储表设计

```sql
CREATE TABLE gov_virtual_dataset (
    id VARCHAR(32) PRIMARY KEY,
    spec_version VARCHAR(8) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    owner_user_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64),
    state VARCHAR(32) NOT NULL DEFAULT 'draft',
    security_level VARCHAR(32) NOT NULL,
    base_model VARCHAR(128) NOT NULL,
    definition JSONB NOT NULL,           -- 完整的 VDS JSON
    usage_stats JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    promoted_pr_url VARCHAR(500)         -- F6 填写
);

CREATE INDEX ix_vds_owner ON gov_virtual_dataset(owner_user_id);
CREATE INDEX ix_vds_workspace ON gov_virtual_dataset(workspace_id);
CREATE INDEX ix_vds_base ON gov_virtual_dataset(base_model);
```

### 4. 与 Card 的关系

老 Metabase fork 的 `AnalyticsCard.query` 字段当前是 MBQL JSON。
本 Sprint 扩展：Card 可以是以下两种 query 之一：

```json
{
  "engine": "mbql_legacy",  // 或 "semantic_v1"
  "query": { ... }
}
```

当 `engine=semantic_v1` 时：
```json
{
  "virtual_dataset_id": "vds_a1b2c3",    // 或 inline base + joins
  "measures": [...],
  "dimensions": [...],
  "filters": [...],
  "derived_metrics": [...],
  "visualization": { "type": "line_chart", "options": {...} }
}
```

### 5. 版本化与冲突

- `spec_version` + `updated_at` 乐观锁
- 客户端保存时带上 `expected_updated_at`，后端校验一致才写
- 不支持 fork / branch（后续 Sprint 评估）

## 二、Arrow IPC 返回约定

### 1. 触发条件

当客户端请求头带 `Accept: application/vnd.apache.arrow.stream`，或 POST body 里 `"format": "arrow_ipc"`，返回 Arrow IPC stream。

### 2. Schema 字段

Arrow schema 的每列对应一个 measure / dimension / derived metric。字段 metadata：

```
column.metadata:
  dts.label          = "营收"
  dts.semantic_type  = "measure" | "dimension" | "derived"
  dts.source_id      = "ads_sales_daily.revenue"
  dts.format_type    = "currency_cny"
  dts.format_scale   = "100"
```

### 3. 类型映射表

| DSL / dbt 类型 | Arrow 类型 |
|---|---|
| number (integer) | int64 |
| number (decimal) | decimal128(38, 9) |
| number (float) | float64 |
| string | utf8 |
| date | date32 |
| datetime | timestamp[ms, UTC] |
| boolean | bool |
| null | null |

### 4. Stream 组织

- 一次查询 = 一个 Arrow IPC stream
- 一个 stream 分若干个 RecordBatch，**每 batch 最多 10 万行**
- 后端强制 `LIMIT`（默认 100 万行），超过 streaming truncate 并在最后 batch 的 metadata 带 `dts.truncated = "true"`

### 5. Metadata header

后端在 HTTP header 里额外返回：

```
X-Semantic-Meta: <base64(JSON)>
```

JSON 内容：
```json
{
  "sql_preview": "SELECT ...",
  "row_count_estimate": 42,
  "elapsed_ms": 187,
  "cache_hit": false,
  "security_applied": ["row_level", "classification_filter"],
  "warnings": [
    { "code": "W001", "message": "Query truncated at 1M rows" }
  ]
}
```

### 6. 错误处理

- Arrow 流不可回退。如果编译期出错，**降级为 JSON 400/422 返回**，不返回 Arrow。
- 运行时错误（DB 超时 / OOM）：发完已有 batch 后，最后 batch 的 metadata 标记 `dts.error = "<code>"`，客户端需检测。

### 7. 前端解码建议

- 用 `apache-arrow` npm 包（或 `@apache-arrow/esnext-esm`）
- 解码后直接喂 ECharts dataset / AntV / DuckDB-Wasm（可选，用于前端二次聚合）
- 单次解码上限 10MB，更大用流式 + 分页渲染

### 8. 本 Sprint 的开关策略

- **默认返回 JSON**，前端不依赖 Arrow
- Arrow 实现作为 Phase 2 能力（F2-T03 的 scope 里会接入）
- 但 **schema + 类型映射表本 Task 必须锁定**，避免未来改接口

## Open Questions

1. Arrow 是否本 Sprint 就实现？（建议：实现但默认关闭，通过 feature flag 打开）
2. VirtualDataset `workspace_id` 是否对齐 AnalyticsCollection？（建议：是，workspace = collection.id）
3. `derived_metrics` 存在 VDS 里还是 Card 里？（建议：都可以——VDS 里的对所有引用它的 Card 可见；Card 里的只对自己可见）
4. Card 的 `engine=mbql_legacy` 是否打警告要求迁移？（建议：Sprint-13 不打，Sprint-14+ 再做迁移引导）

## 影响范围

- 新建 spec markdown + JSON schema 文件
- DDL 样例
- Arrow schema metadata 约定

## 验证

- [ ] JSON schema 通过 `ajv` 校验一个完整 VDS 样例
- [ ] 反例（缺必填字段、非法 state 转换、非法 expression）正确报错
- [ ] DDL 能在 PG 建表成功
- [ ] Arrow 类型映射表覆盖所有 dbt 常见类型
- [ ] open questions 全部 resolution

## 完成标准

- [ ] spec markdown 完成
- [ ] JSON schema 文件通过 lint
- [ ] DDL 草案进 F2 liquibase changelog 候选
- [ ] 评审 sign-off
