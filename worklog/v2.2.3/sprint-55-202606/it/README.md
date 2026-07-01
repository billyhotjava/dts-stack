# Sprint-55 集成测试计划

**状态**: DONE  
**目标**: 证明黄金线菜单架构在 seed、契约测试和运行态 DB 中一致。

## 验证项

| 项目 | 命令/方式 | 预期结果 |
|------|-----------|----------|
| JSON 校验 | `jq empty ...` | 菜单种子、角色默认、locale 可解析 |
| XML 校验 | `xmllint --noout ...` | Liquibase XML 可解析 |
| SQL 预演 | `BEGIN; DO ...; ROLLBACK;` | PostgreSQL 执行无错误 |
| source-contract | `node --test ...portalGoldenLineMenu...` | 黄金线菜单契约通过 |
| dts-admin build | `MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh --image dts-admin --no-save` | 镜像构建成功 |
| runtime smoke | SQL 查询 `portal_menu` | 根分区和 parent/path 符合黄金线 |

## 证据记录

### 2026-06-30 静态验证

- `node --test src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/pages/catalog/DataAssetPortalMenu.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts`
  - 结果: PASS, 17/17
  - 覆盖: 黄金线分区顺序、主题域前置、指标建模一级化、数据消费归并、运行态 reparent 迁移不删除菜单绑定、指标运行监控菜单退役。
- `jq empty source/dts-admin/src/main/resources/config/data/portal-menu-seed.json source/dts-admin/src/main/resources/config/data/role-menu-defaults.json source/dts-platform-webapp/src/locales/lang/zh_CN/sys.json source/dts-platform-webapp/src/locales/lang/en_US/sys.json`
  - 结果: PASS
- `xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260630-02_golden_line_portal_menu_reparent.xml source/dts-admin/src/main/resources/config/liquibase/master.xml`
  - 结果: PASS
- PostgreSQL 事务内 DO block 预演
  - 结果: PASS, `BEGIN / DO / ROLLBACK`

### 2026-06-30 运行态验证

- `MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh --image dts-admin --no-save`
  - 结果: PASS
  - 镜像: `dts-admin:1.0.0`, image id `75ff6aee01d6`
- `docker compose -f docker-compose-app.yml up -d --force-recreate --no-deps dts-admin`
  - 结果: PASS
  - 容器: `v223-dts-admin-1`
  - 说明: Compose 报告历史 orphan `v223-dts-metrics-1`，本次未使用 `--remove-orphans`，避免顺手清理非本 sprint 范围。
- `docker inspect v223-dts-admin-1`
  - 结果: PASS, health `healthy`
  - 运行镜像: `sha256:75ff6aee01d6eae31460c46cfa979fc8fd04a97621289133fe007aedcde367f7`
- `curl http://127.0.0.1:8081/management/health`
  - 结果: PASS, `{"status":"UP","groups":["liveness","readiness"]}`
- `databasechangelog`
  - 结果: PASS
  - 已应用: `20260630-02-golden-line-portal-menu-reparent`
  - 已应用: `20260630-03-remove-metric-modeling-runs-menu`
- `portal_menu` 根分区 smoke
  - 结果: PASS
  - 顺序: `workbench -> data-foundation -> resource -> studio -> metric-modeling -> portal -> consumption -> governance -> ops`
  - 标题: `工作台 -> 数据基础 -> 数据集成 -> 数据开发 -> 指标建模 -> 数据资产 -> 数据消费 -> 治理运营 -> 运维与监控`
- 关键子菜单 smoke
  - 结果: PASS
  - `data-foundation`: `subjects`, `standards`, `templates`
  - `metric-modeling`: `metric-workbench`, `semantic-objects`, `semantic-metrics`, `semantic-models`, `semantic-publish`
  - `consumption`: `services`, `bi-apps`, `screens`
  - `governance`: `qualityRules`, `qualityReport`, `classification`
- 残留检查
  - 结果: PASS
  - 旧根 `services/bi-apps/screens`: `0`
  - 旧指标运行监控菜单: `0`
