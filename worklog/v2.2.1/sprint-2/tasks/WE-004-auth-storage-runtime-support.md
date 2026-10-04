# WE-004

## 标题

适配 auth、storage-state 与 mock server 基础支撑。

## 范围

- `tests/web-e2e/fixtures/`
- `tests/web-e2e/support/mock-auth-server.mjs`
- `tests/web-e2e/support/storage-state.ts`
- `tests/web-e2e/flows/auth-flow.ts`

## 目标

- 保持 `v2.5.0` 的 session bootstrap 模型
- 让当前三端 URL、token fallback 与 returnUrl 行为可用

## 交付

- auth fixture
- storage-state 生成逻辑
- mock auth server

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test specs/core/auth-gateway.spec.ts --list`

## 当前进度

- 已完成：
  - `tests/web-e2e/fixtures/auth.fixture.ts`
  - `tests/web-e2e/fixtures/base.fixture.ts`
  - `tests/web-e2e/fixtures/reset.fixture.ts`
  - `tests/web-e2e/flows/auth-flow.ts`
  - `tests/web-e2e/support/mock-auth-server.mjs`
  - `tests/web-e2e/support/storage-state.ts`

## 风险

- 登录入口若与当前三端真实路由偏差过大，会直接影响后续多数 spec
