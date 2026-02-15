# P0-04 P0 回归门禁自动化

`status`: `done`
`priority`: `P0`

## 目标

将 P0 问题验收从人工口头确认为脚本化门禁，覆盖 x86/ARM 与 legacy/normal/dev。

## 范围

- 文档基线：`worklog/v2.2.0/platform-elt-p0-issues.md`
- 脚本目录：`worklog/v2.2.1/platform/access/scripts`（新建）
- 输出目录：`worklog/v2.2.1/platform/access/raw`（新建）

## 子任务

1. 定义 P0 回归最小用例集（DAG就绪、全量语义、日志可读、ODS联动）。
2. 统一采集结果格式（csv + markdown 摘要）。
3. 增加环境矩阵执行入口。
4. 失败门禁阻断后续阶段任务。

## 验收标准

- 一条命令可跑完整 P0 回归。
- 输出包含通过率、失败项、环境维度。
- 可重复运行并得到一致结构化结果。

## 风险与回滚

- 风险：现场环境差异导致误报。
- 回滚：允许“观察模式”运行，不阻断，仅标记告警。

## 实现进展（2026-02-14）

- 新增回归脚本目录：`worklog/v2.2.1/platform/access/scripts`
- 已实现脚本：
  - `collect-p0-metrics.sh`：从 ingestion 日志提取触发总数、失败数、DAG404、TaskLog404，并输出 CSV/TXT。
  - `render-p0-summary.sh`：将最新指标渲染为 `report/p0-regression-summary.md`。
  - `run-p0-regression.sh`：一键执行采集+渲染，支持 `--strict` 门禁模式。
  - `run-p0-matrix.sh`：按 `mode:arch` 矩阵批量执行采样并输出矩阵摘要，支持 `--strict`。
- 新增输出目录：`worklog/v2.2.1/platform/access/raw`
- 已完成一次本地演练：
  - 命令：`bash worklog/v2.2.1/platform/access/scripts/run-p0-regression.sh --hours 24 --mode normal --arch x86_64 --note init-pass`
  - 产物：
    - `worklog/v2.2.1/platform/access/raw/p0-access-metrics-*.csv`
    - `worklog/v2.2.1/platform/access/raw/p0-access-summary-*.txt`
    - `worklog/v2.2.1/platform/access/report/p0-regression-summary.md`
- 已完成矩阵脚本演练：
  - 命令：`bash worklog/v2.2.1/platform/access/scripts/run-p0-matrix.sh --hours 1 --matrix normal:x86_64`
  - 产物：
    - `worklog/v2.2.1/platform/access/raw/p0-access-matrix-*.csv`
    - `worklog/v2.2.1/platform/access/report/p0-regression-matrix-summary.md`
