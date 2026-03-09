# WE-007

## 标题

迁移 Admin page objects、core spec 与 dev server 接线。

## 范围

- `tests/web-e2e/pages/AdminAiConfigPage.ts`
- `tests/web-e2e/pages/AdminPackManagementPage.ts`
- `tests/web-e2e/specs/core/admin-ai-pack.spec.ts`
- `tests/web-e2e/support/admin-app-mock.ts`

## 目标

- 恢复 Admin 端核心 AI pack 配置回归
- 将 admin 纳入统一 Web E2E 运行模型

## 交付

- Admin page objects
- Core spec
- 对应 mock support

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `pnpm -C source/dts-admin-webapp build`

## 当前进度

- 已完成上述文件迁移

## 风险

- Admin 页面依赖的 mock API 与当前管理端交互契约可能存在偏差
