# F1: 指标元数据扩展

**优先级**: P0
**状态**: READY

## 目标
扩展现有 `gov_indicator_definition` 表，补充计算定义、维度粒度、多表关联、业务属性、LLM 预留等完整的指标元数据字段。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | gov_indicator_definition 扩展字段 Liquibase | P0 | READY | - |
| T02 | GovIndicatorDefinition 实体扩展 | P0 | READY | T01 |
| T03 | IndicatorService 扩展 + API 增强 | P0 | READY | T02 |

## 完成标准
- [ ] 扩展字段迁移成功，现有数据兼容
- [ ] 实体所有新字段可 CRUD
- [ ] API 支持按 domain/category/status 过滤查询
