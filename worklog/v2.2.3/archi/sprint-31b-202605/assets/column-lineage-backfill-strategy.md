# Column Lineage Backfill Strategy

## Decision

Field-level lineage backfill is not one uniform job. DTS treats each source differently:

| Source type | Backfill policy | Source of truth | Default window |
|---|---|---|---|
| `DBT_MODEL` | Backfill recent manifest-derived column lineage windows | dbt artifacts | 90 days |
| `ADDAX_RUN` | Backfill recent declared ingestion lineage | Airflow/Addax run logs | 30 days |
| `OPENLINEAGE_EVENT` | No historical backfill; observe from receiver enablement | live OpenLineage event stream | 0 days |
| `MANUAL_DECLARATION` | No historical backfill; preserve current declarations | platform manual lineage rows | 0 days |

## Dry-Run Contract

```text
GET /api/internal/v1/lineage/backfill/dry-run?type=DBT_MODEL&since=2026-02-17T00:00:00Z
```

The endpoint is service-internal and returns a non-mutating plan:

- normalized lineage source type;
- default lookback window;
- source of truth;
- planned steps;
- expected output counters;
- rollback guidance;
- warnings for no-backfill sources.

The endpoint must never write lineage rows. Real backfill remains an approval-gated operation.

## Rollback

- Dry-run output can be discarded without platform state changes.
- Real DBT or Addax backfill must export a pre-migration snapshot before writing.
- OpenLineage event replay requires approval from the source system owner.
- Manual declarations are protected by owner review; no automated historical replay is allowed.

## Final IT Evidence

Final IT should capture:

- dry-run response for `DBT_MODEL`;
- dry-run response for `ADDAX_RUN`;
- explicit no-backfill warning for `OPENLINEAGE_EVENT`;
- unsupported type rejection with `mutatesData=false`.
