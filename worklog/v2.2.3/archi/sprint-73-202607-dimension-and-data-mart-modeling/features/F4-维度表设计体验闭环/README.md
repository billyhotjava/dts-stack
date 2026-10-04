# F4: 维度表设计体验闭环

**优先级**: P0  
**状态**: DRAFT（等待 F0/F3）

## 目标

用户从现行业务维度创建维度表草稿后，能在一个详情页完成字段、来源、命名、装载、SCD、分区和保留期设计，并获得准确门禁修复入口。

## 契约定义

| 类型 | 契约 | 关键字段 |
|------|------|----------|
| Create | existing `/api/modeling/model-specs` | `planId/domainId/dataMartId/dimensionDefinitionRef/variantCode/name` |
| Field | ModelSpec V3 | `dimensionAttributeCode/redundant/redundancySourceRef` |
| Policy | Dimension implementation | `physicalName/loadStrategy/retentionDays/partitionFields/scdPolicy` |
| Naming | `/warehouse-plans/{planId}/naming/validate` | `modelType/layer/physicalName` |

## UI/UX 规格

- **创建向导**: 计划 → 业务范围 → 业务维度 → 逻辑名称；不要求来源。
- **详情 Tabs**: 概览、字段与属性、来源与关系、实现策略、验证。
- **四态**: 无属性映射、来源未登记、策略不完整、设计已就绪。
- **happy path**: 创建草稿 → 映射属性/导入字段 → 选择来源 → 配置物理名/装载/SCD/保留 → 验证通过。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 重构创建向导与默认唯一实现 | P0 | DRAFT | F3/T01/T02 |
| T02 | 串起属性字段来源与冗余映射 | P0 | DRAFT | T01 |
| T03 | 拆分命名装载SCD与保留期 | P0 | DRAFT | T01 |

## Definition of Ready

- [x] 创建/字段/策略 DTO 已钉死
- [x] 五步 happy path 和修复 Tab 已命名
- [ ] F0/F3 通过

## 完成标准

- [ ] 草稿不因无来源失败
- [ ] 实现前来源和属性映射门禁准确
- [ ] 四类策略语义与校验分离
