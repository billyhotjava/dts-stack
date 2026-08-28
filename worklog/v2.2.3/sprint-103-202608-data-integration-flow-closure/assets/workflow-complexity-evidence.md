# 数据集成流程复杂度证据

检查日期：2026-08-28。

## 结论

当前 7 条 ACTIVE 接入任务全部属于 `SINGLE_TASK_EL`，没有任务声明跨作业依赖、分支、汇聚、循环或条件节点。现有自由画布的 `graph_dsl` 也没有对象型业务配置被执行链消费。因此，本 Sprint 的可编辑 DAG capability 判定为 `NOT_JUSTIFIED`；产品形态冻结为“类型化任务配置 + 服务端只读拓扑 + 任务级运行闭环”。

## 全量任务分类

| taskId | 任务 | 来源 | 步骤 | 依赖形态 | 恢复方式 | 分类 |
|---:|---|---|---:|---|---|---|
| 3 | dtstest1 | MySQL | 1 个接入任务 | 无跨任务依赖 | 任务级失败重试 | SINGLE_TASK_EL |
| 6 | 进度跟进措施表1 | 文本文件 | 1 个接入任务 | 无跨任务依赖 | 重新校验文件后重试 | SINGLE_TASK_EL |
| 7 | 0805 | 文本文件 | 1 个接入任务 | 无跨任务依赖 | 重新校验文件后重试 | SINGLE_TASK_EL |
| 8 | 0805文件1 | 文本文件 | 1 个接入任务 | 无跨任务依赖 | 重新校验文件后重试 | SINGLE_TASK_EL |
| 9 | 0805文件2 | 文本文件 | 1 个接入任务 | 无跨任务依赖 | 重新校验文件后重试 | SINGLE_TASK_EL |
| 10 | 0805文件3 | 文本文件 | 1 个接入任务 | 无跨任务依赖 | 重新校验文件后重试 | SINGLE_TASK_EL |
| 13 | apiconntest1 | HTTP API | 1 个接入任务 | 无跨任务依赖 | 任务级失败重试 | SINGLE_TASK_EL |

## 可复查事实

- 7 条任务均由 `IngestionTask → IngestionTaskRevision → Airflow/Addax/API executor → IngestionExecution` 单任务链执行。
- 活跃任务来源分布为 MySQL 2、文本文件 4、HTTP API 1；目标均为平台默认 PostgreSQL 数据源。
- 88 条历史 execution 中 7 条成功、81 条失败；失败恢复属于单任务重试/补数，不构成多作业编排需求。
- 现存 `graph_dsl` 为 SQL NULL 或 JSON null，没有对象型 DSL 被准入、DAG 发布或执行读取。
- 当前没有至少 3 条具备“多作业 + 分支/汇聚/条件”特征的真实流程，未达到另立 DAG 编辑器的门槛。

## 边界

本结论只覆盖当前运行数据和本 Sprint 的产品范围。若后续出现版本化 SQL/dbt/模型制品及真实跨作业依赖，应另立 capability 评审；不得直接恢复自由节点或让 `graphDsl` 成为执行事实。
