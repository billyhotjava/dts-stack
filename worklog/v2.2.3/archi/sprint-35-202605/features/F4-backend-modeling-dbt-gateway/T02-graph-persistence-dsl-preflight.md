# T02: graph persistence 与 DSL preflight

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

实现 graph draft 持久化和 DSL 预检，使 `dts-metrics` 可以在调用 platform/dbt 前先发现结构性错误。

## 技术设计

- 当前先用服务内 graph draft store 支撑 `POST /api/metrics/graphs` 与 `GET /api/metrics/graphs/{draftId}`；数据库实体化留待后续收口。
- model lifecycle 当前用服务内 version history / rollback event store 支撑 `GET /api/metrics/models/{modelId}/versions` 与 `POST /api/metrics/models/{modelId}/rollback`；数据库实体化留待后续收口。
- preflight 当前检查基础资产、指标、维度、Join 粒度、ODS/STG 层级阻断、DWD grain/primary key。
- 诊断字段统一为 `nodeId`、`edgeId`、`fieldId`、`metricCode`、`severity`、`code`、`message`。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/domain/**`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/**`
- `source/dts-metrics/src/test/**`

## 验证

- [x] ODS/STG 节点 preflight ERROR。
- [x] DWD 直接连接 publish ERROR。
- [x] 复杂指标依赖循环 ERROR。
- [x] 两次发布后默认回滚上一版，并在 version history 中保留 rollback event。

## 完成标准

- [ ] graph preflight 可以阻断明显错误，不依赖 dbt 才发现；当前服务内 draft/version/rollback 已可用，数据库实体、PATCH 更新和并发版本锁仍待补齐。
