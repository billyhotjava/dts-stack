# T02: 接入与 OpenLineage 传播挂钩

**优先级**: P0
**状态**: IN_PROGRESS

**编码状态**: DONE（统一验证延后）
**依赖**: T01,F2-T04

## 目标

在接入血缘和 OpenLineage input/output 边成功写入后触发密级传播。

## 技术设计

- 接入 ODS 血缘、自动 view 血缘和 OpenLineage 使用统一事件。
- 先提交血缘事实，再通过事务 outbox 触发传播。
- OpenLineage classification facet 只可形成声明/升密候选，不能降低已有值。
- 重放同一 run id 幂等。

## 影响范围

`OpenLineageReceiverResource`、`IngestionLineageWriter`、`CatalogAutoLineageService`、outbox。

## 验证

- [ ] COMPLETE/FAIL/replay 和缺 classification facet 场景。
- [ ] 已有 CONFIDENTIAL 不被 facet=INTERNAL 覆盖。

## 完成标准

- [ ] 血缘边和传播事件可通过 run id 对账。
- [ ] 外部 OpenLineage 不成为密级真源。
