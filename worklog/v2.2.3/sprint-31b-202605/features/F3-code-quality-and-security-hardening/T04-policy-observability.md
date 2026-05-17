# T04: policy observability（warn + metric）

**优先级**: P0
**状态**: READY
**依赖**: F1/T02

## 目标

`/api/internal/v1/asset-permission/policy` 当 dataset 未在事实源命中时，必须输出可观测信号：日志 WARN + Micrometer counter，避免「resolver 找不到 dataset，policy 默认空，下游以为没策略」的 silent gap。

## 背景

`AssetPermissionInternalResource.resolveDataset(...)` 在找不到 dataset 时返回 `Optional.empty()`，导致 policy 拼出空 `predicates`，调用方 200 OK 但实际没有任何策略生效。Sprint-31A 没有 metric / log 暴露这种 miss，运维只能事后被发现"为什么这条数据没被 RLS 过滤"。

## 技术设计

1. `AssetPermissionInternalResource.policy(...)` 在 dataset 未命中时：
   ```java
   log.warn(
       "policy: dataset not resolved; assetType={}, assetId={}, key={}, actor={}",
       ref.type(), ref.id(), ref.key(), currentActor()
   );
   meterRegistry.counter(
       "platform.asset.policy.dataset_miss",
       "asset_type", safe(ref.type()),
       "action", safe(request.action())
   ).increment();
   ```
2. 加 `dataset_miss_total` 指标到 Sprint-31 F7 观测仪表板。
3. 若 caller 显式声明 `request.expectStrictPolicy=true`（dts-metrics 在 `apply_rls=true` 时设置），dataset miss 必须返回 HTTP 422 而非 200 + 空策略。
4. dataset miss 占总调用 > 5% 时触发 alert（dashboard 配置，dashboard 改动属于 Sprint-31 F7 follow-up）。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`（`expectStrictPolicy` 字段）
- 观测面板配置（dashboard repo，本任务不涉及，只更新文档说明）

## 验证

- [ ] `AssetPermissionInternalResourceTest.policy_datasetMiss_logsWarnAndIncrementsCounter`
- [ ] `AssetPermissionInternalResourceTest.policy_strictMode_datasetMiss_returns422`
- [ ] Micrometer `MeterRegistry` test 断言 counter 自增

## 完成标准

- [ ] dataset miss 输出 warn + counter。
- [ ] strict mode 下 miss 返回 422，下游能区分。
- [ ] 观测面板有对应指标占位。
