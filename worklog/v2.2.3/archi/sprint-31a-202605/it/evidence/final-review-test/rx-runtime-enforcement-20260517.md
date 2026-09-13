# RX Runtime Enforcement Focused Test Evidence

**日期**: 2026-05-17
**范围**: Sprint-31A RX T03/T04/T05 focused contract/unit tests
**状态**: PASSED

## 覆盖点

- `CodeAssetGrantWriter` 写入代码化资产 ownership 和 owner dept `MANAGE` grant。
- `CatalogAssetIdentityResolver` 解析当前版本支持的代码化资产身份。
- `ServiceDependencyAuthenticationFilter` 允许 `dts-metrics` 只读访问 asset permission policy contract。
- `PlatformCapabilityResource` 暴露 metrics 所需的 platform permission policy contract。
- `MetricArtifactGenerationService` 在 artifact preview 生成候选 dbt SQL 时注入 platform RLS predicate。
- `MetricArtifactGenerationService` 生成 `securityPolicyJson`，携带 masking columns 供 platform/dbt release gate 复核。
- `PlatformContractClient` 覆盖 platform contract 调用与异常包装。
- `CatalogAssetIdentityResolver` 解析 `API_SERVICE` 和 `scopedDataset(...)` key。
- `AssetPermissionInternalResource` 返回 dataset masking columns。

## 执行命令

```bash
cd /opt/prod/s10/v2.2.3/source/dts-platform
./mvnw -q -Dtest=CodeAssetGrantWriterTest,CatalogAssetIdentityResolverTest,ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest,AssetPermissionServiceTest,AssetPermissionAuditServiceTest,CatalogAssetKeyTest test
```

结果：exit 0。

```bash
cd /opt/prod/s10/v2.2.3/source/dts-platform
./mvnw -q -Dtest=CatalogAssetIdentityResolverTest,ApiCatalogServiceTest,AssetPermissionInternalResourceTest test
```

结果：exit 0。

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,PlatformContractClientTest test
```

结果：exit 0。

## 非覆盖范围

- 未执行完整 `npm run backend:unit:test`。
- 未执行前端 build / typecheck。
- 未执行镜像构建或容器重建。
- 未执行 live IT。
- platform/dbt publish gate 运行时二次比对 `securityPolicyJson` 与最新 platform policy、live IT 仍按 RX 后续项闭环。
