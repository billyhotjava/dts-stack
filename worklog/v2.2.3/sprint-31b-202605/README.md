# Sprint-31B: Sprint-31A RX 运行时收口与代码质量加固（202605）

**时间**: 2026-05
**状态**: IN_PROGRESS
**类型**: Implementation / Hardening（dts-platform + dts-metrics + dts-platform-webapp）
**目标**: 收尾 Sprint-31A 的 RX 运行时强制（T03/T04/T05 后半段），修复 Sprint-31A 阶段性提交的性能与安全 review 发现，统一 Sprint-31A 的状态口径与 evidence，并为 Sprint-32 最终统一 IT 提供 cheap compile 前置验证。

**前置依赖**:
- Sprint-31A 已经完成 `CodeAssetGrantWriter` 与 `CatalogAssetIdentityResolver` 主体能力，但仍有性能、审计、masking、状态口径等运行时漏项；
- Sprint-31 主链路 / Sprint-32 服务拆分已 DONE，但全链路从未做过编译验证，跨模块签名漂移风险未被前置；
- 本 Sprint 完成后立即跑 `mvn compile`（不跑测试），把跨模块签名漂移成本前置；完整 IT、build、容器重建仍统一放到 Sprint-32 最终阶段。

## 背景

Sprint-31A 评审后，团队已经主动推进 RX/T03（`CodeAssetGrantWriter`）、RX/T04（`CatalogAssetIdentityResolver` 扩展）、RX/T05（platform `/api/internal/asset-permission/policy` + dts-metrics RLS predicate 注入）。但在评审这批改动时，发现以下五类问题必须通过新 Sprint 收口：

1. **性能问题**：`CatalogAssetIdentityResolver.resolveModelingSqlModel` 与 `AssetPermissionInternalResource.resolveDataset` 都走了 `findAll().stream().filter(...)` 路径，前者会随模型数线性退化，后者是 policy hot path 直接坍塌风险。
2. **silent enforcement**：policy endpoint 未授权时返回 HTTP 200 + `"1 = 0"` predicate；下游无法区分"被拒"与"hit 空策略"。
3. **声明 vs 强制不一致**：manifest `security.apply_rls=true` 仍被视为已生效，但 publish gate 未复用同一策略，column masking 未实现。
4. **代码质量**：`IndicatorService` / `ModelingSqlModelService` 用 `@Autowired(required=false)` setter 注入绕循环依赖，违反 java/patterns.md 的构造器注入强制规则。
5. **状态口径不一致**：Sprint-31A README 与 RX README 各自标 `DONE` 与 `CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS`，下游模块按 DONE 假设对接，造成认知裂缝。

此外，Sprint-31A / Sprint-31 / Sprint-32 三段一次也没编译，IdentityResolver 7 参构造器变更会不会打破调用方，要在 Sprint-32 final IT 前用 cheap compile 拦下，避免 final stage 才暴露跨模块签名问题。

## 目标架构

```text
Sprint-31A RX 主体（DONE）
  -> CodeAssetGrantWriter（GovIndicator/ModelingSqlModel）
  -> CatalogAssetIdentityResolver（GLOSSARY_TERM / DATA_STANDARD / GOV_INDICATOR / MODELING_SQL_MODEL / METRIC_PACK）
  -> /api/internal/v1/asset-permission/policy（row-filter predicates + masking metadata）
  -> dts-metrics MetricArtifactGenerationService preview 注入

Sprint-31B 收口
  -> Repository 索引方法替换 findAll().stream() hot path
  -> Resolver 接入 API_SERVICE / scopedDataset / legacy 兼容代理
  -> Resolver 失败审计与 failure report
  -> 剩余 code asset writer（DataStandard / Glossary / Template / SvcApi）
  -> publish gate 复用 policy contract（preview/publish 同口径）
  -> column masking 注入 + manifest apply_rls 降级为声明
  -> RLS 注入 audit + observability
  -> setter -> constructor injection 与 @Lazy 解耦
  -> policy endpoint 版本化 + 未授权 403
  -> Sprint-31A 状态口径修正 + evidence 补齐
  -> cheap compile-only 前置验证
```

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | RX 残余运行时收口 | P0 | 6 | DONE | Sprint-31A RX/T03-T05 |
| F2 | RLS publish gate 与 column masking 收口 | P0 | 5 | IN_PROGRESS | F1, Sprint-31A RX/T05 |
| F3 | 代码质量与安全 hardening | P0 | 5 | DONE | F1 |
| F4 | Sprint-31A 漏项与口径修正 | P1 | 4 | DONE | F1-F3 |
| F5 | Sprint-31 cheap compile 前置验证 | P0 | 4 | READY | F1-F4 |
| F6 | 前端验收口径收口 | P0 | 5 | DONE | F1-F5 |

**统计**: READY=5, IN_PROGRESS=0, DONE=24, BLOCKED=0

## 非目标

- 不重新设计 RLS / masking 引擎，column masking 复用 platform 已有 `CatalogMaskingRule` / `CatalogMaskingService`，不再开新模型。
- 不剥离 IAM、不替换 OpenMetadata、不引入 SQLMesh / Dagster / Kestra。
- 不承诺 100% code asset writer 接入；本 Sprint 接入剩余高频实体（DataStandard / Glossary / SvcApi），低频实体（Template / Plan）作为 BACKLOG。
- 不引入 license 模块的版本授权判断；continue 走交付边界。
- 不执行 final IT / Docker 镜像构建 / 容器重建；仅在 Sprint-31B 收尾时跑 cheap compile-only。

## 前端验收口径

从 2026-05-18 起，Sprint-31A / Sprint-31 / Sprint-32 相关 Feature 不再以“后端 API 已存在”作为 DONE 标准。凡数据资产、语义指标、BI 消费相关能力，必须能在前端页面看到并完成核心业务动作；如果前端仍是 demo、只有入口、或无法处理错误态，则状态只能是 `CONTRACT_DONE` / `RUNTIME_PARTIAL`，不得标记企业级 DONE。

## 完成标准

- [x] `CatalogAssetIdentityResolver` 与 `AssetPermissionInternalResource` 当前 hot path 不再有 `findAll().stream().filter(...)`；命中 repository 索引方法。
- [x] `CatalogAssetIdentityResolver` 支持 `API_SERVICE`、`scopedDataset(...)` 与 `urn:uuid:` 旧引用当前版本反向解析；resolver 失败写入 `catalog_asset_resolution_failure`，并提供内部 failure report。
- [x] 剩余高频 code asset（DataStandard / Glossary / SvcApi）接入 `CodeAssetGrantWriter`。
- [x] platform `/api/internal/v1/asset-permission/policy` 在未授权时返回 HTTP 403；deprecated legacy path 继续 fail-closed 200 以兼容旧客户端。
- [x] `MetricArtifactGenerationService` publish-dry-run gate 与 preview 复用同一 RLS 策略；column masking 已写入候选 SQL/schema。
- [x] manifest `security.apply_rls` 从「视为已生效」降级为「仅声明」，RLS 实际生效由 platform policy 决定；当前已输出声明 warning、`securityPolicyJson`，true+空策略失败，false+platform 策略 override。
- [x] RLS 注入的 predicates / masked columns 落入 provider/consumer audit 记录，可按 assetId / packId 回溯；当前 dataset miss 已输出 warn + counter，v1 严格策略缺 dataset 时返回 422。
- [x] `IndicatorService` / `ModelingSqlModelService` / `ApiCatalogService` 改回构造器注入；当前 focused tests 已覆盖构造器签名，完整 Spring context 验证留给 F5。
- [x] policy endpoint 版本化（`/api/internal/v1/asset-permission/policy`），capability 与 service-auth 白名单同步更新。
- [x] `lifecycleForModel` / `lifecycleForStatus` 状态映射与既有数据对齐，不批量打 `PENDING_GOVERNANCE`；公共 mapper 已覆盖 Indicator / ModelingSqlModel / ApiService / DataStandard / Glossary，历史 backfill 与仪表板基线已补。
- [x] Sprint-31A README / RX README / sprint-queue 状态口径统一；Sprint-31A IT evidence 5 个空目录已补齐 README 与 owner。
- [x] 权限拒绝原因结构化：`PermissionDecision` / `asset_permission_audit` / internal denied audit JSON + CSV 查询已闭环。
- [ ] `mvn -pl dts-platform compile` + `mvn -pl dts-metrics compile` + `pnpm tsc --noEmit` 全绿，跨模块签名漂移在 cheap stage 修复。
- [x] 数据资产中心、数据产品和 dts-metrics 页面按 F6 完成前端可操作验收。

## 当前实现证据（2026-05-17）

- `./mvnw -q -pl dts-platform -Dtest=CatalogAssetIdentityResolverTest,AssetPermissionInternalResourceTest,DataStandardServiceTest,ModelingAuxResourceTest,ApiCatalogServiceTest,ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test` 通过。
- `./mvnw -q -pl dts-metrics -Dtest=PlatformContractClientTest,MetricArtifactGenerationServiceTest test` 通过。

## 当前实现证据（2026-05-18）

- `node --check source/dts-metrics/src/main/resources/static/metrics/assets/metrics-app.js` 通过。
- `./mvnw -q -pl dts-metrics -Dtest=MetricsFrontendResourceContractTest,MetricPackResourceTest test` 通过。
- `./node_modules/.bin/tsx --test src/routes/sections/dashboard/metricsServiceRoutes.test.ts src/routes/components/router-link.metrics-boundary.source.test.ts src/layouts/components/search-bar.metrics-boundary.source.test.ts` 通过。
- `./mvnw -q -pl dts-platform -Dtest=AssetPermissionServiceTest,AssetPermissionAuditServiceTest,AssetPermissionInternalResourceTest,AssetPermissionAuditQueryResourceTest,ServiceDependencyAuthenticationFilterTest test` 通过。
- `./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,MetricArtifactPublishServiceTest,PlatformContractClientTest,MetricPackResourceTest,MetricsFrontendResourceContractTest test` 通过。
- `./mvnw -q -pl dts-platform -Dtest=AssetPermissionAuditServiceTest,AssetPermissionInternalResourceTest,AssetPermissionAuditQueryResourceTest,ServiceDependencyAuthenticationFilterTest test` 通过。
- `./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,MetricArtifactPublishServiceTest,PlatformContractClientTest test` 通过。

## 相关材料

- Sprint-31A 设计: `worklog/v2.2.3/sprint-31a-202605/README.md`
- Sprint-31A RX 设计: `worklog/v2.2.3/sprint-31a-202605/features/RX-architect-review-hardening/README.md`
- 架构评审追补: `worklog/v2.2.3/sprint-31a-202605/assets/architect-review-integration-response.md`
- Sprint-31 主链路: `worklog/v2.2.3/sprint-31-202605/README.md`
- Sprint-32 服务拆分: `worklog/v2.2.3/sprint-32-202605/README.md`
- 集成测试计划: `worklog/v2.2.3/sprint-31b-202605/it/README.md`
