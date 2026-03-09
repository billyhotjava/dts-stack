# WE-012

## 标题

收口 gate 入口、报告目录与运行文档。

## 范围

- `tests/run_gates.sh`
- `tests/reports/`
- `tests/README.md`
- `tests/web-e2e/README.md`
- `tests/.env.example`

## 目标

- 提供一套面向当前仓库可直接理解和操作的 Web E2E 运行说明
- 保留报告产物约定，方便后续接 CI

## 交付

- Web-only gate 脚本
- 报告目录约定
- 运行说明与环境变量模板

## 验收

- `bash tests/run_gates.sh --gate pr --dry-run`
- `bash tests/run_gates.sh --gate nightly --dry-run`

## 当前进度

- 已完成相关文件调整

## 风险

- gate 脚本只做 Web E2E，后续若继续扩展到非 Web 门禁需重新分层
