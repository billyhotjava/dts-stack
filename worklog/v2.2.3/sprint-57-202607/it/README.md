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
| IT-8 | 三页面范式巡检记录 | 文档 + 页面 | assets/foundation-pages-audit.md；assets/it-8-f3-elements-import-entry.png | ◐ |
| IT-9 | 既有 source-contract 测试全绿（模板 zip 契约不破坏） | node --test | 命令输出 | ☐ |
| IT-10 | 指标工作台指标 -> 指标连线形成 `METRIC_DERIVES` 派生关系 | 页面 + 请求拦截 | 截图 + PUT payload | ☐ |
| IT-11 | 指标工作台边配置保存/删除与业务对象绑定回归 | 页面 + 请求拦截 | 截图 + PUT payload | ☐ |
| IT-12 | 指标工作台预检识别孤立指标、环依赖、草稿依赖、非法公式 JSON | 单测 + 页面 | 命令输出 + `assets/it-12-metric-workbench-toolbar.png` | ◐ |

证据存放：`../assets/`（截图命名 `it-{N}-{描述}.png`）。

## F6 指标工作台语义编排验证记录（2026-07-04）

- `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`：12/12 通过。
- `node --test --experimental-strip-types src/pages/modeling/metricWorkbench.source-contract.test.ts`：11/11 通过。
- `pnpm exec tsc --noEmit`：通过。
- `git diff --check -- source/dts-platform-webapp/src/api/semanticModelingApi.ts source/dts-platform-webapp/src/pages/modeling/MetricWorkbenchPage.tsx source/dts-platform-webapp/src/pages/modeling/metric-workbench source/dts-platform-webapp/src/pages/modeling/metricWorkbench.source-contract.test.ts`：通过。
- Playwright smoke：`http://localhost:3001/#/modeling/metric-workbench` 可渲染指标工作台；接口失败/空数据时工具栏仍可见；截图见 `assets/it-12-metric-workbench-toolbar.png`。
- 待补：真实指标数据下拦截 `PUT /api/semantic/metrics/{id}`，留存 `METRIC_DERIVES` 新增/删除 payload。

## F3 数据元导入入口验证记录（2026-07-04）

- `node --test --experimental-strip-types src/pages/governance/DataStandardPackageTemplate.source-contract.test.ts`：3/3 通过。
- `node --test --experimental-strip-types src/pages/foundation/StandardPackageImport.source-contract.test.ts`：7/7 通过。
- `pnpm exec tsc --noEmit`：通过。
- Playwright smoke：`http://localhost:3001/#/governance/standards/elements` 可见 `导入标准包` 入口；`http://localhost:3001/#/foundation/standard-package?from=elements` 可见 `返回数据元`；点击返回后回到数据元页。截图见 `assets/it-8-f3-elements-import-entry.png`。

## 部署验证记录（2026-07-04）

重建方式：`builds/dts-build.sh --image dts-platform dts-platform-webapp` + `docker compose -f docker-compose-app.yml up -d`（新容器 13:50）。

| 验证点 | 结果 |
|--------|------|
| liquibase 迁移 | ✅ `std_pkg_import_run` / `std_pkg_import_run_item` 两表存在于 dts_platform 库 |
| 标准包端点注册 | ✅ `GET /api/modeling/standard-packages/builtin` 返回 401（需鉴权，非 404） |
| 新前端产物 | ✅ bundle 含 `StandardPackagePage-*.js` 与 `AssetMapView-*.js`（标准包向导 + 资产矩阵已进镜像） |
| 应用启动 | ✅ dts-platform 健康检查通过 |

**待执行（需登录会话）**：IT-1~7 标准包全流程与内置包安装、F5-T01 运行时验证（质量试跑/评分/分级分类联动）、IT-10~12 指标工作台——需页面操作或提供测试账号后 curl 取证。
