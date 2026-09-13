# T03: SensitiveScanResult 落库 + 扫描任务

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标

将 T02 扫描产物落库为可追溯结果，并提供扫描任务编排：手动触发 + 定时执行（可挂 airflow-om），命中字段/规则/密级/置信度全程留痕。

## TDD 测试先行（RED）

- 新增 `SensitiveScanTaskResourceTest`（放 `dts-platform/src/test/java/.../web/rest/security/`）：
  - 手动触发 `POST` 扫描任务 → 返回 taskId，任务完成后可查 `SensitiveScanResult` 列表，含 fieldName/ruleId/classification/confidence/scanRunId。
  - 任务状态机：PENDING→RUNNING→SUCCEEDED/FAILED，断言失败时 result 留痕错误码不静默吞错。
  - 鉴权：非 INSTITUTE_PRIVILEGED 触发返回 403。
- 新增 `SensitiveScanScheduleTest`：定时任务到点调用 `SensitiveScanService` 一次（mock 验证调用），不重复并发执行同一数据集。

## 技术设计（GREEN）

- 新增实体 `SensitiveScanResult`（`domain/security/SensitiveScanResult.java`）：`scanRunId`、`datasetId`、`fieldName`、`ruleId`、`classification`、`confidence`、`suggestedMaskingFunction`、`alreadyMasked`(bool)、审计字段。
- 新增 `SensitiveScanRun`（任务运行记录：触发方式 MANUAL/SCHEDULED、状态、started/finished、命中计数、错误码），参照 `domain/security/SecurityBackupRun.java` 的 run 留痕风格。
- 新增 `repository/security/SensitiveScanResultRepository.java`、`SensitiveScanRunRepository.java`。
- 新增 `web/rest/security/SensitiveScanTaskResource.java`：手动触发、查询任务与结果。
- 定时调度参照 `service/infra/CatalogAutoSyncJob.java` / `service/explore/ResultSetCleanupJob.java` 的 `@Scheduled` 模式；离线/批量场景可挂 `dts-airflow-om` 触发同一服务入口。
- 任务执行调用 T02 `SensitiveScanService`，结果与 run 一并落库；`Optional` 用 `orElseThrow()`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/security/SensitiveScanResult.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/security/SensitiveScanRun.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/security/`（两个 repository，新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/security/SensitiveScanTaskResource.java`（新增）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/SensitiveScanScheduleJob.java`（新增，参照 `service/infra/CatalogAutoSyncJob.java`）
- 关联触发：`source/dts-airflow-om`（定时编排入口）

## 验证

- [ ] 手动触发可生成 run 与 result，字段/规则/密级/置信度留痕完整。
- [ ] 任务状态机正确，失败留痕错误码不静默。
- [ ] 定时任务到点触发一次且不并发重复扫描同一数据集。

## 完成标准

- [ ] 扫描结果与任务可追溯，供 T04 监控视图与建议转规则消费。
