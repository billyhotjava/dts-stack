# P1-02 Schema Drift 策略与工单化

`status`: `done`
`priority`: `P1`

## 目标

让 Schema 漂移从“事件记录”变成“可执行策略 + 可处理工单”。

## 范围

- 后端基础：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/SchemaDriftDetector.java`
- 资源接口：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/CatalogSchemaDriftResource.java`
- 前端：新增接入中心漂移策略与工单视图

## 子任务

1. 任务级策略配置：自动迁移/阻断/待审批。
2. 漂移事件自动生成处理单并关联任务。
3. 增加影响对象分析（表/字段/模型）。
4. 支持处理结果回写（已处理/忽略/驳回）。

## 验收标准

- 漂移事件能按策略自动分流。
- 待审批策略可进入处理闭环。
- 处理结果可追溯并可统计。

## 风险与回滚

- 风险：自动迁移策略可能引入兼容问题。
- 回滚：默认策略使用“待审批”，自动迁移需显式开启。

## 实现进展（2026-02-15）

- 后端事件模型新增字段：
  - `policyMode`（REVIEW/AUTO_APPLY/BLOCK）
  - `ticketStatus`（OPEN/IN_REVIEW/RESOLVED/IGNORED/REJECTED）
  - `ticketAssignee`、`workflowNote`、`handledAt`、`handledBy`
- 采集侧（Inceptor/JDBC/Postgres）写入漂移事件时默认设置：
  - `policyMode=REVIEW`
  - `ticketStatus=OPEN`
- 新增 Schema Drift 工单 API：
  - `GET /api/catalog/schema-drift`（支持策略/工单状态过滤）
  - `POST /api/catalog/schema-drift/{id}/policy`
  - `POST /api/catalog/schema-drift/{id}/ticket`
- 前端元数据采集页新增“Schema 漂移工单”区块，支持：
  - 按策略/工单状态筛选
  - 在线调整策略
  - 更新工单状态、责任人和备注
