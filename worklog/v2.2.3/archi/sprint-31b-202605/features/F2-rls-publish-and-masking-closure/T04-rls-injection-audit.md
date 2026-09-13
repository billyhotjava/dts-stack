# T04: RLS 注入 audit

**优先级**: P0
**状态**: DONE
**依赖**: T01, T02

## 目标

把每次 RLS predicate / column mask 注入的结果写入审计日志，使「为什么我看到这条数据 / 为什么这一列被打码」能被回溯。

## 背景

`AssetPermissionAuditService.recordDecision(...)` 当前只记录 `permission decision`（allowed / denied + reason）。`/api/internal/v1/asset-permission/policy` 在 200 OK 时不写额外 audit，下游排障只能 grep 日志，而日志默认不打 predicate。

## 技术设计

1. `AssetPermissionAuditService` 新增方法：
   ```java
   void recordPolicyInjection(PolicyInjectionAuditEvent event);
   ```
   `PolicyInjectionAuditEvent` 字段：`actor, assetType, assetId, action, predicates, maskedColumns, policySource, predicateHash, occurredAt`。
2. `AssetPermissionInternalResource.policy(...)` 在返回前调用上述方法。
3. `MetricArtifactGenerationService` / `MetricArtifactPublishService` 收到 `RlsPolicyResult` 后 echo 一条 audit，标记 `direction=consumer, packId=...`。
4. 新增 `GET /api/internal/v1/asset-permission/audit/policy-injection?assetId=...&packId=...&since=...&limit=...` 查询接口（service principal 可访问）。
5. 新增 `POST /api/internal/v1/asset-permission/audit/policy-injection`，供 `dts-metrics` 回写 consumer 侧审计。
5. audit 表 schema：
   ```sql
   create table asset_permission_policy_injection (
       id uuid primary key,
       actor text,
       asset_type text,
       asset_id text,
       action text,
       predicates text,         -- JSON array of strings
       masked_columns text,     -- JSON array of MaskedColumn
       policy_source text,
       predicate_hash text,
       direction text,          -- PROVIDER | CONSUMER
       pack_id text,
       occurred_at timestamptz
   );
   create index ix_policy_inject_asset on asset_permission_policy_injection (asset_type, asset_id, occurred_at desc);
   ```

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/permission/AssetPermissionAuditService.java`
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/permission/AssetPermissionPolicyInjection.java`
- 新增 repository + changelog
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`

## 验证

- [x] `AssetPermissionAuditServiceTest.recordPolicyInjectionPersistsPredicatesAndStableHash`
- [x] `AssetPermissionInternalResourceTest.policyReturnsMaskingColumnsForDataset`
- [x] `MetricArtifactGenerationServiceTest.includesPlatformSecurityPolicyArtifactForReleaseGate`
- [x] `PlatformContractClientTest.recordPolicyInjectionCallsPlatformWithServiceAuth`
- [x] `AssetPermissionAuditQueryResourceTest.policyInjectionReturnsProviderAndConsumerRows`

## 完成标准

- [x] 每次成功 policy preview 命中都有 provider + consumer 两条 audit。
- [x] 查询接口可按 assetId / packId 检索。
- [x] predicate_hash 与 publish gateway response 一致，便于追溯。

## 交付证据

- 平台端新增 `asset_permission_policy_injection` 表、repository、查询/回写 internal API，并将 `dts-metrics` service token 白名单扩展到 policy-injection GET/POST。
- `AssetPermissionInternalResource.policy(...)` 返回 RLS/Mask 策略前写 provider 审计。
- `MetricArtifactGenerationService.preview(...)` 在成功生成 artifact 后回写 consumer 审计，`MetricArtifactPublishService` 复用同一 `MetricPolicyAuditSupport` 计算 predicate hash。
- 证据文档：`worklog/v2.2.3/sprint-31b-202605/assets/rls-injection-audit-20260518.md`。
