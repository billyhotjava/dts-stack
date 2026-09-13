# Sprint-57 集成验证

**状态**: DONE（2026-07-04 IT-1~15 已完成 TDD、真实后端 IT 或页面 smoke 补证）

## 验证项

| # | 场景 | 方式 | 证据 | 状态 |
|---|------|------|------|------|
| IT-1 | 模板 zip 下载 → 填写 → preview 校验报告（含故意错误行） | curl + 页面 | `assets/it-1-standard-package-preview-error.json`；`assets/it-1-standard-package-preview-valid.json`；`assets/it-7-standard-package-direct-url.png` | ✅ |
| IT-2 | apply 全流程入库，五类实体计数正确 | curl + SQL 计数 | `assets/it-2-standard-package-apply-counts.json` | ✅ |
| IT-3 | rollback 后计数还原，导入 run 状态回写为 `ROLLED_BACK` | curl + SQL | `assets/it-3-standard-package-rollback-counts.json`；`assets/it-3-standard-package-history-rollback-page.png` | ✅ |
| IT-4 | 旧数据元直导端点兼容 + 出现在 runs 历史 | curl | `assets/it-4-metadata-standards-legacy-import-runs.json` | ✅ |
| IT-5 | 内置国标包一键安装（gbt-2261/4658/2260 + 数据元包），重复安装幂等 | curl + 页面 | `assets/it-5-builtin-install.json`；`assets/it-5-builtin-install-page.png` | ✅ |
| IT-6 | 数据元 code_set 与已安装码表关联正确 | SQL | `assets/it-6-code-set-association.json` | ✅ |
| IT-7 | 菜单/直达 URL 可访问，不落 /workbench 兜底 | 页面 | `assets/it-7-standard-package-direct-url.png`；`assets/it-7-standard-package-direct-url.json` | ✅ |
| IT-8 | 三页面范式巡检记录 | 文档 + 页面 | assets/foundation-pages-audit.md；assets/it-8-f3-elements-page.png；assets/it-8-f3-glossary-page.png；assets/it-8-f3-reference-codes-page.png | ✅ |
| IT-9 | 既有 source-contract 测试全绿（模板 zip 契约不破坏） | node --test | 相关契约 14/14 通过；最终补跑见下方记录 | ✅ |
| IT-10 | 指标工作台指标 -> 指标连线形成 `METRIC_DERIVES` 派生关系 | 页面 + 请求拦截 + 真实 API | `assets/it-10-metric-workbench-derives-edge.png`；`assets/it-10-metric-derives-put-payload.json`；`assets/it-10-metric-derives-real-put-payload.json` | ✅ |
| IT-11 | 指标工作台边配置保存/删除与业务对象绑定回归 | 页面 + 请求拦截 + 真实 API | `assets/it-11-metric-derives-delete-payload.json`；`assets/it-10-metric-derives-real-put-payload.json` 的 `remove` 段 | ✅ |
| IT-12 | 指标工作台预检识别孤立指标、环依赖、草稿依赖、非法公式 JSON | 单测 + 页面 | 命令输出 + `assets/it-12-metric-workbench-toolbar.png` + `assets/it-12-metric-workbench-narrow.png` | ✅ |
| IT-13 | F5 分级分类深链：资产台账可直达数据集安全字段绑定 | source-contract + 页面 + 真实 API/SQL | `assets/it-13-f5-data-security-deeplink.png`；`assets/it-13-14-f5-security-real-linkage.json` | ✅ |
| IT-14 | F5 分类映射批量导入/导出命令可见 | source-contract + 页面 + 真实 API | `assets/it-14-f5-classification-batch.png`；`assets/it-13-14-f5-security-real-linkage.json` | ✅ |
| IT-15 | F5 质量规则执行后可深链到质量报告数据集上下文 | source-contract + 页面 + 真实 API | `assets/it-15-f5-quality-report-deeplink.png`；`assets/it-15-f5-quality-real-run.json` | ✅ |

证据存放：`../assets/`（截图命名 `it-{N}-{描述}.png`）。

## F6 指标工作台语义编排验证记录（2026-07-04）

- `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`：12/12 通过。
- `node --test --experimental-strip-types src/pages/modeling/metricWorkbench.source-contract.test.ts`：11/11 通过。
- `pnpm exec tsc --noEmit`：通过。
- `git diff --check -- source/dts-platform-webapp/src/api/semanticModelingApi.ts source/dts-platform-webapp/src/pages/modeling/MetricWorkbenchPage.tsx source/dts-platform-webapp/src/pages/modeling/metric-workbench source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts`：通过。
- Playwright smoke：`http://localhost:3001/#/modeling/metric-workbench` 可渲染指标工作台；接口失败/空数据时工具栏仍可见；截图见 `assets/it-12-metric-workbench-toolbar.png`。
- Playwright mock smoke：指标节点右侧 source handle 可起线；`订单金额 -> 客单价` 形成 `METRIC_DERIVES`，payload 见 `assets/it-10-metric-derives-put-payload.json`，截图见 `assets/it-10-metric-workbench-derives-edge.png`。
- Playwright mock smoke：删除 `METRIC_DERIVES` 后，payload 移除 `dependsOnMetricIds`，见 `assets/it-11-metric-derives-delete-payload.json`。
- Playwright smoke：窄屏可用性截图见 `assets/it-12-metric-workbench-narrow.png`。
- 真实后端 IT：使用 `sprint57_it` 会话直连 `http://127.0.0.1:18082/api/semantic`，选择真实指标 `94b2cfc4-52fc-46b6-a611-0e0aff42dd53` 作为来源、`f75d94ec-e6ac-45fd-9b48-bb285b9fdbf3` 作为目标；`PUT /api/semantic/metrics/{id}` 增加 `formulaJson.dependsOnMetricIds` 后 HTTP 200，读回确认持久化；随后再次 PUT 恢复原公式 HTTP 200。证据见 `assets/it-10-metric-derives-real-put-payload.json`。

## 标准包真实闭环验证记录（2026-07-04）

- `./mvnw -Dtest=StandardPackageApplyServiceTest -Dspotless.apply.skip=true test`：7/7 通过。
- `git diff --check -- source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/StandardPackageApplyService.java source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/StandardPackageApplyServiceTest.java`：通过。
- `it-1-standard-package-preview-error.json`：故意错误包 preview HTTP 200，`blocking=true`，`totalErrors=1`。
- `it-1-standard-package-preview-valid.json`：有效包 preview HTTP 200，`blocking=false`，`totalErrors=0`，生成 `runId=9afbd809-d556-4ad1-a3e2-33ba5c219b4e`。
- `it-2-standard-package-apply-counts.json`：apply HTTP 200，`status=APPLIED`，新增 6；SQL 计数为术语 1、数据元 1、码表目录 1、码值 2、映射 1、run_items 6。
- `it-3-standard-package-rollback-counts.json`：rollback HTTP 200，`status=ROLLED_BACK`，deleted 6、restored 0、skipped 0；SQL 计数全部回到 0。
- `it-4-metadata-standards-legacy-import-runs.json`：旧端点 `/api/modeling/metadata-standards/import` HTTP 200，新增 1，并写入 `LEGACY_SINGLE|APPLIED` 历史；验证后已 rollback 清理。
- `it-5-builtin-install.json`：性别、学历、行政区划、常用数据元四个内置包安装成功；重复安装性别包新增 0、更新 5，证明幂等。
- `it-6-code-set-association.json`：常用数据元与已安装性别/学历/行政区划码表各存在 1 条 `code_set` 关联。
- 页面直达 smoke：`#/foundation/standard-package` 未落 `/workbench`，截图见 `it-7-standard-package-direct-url.png`；内置包与历史页截图见 `it-5-builtin-install-page.png`、`it-3-standard-package-history-rollback-page.png`。页面 smoke 使用路由级 API fixture，后端真实性以本节 curl/SQL JSON 为准。

## F5 分级分类资产联动验证记录（2026-07-04）

- `node --test src/pages/security/F5DataSecurityLinkage.source-contract.test.ts`：4/4 通过。
- 相关回归：`node --test src/pages/catalog/Sprint45GovernanceAsset.source-contract.test.ts src/pages/governance/qualityReportRepairLink.source-contract.test.ts src/pages/security/F5DataSecurityLinkage.source-contract.test.ts`：9/9 通过。
- `pnpm exec tsc --noEmit`：通过。
- Playwright smoke：`http://localhost:3001/#/security/data-security?tab=datasetSecurity&datasetId=asset-smoke-001` 可渲染分级分类页，激活 `数据集安全字段`，选择框显示 `asset-smoke-001`；截图见 `assets/it-13-f5-data-security-deeplink.png`。
- Playwright smoke：`http://localhost:3001/#/security/data-security?tab=classification` 可见 `批量导入`、`导出映射`、`保存映射`；截图见 `assets/it-14-f5-classification-batch.png`。
- TDD 修复：真实 restore 首次发现 `PUT /api/catalog/datasets/{id}/security-mapping` 传 `null/null` 时因 `Map.of(..., null)` 返回 500；新增 `CatalogSecurityResourceTest.upsertDatasetSecurityMapping_clearsMappingWithNullablePayload` 锁定清空响应和删除行为，修复后 `CatalogSecurityResourceTest,StandardPackageApplyServiceTest` 8/8 通过。
- 真实后端 IT：`GET/POST/PUT /api/catalog/classification-mapping*` 完成分类映射 validate/import/export/restore；`GET/PUT /api/catalog/datasets/{id}/security-mapping` 完成安全字段保存、联动查询和清空恢复；修复后 restore HTTP 200，最终返回 `dataLevelField=null, deptField=null`，`catalog_dataset_security_mapping` 查询行数为 0。证据见 `assets/it-13-14-f5-security-real-linkage.json`。

## F5 质量管控与质量报告闭环验证记录（2026-07-04）

- `node --test src/pages/governance/QualityRulesReportFlow.source-contract.test.ts`：2/2 通过。
- `pnpm exec tsc --noEmit`：通过。
- Playwright smoke：`http://localhost:3001/#/governance/rules?tab=report&datasetId=quality-smoke-001` 可激活 `质量报告`，数据集选择保留 `quality-smoke-001`，`导出报告` 可见；截图见 `assets/it-15-f5-quality-report-deeplink.png`。
- 真实后端 IT：创建 Sprint-57 专用质量规则 `833298ed-d4f0-4a48-a5cb-51a410d8af04`，绑定数据集 `271b3ea0-b1a1-4f14-9cb2-baa09c7eadc8` 后 dry-run HTTP 200，生成 run `599dd1fd-710f-4a2e-be60-1e1251a00438`；当前环境 Hive 未配置，run 状态为 `FAILED` 且错误分类为 `CONFIG`，但 report 查询和 rule history 均 HTTP 200 并能回查该运行记录。该规则保留用于审计追溯，证据见 `assets/it-15-f5-quality-real-run.json`。

## F3 数据元导入入口验证记录（2026-07-04）

- `node --test --experimental-strip-types src/pages/governance/DataStandardPackageTemplate.source-contract.test.ts`：3/3 通过。
- `node --test --experimental-strip-types src/pages/foundation/StandardPackageImport.source-contract.test.ts`：7/7 通过。
- `pnpm exec tsc --noEmit`：通过。
- Playwright smoke：`http://localhost:3001/#/governance/standards/elements` 可见 `导入标准包` 入口；`http://localhost:3001/#/foundation/standard-package?from=elements` 可见 `返回数据元`；点击返回后回到数据元页。截图见 `assets/it-8-f3-elements-import-entry.png`。

## F3 业务术语页验证记录（2026-07-04）

- `node --test --experimental-strip-types src/pages/governance/GlossaryPage.source-contract.test.ts`：4/4 通过。
- 相关契约合并回归：`node --test --experimental-strip-types src/pages/governance/DataStandardPackageTemplate.source-contract.test.ts src/pages/governance/GlossaryPage.source-contract.test.ts src/pages/foundation/StandardPackageImport.source-contract.test.ts`：14/14 通过。
- `pnpm exec tsc --noEmit`：通过。
- `git diff --check -- source/dts-platform-webapp/src/pages/governance/GlossaryPage.tsx source/dts-platform-webapp/src/api/platformApi.ts source/dts-platform-webapp/src/pages/governance/GlossaryPage.source-contract.test.ts`：通过。
- Playwright smoke：`http://localhost:3001/#/governance/standards/glossary` 可见 `导出CSV`；下载文件名 `01-business-terms.csv`，首行为 `term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes`。当前环境无术语数据，详情抽屉带数据烟测与“导出再导入 preview=更新”留待 IT 补证。截图见 `assets/it-9-f3-glossary-export.png`。

## F3 三页面范式巡检验证记录（2026-07-04）

- `node --test --experimental-strip-types src/pages/governance/FoundationPagesInteraction.source-contract.test.ts`：3/3 通过。
- 巡检记录：`assets/foundation-pages-audit.md`。
- Playwright smoke（1366 宽）：数据元、业务术语、公共码表三页可访问并截图，见 `assets/it-8-f3-elements-page.png`、`assets/it-8-f3-glossary-page.png`、`assets/it-8-f3-reference-codes-page.png`。

## 部署验证记录（2026-07-04）

重建方式：`builds/dts-build.sh --image dts-platform dts-platform-webapp` + `docker compose -f docker-compose-app.yml up -d`（标准包/前端新容器 13:50）；F5 空值清理修复后再次执行 `./builds/dts-build.sh --image dts-platform` + `docker compose -f docker-compose-app.yml up -d dts-platform`（平台容器 15:44）。

| 验证点 | 结果 |
|--------|------|
| liquibase 迁移 | ✅ `std_pkg_import_run` / `std_pkg_import_run_item` 两表存在于 dts_platform 库 |
| 标准包端点注册 | ✅ `GET /api/modeling/standard-packages/builtin` 返回 401（需鉴权，非 404） |
| 新前端产物 | ✅ bundle 含 `StandardPackagePage-*.js` 与 `AssetMapView-*.js`（标准包向导 + 资产矩阵已进镜像） |
| 应用启动 | ✅ dts-platform 健康检查通过；`http://127.0.0.1:18082/management/health` 返回 200 |
| F5/F6 真实后端补证 | ✅ `it-10-metric-derives-real-put-payload.json`、`it-13-14-f5-security-real-linkage.json`、`it-15-f5-quality-real-run.json` 已留存 |

**剩余风险**：质量规则 dry-run 已真实生成 run/report/history，但当前环境未配置 Hive/Inceptor 执行源，因此本轮验证的是“规则调度、失败归档、报告回查闭环”，不是业务 SQL 成功评分。

## F4 部署验证记录（2026-07-05，T05~T08 上线）

重建方式：`builds/dts-build.sh --image dts-platform dts-platform-webapp` + `docker compose -f docker-compose-app.yml up -d dts-platform dts-platform-webapp`（两容器新建时间 2026-07-05T00:51Z，基线 07-04T12:46Z）。

首次构建失败：并行分支提交 0d8eb3745 的派生指标编译使用 `StringBuffer`，触发 modernizer `Prefer java.lang.StringBuilder`；已等价替换为 `StringBuilder`（`Matcher.appendReplacement/appendTail` Java 9+ 原生支持，签名与行为不变），修复提交 d0c93cccb 后构建通过。

| 验证点 | 结果 |
|--------|------|
| overview 聚合端点注册 | ✅ 容器内 `GET http://127.0.0.1:8081/api/catalog/assets-v2/overview` 返回 401（需鉴权，非 404） |
| 地图页进镜像 | ✅ bundle 含 `AssetOverviewPage-*.js`，且含 `asset-overview-matrix` 标识 |
| 台账页进镜像 | ✅ `DatasetsPage-*.js` 含 `资产登记台账`；`index-*.js` 含 `assets-v2/overview` 调用 |
| 应用启动 | ✅ compose 报告 dts-platform Healthy；JDBC catalog sync 正常执行（datasets=508） |
| 菜单 | ✅ dts_admin.portal_menu id=9443 已于 07-04 更新指向 `/catalog/assets/ledger`（无需重建 dts-admin） |

登录说明：环境为 PKI（UKey）登录，无法做登录态浏览器实测；采用与既有 IT 取证一致的「端点 401 + bundle 标识 + dev 3001 未登录 smoke（截图已归档 it-17）」组合证据。
