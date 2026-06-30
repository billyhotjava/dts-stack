# Runtime And API Migration Register

| Item | Current Surface | Migration Decision | Owner Task | Status |
|------|-----------------|--------------------|------------|--------|
| Visual metric shell | `source/dts-metrics-webapp` under `/metrics/*` | Retire from default UI; use `MetricWorkbenchPage` | F1/T03, F3/T04 | PARTIAL_DONE: default route retired; capability parity still F3/T04 |
| Graph draft/preflight | `/api/metrics/graphs*` | Inventory consumers first; map reusable validation to platform semantic APIs only if page needs it | F0/T02 | READY |
| Model lifecycle APIs | `/api/metrics/models/{modelId}/*` | Keep source temporarily; platform semantic pages use `/semantic/models/*` as canonical | F0/T02, F3/T03 | READY |
| Platform model validation | `/api/internal/metrics/model-validation` | Keep internal endpoint temporarily; default trusted service names no longer include `dts-metrics`; legacy compose can opt back in | F2/T03 | DONE_KEEP_TEMPORARY |
| Compose service | `dts-metrics` in `docker-compose-app.yml` | Removed from default app compose; retained in `docker-compose.legacy.yml` | F2/T01 | DONE |
| Metrics DB env | `PG_DB_METRICS`, `PG_USER_METRICS`, `PG_PWD_METRICS` | Stop default generation unless legacy profile is enabled | F2/T02 | DONE |
| Image env | `IMAGE_DTS_METRICS` | Removed from default image version files; legacy users set it explicitly | F2/T02 | DONE |
| Build all path | `builds/dts-build.sh` default all includes metrics | Removed from default all; explicit `--image dts-metrics --legacy` remains for rollback | F2/T02 | DONE |
| Maven module | `source/pom.xml` includes `dts-metrics` | Defer removal until Sprint-55 physical deletion decision | F2/T04 | DOCUMENTED |

## API Principle

Do not create replacement APIs just to match old `dts-metrics` shape. Replacement work starts from visible platform pages and only adds backend gaps needed by those pages.
