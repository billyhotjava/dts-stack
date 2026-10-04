# T02: policy endpoint 版本化

**优先级**: P1
**状态**: DONE
**依赖**: Sprint-31A RX/T05

## 目标

把 `POST /api/internal/asset-permission/policy` 改为 `POST /api/internal/v1/asset-permission/policy`，建立 internal contract 的版本约定，使 F2/T02 加 column masking、F2/T04 加 audit hash 时可以走 `/v2/` 不破坏现有客户端。

## 背景

Sprint-31A RX/T05 的 policy endpoint 直接挂在 `/api/internal/asset-permission/policy` 下，无版本前缀。`RlsPolicyResult` 是 record，字段扩展往往触发反序列化兼容问题；加 column masking 时必然破坏老客户端。

## 技术设计

1. 平台同时挂两条路径：
   - `POST /api/internal/v1/asset-permission/policy` —— 新规范，long-term
   - `POST /api/internal/asset-permission/policy` —— deprecated alias，记录 deprecation log
2. `dts-metrics` `PlatformContractClient` 调用全部改为 `/v1/`。
3. 在 `assets/internal-api-version-policy.md` 记录 internal API 的版本约定：
   - 任何新增字段不破坏旧 client → 同 version；
   - 字段语义变化 / 删除 → bump version；
   - deprecated alias 至少保留一个 Sprint 周期。
4. `ServiceDependencyAuthenticationFilter` 白名单同时允许 `/v1/` 与 deprecated 路径。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`
- 新文档：`worklog/v2.2.3/sprint-31b-202605/assets/internal-api-version-policy.md`

## 验证

- [x] `AssetPermissionInternalResourceTest.policyV1ReturnsForbiddenWhenDenied`
- [ ] `AssetPermissionInternalResourceTest.policy_deprecatedPathLogsWarning`
- [x] `PlatformContractClientTest.resolveRlsPolicyCallsVersionedPlatformContractWithServiceAuth`

## 完成标准

- [x] `/v1/` 路径生效。
- [x] 旧路径保留；deprecation log 待补。
- [x] 版本约定文档化。
