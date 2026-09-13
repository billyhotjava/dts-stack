# T04: 替换 MetricModelLifecycleService 三个 ConcurrentHashMap + 乐观锁

**优先级**: P0
**状态**: DONE（gitnexus_impact=LOW；并发 publish→409 已 IT 验证）
**依赖**: T02, T03

## 目标

把 `MetricModelLifecycleService` 的 `modelStates` / `modelVersions` / `rollbackEvents` 三个 `ConcurrentHashMap` 换成 Repository 存储，并为版本写入加乐观锁。

## 技术设计

- 新增 `MetricModelStateRepository` / `MetricModelVersionRepository` / `MetricRollbackEventRepository`。
- 重写 generateArtifacts / validateModel / submitReview / publishDryRun / publish / rollback / versionHistory 的状态读写，走 repository。
- `metric_model_version` 用 `@Version` 乐观锁；并发 publish 触发 `OptimisticLockException` → 映射 409 `metric_version_conflict`（已在错误码表定义）。
- 状态机门禁（requireStatus）语义不变；事务边界用 `@Transactional` 包住"读状态→远程调用→写状态"（注意：远程调用不应在长事务内持有 DB 连接，按需拆分读/写事务）。
- **编辑前对 `MetricModelLifecycleService.publish` 等核心方法跑 `gitnexus_impact`，HIGH/CRITICAL 风险先报告。**

## 影响范围

- `MetricModelLifecycleService`（状态后端重写，业务逻辑不变）。
- 新增 3 个 repository。
- `MetricContractErrorCode`（确认 409 冲突码已就位）。

## 验证
- [ ] 既有 `MetricModelLifecycleResourceTest` 全绿。
- [ ] 并发两次 publish 同一 model，仅一个成功，另一个 409。
- [ ] publish 后重启，versionHistory 仍返回完整版本与回滚链。

## 完成标准
- [ ] `MetricModelLifecycleService` 不再持有内存 Map；版本写入有乐观锁。
