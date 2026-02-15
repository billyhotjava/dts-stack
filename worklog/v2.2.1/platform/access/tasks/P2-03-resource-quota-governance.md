# P2-03 运行资源与配额治理

`status`: `in-progress`
`priority`: `P2`

## 目标

支持任务并发与资源治理，降低高峰期相互挤压和雪崩风险。

## 范围

- 调度/执行层：Airflow + ingestion 服务
- 平台侧：任务配置与运维可视化

## 子任务

1. 增加任务级并发上限与队列优先级。
2. 增加来源/项目级配额（并发、吞吐、执行窗口）。
3. 增加限流与拒绝策略（含告警）。
4. 增加资源利用率可视化（队列长度、等待时长）。

## 验收标准

- 高峰期任务可按策略排队，不出现大面积超时。
- 资源限额触发后可见告警与处置建议。
- 配额策略可按项目独立配置。

## 风险与回滚

- 风险：限流策略过严影响业务时效。
- 回滚：支持策略灰度与按项目回退默认值。

## 实现进展（2026-02-15）

- 后端并发/窗口治理落地：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
  - 任务执行前新增治理校验：任务并发上限、来源并发上限、执行窗口；
  - 触发失败时返回可读错误并写入失败分类/审计元数据。
- 治理配置入参与持久化补齐：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
  - `sync.governance` 支持 `maxConcurrentRuns/sourceConcurrencyLimit/priority/rejectPolicy/windowStart/windowEnd/windowTimezone`；
  - 修复任务更新时对 `syncConfig` 的清空问题，避免非增量任务丢失治理配置。
- 治理概览 API：
  - `GET /api/ingestion/tasks/executions/governance-overview?hours=24`
  - 聚合输出运行中、排队中、队列长度、策略拒绝数、平均耗时和来源负载分布。
- 前端配置与可视化：
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
  - 创建/编辑页面新增“运行治理策略”配置区；
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
  - 入湖任务列表新增“资源与配额治理”概览卡片（含来源负载表）。
  - `source/dts-platform-webapp/src/api/ingestion.ts`
  - 新增治理概览 DTO 与 API 封装。

## 已执行验证

- `cd source/dts-ingestion && mvn -DskipTests compile`
- `pnpm -C source/dts-platform-webapp build`
