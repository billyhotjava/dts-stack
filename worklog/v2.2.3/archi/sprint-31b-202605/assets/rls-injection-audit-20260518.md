# RLS 注入审计闭环证据

日期：2026-05-18

## 目标

Sprint-31B F2/T04 要求把 RLS predicate 和 column mask 的实际注入结果写入可查询审计，支持回答：

- platform 为什么给这个调用方返回了某条 row filter？
- metrics 生成 artifact 时实际采用了哪组 predicates / masked columns？
- publish dry-run 的 `appliedPredicateHash` 是否能回溯到具体策略内容？

## 已落地

1. 平台新增 `asset_permission_policy_injection` 表。
2. 平台新增 provider 审计：`AssetPermissionInternalResource.policy(...)` 在成功返回策略前写入 `direction=PROVIDER`。
3. metrics 新增 consumer 审计：`MetricArtifactGenerationService.preview(...)` 在成功生成 artifact 后调用 platform internal API 写入 `direction=CONSUMER`。
4. metrics publish dry-run 和 consumer audit 共用 `MetricPolicyAuditSupport` 计算 `sha256:` predicate hash。
5. 平台新增 internal 查询/回写接口：
   - `GET /api/internal/v1/asset-permission/audit/policy-injection`
   - `POST /api/internal/v1/asset-permission/audit/policy-injection`
6. `dts-metrics` service token 白名单允许上述 GET/POST，但仍不允许变更 asset grants。

## 验证

已执行：

```bash
./mvnw -q -pl dts-platform -Dtest=AssetPermissionAuditServiceTest,AssetPermissionInternalResourceTest,AssetPermissionAuditQueryResourceTest,ServiceDependencyAuthenticationFilterTest test
./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,MetricArtifactPublishServiceTest,PlatformContractClientTest test
```

结果：通过。

## 说明

本轮闭合的是“策略注入可审计”。生产级端到端仍需要在 F2/T05 做 live/dry-run 方言验证，确认生成 SQL 在目标数据库方言下能够正确执行。
