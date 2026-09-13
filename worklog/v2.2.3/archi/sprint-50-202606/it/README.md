# Sprint-50 IT Evidence

**状态**: DONE

## 目标

记录 Sprint-50 数据标准与 dbt 模型契约联动的 source-contract、构建、Chrome95 smoke 和人工 review 证据。

## 当前计划

| 阶段 | 证据 | 状态 |
|------|------|------|
| F0 前端页面承载面 | 页面路由、主按钮、状态、Chrome95 截图 | DONE |
| F1-F5 标准/dbt 契约 | 字段标准映射、seeds、schema.yml、门禁检查 | DONE |
| F6 收口 | 构建、review、已知风险 | DONE |

## 已完成证据

| 时间 | 命令 | 结果 |
|------|------|------|
| 2026-06-19 | `pnpm exec tsx --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/modeling/modelingToolbar.helpers.test.ts` | 7/7 pass |
| 2026-06-19 | `pnpm build` | pass；`LEGACY_BROWSER_BUILD=1`，Vite build 完成 |
| 2026-06-19 | `./mvnw -q -Dspotless.apply.skip=true -Dtest=ModelingSqlModelServiceTest#saveStandardBindings_shouldPersistBindingsInSemanticContractAndGenerateDbtSchemaYml test` | pass |
| 2026-06-19 | Playwright mock smoke：`/#/studio/sql-modeling` | pass；桌面/窄屏均检测到 SQL 建模页、字段标准绑定、`STD_ORDER_STATUS`、`ORDER_STATUS`，console error 为空 |

## 已知风险

- `./mvnw -q -Dspotless.apply.skip=true -Dtest=ModelingSqlModelServiceTest test` 全类仍有既有失败：批量导入 ZIP 未找到 `.tsv` 清单、导入写入失败预期未抛出、治理删除文件预期未删除。失败点集中在既有批量导入/文件删除测试路径，与本次新增标准绑定目标用例无关，需要单独排期修复或先恢复该测试类的 mock/fixture。
- SQL 建模页是桌面级工作台页面，窄屏 smoke 能加载并检测到标准绑定内容，但视觉上仍是桌面宽度横向承载；如需移动端可用，需要单独做响应式重构。

## 浏览器证据

| 项 | 状态 |
|----|------|
| 1366x768 SQL 建模页截图 | `screenshots/sql-modeling-mocked-desktop-1366x768.png` |
| 窄屏 SQL 建模页截图 | `screenshots/sql-modeling-mocked-mobile-390x844.png` |
| 浏览器 console/network smoke | `screenshots/playwright-sql-modeling-mocked-smoke.json` |

## 约束

- 先写 source-contract，再实现。
- Chrome95 和 1366x768 是 UI 验收基线。
- 不新增菜单或页面，所有证据必须对应现有路由。
