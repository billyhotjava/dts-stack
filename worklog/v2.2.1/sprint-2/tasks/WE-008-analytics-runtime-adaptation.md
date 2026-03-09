# WE-008

## 标题

适配 Analytics runtime、路径与 page objects。

## 范围

- `tests/web-e2e/playwright.config.ts`
- `tests/web-e2e/pages/AnalyticsAiAssistantPage.ts`
- `tests/web-e2e/pages/AnalyticsQueryPage.ts`
- `tests/web-e2e/pages/DashboardPage.ts`
- `tests/web-e2e/pages/ScreenDesignerPage.ts`
- `tests/web-e2e/pages/DatasourcePage.ts`

## 目标

- 将 analytics dev server 从旧路径切到 `source/dts-analytics-webapp/modern`
- 让 analytics 端 page object 能在当前仓库布局下复用

## 交付

- analytics runtime 路径修正
- analytics 页面对象迁移

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `pnpm -C source/dts-analytics-webapp/modern build`

## 当前进度

- 已完成上述文件迁移与路径修正

## 风险

- analytics modern 的路由、样式和挂载路径若有调整，会影响 base URL 与页面断言
