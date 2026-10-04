# Platform Access（数据接入中心）

## 目录

- 报告：`worklog/v2.2.1/platform/access/report/data-access-center-gap-analysis.md`
- 完成摘要：`worklog/v2.2.1/platform/access/report/completion-summary.md`
- 回归摘要：`worklog/v2.2.1/platform/access/report/p0-regression-summary.md`
- 任务清单：`worklog/v2.2.1/platform/access/tasks/README.md`
- 自动化脚本：`worklog/v2.2.1/platform/access/scripts`
- 原始结果：`worklog/v2.2.1/platform/access/raw`

## 说明

本目录用于承接“数据接入中心”专项优化，按照 `P0 -> P1 -> P2` 顺序推进。

## 常用命令

- 预发布检查（编译+矩阵）：`bash worklog/v2.2.1/platform/access/scripts/run-preflight.sh`
- 单环境回归：`bash worklog/v2.2.1/platform/access/scripts/run-p0-regression.sh --hours 24 --mode normal --arch x86_64`
- 矩阵回归：`bash worklog/v2.2.1/platform/access/scripts/run-p0-matrix.sh --hours 24 --matrix normal:x86_64,legacy:aarch64,dev:x86_64`
