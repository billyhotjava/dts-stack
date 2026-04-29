# F5: 同步任务向导与批量建任务

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把接入任务配置从工程型 JSON 操作升级为产品化向导，支持单表、多表批量、字段选择、增量策略、调度绑定和生成前预检。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 三步任务向导信息架构 | P0 | DONE | F2-F4 |
| T02 | 表/字段选择与批量任务草稿 | P0 | DONE | T01 |
| T03 | 同步策略配置：全量、追加、时间戳增量、主键增量 | P0 | IN_PROGRESS | T01-T02 |
| T04 | 调度绑定与任务生成提交 | P0 | DONE | T03 |
| T05 | 向导前端与任务草稿恢复 | P1 | READY | T01-T04 |

## 完成标准

- [x] 用户按“选数据源 -> 选表字段/策略 -> 生成任务调度”完成任务创建。
- [x] 支持多表批量创建。
- [ ] 支持字段选择、字段重命名、目标表预览。
- [ ] 支持全量覆盖、全量追加、时间戳增量、主键增量。
- [x] 创建后自动生成 ODS、Addax Job、Airflow DAG、dbt source 和初始血缘。

## 当前落地

- Schema Discover 弹窗承载当前轻量任务向导：先选择数据源，再选择单表或前 20 张表批量预览 ODS，最后生成同步任务。
- `OdsGenerationService.buildSyncTaskDraft` 根据 ODS 方案生成 ingestion task payload，包括 source、destination、streams、sync、Airflow、dbt 和 lineage 配置。
- 前端“生成同步任务”复用 `/ingestion/tasks`，由平台代理注入默认数据湖目标端，ingestion 服务生成 Addax Job 和 Airflow DAG。
- 前端已开放 ODS schema、来源系统编码、业务编码和同步模式配置，支持 `full_refresh` 与时间戳增量主链；增量模式会校验所选表存在共同增量候选字段，避免批量任务生成后出现不可解释的 watermark。
- 前端生成同步任务前会自动执行 F7 `ods-precheck`；失败阻断提交，警告需二次确认后才继续 `ods-apply`、`sync-task-draft` 和 `/ingestion/tasks`。

## 待补

- 独立三步式任务向导页面、草稿恢复和字段级选择/重命名 UI。
- 增量策略配置还需要补全全量追加、主键增量、覆盖模式细分和字段级人工指定。
