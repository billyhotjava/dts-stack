# P1-08 NL2SQL 语义召回（Schema RAG + 词典）

`status`: `planned`  
`priority`: `P1`  
`inspiration`: `语义层 + Schema RAG + few-shot 示例召回`

## 目标

提升 NL2SQL 的命中率与稳定性，让模型在真实库上“找得到表、认得字段、理解口径”。

## 子任务

1. Schema RAG
- 建立表/字段/注释/别名索引，按问题召回 Top-K 候选上下文。
- 支持按数据源与权限范围过滤召回结果。

2. 业务同义词词典
- 维护“业务词 -> 指标/字段”映射（如“产量=output_qty”）。
- 支持词典版本化与灰度切换。

3. few-shot 样本召回
- 基于历史成功样本召回相似案例，注入 Prompt。
- 控制上下文长度，避免 token 膨胀。

4. 口径注入
- 对关键指标注入口径描述（聚合方式、时间粒度、过滤约束）。
- 与 `semanticModelHints` 对齐。

## 验收标准

- 高频问题正确率提升（目标 +10% 以上）。
- “字段不存在”类错误占比显著下降。
- 召回链路可观测，能追踪每次生成使用的上下文来源。

## 风险与回滚

- 风险：召回上下文过多导致延迟升高。  
- 回滚：限制 Top-K 和 token budget，降级到词典优先模式。

## 实现难度评估

- 难度：`高`
- 预计周期：`2 ~ 3 周`

## 前置依赖

- `P0-06-nl2sql-eval-feedback-loop.md`
- `P0-07-nl2sql-safety-retry-pipeline.md`

