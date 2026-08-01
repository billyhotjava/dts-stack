# T03：实现逻辑、技术、运行三类 provenance 投影

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01～T02

## 目标

让页面明确区分逻辑声明、dbt 编译结构和数据库实际观测，避免把缺失或漂移字段显示成同一事实。

## Contract-first

- **输入**：ModelSpec revision、Implementation artifact、manifest/catalog、candidate/relation evidence。
- **输出**：每个 model/field/dependency 属性携带 `DECLARED|COMPILED|OBSERVED`、source checksum、observedAt。
- **错误路径**：运行证据未绑定 candidate/version/attempt 时不得返回 OBSERVED；过期或 stale 证据标记 drift，不覆盖 declared。
- **复用点**：CatalogAssetKey、PhysicalRelationObservation、现有 artifact store。

## 验证

- [ ] declared-only、compiled-only、observed-drift 三组 fixture。
- [ ] candidate/version/attempt 不匹配返回明确错误/缺失态。

## Definition of Done

- [ ] UI 可以解释每个值来自哪里、是否最新。
- [ ] 投影是读模型，不拥有业务状态。
