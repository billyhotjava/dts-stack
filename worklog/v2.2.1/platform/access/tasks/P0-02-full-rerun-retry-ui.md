# P0-02 执行历史补齐 FULL_RERUN

`status`: `done`
`priority`: `P0`

## 目标

将执行历史页从“仅失败重试”升级为“失败重试 + 整批重跑”。

## 范围

- 前端：`source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`
- API：`source/dts-platform-webapp/src/api/ingestion.ts`
- 后端能力（已具备）：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`

## 子任务

1. 在执行历史操作区新增“整批重跑（FULL_RERUN）”。
2. 增加二次确认与风险提示。
3. 统一重试结果提示与进度轮询。
4. 执行历史中记录重跑模式（FAILED_ONLY/FULL_RERUN）。

## 验收标准

- 成功执行记录可触发 FULL_RERUN。
- 失败记录仍可触发 FAILED_ONLY。
- 两种模式均能在执行历史中区分。

## 风险与回滚

- 风险：误触发全量重跑造成资源波动。
- 回滚：默认仅对管理员显示 FULL_RERUN，并保留开关。

## 实现进展（2026-02-14）

- 已在执行历史页增加“整批重跑”入口（非 running 状态可见）。
- 已增加 FULL_RERUN 确认弹窗，避免误触发。
- 已抽取统一重试提交流程，FAILED_ONLY / FULL_RERUN 共用状态刷新与进度轮询。
- 前端构建通过：`pnpm -C source/dts-platform-webapp build`。
- 已补齐执行历史“重跑模式可区分”：
  - 数据模型：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/domain/IngestionExecution.java`
  - DTO/Mapper：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/IngestionExecutionDTO.java`、`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/mapper/IngestionExecutionMapper.java`
  - 服务赋值：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
  - 前端展示：`source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`（新增“触发方式”列）
  - Liquibase：`source/dts-ingestion/src/main/resources/config/liquibase/changelog/20260215_02_ingestion_execution_trigger_mode.xml`
- 已执行验证：
  - `cd source/dts-ingestion && mvn -DskipTests compile`
  - `pnpm -C source/dts-platform-webapp build`
