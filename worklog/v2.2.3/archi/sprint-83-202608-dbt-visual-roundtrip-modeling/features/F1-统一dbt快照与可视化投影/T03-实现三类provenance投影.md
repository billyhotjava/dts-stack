# T03：实现逻辑、技术、运行三类 provenance 投影

**优先级**：P0
**状态**：CODE_COMPLETE
**依赖**：T01～T02

## 目标

让页面明确区分逻辑声明、dbt 编译结构和数据库实际观测，避免把缺失或漂移字段显示成同一事实。

## Contract-first

- **输入**：ModelSpec revision、Implementation artifact、manifest/catalog、latestPublishedRef/servingRef、candidate/relation evidence。
- **输出**：每个 model/field/dependency 属性携带 `DECLARED|COMPILED|OBSERVED`、source checksum、observedAt；模型级返回 publication/serving refs 和 previewCapability。
- **错误路径**：运行证据未绑定 serving 或显式成功 candidate/version/attempt 时不得返回 OBSERVED；过期或 stale 证据标记 drift，不覆盖 declared，也不切换 serving。
- **复用点**：CatalogAssetKey、PhysicalRelationObservation、现有 artifact store。

## 验证

- [ ] declared-only、compiled-only、observed-drift 三组 fixture。
- [ ] candidate/version/attempt 不匹配返回明确错误/缺失态。
- [ ] latestPublished=r2/serving=r1 时逻辑展示 r2、可消费物理观测明确固定 r1，不混成单一“当前版本”。

## Definition of Done

- [ ] UI 可以解释每个值来自哪里、是否最新。
- [ ] 投影是读模型，不拥有业务状态。
