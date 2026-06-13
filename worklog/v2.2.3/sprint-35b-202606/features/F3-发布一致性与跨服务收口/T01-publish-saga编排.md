# T01: publish saga/outbox 编排（幂等 + 补偿）

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

把 `MetricModelLifecycleService.publish` 从"线性远程调用 + 本地内存写"改造为可重试、幂等、可补偿的发布编排。

## 技术设计

- 引入 publish 编排步骤：releaseGate check → release submit → BI register → lineage register → audit，每步结果落库（基于 F1 持久化）。
- 幂等键：modelId + version；重复 publish 同版本返回既有结果而非重复提交。
- 失败补偿：任一注册步骤失败 → 状态置 PUBLISH_BLOCKED（不置 PUBLISHED），记录失败步骤，支持从断点重试（outbox/状态驱动）。
- 不引入分布式事务中间件；用"持久化状态机 + 幂等重试"实现最终一致（YAGNI）。

## 影响范围
- `MetricModelLifecycleService.publish`、新增发布编排状态字段。

## 验证
- [ ] 重复 publish 同版本幂等。
- [ ] 模拟 lineage register 失败 → 状态 PUBLISH_BLOCKED，重试可恢复。

## 完成标准
- [ ] publish 不再产生"metrics PUBLISHED 但下游未注册"的分叉。
