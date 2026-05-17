# Lifecycle Mapping Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=CodeAssetLifecycleMapperTest test
./mvnw -q -pl dts-platform -Dtest=CodeAssetLifecycleMapperTest,DataStandardServiceTest,ApiCatalogServiceTest,PlatformCapabilityResourceTest test
```

## Result

Both focused lifecycle test commands passed.

## Covered Behavior

- Indicator statuses `DRAFT / PENDING_APPROVAL / APPROVED` map to `DRAFT_GOVERNANCE`, not `PENDING_GOVERNANCE`.
- Modeling SQL model statuses `PROMOTED / ACTIVE / TESTING / DRAFT` map to lifecycle values without false governance gaps.
- API service and data standard status mapping now use the same lifecycle mapper.
- Platform capability advertises `DRAFT_GOVERNANCE` and `TESTING` as lifecycle statuses.

## Known Gap

The broader `ModelingSqlModelServiceTest` class still has unrelated historical fixture/file-operation failures. This evidence uses the focused lifecycle and service tests instead.
