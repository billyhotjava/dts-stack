# F3：指标业务上下文

**优先级**：P1
**状态**：DONE（Architecture）

## 目标

把指标的业务分类、数据域、业务过程、指标类型和可选分组拆成可校验关系，消除 `category/domain` 自由文本多义。

## 契约定义

| 类型 | 已批准契约 | 冻结内容 |
|---|---|---|
| 业务归属 | businessCategoryId | 单值/多值，所有指标是否必填 |
| 建模上下文 | dataDomainId + businessProcessId | 原子/派生/复合的必填与继承规则 |
| 指标语义 | metricType | ATOMIC/DERIVED/COMPOSITE，与 category 分离 |
| 展示分组 | metricGroup/tags | 是否需要及治理方式 |

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结指标业务归属契约 | P1 | DONE | F1/T01、F1/T02 |

## Definition of Ready

- [x] F1 的业务分类/域/过程稳定身份与 owner 契约可供消费
- [x] `GovIndicatorDefinition` 当前字段、版本/引用和 18 个直接消费者已列为评审输入
- [x] 单/多业务分类、跨分类复合、必填/继承和旧字段回填均有候选方案与反例
- [x] HIGH 风险在 IT-04 参与者与实施前 impact 门禁中明确登记
- [x] T01 输出位置、失败规则和 IT-04 验收方式已明确；未把待决策略写成 DoR 已完成项

## Feature Definition of Done

- [x] T01 达到 DONE，ADR-86-06/07 达到 `ACCEPTED`
- [x] category、metricType、metricGroup、dataDomainId、businessProcessId 语义互不复用
- [x] ATOMIC/DERIVED/COMPOSITE 的必填、继承、跨分类和来源版本规则可验证
- [x] 旧字符串字段的兼容、回填失败和 Contract 门禁已进入下一实施切片
- [x] IT-04 有真实结论和逐消费者测试矩阵链接
