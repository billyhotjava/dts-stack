# v2.2.1 P0 现场回归矩阵（legacy/normal/dev × x86/ARM）

## 1. 执行范围
- 目标：验证 `V221-P0-001/002/003` 在线下部署环境的行为一致性。
- 环境维度：
  - `legacy-x86`
  - `legacy-arm-kylin`
  - `normal-x86`
  - `normal-arm-kylin`
  - `dev-x86`
  - `dev-arm-kylin`

## 2. 用例清单
| 用例ID | 用例名称 | 期望结果 |
| --- | --- | --- |
| P0-C01 | Excel 全量重复执行 | 第二次执行前目标表先 `DROP + CREATE`，仅保留最新文件数据 |
| P0-C02 | 源库全量重复执行 | 第二次执行前目标表先 `DROP + CREATE`，历史脏列不残留 |
| P0-C03 | DAG 创建后立即触发 | 首次 trigger 可在等待窗口内成功，不出现长时间 404 报错 |
| P0-C04 | 平台查看日志 | 平台日志与 Airflow task instance 对齐，无 404 误判 |
| P0-C05 | ODS 一键生成批量 | 单映射失败不阻断整批，结果包含可读 skip/failed 原因 |

## 3. 执行记录模板
| 环境 | P0-C01 | P0-C02 | P0-C03 | P0-C04 | P0-C05 | 结论 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| legacy-x86 | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |
| legacy-arm-kylin | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |
| normal-x86 | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |
| normal-arm-kylin | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |
| dev-x86 | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |
| dev-arm-kylin | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | |

## 4. 失败归档模板
- 环境：
- 用例ID：
- 现象：
- 触发步骤：
- 关键日志（Platform/Ingestion/Airflow/Addax）：
- 初步根因：
- 修复建议：
- 是否阻塞上线：
