# Sprint-57 集成验证

**状态**: IN_PROGRESS（2026-07-04 容器已重建，自动化验证通过；带会话流程待执行）

## 验证项

| # | 场景 | 方式 | 证据 | 状态 |
|---|------|------|------|------|
| IT-1 | 模板 zip 下载 → 填写 → preview 校验报告（含故意错误行） | curl + 页面 | 报文 + 截图 | ☐ |
| IT-2 | apply 全流程入库，五类实体计数正确 | curl + SQL 计数 | 报文 + SQL 输出 | ☐ |
| IT-3 | rollback 后计数还原；被篡改实体 SKIPPED | curl + SQL | 报文 | ☐ |
| IT-4 | 旧数据元直导端点兼容 + 出现在 runs 历史 | curl | 报文 | ☐ |
| IT-5 | 内置国标包一键安装（gbt-2261/4658/2260 + 数据元包），重复安装幂等 | 页面 | 截图 | ☐ |
| IT-6 | 数据元 code_set 与已安装码表关联正确 | 页面 + SQL | 截图 | ☐ |
| IT-7 | 菜单/直达 URL 可访问，不落 /workbench 兜底 | 页面 | 截图 | ☐ |
| IT-8 | 三页面范式巡检记录 | 文档 + 页面 | assets/foundation-pages-audit.md；assets/it-8-f3-elements-page.png；assets/it-8-f3-glossary-page.png；assets/it-8-f3-reference-codes-page.png | ✅ |
| IT-9 | 既有 source-contract 测试全绿（模板 zip 契约不破坏） | node --test | 相关契约 14/14 通过；完整回归待最终补跑 | ◐ |
| IT-10 | 指标工作台指标 -> 指标连线形成 `METRIC_DERIVES` 派生关系 | 页面 + 请求拦截 | `assets/it-10-metric-workbench-derives-edge.png`；`assets/it-10-metric-derives-put-payload.json` | ◐ |
| IT-11 | 指标工作台边配置保存/删除与业务对象绑定回归 | 页面 + 请求拦截 | `assets/it-11-metric-derives-delete-payload.json` | ◐ |
| IT-12 | 指标工作台预检识别孤立指标、环依赖、草稿依赖、非法公式 JSON | 单测 + 页面 | 命令输出 + `assets/it-12-metric-workbench-toolbar.png` + `assets/it-12-metric-workbench-narrow.png` | ✅ |
| IT-13 | F5 分级分类深链：资产台账可直达数据集安全字段绑定 | source-contract + 页面 | `assets/it-13-f5-data-security-deeplink.png` | ✅ |
| IT-14 | F5 分类映射批量导入/导出命令可见 | source-contract + 页面 | `assets/it-14-f5-classification-batch.png` | ✅ |

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
- 待补：真实后端指标数据下拦截 `PUT /api/semantic/metrics/{id}`。当前本地 `/api/semantic/*` 经 Vite 代理返回 500，本轮使用 mock 语义数据验证前端连线与 payload 形态。

## F5 分级分类资产联动验证记录（2026-07-04）

- `node --test src/pages/security/F5DataSecurityLinkage.source-contract.test.ts`：4/4 通过。
- 相关回归：`node --test src/pages/catalog/Sprint45GovernanceAsset.source-contract.test.ts src/pages/governance/qualityReportRepairLink.source-contract.test.ts src/pages/security/F5DataSecurityLinkage.source-contract.test.ts`：9/9 通过。
- `pnpm exec tsc --noEmit`：通过。
- Playwright smoke：`http://localhost:3001/#/security/data-security?tab=datasetSecurity&datasetId=asset-smoke-001` 可渲染分级分类页，激活 `数据集安全字段`，选择框显示 `asset-smoke-001`；截图见 `assets/it-13-f5-data-security-deeplink.png`。
- Playwright smoke：`http://localhost:3001/#/security/data-security?tab=classification` 可见 `批量导入`、`导出映射`、`保存映射`；截图见 `assets/it-14-f5-classification-batch.png`。
- 待补：真实 catalog 后端联动。当前本地 `/api/catalog/classification-mapping`、`/api/catalog/masking-rules`、`/api/catalog/datasets/{id}/security-mapping`、`/api/catalog/classification-masking/linkage` 经 Vite 代理返回 500，本轮不宣称真实数据保存/联动已完成。

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

重建方式：`builds/dts-build.sh --image dts-platform dts-platform-webapp` + `docker compose -f docker-compose-app.yml up -d`（新容器 13:50）。

| 验证点 | 结果 |
|--------|------|
| liquibase 迁移 | ✅ `std_pkg_import_run` / `std_pkg_import_run_item` 两表存在于 dts_platform 库 |
| 标准包端点注册 | ✅ `GET /api/modeling/standard-packages/builtin` 返回 401（需鉴权，非 404） |
| 新前端产物 | ✅ bundle 含 `StandardPackagePage-*.js` 与 `AssetMapView-*.js`（标准包向导 + 资产矩阵已进镜像） |
| 应用启动 | ✅ dts-platform 健康检查通过 |

**待执行（需登录会话/可用后端数据）**：IT-1~7 标准包全流程与内置包安装、F5-T01 运行时验证（质量试跑/评分/分级分类联动）、IT-10~11 指标工作台真实后端数据 payload 补证。
