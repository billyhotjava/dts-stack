# F3：指标业务上下文

**优先级**：P0
**状态**：DRAFT

## 目标

把指标的业务分类、数据域、业务过程、指标类型和可选分组拆成可校验关系，消除 `category/domain` 自由文本多义。

## 契约定义

| 类型 | 候选契约 | 待冻结内容 |
|---|---|---|
| 业务归属 | businessCategoryId | 单值/多值，所有指标是否必填 |
| 建模上下文 | dataDomainId + businessProcessId | 原子/派生/复合的必填与继承规则 |
| 指标语义 | metricType | ATOMIC/DERIVED/COMPOSITE，与 category 分离 |
| 展示分组 | metricGroup/tags | 是否需要及治理方式 |

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 冻结指标业务归属契约 | DRAFT | F1/T01、F1/T02 |

## Definition of Ready

- [ ] 跨业务分类复合指标政策已决定
- [ ] 原子/派生/复合必填矩阵已确认
- [ ] 旧字符串兼容与回填策略已确认
- [ ] HIGH 风险消费者清单已复核
- [ ] 指标来源引用的是不可变模型 revision 或稳定资产身份，未用名称/可变 head 代替
