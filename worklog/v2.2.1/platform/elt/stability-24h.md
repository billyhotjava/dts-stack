# 24h 稳定性执行记录（Platform）

## 1. 环境信息
- 日期：
- 执行人：
- 分支 / 提交：
- 架构：`x86_64` / `aarch64`
- 模式：`legacy` / `normal` / `dev`
- 关键镜像版本：
  - `dts-ingestion`：
  - `dts-platform`：
  - `dts-addax`：
  - `dts-airflow-*`：

## 2. 压测场景
- 场景 A：Excel 全量入湖（小文件）
- 场景 B：Excel 全量入湖（大文件）
- 场景 C：源库全量入湖（多表）
- 场景 D：源库增量入湖（轮询）
- 场景 E：查询沉淀数据集 -> 发布 -> 看板读取

## 3. 关键指标（按小时记录）
| Hour | 任务总数 | 成功数 | 失败数 | 平均耗时(s) | P95(s) | DAG 404 次数 | Task Log 404 次数 | 备注 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| 00-01 |  |  |  |  |  |  |  |  |
| 01-02 |  |  |  |  |  |  |  |  |
| ... |  |  |  |  |  |  |  |  |
| 23-24 |  |  |  |  |  |  |  |  |

## 4. 失败样本 TopN
| 时间 | taskId | executionId | failure_category | failure_advice | 根因 | 修复动作 | 复测结果 |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## 5. 结论
- 通过标准：
  - 成功率 >= 99%
  - 无持续性 DAG 不可触发
  - 无“成功但无数据”静默失败
- 实际结果：
- 待修复项：

### Auto Backfill 20260213T094146Z
- 观测窗口：最近 168 小时
- 模式/架构：normal / x86_64
- 总任务数：3（成功 2 / 失败 1，成功率 66.67%）
- DAG 404：0；Task Log 404：0
- 失败分类 Top:
  - UNCLASSIFIED: 1
- 证据文件：`raw/summary-20260213T094146Z.txt`、`raw/hourly-metrics-20260213T094146Z.csv`、`raw/failure-top-20260213T094146Z.csv`

### Auto Backfill 20260213T094326Z
- 观测窗口：最近 168 小时
- 模式/架构：normal / x86_64
- 总任务数：3（成功 2 / 失败 1，成功率 66.67%）
- DAG 404：0；Task Log 404：0
- 失败分类 Top:
  - UNCLASSIFIED: 1
- 证据文件：`raw/summary-20260213T094326Z.txt`、`raw/hourly-metrics-20260213T094326Z.csv`、`raw/failure-top-20260213T094326Z.csv`























## 7. 环境矩阵（自动汇总）
| Run UTC | Mode | Arch | Total | Success | Failed | Success Rate | DAG 404 | TaskLog 404 | Result | Note |
|---|---|---|---:|---:|---:|---:|---:|---:|---|---|
| 20260213T094326Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | sample-168h |
| 20260213T094326Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | format-fix |
| 20260214T115349Z | normal | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | no-pg-container-or-query-failed |
| 20260214T115543Z | normal | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | run-all-smoke |
| 20260214T115906Z | normal | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | run-all-escalated |
| 20260214T115932Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | run-all-168h |
| 20260214T115939Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | run-all-168h-p1 |
| 20260214T120042Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | run-all-168h-strict |
| 20260214T120627Z | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | matrix-normal-x86_64 |
| 20260214T120628Z | legacy | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | matrix-legacy-x86_64 |
| 20260214T120628Z | dev | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | matrix-dev-x86_64 |
| 20260214T120653Z-1490075 | legacy | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | matrixfix-legacy-x86_64 |
| 20260214T120653Z-1490428 | dev | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | matrixfix-dev-x86_64 |
| 20260214T133537Z-1731149 | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | guardok-normal-x86_64 |
| 20260214T133554Z-1732226 | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | guardok-normal-x86_64 |
| 20260214T133643Z-1734911 | normal | x86_64 | 3 | 2 | 1 | 66.67% | 0 | 0 | OBSERVED | finalcheck-normal-x86_64 |
| 20260217T061842Z-3328693 | normal | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-normal-x86_64 |
| 20260217T061947Z-3330467 | legacy | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-legacy-x86_64 |
| 20260217T061948Z-3330731 | dev | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-dev-x86_64 |
| 20260217T062220Z-3333745 | legacy | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-legacy-x86_64 |
| 20260217T062220Z-3334028 | dev | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-dev-x86_64 |
| 20260217T062227Z-3334589 | normal | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-normal-x86_64 |
| 20260217T062227Z-3334853 | legacy | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-legacy-x86_64 |
| 20260217T062228Z-3335140 | dev | x86_64 | 0 | 0 | 0 | 0.00% | 0 | 0 | OBSERVED | p3-02-devcenter-dev-x86_64 |
