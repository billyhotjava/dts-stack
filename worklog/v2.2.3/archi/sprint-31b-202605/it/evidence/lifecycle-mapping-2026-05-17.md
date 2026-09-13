# Lifecycle Mapping Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-platform -Dtest=CodeAssetLifecycleMapperTest test
./mvnw -q -pl dts-platform -Dtest=CodeAssetLifecycleMapperTest,DataStandardServiceTest,ApiCatalogServiceTest,PlatformCapabilityResourceTest test
./mvnw -q -pl dts-platform -Dtest=CodeAssetLifecycleMapperTest,ModelingAuxResourceTest,ApiCatalogServiceTest,DataStandardServiceTest test
```

## Result

Both focused lifecycle test commands passed.

## Covered Behavior

- Indicator statuses `DRAFT / PENDING_APPROVAL / APPROVED` map to `DRAFT_GOVERNANCE`, not `PENDING_GOVERNANCE`.
- Modeling SQL model statuses `PROMOTED / ACTIVE / TESTING / DRAFT` map to lifecycle values without false governance gaps.
- API service and data standard status mapping now use the same lifecycle mapper.
- Glossary code asset writer now uses the same lifecycle mapper instead of a private partial mapping.
- Platform capability advertises `DRAFT_GOVERNANCE` and `TESTING` as lifecycle statuses.
- Liquibase `20260517_03_code_asset_lifecycle_backfill.xml` executes `scripts/backfill-code-asset-lifecycle.sql` to backfill existing `GOV_INDICATOR / MODELING_SQL_MODEL / GLOSSARY_TERM / API_SERVICE` ownership and manage grants.

## Deployment Baseline Query

```sql
select asset_type,
       regexp_replace(grant_reason, '.*lifecycle=([^;]+).*', '\1') as lifecycle,
       count(*) as cnt
  from asset_grant
 where asset_type in ('GOV_INDICATOR', 'MODELING_SQL_MODEL', 'GLOSSARY_TERM', 'API_SERVICE')
   and grant_reason like 'code asset sync;%'
 group by asset_type, regexp_replace(grant_reason, '.*lifecycle=([^;]+).*', '\1')
 order by asset_type, lifecycle;
```

## Known Gap

The broader `ModelingSqlModelServiceTest` class still has unrelated historical fixture/file-operation failures. This evidence uses the focused lifecycle and service tests instead.
