# Sprint-96 集中验收证据

**执行时间**：2026-08-20
**状态**：PASS_WITH_GAPS
**边界**：本轮完成代码和隔离 Mock 浏览器验收；未部署容器、未执行生产菜单迁移、未发布客户草稿。

## IT-01：发布目录与批量水合

| 命令 | 结果 | 说明 |
|---|---|---|
| `mvn -q -Dtest=ScreenPortalCatalogSourceContractTest test`（dts-analytics） | PASS / exit 0 | 证明 `publishedOnly` 参数、批量 Repository 方法以及 list 内无逐屏 `findFirst...` |
| `mvn -q -DskipTests package`（dts-analytics） | PASS / exit 0 | Java 生产构建通过 |
| `mvn -q -Dtest=ScreenResourceIT#publishedOnlyScreenDirectoryShouldExcludeDraftsAndServeCurrentPublishedVersion test` | BLOCKED / exit 1 | ApplicationContext 在既有 `0052_analysis_publication.xml` 的 PostgreSQL `WITH ... UPDATE ... FROM` 语句处被 H2 42001 阻断；测试方法未执行，非 Sprint-96 断言失败 |

阻断证据：`target/surefire-reports`；核心错误为 `0052-01-analysis-revision-publication-expand` 在 H2 解析失败。该既有 fixture 修复不属于本 Sprint，发布目录行为由 source contract、package 和 Mock E2E 请求/响应闭环覆盖。

## IT-02：菜单原位迁移契约

命令：

```text
mvn -q '-Dtest=PortalMenuSeedDefaultsContractTest#portalMenuSeedPlacesAnalyticsUnderDataAnalysisServicesWithoutChangingRoleDefaults+sprint96DataPortalMenuMigrationUpdatesExistingRowAndPreservesVisibilityBindings' test
```

结果：PASS / exit 0。已证明：seed/default 标题和路由一致；master 包含 changelog；迁移包含 snapshot/seed hash；未删除 `portal_menu` 或 `portal_menu_visibility`。实际 PostgreSQL apply/rollback 未演练，按 G3 记录 GAP。

## IT-03：前端契约与容量

命令：

```text
node --test src/analytics/pages/screens/dataPortalTree.test.ts src/analytics/pages/screens/DataPortalPage.source-contract.test.ts
```

结果：5/5 PASS。覆盖：治理域分组、未知域“未归类”、搜索、中文稳定排序、1,000 可见叶子、路由、published-only、四态、管理入口和嵌入模式。1,000 叶子用例约 15ms，满足本地 <100ms 预算。

## IT-04：legacy build 与浏览器 E2E

| 命令 | 结果 |
|---|---|
| `pnpm build`（脚本内 `LEGACY_BROWSER_BUILD=1`、TypeScript + Vite） | PASS / exit 0 |
| Sprint-96 UI 文件禁用模式检查（`:has`/container query/new viewport units/toSorted/Object.groupBy） | PASS |
| `PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome-stable pnpm exec playwright test -c playwright.sprint96-mock.config.ts` | 3/3 PASS |

E2E 覆盖：

- 1366×768：主题域树、默认选择、`/bi/portal/{screenId}` 深链、切换叶子、iframe published runtime。
- 820×720：空态与“大屏管理”入口。
- API 503：可重试错误态，不渲染 runtime。
- 成功/空态收集 console error、page error、>=400 response，最终均为空。
- 首次 E2E 捕获并修复 HashRouter 查询串读取问题；最终详情请求断言 `mode=published&fallbackDraft=false`。

截图：

- `/tmp/dts-sprint96-playwright-results/sprint96-data-portal.mock--c184c-d-renders-published-runtime/data-portal-desktop.png`
- `/tmp/dts-sprint96-playwright-results/sprint96-data-portal.mock--bac64--state-at-a-narrow-viewport/data-portal-empty-narrow.png`

构建仅有仓库既有告警：Browserslist 数据过期、既有混合 dynamic/static import 和大 chunk 提示；无类型或编译错误。

## IT-05：环境与遗留 GAP

| GAP | 影响 | 后续关闭方式 |
|---|---|---|
| Chrome95 executable 缺失；当前系统 Chrome 150 | legacy 产物已构建，但不能声称客户版本实机 PASS | 客户环境或 Chrome95 镜像跑同一 E2E |
| 运行库当前 0 个 current-published 大屏 | 不可验收真实填充态，且不得擅自发布客户草稿 | 由 owner 正常发布一张验收大屏后走 `/bi/portal/{id}` |
| H2 被既有 0052 PostgreSQL SQL 阻断 | `ScreenResourceIT` 方法未执行 | 独立修复测试数据库兼容或改用 PostgreSQL Testcontainers |
| 菜单 migration rollback 未演练 | G3 不能标纯 PASS | 在隔离 PostgreSQL 副本执行 update/rollback 并比较 menu/visibility/hash |
| 未部署本轮代码 | 当前运行实例不能作为新页面验收证据 | 用户明确要求部署后按 release-plan 顺序重建并验收 |

## IT-06：范围证明

- RED checkpoint：`b1f753c40 test: add Sprint-96 data portal contract reproducers`。
- GREEN 提交前 GitNexus staged detect：19 个 changed symbols、2 个 affected processes、risk=MEDIUM；受影响流程均以 `ScreenPreviewPage` 为入口，无 HIGH/CRITICAL 项。后续 context 复核显示改动仍复用既有主题、缩放、轮播、访问追踪与组件渲染依赖。
- 共享工作区的 AGENTS/CLAUDE、Sprint-94、BI/建模/治理修改均为用户既有变更，不纳入 Sprint-96 stage。
