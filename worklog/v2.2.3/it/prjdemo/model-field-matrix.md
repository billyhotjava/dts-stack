# PRJDEMO 模型字段与转换矩阵

本文件是 `README.md` 第 11、13 节的录入清单。字段代码、模型物理表名和码值不得翻译成中文。

## 1. DWD：项目任务快照明细

- 模型类型：`FACT`
- 分层：`DWD`
- 物理表：`public.prjdemo_dwd_project_task_snapshot`
- 粒度：一个项目任务在一个快照日期一行
- 事实形态：`PERIODIC_SNAPSHOT`
- 时间语义：`SNAPSHOT_DATE`
- 去重键：`task_snapshot_id`
- 输入：当前建模方案中已确认的 `public.ods_prjdemo_project_task_snapshot`

| 序号 | 输出字段 | 类型 | 可空 | 角色 | ODS 来源 | 转换 | 标准/码表 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | task_snapshot_id | STRING | 否 | KEY | task_snapshot_id | 直接映射 | 任务快照标识标准 |
| 2 | project_snapshot_id | STRING | 否 | ATTRIBUTE | project_snapshot_id | 直接映射 | 项目快照标识标准，如已建立 |
| 3 | snapshot_date | DATE | 否 | TIME | snapshot_date | 必要时 CAST DATE | 日期标准 |
| 4 | project_code | STRING | 否 | ATTRIBUTE | project_code | 直接映射 | 项目编码标准，如已建立 |
| 5 | project_name | STRING | 否 | ATTRIBUTE | project_name | 直接映射 | 项目名称标准，如已建立 |
| 6 | task_code | STRING | 否 | ATTRIBUTE | task_code | 直接映射 | 任务编码标准，如已建立 |
| 7 | task_name | STRING | 否 | ATTRIBUTE | task_name | 直接映射 | 任务名称标准，如已建立 |
| 8 | task_status_code | STRING | 否 | ATTRIBUTE | task_status_code | 直接映射 | `PRJDEMO_TASK_STATUS` |
| 9 | progress_pct | DECIMAL | 否 | MEASURE | progress_pct | 必要时 CAST DECIMAL | `PRJDEMO_PROGRESS_PCT` / PERCENT |
| 10 | plan_end_date | DATE | 否 | ATTRIBUTE | plan_end_date | 必要时 CAST DATE | 日期标准 |
| 11 | actual_cost | DECIMAL | 否 | MEASURE | actual_cost | 必要时 CAST DECIMAL | `PRJDEMO_ACTUAL_COST` / CNY |
| 12 | source_batch_id | STRING | 否 | ATTRIBUTE | source_batch_id | 直接映射 | 批次标识标准，如已建立 |

转换约束：

- 不配置 JOIN；
- 不配置聚合；
- 不配置业务过滤；
- 只在源类型不匹配时做 DATE/DECIMAL 显式转换；
- 去重仅按稳定快照主键 `task_snapshot_id`，不能按中文项目名或任务名去重。

## 2. DWS：项目进度汇总

- 模型类型：`SUMMARY`
- 分层：`DWS`
- 物理表：`public.prjdemo_dws_project_progress`
- 粒度：一个项目在一个快照日期一行
- 输入：已发布的 DWD `项目任务快照明细` 当前版本
- 分组键：`project_snapshot_id`、`snapshot_date`、`project_code`、`project_name`

| 序号 | 输出字段 | 类型 | 可空 | 角色 | DWD 来源 | 配置 | 标准/单位 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | project_snapshot_id | STRING | 否 | KEY | project_snapshot_id | GROUP BY | 项目快照标识标准 |
| 2 | snapshot_date | DATE | 否 | TIME | snapshot_date | GROUP BY | 日期标准 |
| 3 | project_code | STRING | 否 | ATTRIBUTE | project_code | GROUP BY | 项目编码标准，如已建立 |
| 4 | project_name | STRING | 否 | ATTRIBUTE | project_name | GROUP BY | 项目名称标准，如已建立 |
| 5 | task_total | BIGINT | 否 | MEASURE | task_code | COUNT | `PRJDEMO_TASK_COUNT` / COUNT |
| 6 | avg_progress_pct | DECIMAL | 否 | MEASURE | progress_pct | AVG | `PRJDEMO_PROGRESS_PCT` / PERCENT |
| 7 | actual_cost_amount | DECIMAL | 否 | MEASURE | actual_cost | SUM | `PRJDEMO_ACTUAL_COST` / CNY |

聚合约束：

- 每个非度量输出均选择 `GROUP BY`；
- 每个度量输出均选择对应聚合函数；
- `task_total` 按当前 8 行固定快照数据使用 `COUNT(task_code)`；若后续允许同一任务重复记录，应先在 DWD 解决唯一性，不要在本 Demo 临时改为业务含义不明的计数；
- 不配置 JOIN、过滤或去重；
- 输出字段不得保留未分组、未聚合的悬空映射。

## 3. 预期结果

| project_snapshot_id | snapshot_date | project_code | project_name | task_total | avg_progress_pct | actual_cost_amount |
| --- | --- | --- | --- | --- | ---: | ---: | ---: |
| PPS_PRJA_20260901 | 2026-09-01 | PRJ-A | 数据治理平台升级 | 4 | 72.50 | 91000.00 |
| PPS_PRJB_20260901 | 2026-09-01 | PRJ-B | 经营分析看板建设 | 4 | 53.75 | 51000.00 |

模型物化后必须以物理表结果为准，并在 `evidence-register.md` 记录运行 ID 和查询/资产证据。
