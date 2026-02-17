# P3-02 回归矩阵执行说明

## 目标

在 `x86/ARM + normal/legacy/dev` 组合下，统一执行开发中心回归并沉淀失败 TopN 与趋势数据。

## 脚本

- 执行矩阵：`worklog/v2.2.1/platform/developmnet/scripts/run-p3-02-matrix.sh`
- 生成报告：`worklog/v2.2.1/platform/developmnet/scripts/render-p3-02-report.sh`

## 常用命令

```bash
# 仅预览命令（不执行）
worklog/v2.2.1/platform/developmnet/scripts/run-p3-02-matrix.sh --dry-run

# 执行默认矩阵（168h + normal/legacy/dev + 当前架构）
worklog/v2.2.1/platform/developmnet/scripts/run-p3-02-matrix.sh

# 指定架构与模式
worklog/v2.2.1/platform/developmnet/scripts/run-p3-02-matrix.sh \
  --arch aarch64 \
  --modes legacy,normal \
  --hours 72 \
  --result OBSERVED

# 仅重渲染报告
worklog/v2.2.1/platform/developmnet/scripts/render-p3-02-report.sh
```

## 产物路径

- 原始数据：`worklog/v2.2.1/platform/developmnet/raw/`
  - `p3-02-summary-<run_at_utc>.txt`
  - `p3-02-failure-top-<run_at_utc>.csv`
  - `p3-02-hourly-<run_at_utc>.csv`
  - `p3-02-trend-<ts>.tsv`
  - `p3-02-failure-topn-<run_at_utc>.tsv`
- 报告：`worklog/v2.2.1/platform/developmnet/report/p3-02-matrix-latest.md`

## 判定建议

- 发布前需覆盖目标部署模式（至少 `legacy + normal`）。
- `has_failure_category=1` 或 `dag_404_count/tasklog_404_count > 0` 时，禁止直接发布。
- 若 ARM 环境资源不足，按“P0 全量 + P1/P2 抽样”策略执行并记录豁免说明。
