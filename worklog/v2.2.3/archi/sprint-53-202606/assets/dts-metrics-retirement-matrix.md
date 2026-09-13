# dts-metrics Retirement Matrix

| Surface | Current Evidence | Decision | Target | Sprint Task | Verification |
|---------|------------------|----------|--------|-------------|--------------|
| Portal menu `指标与语义` under BI apps | Removed locally from `portal-menu-seed.json`; new `指标建模` appears under studio | Retire old BI menu, keep platform modeling menu | `/modeling/metric-workbench`, `/modeling/semantic/*` | F1/T01 | DONE |
| Role defaults for old metrics entries | Replaced with `studioMetric*` / `studioSemantic*` defaults | Replace old role default routes/codes with new studio semantic routes | platform modeling routes | F1/T02 | DONE |
| `/bi-apps/metrics/*` static routes | Redirect component registered; no iframe | Preserve compatibility as platform redirects | mapped `/modeling/*` routes | F1/T03 | DONE |
| `/modeling/semantic-center/*` | Redirect component registered; no iframe | Preserve compatibility as redirects to platform semantic pages | `/modeling/semantic/*` | F1/T03 | DONE |
| `/bi/semantic-modeling` | Redirects to platform semantic model page | Redirect to platform semantic model page | `/modeling/semantic/models` | F1/T03 | DONE |
| `docker-compose-app.yml` service | Removed from default app compose; legacy compose still keeps explicit service and routers | Retired from default compose, retained in legacy rollback path | no default service | F2/T01 | DONE |
| `init.sh` env generation | Default template no longer emits metrics DB/token/image values; legacy flag can emit them | Stop emitting default metrics env unless legacy flag enabled | platform-only defaults | F2/T02 | DONE |
| `builds/dts-build.sh` | Default all path no longer builds metrics webapp/module/image; explicit legacy image build remains | Remove from default all; keep explicit legacy image build if needed | platform build only | F2/T02 | DONE |
| `source/pom.xml` module | Still includes `dts-metrics` module | Defer physical removal; only avoid default release build first | later Sprint-55 | F2/T04 | DOCUMENTED |
| `/api/metrics/*` API | Metrics service has real APIs but incomplete persistence history | Do not delete yet; inventory consumers and replace with platform APIs | platform semantic APIs | F0/T02, F3 | API migration register |

## Decision

Sprint-53 retires `dts-metrics` from default customer/product paths. It does not delete the service source tree. Physical deletion is a later decision after the platform metric workbench and golden-chain path prove replacement coverage.
