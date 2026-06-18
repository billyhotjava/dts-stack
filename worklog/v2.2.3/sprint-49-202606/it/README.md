# Sprint-49 IT Evidence

## 验证范围

- `/foundation/connectors` 连接器目录表格布局和按钮闭环。
- `/foundation/connectors` 到 `/foundation/data-sources` 的数据源创建串联。
- 后续 P0 页面按 Sprint-48 矩阵逐项追加。

## 命令记录

- `node --test --experimental-strip-types src/pages/foundation/ConnectorRegistryPage.source-contract.test.ts`
  - 结果：3/3 pass。
- `node --test --experimental-strip-types src/pages/foundation/ConnectorRegistryPage.source-contract.test.ts src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchLocalPreferences.test.ts`
  - 结果：12/12 pass。
- `node --test --experimental-strip-types src/pages/foundation/ConnectorRegistryPage.source-contract.test.ts src/pages/foundation/DataSourcesPage.source-contract.test.ts src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchLocalPreferences.test.ts`
  - 结果：15/15 pass。
- `pnpm exec biome check src/pages/foundation/ConnectorRegistryPage.tsx src/pages/foundation/ConnectorRegistryPage.source-contract.test.ts`
  - 结果：通过。
- `pnpm exec biome check --formatter-enabled=false src/global.css`
  - 结果：通过。`global.css` 存在历史全量 formatter 差异，本次只做页面级新增 CSS，不做全量重排。
- `pnpm build`
  - 结果：通过。保留既有 Browserslist 过期提示和 chunk size warning。
- `node ../../worklog/v2.2.3/sprint-49-202606/it/scripts/connector-registry-smoke.mjs`
  - 结果：通过。
  - 1366x768 指标：`capabilityCellWidth=160`、`actionCellWidth=360`、`capabilityRows=1`、`actionRows=1`、`actionButtonCount=5`。
  - 按钮闭环：点击“配置”打开 `PostgreSQL / 配置要求`，点击“查看模板”打开 `PostgreSQL / 配置模板`，抽屉 footer 存在“创建数据源”交接按钮，Drawer `left=647`、`width=720`。
  - 创建串联：点击 Drawer footer “创建数据源”进入 `/foundation/data-sources`，自动打开“新增数据源”，预选 `PostgreSQL · ADDAX`，Modal `left=439`、`width=488`。

## 浏览器证据

- `it/evidence/connector-registry-1366x768.png`
- `it/evidence/connector-registry-drawer-1366x768.png`
- `it/evidence/data-source-create-from-connector-1366x768.png`
- `it/scripts/connector-registry-smoke.mjs` 使用 hash 路由 `/#/foundation/connectors`，注入测试登录态并拦截 `/api/infra/connectors` 返回稳定夹具数据。

## Chrome95 风险点

- 不使用 container query、`:has()`、`dvh/svh/lvh`。
- 表格必须有稳定 `scroll.x`、固定列宽、nowrap 操作列。
- 长中文按钮和 Tag 不得挤压成不可读竖排。
- 当前 smoke 捕获到一个既有全局导航 warning：`src/components/nav/vertical/nav-list.tsx` 存在 `li` 嵌套 `li` 的 React dev warning。本次连接器目录切片不修改全局导航，已在脚本中记录为已知非本页面 warning。
