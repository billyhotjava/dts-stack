# F4: ODS 与 dbt source 自动生成

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

从 Schema Discover 结果自动生成 ODS 表结构、技术字段、Addax Job 初稿和 dbt `source.yml`，让接入任务天然进入 dbt 和 Sprint-20 血缘链路。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ODS 命名规则与技术字段模板 | P0 | DONE | F3 |
| T02 | 源字段到 ODS 字段类型映射与预览 | P0 | DONE | T01 |
| T03 | dbt source.yml 自动生成/合并 | P0 | DONE | T01-T02 |
| T04 | Addax Job 与 Airflow DAG 生成链路收敛 | P0 | DONE | T01-T03 |

## 完成标准

- [x] 源表 `erp_project` 可预览生成 `ods_erp_project`。
- [x] ODS 表默认包含 `source_system`、`source_table`、`source_pk`、`extract_time`、`batch_id`、`is_deleted`、`raw_json` 等技术字段。
- [ ] 字段类型映射可人工覆盖。
- [x] 自动生成 dbt source，并能被 dbt manifest 血缘识别。
- [x] Addax Job 和 Airflow DAG 生成不要求用户手写 JSON/Python。

## 当前落地

- 新增 `POST /api/infra/data-sources/{id}/ods-preview`，从 Schema Discover 表生成 ODS 表名、字段类型、技术字段、DDL、dbt source YAML、Addax Job 草稿和 Airflow DAG 草稿。
- 新增 `POST /api/infra/data-sources/{id}/ods-apply`，写入 ODS 映射、catalog dataset/table/column、刷新 dbt `ods_sources.yml`，并写入 Sprint-20 接入血缘。
- 新增 `POST /api/infra/data-sources/{id}/sync-task-draft`，从 ODS 方案生成 ingestion task payload，复用默认数据湖目标端创建 Addax Job 和 Airflow DAG。
- 数据源页面的 Schema 探测弹窗新增单表/批量 ODS 预览和“生成 ODS 映射与 dbt source”操作。
- 数据源页面新增“生成同步任务”，会先 upsert ODS/dbt source/血缘，再创建入湖任务并触发 Addax/Airflow 生成。
- dbt source 技术字段识别扩展到 `source_system`、`source_table`、`source_pk`、`extract_time`、`batch_id`、`is_deleted`、`raw_json` 等无 `_dts_` 前缀字段。

## 待补

- 字段类型 override 需要前端表格化编辑，而不是仅支持 API payload。
