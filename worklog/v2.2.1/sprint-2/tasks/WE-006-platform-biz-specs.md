# WE-006

## 标题

迁移 Platform 业务流 spec 与 mock 支撑。

## 范围

- `tests/web-e2e/specs/biz/erp-ingest-visible.spec.ts`
- `tests/web-e2e/specs/biz/ai-modeling-flow.spec.ts`
- `tests/web-e2e/specs/biz/auth-rbac-hitl.spec.ts`
- `tests/web-e2e/specs/biz/governance-remediation.spec.ts`
- `tests/web-e2e/support/platform-erp-ingestion-mock.ts`
- `tests/web-e2e/support/platform-ai-modeling-mock.ts`
- `tests/web-e2e/support/platform-auth-rbac-hitl-mock.ts`
- `tests/web-e2e/support/platform-governance-remediation-mock.ts`

## 目标

- 回迁 Platform 侧四条核心业务流
- 让 `biz-e2e` 与 `web-e2e-full` 有实际差异化覆盖

## 交付

- 四条 Platform 业务流 spec
- 配套 mock 与数据支撑

## 验收

- `python3 tests/run_suite.py --suite biz-e2e --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test specs/biz --list`

## 当前进度

- 已完成上述文件迁移

## 风险

- 业务流场景多且 mock 较重，后续易出现 flaky 与误报
