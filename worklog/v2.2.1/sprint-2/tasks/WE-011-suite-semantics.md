# WE-011

## 标题

重组 `core`、`biz`、`full`、`quarantine` suite 语义。

## 范围

- `tests/suites.json`
- `tests/web-e2e/README.md`
- `tests/README.md`

## 目标

- 修复 `v2.5.0` 中 `core` 与 `full` 基本重复的问题
- 让 `full = core + biz` 成为明确的汇总回归入口

## 交付

- 5 条 `web-e2e-core`
- 5 条 `biz-e2e`
- 10 条 `web-e2e-full`
- 1 条 `web-e2e-quarantine`

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `python3 tests/run_suite.py --suite biz-e2e --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`

## 当前进度

- 已完成 suite 与 README 调整

## 风险

- suite 语义如果与团队认知不一致，后续使用者会误把 `full` 当成重复入口
