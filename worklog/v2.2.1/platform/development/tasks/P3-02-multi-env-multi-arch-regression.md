# P3-02 多环境多架构回归矩阵

- 优先级：P3
- 状态：in-progress

## 范围

- 建立 x86/ARM + legacy/normal/dev 的开发中心回归矩阵。

## 子任务

- 定义开发中心回归用例集（建模、dbt、SQL、编排入口）。
- 接入矩阵执行脚本与结果归档。
- 建立失败 TopN 和趋势报告。

## 验收标准

- 任一模式回归失败可快速定位。
- 发布前必须完成目标矩阵验证。

## 风险与回滚

- 风险：ARM 环境资源不足，回归耗时长。
- 回滚：采用分层矩阵（P0 全量，P1/P2 抽样）。

## 当前进展（2026-02-17）

- 已落地矩阵执行与报告脚本：
  - `worklog/v2.2.1/platform/development/scripts/run-p3-02-matrix.sh`
  - `worklog/v2.2.1/platform/development/scripts/render-p3-02-report.sh`
- 已补充执行说明：
  - `worklog/v2.2.1/platform/development/report/p3-02-matrix-runbook.md`
- 产物归档规范已明确：
  - `raw/p3-02-summary-*.txt`
  - `raw/p3-02-failure-top-*.csv`
  - `raw/p3-02-hourly-*.csv`
  - `raw/p3-02-trend-*.tsv`
  - `report/p3-02-matrix-latest.md`
- 已完成 x86 三模式首轮回归并回填：
  - `normal` / `legacy` / `dev`
  - 最新报告：`worklog/v2.2.1/platform/development/report/p3-02-matrix-latest.md`
- 已加“跨架构标签保护”：
  - 默认禁止在 x86 直接标记 `--arch aarch64`
  - 仅在显式 `--allow-cross-arch-label` 时允许（仅调试用途）
- 已补 `collect_failed` 诊断增强：
  - 报告渲染会显式标记 `failure_collect_failed` / `hourly_collect_failed`
  - 采集失败时保留 `*.err` 文件，便于定位 SQL/权限问题

## 待完成

- 在目标环境执行 ARM 首轮矩阵（legacy/normal/dev）。
- 对比 x86 与 ARM 差异并回填到 `report/p3-02-matrix-latest.md`。
- ARM 执行命令已验证可用（dry-run）：
  - `worklog/v2.2.1/platform/development/scripts/run-p3-02-matrix.sh --hours 24 --modes normal,legacy,dev --arch aarch64 --result OBSERVED`

## 本轮回归命令（x86）

- `worklog/v2.2.1/platform/development/scripts/run-p3-02-matrix.sh --dry-run`
- `worklog/v2.2.1/platform/development/scripts/run-p3-02-matrix.sh --hours 24 --modes legacy,dev --result OBSERVED`
- `worklog/v2.2.1/platform/development/scripts/run-p3-02-matrix.sh --hours 24 --modes normal,legacy,dev --result OBSERVED`

## 首轮实测（已完成）

- 时间：`20260217T061842Z-3328693`
- 环境：`x86_64 + normal + 24h`
- 产物：
  - `worklog/v2.2.1/platform/development/raw/p3-02-summary-20260217T061842Z-3328693.txt`
  - `worklog/v2.2.1/platform/development/raw/p3-02-failure-top-20260217T061842Z-3328693.csv`
  - `worklog/v2.2.1/platform/development/raw/p3-02-hourly-20260217T061842Z-3328693.csv`
  - `worklog/v2.2.1/platform/development/report/p3-02-matrix-latest.md`
