# T05: Resolver 失败审计与 failure report

**优先级**: P1
**状态**: READY
**依赖**: T03, T04

## 目标

`CatalogAssetIdentityResolver.resolveIdentity(String)` 返回 `Optional.empty()` 时必须产生可审计记录，并提供一个聚合 failure report 接口，供数据资产门户与运维定位「为什么这条 ref 解析不到资产」。

## 背景

T01-T04 把 resolver 能力扩展到 6 种 asset_type + scoped dataset + legacy ref，但解析失败仍然是 silent —— 日志只有一条 debug，调用方拿到 `Optional.empty()` 继续 fallback 到默认 dataset 或直接报「permission denied」，造成排障极难。

Sprint-31A RX/T04 验收建议中明确："增加 resolver 失败审计或 failure report 证据"。

## 技术设计

1. 增加 `CatalogAssetIdentityResolutionAuditService`：
   - 写入 `catalog_asset_resolution_failure` 表，字段：`id, ref, requested_at, caller, type_hint_guess, reason`
   - `reason` 枚举：`UNKNOWN_TYPE_HINT / TYPE_REPOSITORY_MISS / LEGACY_FORMAT_NOT_RECOGNIZED / AMBIGUOUS_MATCH`
2. `resolveIdentity` 内部，所有 `return Optional.empty()` 路径前调用 audit service。caller 由 `MDC` 或参数注入。
3. 新增 `GET /api/internal/catalog/asset-resolution-failures?since=...&limit=...` 内部 endpoint，仅服务身份可访问（`@PreAuthorize("hasAuthority('SERVICE_DEPENDENCY')")`）。
4. 新建 `assets/resolver-failure-report.md`，文档化排查流程：典型 ref 失败模式 → 应该走哪个 writer/migration。

## 影响范围

- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetIdentityResolutionAuditService.java`
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogAssetResolutionFailure.java`
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/catalog/CatalogAssetResolutionFailureRepository.java`
- 新 changelog
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/CatalogAssetResolutionFailureResource.java`

## 验证

- [ ] `CatalogAssetIdentityResolverTest.failureAudit_recordsUnknownTypeHint`
- [ ] `CatalogAssetResolutionFailureResourceTest` 覆盖服务身份访问与未授权 403
- [ ] failure-report.md 写明常见模式与 owner

## 完成标准

- [ ] 任何 `resolveIdentity` 失败都有审计记录，调用方可观测。
- [ ] 内部 API 仅 service principal 可访问。
- [ ] failure report 文档化常见模式。
