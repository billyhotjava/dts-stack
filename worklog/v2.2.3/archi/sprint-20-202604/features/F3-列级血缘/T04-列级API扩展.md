# T04: 列级 API 扩展（includeColumns=true）

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

让 `/api/catalog/lineage/impact` 支持 `includeColumns=true` 参数，返回结构带 `columnEdges[]`，前端可在不改 URL 的情况下打开列级视图。

## 技术设计

### API 变更

`CatalogLineageResource.getImpact()`：

```java
@GetMapping("/api/catalog/lineage/impact")
public ResponseEntity<LineageGraphResponse> getImpact(
    @RequestParam UUID datasetId,
    @RequestParam(defaultValue = "BOTH") Direction direction,
    @RequestParam(defaultValue = "3") @Min(1) @Max(10) int depth,
    @RequestParam(defaultValue = "false") boolean includeColumns,
    @RequestParam(required = false) String columnFilter,    // dataset.column 精确过滤
    ...
)
```

### 响应结构扩展

```json
{
  "nodes": [...],
  "edges": [...],
  "columnEdges": [                          // 新增，仅 includeColumns=true 时返回
    {
      "id": "uuid",
      "datasetEdgeId": "uuid",              // 关联到父边
      "upstreamDatasetId": "uuid",
      "downstreamDatasetId": "uuid",
      "upstreamColumn": "gross_amount",
      "downstreamColumn": "amount",
      "transformType": "EXPRESSION",
      "expression": "gross_amount * 0.94",
      "confidence": "HIGH"
    }
  ],
  "impactStats": { "...": "..." }
}
```

### 性能保护

- 默认 `includeColumns=false`，保持向后兼容
- 开启时单次返回列边数 hard cap 5000，超出截断 + warning header
- 加缓存：相同参数 5min Redis 缓存（如项目已有缓存层）

### `columnFilter`

支持精确列过滤，例如 `?columnFilter=ods_orders.gross_amount` 只返回涉及该列的列边及其父 dataset 边。前端"列级追溯"场景用。

## 影响范围

- 修改 `dts-platform/.../web/rest/CatalogLineageResource.java`
- 修改 `dts-platform/.../service/catalog/CatalogLineageService.java`（impact 查询）
- 新增 `dts-platform/.../service/catalog/lineage/dto/ColumnEdgeDto.java`
- 新增 `dts-platform/.../repository/catalog/CatalogDatasetLineageColumnRepository.java`（如 T01 没建）

## 验证

- [ ] 单测：`includeColumns=false` 不返回 columnEdges 字段
- [ ] 单测：`includeColumns=true` 返回结构正确
- [ ] 性能：100 节点 + 1000 列边场景，P95 < 800ms
- [ ] columnFilter 过滤正确性
- [ ] 列边超 5000 时返回 truncated header
- [ ] 旧版前端调用不传 includeColumns 无影响

## 完成标准

- [ ] API 扩展实现并通过单测
- [ ] OpenAPI 文档更新（如有 swagger）
- [ ] 性能达标
- [ ] 向后兼容验证通过
