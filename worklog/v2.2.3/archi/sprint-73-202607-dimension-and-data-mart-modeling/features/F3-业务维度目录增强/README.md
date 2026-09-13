# F3: 业务维度目录增强

**优先级**: P0  
**状态**: DRAFT（等待 F0/F2）

## 目标

让业务维度成为可复用的分析视角定义：有明确业务范围、语义属性、主键属性、层级、责任人和现行 revision，而不是只有名称的台账。

## 契约定义

复用 `/api/modeling/dimension-definitions`，增加：

- `scopeType: DOMAIN | DATA_MART`
- `dataMartId?: UUID`
- `attributes: DimensionAttribute[]`
- hierarchy level 从属性 code 解析

## UI/UX 规格

- **入口**: `/modeling/dimensions`，继续使用现有维度目录。
- **登记**: 先选“共享维度/集市维度”，再选业务分类；集市维度必须选已确认且包含该分类的 DataMart。
- **详情**: 基本信息、语义属性、层级三个区块；草稿可无属性，确认现行前至少有一个主键属性。
- **四态**: 无维度、加载中、服务失败、列表/详情成功。
- **happy path**: 登记草稿 → 添加属性/主键 → 配置层级 → 确认为现行 → 创建维度表。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 扩展维度范围属性与版本契约 | P0 | DRAFT | F0、F2/T01 |
| T02 | 重构维度目录登记编辑体验 | P0 | DRAFT | T01、F2/T02 |
| T03 | 迁移存量维度并闭合兼容 | P0 | DRAFT | T01、F0/T02 |

## Definition of Ready

- [x] API/JSON/schema/error contract 已钉死
- [x] 页面与三块编辑区已命名
- [ ] F0 通过且 DataMart API 可用

## 完成标准

- [ ] DOMAIN/DATA_MART 两种范围真实可用
- [ ] 属性和层级 revision-pinned
- [ ] 存量 DOMAIN 维度无损读取
