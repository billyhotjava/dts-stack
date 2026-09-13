# F5: 同步任务向导与批量建任务

**优先级**: P0
**状态**: DONE

## 目标

把接入任务配置从工程型 JSON 操作升级为产品化向导，支持单表、多表批量、字段选择、增量策略、调度绑定和生成前预检。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 三步任务向导信息架构 | P0 | DONE | F2-F4 |
| T02 | 表/字段选择与批量任务草稿 | P0 | DONE | T01 |
| T03 | 同步策略配置：全量、追加、时间戳增量、主键增量 | P0 | DONE | T01-T02 |
| T04 | 调度绑定与任务生成提交 | P0 | DONE | T03 |
| T05 | 向导前端与任务草稿恢复 | P1 | DONE | T01-T04 |

## 完成标准

- [x] 用户按“选数据源 -> 选表字段/策略 -> 生成任务调度”完成任务创建。
- [x] 支持多表批量创建。
- [x] 支持字段选择、字段重命名、目标表预览。
- [x] 支持全量覆盖、全量追加、时间戳增量、主键增量。
- [x] 创建后自动生成 ODS、Addax Job、Airflow DAG、dbt source 和初始血缘。

## 当前落地

- Schema Discover 弹窗承载当前轻量任务向导：先选择数据源，再选择单表或前 20 张表批量预览 ODS，最后生成同步任务。
- `OdsGenerationService.buildSyncTaskDraft` 根据 ODS 方案生成 ingestion task payload，包括 source、destination、streams、sync、Airflow、dbt 和 lineage 配置。
- 前端“生成同步任务”复用 `/ingestion/tasks`，由平台代理注入默认数据湖目标端，ingestion 服务生成 Addax Job 和 Airflow DAG。
- 前端已开放 ODS schema、来源系统编码、业务编码和同步模式配置，支持 `full_refresh` 与时间戳增量主链；增量模式会校验所选表存在共同增量候选字段，避免批量任务生成后出现不可解释的 watermark。
- 前端生成同步任务前会自动执行 F7 `ods-precheck`；失败阻断提交，警告需二次确认后才继续 `ods-apply`、`sync-task-draft` 和 `/ingestion/tasks`。
- 后端 ODS/任务草稿请求已支持字段级 `include=false`、`targetName`、`targetDataType`，为独立向导页的字段选择、重命名和类型覆盖表格预留稳定契约。
- 后端同步策略契约补齐 `full_refresh`、`append/full_append`、`incremental/timestamp_incremental`、`primary_key_incremental/pk_incremental`；主键增量要求多表存在同名数值主键，并映射为 ingestion 增量任务。
- 前端 Schema Discover 任务生成配置补齐全量覆盖、全量追加、时间戳增量和主键增量选项。
- 前端字段编辑表格支持按表配置字段包含/排除、目标字段名和目标类型覆盖；预览后展示目标 ODS 表结构，提交时复用同一份 ODS request 生成任务草稿。
- 常规入湖任务创建页已具备草稿恢复能力，Connector Center 侧任务草稿生成后仍落到同一 ingestion task 主模型。

## 待补

- 无。
