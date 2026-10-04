# WE-002

## 标题

裁剪 Web suite registry 与 gate 编排。

## 范围

- `tests/suites.json`
- `tests/run_gates.sh`
- `tests/run_suite.py`

## 目标

- 只保留四组 Web suite
- 让 `pr`、`nightly`、`release` gate 对应清晰的 Web 回归集合

## 交付

- `web-e2e-core`
- `biz-e2e`
- `web-e2e-full`
- `web-e2e-quarantine`

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`
- `bash tests/run_gates.sh --gate pr --dry-run`

## 当前进度

- 已完成：
  - `tests/suites.json`
  - `tests/run_gates.sh`
  - `tests/run_suite.py`

## 风险

- suite 与 gate 语义不清时，后续 CI 接线容易重复运行或漏跑
