# RLS Masking Evidence - 2026-05-17

## Commands

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -pl dts-metrics -Dtest=MetricPackValidationServiceTest,PlatformContractClientTest,MetricArtifactGenerationServiceTest test
```

## Result

Focused metrics artifact-generation tests passed.

## Covered Behavior

- Platform `maskedColumns` are written into generated candidate dbt SQL for dimensions through `dts_mask(...)`.
- The generated `schema.yml` marks masked dimensions with a platform masking note.
- Preview fails before SQL generation when a metric formula references a platform-masked input column.
- A `maskingMacroSql` candidate artifact is emitted when platform policy returns masked columns.
- `security.apply_rls=false` no longer disables platform policy; a non-empty platform policy overrides the manifest declaration and injects RLS.
- `security.apply_rls=true` with an empty platform policy fails preview.
