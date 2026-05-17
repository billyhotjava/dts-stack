# Sprint-31B 集成测试计划

## 测试策略

Sprint-31B 与 Sprint-31A / Sprint-31 / Sprint-32 保持「中途不跑完整 IT」的协同，但本 Sprint 收尾时**必须**执行一次 cheap compile-only 验证（F5），把跨模块签名漂移在最低成本暴露并修复。完整 IT、build、镜像构建、容器重建仍统一放到 Sprint-32 最终阶段。

## 验收链路

```text
Sprint-31A RX 主体
  -> Sprint-31B F1 RX 运行时收口（resolver / writer / hot path 索引）
  -> Sprint-31B F2 RLS publish gate + column masking + audit
  -> Sprint-31B F3 代码质量与安全 hardening
  -> Sprint-31B F4 Sprint-31A 漏项口径修正
  -> Sprint-31B F5 cheap compile-only 验证
  -> Sprint-32 final IT + build + 容器重建
```

## Evidence 目录

| 场景 | 证据目录 | 触发 |
|---|---|---|
| cheap compile（dts-platform） | `it/evidence/cheap-compile/dts-platform-{date}.md` | F5/T01 |
| cheap compile（dts-metrics） | `it/evidence/cheap-compile/dts-metrics-{date}.md` | F5/T02 |
| cheap compile（webapp tsc） | `it/evidence/cheap-compile/dts-platform-webapp-{date}.md` | F5/T03 |
| RLS dialect IT | `it/evidence/rls-dialect/postgres-{date}.log` `doris-{date}.log` | F2/T05 |
| resolver failure 审计 | `it/evidence/resolver-failure/{date}.md` | F1/T05 |
| code asset writer 增量 | `it/evidence/code-asset-writer/{date}.md` | F1/T06 |
| policy 端点版本切换 | `it/evidence/policy-versioning/{date}.md` | F3/T02 |

## 收尾命令（按顺序）

```bash
# 1. cheap compile-only
cd /opt/prod/s10/v2.2.3/source
./mvnw -pl dts-platform -am -DskipTests compile
./mvnw -pl dts-metrics -am -DskipTests compile

# 2. webapp typecheck
cd /opt/prod/s10/v2.2.3/source/dts-platform-webapp
pnpm install --frozen-lockfile
pnpm tsc --noEmit

# 3. focused unit tests（仅 RX 闭环改动涉及的范围）
cd /opt/prod/s10/v2.2.3/source
./mvnw -pl dts-platform -Dtest=CatalogAssetIdentityResolverTest,CodeAssetGrantWriterTest,CodeAssetLifecycleMapperTest,AssetPermissionInternalResourceTest,AssetPermissionServiceTest,AssetPermissionAuditServiceTest test
./mvnw -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,MetricArtifactPublishServiceTest,PlatformContractClientTest,MetricSqlGeneratorTest test
```

## 留给 Sprint-32 final 阶段执行

- `worklog/v2.2.3/sprint-31-202605/it/scripts/golden-path-smoke.sh`
- `worklog/v2.2.3/sprint-31-202605/it/scripts/observability-admission-check.sh`
- `worklog/v2.2.3/sprint-32-202605/it/scripts/metrics-mvp-admission-check.sh`
- Docker 镜像构建 (`./builds/dts-build.sh --image ...`)
- 容器 force-recreate

## 当前阶段禁止项

- 不在每个任务后跑完整 `mvn test` / `pnpm build` / `docker build`。
- 不做中间容器重建。
- 不把 cheap compile 结果伪造为完整 IT 通过。
- 不把文档 READY 误标为代码 DONE。
