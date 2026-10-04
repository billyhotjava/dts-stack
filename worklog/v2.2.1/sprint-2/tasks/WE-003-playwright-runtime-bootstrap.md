# WE-003

## 标题

回迁 Playwright runtime 与项目骨架。

## 范围

- `tests/web-e2e/package.json`
- `tests/web-e2e/pnpm-lock.yaml`
- `tests/web-e2e/playwright.config.ts`
- `tests/web-e2e/global.setup.ts`
- `tests/web-e2e/tsconfig.json`
- `tests/web-e2e/.gitignore`
- `tests/web-e2e/reports/.gitkeep`

## 目标

- 恢复可执行的 Playwright 项目
- 为三端 dev server、HTML report、artifact 目录提供统一入口

## 交付

- Playwright 配置、依赖锁文件、全局 setup 与报告目录

## 验收

- `pnpm install --frozen-lockfile --dir tests/web-e2e`
- `pnpm --dir tests/web-e2e exec playwright test --list`

## 当前进度

- 已完成：
  - `tests/web-e2e/package.json`
  - `tests/web-e2e/pnpm-lock.yaml`
  - `tests/web-e2e/playwright.config.ts`
  - `tests/web-e2e/global.setup.ts`
  - `tests/web-e2e/tsconfig.json`
  - `tests/web-e2e/.gitignore`
  - `tests/web-e2e/reports/.gitkeep`

## 风险

- 如果浏览器依赖缺失，spec discoverability 与执行会在安装阶段受阻
