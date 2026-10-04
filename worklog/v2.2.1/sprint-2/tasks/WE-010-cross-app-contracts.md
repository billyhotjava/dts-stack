# WE-010

## 标题

迁移跨端 auth 与 selector contract 用例。

## 范围

- `tests/web-e2e/specs/core/auth-gateway.spec.ts`
- `tests/web-e2e/specs/contracts/testid-contract.spec.ts`
- `tests/web-e2e/specs/smoke/app-shell.spec.ts`

## 目标

- 保留低成本、跨端的基础回归能力
- 为后续 selector 稳定性治理留出基线

## 交付

- auth gateway spec
- selector contract spec
- app shell smoke spec

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `pnpm --dir tests/web-e2e exec playwright test specs/contracts/testid-contract.spec.ts --list`

## 当前进度

- 已完成上述文件迁移

## 风险

- 合同类 spec 成本低，但若长期不跟页面同步会快速失真
