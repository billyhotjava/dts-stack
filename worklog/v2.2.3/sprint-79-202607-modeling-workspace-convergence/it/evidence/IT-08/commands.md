# IT-08 commands

执行时间：2026-07-31 02:33～02:50 CST

迁移前：

```text
5 个 allowlist menu 均 deleted=false
5 个 menu id 均存在
portal_menu_visibility count=5
```

部署与验证：

```bash
docker compose -f docker-compose-app.yml up -d --no-deps \
  --force-recreate --wait dts-admin

docker exec v223-dts-pg-1 psql -U postgres -d dts_admin -AtF '|' -c \
  "SELECT count(*) FILTER (WHERE deleted = TRUE) AS soft_deleted,
          count(*) FILTER (WHERE last_modified_by = 'sprint79-menu-convergence') AS marked,
          (SELECT count(*) FROM portal_menu_visibility pmv
           WHERE pmv.menu_id = ANY(array_agg(pm.id))) AS visibility_bindings
     FROM portal_menu pm
    WHERE name IN (
      'sys.nav.portal.warehousePlans',
      'sys.nav.portal.studioSemanticObjects',
      'sys.nav.portal.studioSemanticModels',
      'sys.nav.portal.studioMetricWorkbench',
      'sys.nav.portal.dataMetrics'
    );"
```

迁移后：

```text
5 行 deleted=true
last_modified_by=sprint79-menu-convergence
visibility bindings=5
databasechangelog exectype=EXECUTED
query result=5|5|5
```

真实回滚与恢复：

```text
rollback SQL  = UPDATE 5
old platform  = sha256:46b0435f...
old webapp    = sha256:6ad5170b...
old admin     = sha256:941e5e46...
old HTTPS     = 200
restore SQL   = UPDATE 5
final HTTPS   = 200
```
