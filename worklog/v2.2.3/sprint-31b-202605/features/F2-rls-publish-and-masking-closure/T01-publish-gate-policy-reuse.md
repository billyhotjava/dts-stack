# T01: publish gate 复用 policy contract

**优先级**: P0
**状态**: READY
**依赖**: Sprint-31A RX/T05

## 目标

把 `MetricArtifactGenerationService` 的 RLS 注入从 preview 阶段扩展到 publish 阶段，使 dbt publish gateway 与 preview 用同一 `/api/internal/v1/asset-permission/policy` 调用、同一 `RlsPolicyResult`、同一 SQL 生成路径。

## 背景

Sprint-31A RX/T05 已经在 preview 阶段做了：
1. 调 platform policy contract
2. 把 row-filter predicates 写入候选 dbt SQL 的 `where` 子句

但 publish 阶段（`MetricArtifactPublishService` 或等价路径，提交到 platform dbt publish gateway）当前用 stored artifact，等于 preview 写过一次后就缓存了，actor / policy 后续变化不会重算 —— 形成 "preview 一份 SQL，publish 另一份" 的口径偏差。

## 技术设计

1. publish 入口（`MetricArtifactPublishService` 或在 `dts-metrics` 调用 `dbt publish gateway` 的拦截层）必须重新调用 `platformContractClient.resolveRlsPolicy(...)`。
2. 抽出 `MetricSqlGenerator`，preview 与 publish 都通过它生成最终 SQL，签名为：
   ```java
   String generateDbtModelSql(String modelName,
                              String sourceModel,
                              List<String> dimensions,
                              List<MetricColumn> metrics,
                              PlatformContractClient.RlsPolicyResult policy);
   ```
3. publish 前若 policy 变更（与 stored artifact 的 policy hash 不一致），必须重新写入 artifact 仓库；hash 落入 audit。
4. publish gateway response 携带 `appliedPolicySource` / `appliedPredicateHash` 字段。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`
- 新增 `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricSqlGenerator.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactPublishService.java`（或等价）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtPublishGateway.java`（携带 hash 字段）

## 验证

- [ ] `MetricArtifactGenerationServiceTest.publish_reusesPreviewPolicy` 验证 publish 重新调 policy contract
- [ ] `MetricArtifactPublishServiceTest.policyHashChange_regeneratesArtifact`
- [ ] preview / publish 输出 SQL diff 仅在 policy 变化时存在

## 完成标准

- [ ] preview 与 publish 共享 `MetricSqlGenerator`。
- [ ] publish 始终重新调 policy，不依赖缓存。
- [ ] publish response 含 policy source 与 hash。
