# Policy Versioning Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=CatalogAssetIdentityResolverTest,AssetPermissionInternalResourceTest,DataStandardServiceTest,ModelingAuxResourceTest,ApiCatalogServiceTest,ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test
./mvnw -q -pl dts-metrics -Dtest=PlatformContractClientTest,MetricArtifactGenerationServiceTest test
```

## Result

Both focused test commands passed.

## Covered Behavior

- `dts-metrics` service-auth can call `/api/internal/v1/asset-permission/policy`.
- `/api/internal/capabilities` advertises the v1 policy endpoint.
- `PlatformContractClient.resolveRlsPolicy(...)` calls the v1 platform contract.
- v1 denied policy responses return HTTP 403; legacy policy path remains compatible.
