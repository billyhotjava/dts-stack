# WE-001

## 标题

恢复 `tests/` 顶层骨架与 Web-only 文档。

## 范围

- `tests/README.md`
- `tests/.env.example`
- `tests/.gitignore`
- `tests/reports/README.md`
- `tests/reports/.gitkeep`

## 目标

- 在当前仓库重新建立 Web 自动化测试入口
- 明确本轮只保留 Web E2E 所需资产，不回带非 Web gate

## 交付

- `tests/` 顶层文档与环境变量模板可读
- 报告目录与忽略规则可用

## 验收

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `sed -n '1,120p' tests/README.md`

## 当前进度

- 已完成：
  - `tests/README.md`
  - `tests/.env.example`
  - `tests/.gitignore`
  - `tests/reports/README.md`
  - `tests/reports/.gitkeep`

## 风险

- 文档与真实 suite 语义若不同步，后续维护成本会快速上升
