# P2-03 运行资源与配额治理

`status`: `done`
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
  - 新增项目级并发治理：`projectKey + projectConcurrencyLimit`；
  - `rejectPolicy=QUEUE` 时支持限时排队等待（30s）后再触发，超时给出明确原因；
  - 排队策略新增优先级生效：`priority` 支持高优先级先出队，同优先级按创建时间 FIFO；
  - 任务进入执行前会先落库 `preparing`，确保排队可见且优先级判定有统一依据；
  - 触发失败时返回可读错误并写入失败分类/审计元数据。
- 治理配置入参与持久化补齐：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
  - `sync.governance` 支持 `maxConcurrentRuns/sourceConcurrencyLimit/projectConcurrencyLimit/projectKey/priority/rejectPolicy/windowStart/windowEnd/windowTimezone`；
  - 修复任务更新时对 `syncConfig` 的清空问题，避免非增量任务丢失治理配置。
- 治理概览 API：
  - `GET /api/ingestion/tasks/executions/governance-overview?hours=24`
  - 聚合输出运行中、排队中、队列长度、策略拒绝数、平均耗时、平均/最长排队等待、来源负载分布、项目负载分布。
- 前端配置与可视化：
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
  - 创建/编辑页面新增“运行治理策略”配置区（含项目标识、项目并发上限）；
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
  - 入湖任务列表新增“资源与配额治理”概览卡片（含来源负载表、项目负载表、治理拒绝告警、平均/最长排队等待）。
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`
  - 执行历史新增“排队等待”列，直接展示治理队列等待时长。
  - 失败分类新增治理类目（治理拒绝/治理队列超时），支持在执行历史快速筛选。
  - 新增“仅治理失败”快捷筛选入口（一键筛选 GOVERNANCE_LIMIT / GOVERNANCE_QUEUE_TIMEOUT）。
  - `source/dts-platform-webapp/src/api/ingestion.ts`
  - 新增治理概览 DTO（含 projectLoads）与 API 封装。

## 已执行验证

- `cd source/dts-ingestion && mvn -DskipTests compile`
- `pnpm -C source/dts-platform-webapp build`
