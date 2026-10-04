# Code Asset Writer Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=CatalogAssetIdentityResolverTest,AssetPermissionInternalResourceTest,DataStandardServiceTest,ModelingAuxResourceTest,ApiCatalogServiceTest,ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test
```

## Result

The focused platform test command passed.

## Covered Behavior

- `DataStandardService.create(...)` writes `DATA_STANDARD` code assets through `CodeAssetGrantWriter`.
- `ModelingAuxResource.createGlossaryTerm(...)` writes `GLOSSARY_TERM` code assets through `CodeAssetGrantWriter`.
- `ApiCatalogService` keeps `API_SERVICE` writer wiring under constructor injection.
- `CatalogAssetIdentityResolver` resolves indexed ModelingSqlModel lookups without full table scan.
- `AssetPermissionInternalResource` resolves dataset policy refs through indexed repository methods.
