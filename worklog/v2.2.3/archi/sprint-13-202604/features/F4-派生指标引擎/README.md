# F4: 派生指标引擎

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

实现派生指标的**从 DSL 到 SQL**的完整翻译管道：解析 → 语义校验 → measure-of-measures 正确展开 → SQL 片段输出。让分析师能用 `[revenue] / [order_count]`、`growth([revenue], 12)`、`ratio(count_distinct(...), count(...))` 这种**公式语法**表达比例、增长率、条件聚合，而不写 SQL。

## 关键正确性

**派生指标必须和原子指标一样在 fanout 场景算对。** 也就是说 `[付费率] = [付费订单数] / [总订单数]` 如果涉及 1:N join，编译器要把分子分母**各自在正确粒度预聚合**再做比例，而不是简单 `sum/sum`。这比 F3-T03 SymmetricAggregate 还要复杂一层。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ExpressionParser（ANTLR grammar + visitor） | P0 | READY | F1/T03 |
| T02 | 派生指标编译到 SQL + measure-of-measures 展开 | P0 | READY | T01, F3/T03 |
| T03 | 函数白名单执行器与错误码 | P0 | READY | T01 |

## 完成标准

- [ ] ANTLR grammar 能正确 parse F1-T03 spec 里的 5 个样例 + 5 个反例
- [ ] 编译到 SQL 后结果与手工 SQL 一致（对 5 个样例验证）
- [ ] fanout + 派生指标的组合在 9 个场景下正确（对照 `it/evidence/f4-derived-matrix.md`）
- [ ] 循环依赖拒绝
- [ ] 类型不匹配拒绝
- [ ] 函数白名单严格（任何非白名单函数 → 编译错）
- [ ] 错误信息本地化（中英）
- [ ] 单元测试覆盖率 ≥ 85%
