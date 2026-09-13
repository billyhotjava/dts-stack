# Data Asset Pages Evidence - 2026-05-18

## Scope

- Sprint-31B F6/T02: assets-v2 资产详情工作台。
- Sprint-31B F6/T03: 数据产品成员与产品合同视图。
- Sprint-31B F6/T04: 资产地图治理缺口处置与台账视图。
- 数据资产门户搜索页：assets-v2 主目录搜索与资产地图筛选条件互通。
- 数据资产门户菜单：资产台账入口指向 assets-v2 台账视图，不再把用户带回旧详情页。

## Verified Commands

```bash
source/dts-platform-webapp/node_modules/.bin/tsx --test \
  source/dts-platform-webapp/src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/catalog/DatasetsPage.remediation.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/catalog/DataProductsPage.source-contract.test.ts \
  source/dts-platform-webapp/src/pages/catalog/DataSearchPage.source-contract.test.ts
```

Result: 14 tests passed.

```bash
pnpm exec tsc --noEmit
```

Working directory: `source/dts-platform-webapp`.

Result: exit 0.

```bash
node -e "JSON.parse(require('fs').readFileSync('source/dts-admin/src/main/resources/config/data/portal-menu-seed.json','utf8')); JSON.parse(require('fs').readFileSync('source/dts-admin/src/main/resources/config/data/role-menu-defaults.json','utf8')); console.log('json ok')"
```

Result: `json ok`.

## Acceptance Notes

- `/catalog/datasets/:id` now exposes six enterprise asset workbench tabs: 概览、字段契约、治理责任、质量与 SLA、血缘与影响、权限申请.
- Legacy tab deep links remain compatible: `fields` and `technical` map to 字段契约；`lineage` maps to 血缘与影响；`quality` and `sla` map to 质量与 SLA.
- `/catalog/assets` supports both 卡片 and 台账 views. The 台账 view uses the same assets-v2 result set and exposes detail, governance, and lineage actions.
- `/catalog/data-products` now exposes read-only 数据产品合同 in addition to create/edit flows, so users can inspect member assets, core metrics, classification, SLA, and consumer entry without entering edit mode.
- `/catalog/search` now searches assets-v2 first and keeps the asset-map filter cache key aligned with `catalog.asset.filter.v2`.
- `sys.nav.portal.dataPortalDetail` is now labeled 资产台账 and routes to `/catalog/assets?view=table`; the old `/catalog/asset-detail` route is retained only as a backward-compatible route.
