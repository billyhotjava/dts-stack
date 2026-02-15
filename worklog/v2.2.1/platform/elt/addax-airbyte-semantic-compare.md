# Addax / Airbyte 语义对照（Platform）

## 1. 背景
- 当前单机编排：Addax + Airflow
- 目标 K8s 编排：Airbyte + Airflow/Argo（待定）
- 核心要求：全量/增量语义一致，幂等、可审计、可重放。

## 2. 语义对照矩阵
| 维度 | Addax 当前行为 | Airbyte 目标行为 | 是否一致 | 差距说明 | 统一策略 |
|---|---|---|---|---|---|
| 全量导入 | DROP + CREATE + INSERT |  |  |  |  |
| 增量导入 | 基于主键/时间列轮询 |  |  |  |  |
| 文件导入 | 仅全量 |  |  |  |  |
| schema 演进 |  |  |  |  |  |
| 错误重试 |  |  |  |  |  |
| 审计字段 | `source_system`,`import_time` |  |  |  |  |
| 时区 | `Asia/Shanghai` |  |  |  |  |

## 3. 迁移前置检查
- 连接器能力映射（是否支持 CDC / 增量游标）。
- 各数据源驱动兼容性（x86/ARM）。
- 历史任务回放抽样（同一输入输出是否一致）。

## 4. 验收标准
- 同一任务在 Addax 与 Airbyte 输出行数、关键字段 hash 一致。
- 全量语义一致：重复执行后“以本次输入为准”。
- 增量语义一致：无漏数、无重数、可断点续跑。

## 自动对照 20260213T094615Z
- 输入：`ADDAX` vs `AIRBYTE`，key=`hour_slot`
- 结果：PASS（only-left=0, only-right=0, mismatch=0）
- 报告：`raw/semantic-compare-20260213T094615Z.md`

## 自动对照 20260214T115413Z
- 输入：`ADDAX` vs `AIRBYTE`，key=`hour_slot`
- 结果：PASS（only-left=0, only-right=0, mismatch=0）
- 报告：`raw/semantic-compare-20260214T115413Z.md`

## 自动对照 20260214T115939Z
- 输入：`ADDAX` vs `AIRBYTE`，key=`hour_slot`
- 结果：PASS（only-left=0, only-right=0, mismatch=0）
- 报告：`raw/semantic-compare-20260214T115939Z.md`
