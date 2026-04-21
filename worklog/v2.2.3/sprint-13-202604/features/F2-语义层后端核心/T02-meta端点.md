# T02: `/api/semantic/meta` 元信息端点

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现 F1-T02 定义的 `/api/semantic/meta` 端点，按当前用户密级 + 角色过滤后返回可用的 model / metric / dimension / join 列表。这是前端指标树、维度树、画布节点候选的唯一数据源。

## 技术设计

### 1. Handler 结构

```java
@RestController
@RequestMapping("/api/semantic")
public class SemanticMetaResource {

    @GetMapping("/meta")
    public SemanticMetaResponse getMeta(
        @RequestParam(required = false) String subjectArea,
        @RequestParam(required = false) Boolean exposedToModeler,
        @AuthenticationPrincipal UserClaims user
    ) { ... }

    @GetMapping("/graph")
    public JoinGraphResponse getJoinGraph(@AuthenticationPrincipal UserClaims user) { ... }
}
```

### 2. 过滤规则（按序执行）

1. **exposed_to_modeler 过滤**：只返回 `meta.dts.exposed_to_modeler = true` 的 model
2. **密级过滤**：剔除 `security_level > user.security_level` 的 model
3. **主题域过滤**（若请求带 subjectArea）：只保留匹配的
4. **orphaned 过滤**：`sync_status = orphaned` 的 metric 不返回，但 ingest 异常时降级展示（带警告）
5. **OP_ADMIN 旁路**：`OP_ADMIN` 角色跳过密级过滤但审计日志记录

### 3. Cache

meta 信息变化频率低（manifest ingest 才变），强 Cache：

- Redis key: `semantic:meta:<user_security_level>:<manifest_version>`
- TTL: 10 分钟
- ManifestIngestor ingest 成功后主动 `DEL semantic:meta:*`

### 4. 响应体组装

`SemanticMetaAssembler` 服务：
1. 从 `gov_indicator_definition` 查 metric / dimension
2. 从 `gov_join_edge` 查 join edges
3. 从 `catalog_domain` 查 subject area label
4. 根据 user claims 过滤、脱敏（密级超限的 dimension.label 脱敏为 "***"）
5. 按 F1-T02 契约组装 JSON

### 5. `/api/semantic/graph` 端点

返回 JoinGraph 的可视化结构（供 F5-T03 画布使用）：

```json
{
  "spec_version": "1",
  "nodes": [
    { "id": "ads_sales_daily", "label": "销售日汇总", "subject_area": "sales", "exposed": true }
  ],
  "edges": [
    { "id": "e1", "from": "ads_sales_daily", "to": "dim_customer", "type": "many_to_one", "approval_required": false }
  ]
}
```

前端在画布初始化时一次性拿全图，用户只能沿 edge 拖拽。

### 6. 错误处理

- `manifest` 未 ingest：返回 `503 Service Unavailable`，message 提示"等待 dbt manifest 同步"
- 用户无任何可见 model：返回 200 + 空数组（不是 403）

### 7. 性能目标

- 冷启动 p95 < 200ms（1000 个 metric 规模）
- 缓存命中 p95 < 20ms
- 单次响应 JSON 体积 < 500KB

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `web/rest/semantic/SemanticMetaResource.java` |
| 新建 | `service/semantic/meta/SemanticMetaAssembler.java` |
| 新建 | DTO：`SemanticMetaResponse`、`ModelMeta`、`MetricMeta`、`DimensionMeta`、`JoinMeta`、`JoinGraphResponse` |
| 测试 | `SemanticMetaAssemblerTest`、`SemanticMetaResourceIT` |

## 验证

- [ ] 端点返回结构完全符合 F1-T02 OpenAPI 契约（用 openapi-generator 生成的 TS 客户端直接消费）
- [ ] 密级过滤：用不同密级的 JWT 请求，返回的 model 数量不同
- [ ] subjectArea 过滤有效
- [ ] OP_ADMIN 跳过密级并正确打审计
- [ ] Cache 命中时响应时间下降明显
- [ ] manifest 未 ingest 时返回 503
- [ ] 集成测试用真实数据库 + Redis

## 完成标准

- [ ] 新端点可通过 `curl` + JWT 调用正常返回
- [ ] Postman / Bruno collection 存 `assets/api-collections/semantic-meta.json`
- [ ] 单元 + 集成测试覆盖率 ≥ 75%
- [ ] OpenAPI 样例响应与实际响应 diff = 0
