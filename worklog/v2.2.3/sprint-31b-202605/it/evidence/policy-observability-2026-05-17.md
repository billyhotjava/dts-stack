# Policy Observability Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=CatalogAssetIdentityResolverTest,AssetPermissionInternalResourceTest test
./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest test
```

## Result

Both focused test commands passed.

## Covered Behavior

- Legacy `urn:uuid:` code asset references resolve through `CatalogAssetIdentityResolver`.
- Policy dataset misses emit a WARN log and increment `dts.platform.asset_permission.policy.dataset_miss`.
- v1 policy returns HTTP 422 with `dataset_not_resolved` when a DATASET policy asset cannot be resolved; legacy policy keeps compatibility.
- `security.apply_rls=true` with an empty platform policy fails artifact preview instead of silently generating SQL.
