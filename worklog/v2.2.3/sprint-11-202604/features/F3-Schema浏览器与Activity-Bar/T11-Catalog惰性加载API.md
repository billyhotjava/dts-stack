# T11: Catalog 惰性加载 API

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

基于现有 `SqlCatalogService`，新增按层级惰性返回的 API，解决一次性返回万表导致前端卡顿。

## 技术设计

### API 清单

| 方法 | 路径 | 返回 |
|---|---|---|
| `GET` | `/api/sql/v2/catalog/datasources` | 当前用户可访问的数据源列表 |
| `GET` | `/api/sql/v2/catalog/{dsId}/schemas` | 某数据源的 schema 列表 |
| `GET` | `/api/sql/v2/catalog/{dsId}/schemas/{schema}/tables` | 某 schema 的表 |
| `GET` | `/api/sql/v2/catalog/{dsId}/tables/{tableFqn}/columns` | 某表的列（`tableFqn` = `schema.table`） |
| `GET` | `/api/sql/v2/catalog/{dsId}/search?q={kw}&limit=50` | 跨 schema 模糊搜索表名/列名 |

### 返回体示例

```json
// /tables 响应
[
  { "name": "users", "type": "TABLE", "comment": "用户主表", "rowCountEstimate": 12340 },
  { "name": "v_orders", "type": "VIEW", "comment": null, "rowCountEstimate": null }
]

// /columns 响应
[
  { "name": "id", "dataType": "BIGINT", "nullable": false, "comment": "主键", "ordinalPosition": 1 },
  { "name": "name", "dataType": "VARCHAR(200)", "nullable": true, "comment": "用户名", "ordinalPosition": 2 }
]
```

### 权限

- 数据源列表由现有 `InfrastructureDataSource` 按用户权限过滤
- 表/列查询受 `SecuritySqlRewriter` 同一套行列级策略约束

### 缓存

- 服务端 Caffeine：`(dsId, schema)` key，TTL 5min
- 失效：数据源配置变更时清空
- 前端 React Query：`staleTime = 5min`，`cacheTime = 30min`

### 搜索复用 NL2SQL 语义召回

- `search` 端点可直接委托给现有 `Nl2SqlSemanticRecallService`
- 无语义能力时 fallback 到 LIKE 查询

### 审计

- `GET /tables` / `/columns` 埋点 `SQL_CATALOG_BROWSE`，字段 `datasourceId`, `schema`, `table`

## 影响范围

- `SqlIdeResource` 新增 5 个 GET 端点
- `SqlCatalogService` 新增 per-layer 方法（或新增 `CatalogLazyService`）
- Caffeine 缓存配置

## 验证

- [ ] 5 个端点功能正确
- [ ] 权限正确（无权访问数据源返回 403）
- [ ] 缓存命中 `/schemas` 响应 ≤50ms
- [ ] 万表场景 `/tables` 响应 ≤500ms
- [ ] 审计埋点正确

## 完成标准

- [ ] 5 个端点通过集成测试
- [ ] 缓存策略验证
