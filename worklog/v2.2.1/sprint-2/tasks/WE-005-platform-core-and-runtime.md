# WE-005

## 标题

迁移 Platform page objects、core spec 与 dev server 接线。

## 范围

- `tests/web-e2e/pages/LoginPage.ts`
- `tests/web-e2e/pages/PlatformAiAssistantPage.ts`
- `tests/web-e2e/pages/IngestionPage.ts`
- `tests/web-e2e/pages/ModelingPage.ts`
- `tests/web-e2e/pages/GovernancePage.ts`
- `tests/web-e2e/specs/core/platform-ai-assistant.spec.ts`
- `tests/web-e2e/support/platform-ai-agent-mock.ts`

## 目标

- 恢复 Platform 端最小核心回归链路
- 让 Playwright 能通过 platform dev server 承载 UI 自动化

## 交付

- Platform page objects
- Core AI assistant spec
- 对应 mock 支撑

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `pnpm -C source/dts-platform-webapp build`

## 当前进度

- 已完成上述文件迁移

## 风险

- Platform 页面结构与 `v2.5.0` 差异可能导致 selector 与流程断裂
