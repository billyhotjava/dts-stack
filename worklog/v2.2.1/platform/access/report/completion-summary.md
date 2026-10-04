# v2.2.1 Platform Access 完成摘要

## 范围

- 目录：`worklog/v2.2.1/platform/access/tasks`
- 覆盖任务：`P0-01` ~ `P2-03`

## 本轮关键补齐

1. 调度配置 UI/API 对齐（P0-01）
- 前端已支持 `manual/interval/cron` 创建、编辑、回显。
- 后端已对 `syncSchedule` 做严格校验（含 `CronExpression.parse` 与 `interval` 正整数校验）。

2. 执行历史 FULL_RERUN 闭环（P0-02）
- 执行历史支持 `FAILED_ONLY` 与 `FULL_RERUN` 重跑入口。
- 新增执行触发模式字段 `trigger_mode`（`MANUAL/FAILED_ONLY/FULL_RERUN`）并在历史页展示“触发方式”。

3. 元数据采集调度运维（P0-03）
- `autoSyncEnabled/autoSyncCron` 已支持运行时生效，不再依赖重启。
- API 文案与 `cronRuntimeEditable=true` 已对齐。

4. P0 回归门禁自动化（P0-04）
- 新增矩阵脚本：`scripts/run-p0-matrix.sh`，支持 `mode:arch` 批量采样和 `--strict` 门禁。

5. 模板化接入与资源治理增强（P2）
- 模板新增渲染预检接口：`POST /api/ingestion/templates/{templateId}/render`。
- 资源治理新增优先级队列策略：高优先级先出队，同优先级 FIFO。

## 验证命令

- `cd source/dts-ingestion && mvn -DskipTests compile`
- `cd source/dts-platform && mvn -DskipTests compile`
- `pnpm -C source/dts-platform-webapp build`

## 交付后操作（上线前）

1. 执行 ingestion Liquibase 迁移，落地 `trigger_mode` 新列：
- 文件：`source/dts-ingestion/src/main/resources/config/liquibase/changelog/20260215_02_ingestion_execution_trigger_mode.xml`

2. 重启 ingestion / platform 服务后，做一次回归：
- 创建 cron 任务并编辑 cron，确认运行时生效；
- 执行失败重试与整批重跑，确认执行历史“触发方式”正确；
- 跑 `run-p0-matrix.sh --strict`，确认门禁结果。
