# P1-03 可观测性增强（SLA/失败趋势/MTTR）

`status`: `in-progress`
`priority`: `P1`

## 目标

建立接入中心运行质量指标，支持稳定性运营与问题快速定位。

## 范围

- 前端：`TransformPage`、`TransformExecutionHistoryPage`、`MetadataPage`
- 后端：执行记录、失败分类、重试审计相关接口

## 子任务

1. 增加 SLA 指标（成功率、超时率、平均耗时）。
2. 增加失败趋势与 TopN 失败分类。
3. 增加 MTTR（平均恢复时长）统计。
4. 增加按任务/来源/时间窗口的筛选。

## 验收标准

- 关键指标可按天/周查看趋势。
- 可从指标钻取到具体失败执行与日志。
- 指标与执行数据口径一致。

## 风险与回滚

- 风险：统计查询对主库造成压力。
- 回滚：先做离线汇总表，查询走汇总层。

## 实现进展（2026-02-15）

- 后端新增执行可观测指标 DTO：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/IngestionExecutionObservabilityDTO.java`
- 执行服务新增聚合统计能力（窗口/来源/任务过滤）：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
  - 指标已覆盖：`successRate`、`timeoutRate`、`avgDurationSeconds`、`mttrSeconds`、失败分类 Top5、日趋势。
- 新增可观测 API：
  - `GET /api/ingestion/tasks/executions/observability`
  - 支持参数：`taskId`、`sourceType`、`sourceDataSourceId`、`from`、`to`、`days`、`timeoutMinutes`。
- 前端接入：
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
  - 新增可观测看板区块（任务/来源/时间窗/超时阈值筛选 + SLA 指标 + 失败 Top5 + 日趋势）。
- 执行历史页增强过滤：
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`
  - 新增状态与失败分类筛选，直接透视失败执行明细。
