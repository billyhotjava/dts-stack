# T01: 冻结 DataMart 与维度关系契约

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

以自动化契约守卫冻结 `CatalogDomain ↔ DataMart ↔ DimensionDefinition ↔ DIMENSION ModelSpec ↔ CatalogAsset` 的关系。

## 技术设计 (Contract-first)

- **输入契约**: `assets/contract-design.md`。
- **输出契约**: Java/TypeScript contract test，断言 DTO 字段、合法状态、关系基数和错误码。
- **数据流**: 分类 → 集市多对多 → 维度 scope → ModelSpec revision pin → 发布资产。
- **错误路径**: DATA_MART 维度缺 mart、mart 不含 domain、重复活动维度表均 fail closed。
- **复用点**: `DimensionDefinitionContract`、`ModelSpecContract`、`CatalogDomainResolutionPort`、统一资产键。
- **实现方案**: 先写 RED contract tests；实现 Task 只能让这些测试转绿，不允许改测试绕过 ADR。

## 影响范围

契约测试、后续 DTO；不直接实现持久化。

## 验证

- [ ] DOMAIN/DATA_MART XOR 规则
- [ ] 一维度多实现与 scope 内默认唯一规则
- [ ] DRAFT 不入资产、PUBLISHED 才交接规则

## Definition of Done

- [ ] 契约测试可独立运行
- [ ] 所有后续 Feature 引用相同错误码
- [ ] 无平行资产/模型 owner
