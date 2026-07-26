# F2：带统计的主题域树契约

**优先级**：P0
**状态**：DRAFT

## 目标

用单一接口同时返回主题域树与域级统计，消除"树与矩阵列名两个数据源"的漂移，为导航提供体量与健康度。

## 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 端点 | `GET /api/catalog/domains/tree?withStats=true` | `withStats` 缺省为 `false`，保持既有调用方零影响 |
| 响应 | 见下方 schema | `withStats=false` 时响应结构与现状**完全一致**（向后兼容） |
| 服务 | `CatalogDomainService.treeWithStats(activeDept)` | 组合域树 + `CatalogAssetPortalService.domainStats` |
| 前端 API | `platformApi.getDomainTree(options?)` | `getDomainTree()` 保持现签名可用 |

### 响应 schema（`withStats=true`）

```jsonc
{
  "tree": [
    { "id": "uuid", "name": "地铁域", "code": "DTMS", "children": [] }
  ],
  "stats": {
    "all":        { "total": 360, "attention": 360 },
    "unassigned": { "total": 360, "attention": 360 },
    "byDomain":   { "<uuid>": { "total": 0, "attention": 0 } },
    "scanned":    360,
    "truncated":  false
  }
}
```

## 实现约束

- `withStats=false` 必须走原路径、不触发统计计算。既有调用方（`SemanticModelingService` 引用了 `/api/catalog/domains/tree`，见 L13 邻域）不得因此变慢。
- `byDomain` 的 key 是域 UUID 字符串。缺少 `id` 的域**不出现**在 `byDomain` 中，前端据此渲染 disabled（ADR-75-07）。
- 统计随 `activeDept` 变化，不可跨用户缓存。若引入缓存必须以 `(principal, activeDept)` 为 key。
- 审计：沿用 `CATALOG_ASSET_OVERVIEW` 的审计模式，`withStats=true` 时记录 scanned/truncated。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 定义 domainStats 服务方法与响应契约 | P0 | DRAFT | F1/T04 |
| T02 | 扩展 domains/tree 端点并保持向后兼容 | P0 | DRAFT | T01 |
| T03 | 前端 API 层接入并移除矩阵列名的 listDomains 依赖 | P0 | DRAFT | T02 |

## Definition of Ready

- [ ] 已运行 `gitnexus_impact` 于 `CatalogDomainResource.tree`，确认全部既有调用方
- [ ] 已确认 `SemanticModelingService` 对该端点的使用方式（capability 声明还是实际调用）
- [ ] 已确认审计事件命名不与既有事件冲突

## Definition of Done

- [ ] `withStats=false` 的响应与改动前逐字节一致（契约测试断言）
- [ ] `withStats=true` 的 schema 与前端类型对齐，`tsc --noEmit` 通过
- [ ] 矩阵列名不再调用 `listDomains(0, 200)`，L05 的漂移消除
- [ ] 端点测试覆盖：无域、单域、多级子域、缺 id 域四种情况
