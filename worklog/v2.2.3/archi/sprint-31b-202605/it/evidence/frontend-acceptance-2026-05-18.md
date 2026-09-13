# Frontend Acceptance Evidence - 2026-05-18

## Scope

- Sprint-31B F6/T05: `dts-metrics` page acceptance.
- Boundary rule: `dts-platform-webapp` only keeps menu/route entry points; metrics business pages are served by the independent `dts-metrics` service.

## Verified Commands

```bash
node --check source/dts-metrics/src/main/resources/static/metrics/assets/metrics-app.js
```

Result: exit 0.

```bash
./mvnw -q -pl dts-metrics -Dtest=MetricsFrontendResourceContractTest,MetricPackResourceTest test
```

Result: exit 0.

```bash
./mvnw -q -pl dts-metrics -Dtest=MetricWorkspaceResourceTest,MetricsFrontendResourceContractTest,MetricPackResourceTest test
```

Result: exit 0.

```bash
./node_modules/.bin/tsx --test \
  src/routes/sections/dashboard/metricsServiceRoutes.test.ts \
  src/routes/components/router-link.metrics-boundary.source.test.ts \
  src/layouts/components/search-bar.metrics-boundary.source.test.ts
```

Result: 5 tests passed.

```bash
node -e "JSON.parse(require('fs').readFileSync('source/dts-admin/src/main/resources/config/data/portal-menu-seed.json','utf8')); JSON.parse(require('fs').readFileSync('source/dts-admin/src/main/resources/config/data/role-menu-defaults.json','utf8')); console.log('json ok')"
```

Result: exit 0.

```bash
pnpm exec tsx --test src/pages/catalog/DataProductsPage.source-contract.test.ts
```

Result: 3 tests passed.

```bash
VITE_CACHE_DIR=.vite-cache pnpm exec tsc --noEmit
```

Result: exit 0.

## Acceptance Notes

- `/metrics/dictionary` and `/metrics/semantic/subjects` can call `/api/metrics/capabilities`.
- `/metrics/**` loads `/api/metrics/workspace/snapshot` for workspace data instead of relying only on local JS arrays.
- `/metrics/semantic/objects`, `/metrics/semantic/metrics`, and `/metrics/semantic/models` can call `/api/metrics/packs/preview-artifacts`.
- `/metrics/semantic/publish` can call `/api/metrics/packs/publish-dry-run` as publish dry-run.
- `/metrics/semantic/runs` and `/metrics/operations` can refresh metrics service observability.
- Platform legacy routes and navigation leave the platform React router and redirect to `/metrics/**`.
- `/catalog/data-products` is now reachable from the `数据资产门户` menu and has a matching `role-menu-defaults` entry.
