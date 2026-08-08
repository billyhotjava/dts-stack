# Demo 模型 DDL 参考（页面建表帮助）

本文件与 `assets/model-field-matrix.csv`、`dbt/models/**` 保持一致，供按 `03-ui-runbook.md` 第 7 章在
维度建模工作台创建/校验模型时对照填写。

## 通用约定

- 页面字段类型下拉只有 `STRING / BOOLEAN / INT / BIGINT / DECIMAL / DATE / TIMESTAMP`；DDL 中给出对应的 PostgreSQL 类型。
- “表名”用小写英文、数字、下划线，并遵守分层前缀：
  - 维度表/明细表：`dim_` / `dwd_`（DWD）
  - 汇总表：`dws_`（DWS）
  - 应用表：`ads_`（ADS）
- 每个模型的粒度键（KEY 字段）在页面勾选“主键”，且“非空”必须勾选。
- 概念维度不维护属性；主键在维度表字段层声明。“维度属性编码”仅在现行维度已定义属性编码时填写，否则留空。
- 状态/编码字段在“标准映射”中绑定公共码表；计数/百分比/金额字段绑定数据元与单位。
- 密级统一选择现有公开级别（示例值 `DATA_PUBLIC`）。

---

## 1. `it_demo_dwd_dim_date`（DIMENSION / DWD）

粒度：一个公历日期一行；粒度键：`date_key`；物化：table / FULL；日期范围 2026-01-01 ~ 2027-12-31。

```sql
create table it_demo_dwd_dim_date (
    date_key      integer not null,
    full_date     date    not null,
    year_no       integer not null,
    month_no      integer not null,
    iso_week_no   integer not null,
    is_workday    boolean not null,
    constraint pk_it_demo_dwd_dim_date primary key (date_key)
);

comment on table it_demo_dwd_dim_date is '统一公历日期维度';
comment on column it_demo_dwd_dim_date.date_key is '日期键（YYYYMMDD）';
comment on column it_demo_dwd_dim_date.full_date is '日期';
comment on column it_demo_dwd_dim_date.year_no is '年份';
comment on column it_demo_dwd_dim_date.month_no is '月份';
comment on column it_demo_dwd_dim_date.iso_week_no is 'ISO周数';
comment on column it_demo_dwd_dim_date.is_workday is '是否工作日';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `date_key` | INT | 日期键 | ✓ | ✓ | | IT-DE-DATE-KEY | DATE_DIMENSION |
| `full_date` | DATE | 日期 | | ✓ | | | DATE_DIMENSION |
| `year_no` | INT | 年份 | | ✓ | | | DATE_DIMENSION |
| `month_no` | INT | 月份 | | ✓ | | | DATE_DIMENSION |
| `iso_week_no` | INT | ISO周数 | | ✓ | | | DATE_DIMENSION |
| `is_workday` | BOOLEAN | 是否工作日 | | ✓ | | | DATE_DIMENSION |

---

## 2. `it_demo_dwd_dim_org`（DIMENSION / DWD）

粒度：一个组织一行；粒度键：`org_code`；SCD：TYPE1；物化：table / FULL；来源：已确认 `ods_it_demo_org`。

```sql
create table it_demo_dwd_dim_org (
    org_code           varchar(32)  not null,
    org_name           varchar(100) not null,
    parent_org_code    varchar(32),
    org_level_code     varchar(32)  not null,
    record_status_code varchar(32)  not null,
    constraint pk_it_demo_dwd_dim_org primary key (org_code)
);

comment on table it_demo_dwd_dim_org is 'Demo 组织当前维度';
comment on column it_demo_dwd_dim_org.org_code is '组织编码';
comment on column it_demo_dwd_dim_org.org_name is '组织名称';
comment on column it_demo_dwd_dim_org.parent_org_code is '上级组织编码';
comment on column it_demo_dwd_dim_org.org_level_code is '组织层级编码（OL-HQ/OL-DEPT）';
comment on column it_demo_dwd_dim_org.record_status_code is '记录状态编码（RS-ACTIVE/RS-INACTIVE）';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `org_code` | STRING | 组织编码 | ✓ | ✓ | | IT-DE-ORG-CODE | org_code |
| `org_name` | STRING | 组织名称 | | ✓ | | | org_name |
| `parent_org_code` | STRING | 上级组织编码 | | 可空 | | IT-DE-ORG-CODE | parent_org_code |
| `org_level_code` | STRING | 组织层级编码 | | ✓ | | | org_level_code |
| `record_status_code` | STRING | 记录状态编码 | | ✓ | | IT-DE-STATUS-CODE | record_status_code |

---

## 3. `it_demo_dwd_dim_project`（DIMENSION / DWD）

粒度：一个研发项目一行；粒度键：`project_code`；SCD：TYPE1；物化：table / FULL；来源：已确认 `ods_it_demo_project`。

```sql
create table it_demo_dwd_dim_project (
    project_code        varchar(32)  not null,
    project_name        varchar(200) not null,
    owner_org_code      varchar(32)  not null,
    manager_name        varchar(100) not null,
    project_status_code varchar(32)  not null,
    plan_start_date     date         not null,
    plan_end_date       date         not null,
    constraint pk_it_demo_dwd_dim_project primary key (project_code)
);

comment on table it_demo_dwd_dim_project is 'Demo 项目当前维度';
comment on column it_demo_dwd_dim_project.project_code is '项目编码';
comment on column it_demo_dwd_dim_project.project_name is '项目名称';
comment on column it_demo_dwd_dim_project.owner_org_code is '责任组织编码';
comment on column it_demo_dwd_dim_project.manager_name is '项目负责人（合成展示）';
comment on column it_demo_dwd_dim_project.project_status_code is '项目状态编码（PJ-ACTIVE/PJ-CLOSED）';
comment on column it_demo_dwd_dim_project.plan_start_date is '计划开始日期';
comment on column it_demo_dwd_dim_project.plan_end_date is '计划结束日期';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `project_code` | STRING | 项目编码 | ✓ | ✓ | | IT-DE-PROJECT-CODE | project_code |
| `project_name` | STRING | 项目名称 | | ✓ | | | project_name |
| `owner_org_code` | STRING | 责任组织编码 | | ✓ | | IT-DE-ORG-CODE | owner_org_code |
| `manager_name` | STRING | 项目负责人 | | ✓ | | | manager_name |
| `project_status_code` | STRING | 项目状态编码 | | ✓ | | IT-DE-STATUS-CODE | project_status_code |
| `plan_start_date` | DATE | 计划开始日期 | | ✓ | | | plan_start_date |
| `plan_end_date` | DATE | 计划结束日期 | | ✓ | | | plan_end_date |

---

## 4. `it_demo_dwd_fct_task_snapshot`（FACT / DWD）

粒度：一个项目任务在一个快照日期一行；粒度键：`task_snapshot_id`；事实形态：PERIODIC_SNAPSHOT；
时间语义：SNAPSHOT_DATE（`snapshot_date`）；物化：table / FULL。

```sql
create table it_demo_dwd_fct_task_snapshot (
    task_snapshot_id      varchar(32)  not null,
    task_code             varchar(32)  not null,
    snapshot_date         date         not null,
    project_code          varchar(32)  not null,
    owner_org_code        varchar(32)  not null,
    task_name             varchar(200) not null,
    owner_name            varchar(100) not null,
    task_status_raw       varchar(32),
    risk_level_raw        varchar(32),
    task_status_code      varchar(32)  not null,
    risk_level_code       varchar(32)  not null,
    plan_start_date       date         not null,
    plan_end_date         date         not null,
    actual_finish_date    date,
    progress_pct          numeric(5,2) not null,
    task_count            integer      not null,
    completed_task_count  integer      not null,
    overdue_task_count    integer      not null,
    high_risk_task_count  integer      not null,
    plan_cost_amount      numeric(18,2) not null,
    actual_cost_amount    numeric(18,2) not null,
    cost_variance_amount  numeric(18,2) not null,
    source_updated_at     timestamptz  not null,
    source_batch_id       varchar(64)  not null,
    constraint pk_it_demo_dwd_fct_task_snapshot primary key (task_snapshot_id)
);

comment on table it_demo_dwd_fct_task_snapshot is '项目任务快照事实';
comment on column it_demo_dwd_fct_task_snapshot.task_snapshot_id is '任务快照标识（task_code+snapshot_date 哈希）';
comment on column it_demo_dwd_fct_task_snapshot.task_code is '任务编码';
comment on column it_demo_dwd_fct_task_snapshot.snapshot_date is '快照日期（业务时间）';
comment on column it_demo_dwd_fct_task_snapshot.project_code is '项目编码';
comment on column it_demo_dwd_fct_task_snapshot.owner_org_code is '责任组织编码（来自项目主数据）';
comment on column it_demo_dwd_fct_task_snapshot.task_name is '任务名称';
comment on column it_demo_dwd_fct_task_snapshot.owner_name is '任务负责人（合成展示）';
comment on column it_demo_dwd_fct_task_snapshot.task_status_raw is '任务状态原始值';
comment on column it_demo_dwd_fct_task_snapshot.risk_level_raw is '风险等级原始值';
comment on column it_demo_dwd_fct_task_snapshot.task_status_code is '任务状态标准码（IT-RC-TASK-STATUS）';
comment on column it_demo_dwd_fct_task_snapshot.risk_level_code is '风险等级标准码（IT-RC-RISK-LEVEL）';
comment on column it_demo_dwd_fct_task_snapshot.plan_start_date is '计划开始日期';
comment on column it_demo_dwd_fct_task_snapshot.plan_end_date is '计划结束日期';
comment on column it_demo_dwd_fct_task_snapshot.actual_finish_date is '实际完成日期';
comment on column it_demo_dwd_fct_task_snapshot.progress_pct is '任务进度（0~100）';
comment on column it_demo_dwd_fct_task_snapshot.task_count is '任务计数';
comment on column it_demo_dwd_fct_task_snapshot.completed_task_count is '已完成任务计数';
comment on column it_demo_dwd_fct_task_snapshot.overdue_task_count is '延期任务计数';
comment on column it_demo_dwd_fct_task_snapshot.high_risk_task_count is '高风险任务计数';
comment on column it_demo_dwd_fct_task_snapshot.plan_cost_amount is '计划成本';
comment on column it_demo_dwd_fct_task_snapshot.actual_cost_amount is '实际成本';
comment on column it_demo_dwd_fct_task_snapshot.cost_variance_amount is '成本偏差（实际-计划）';
comment on column it_demo_dwd_fct_task_snapshot.source_updated_at is '源端更新时间';
comment on column it_demo_dwd_fct_task_snapshot.source_batch_id is '源批次号';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `task_snapshot_id` | STRING | 任务快照标识 | ✓ | ✓ | | IT-DE-RECORD-ID | hash(task_code \|\| snapshot_date) |
| `task_code` | STRING | 任务编码 | | ✓ | | IT-DE-TASK-CODE | task_code |
| `snapshot_date` | DATE | 快照日期 | | ✓ | | | snapshot_date（TIME 字段） |
| `project_code` | STRING | 项目编码 | | ✓ | | IT-DE-PROJECT-CODE | project_code |
| `owner_org_code` | STRING | 责任组织编码 | | ✓ | | IT-DE-ORG-CODE | lookup project.owner_org_code |
| `task_name` | STRING | 任务名称 | | ✓ | | | task_name |
| `owner_name` | STRING | 任务负责人 | | ✓ | | | owner_name |
| `task_status_code` | STRING | 任务状态编码 | | ✓ | | IT-DE-STATUS-CODE / IT-RC-TASK-STATUS | normalize task_status_raw |
| `risk_level_code` | STRING | 风险等级编码 | | ✓ | | IT-DE-STATUS-CODE / IT-RC-RISK-LEVEL | normalize risk_level_raw |
| `task_status_raw` | STRING | 任务状态原始值 | | 可空 | | | task_status_raw |
| `risk_level_raw` | STRING | 风险等级原始值 | | 可空 | | | risk_level_raw |
| `source_updated_at` | TIMESTAMP | 源端更新时间 | | ✓ | | | updated_at |
| `source_batch_id` | STRING | 源批次号 | | ✓ | | | source_batch_id |
| `plan_start_date` | DATE | 计划开始日期 | | ✓ | | | plan_start_date |
| `plan_end_date` | DATE | 计划结束日期 | | ✓ | | | plan_end_date |
| `actual_finish_date` | DATE | 实际完成日期 | | 可空 | | | actual_finish_date |
| `progress_pct` | DECIMAL | 任务进度 | | ✓ | | IT-DE-PERCENT / PERCENT | progress_pct |
| `task_count` | INT | 任务计数 | | ✓ | | IT-DE-COUNT / COUNT | 1 |
| `completed_task_count` | INT | 已完成任务计数 | | ✓ | | IT-DE-COUNT / COUNT | task_status_code = TS-DONE |
| `overdue_task_count` | INT | 延期任务计数 | | ✓ | | IT-DE-COUNT / COUNT | 状态/日期谓词 |
| `high_risk_task_count` | INT | 高风险任务计数 | | ✓ | | IT-DE-COUNT / COUNT | risk_level_code = RL-HIGH |
| `plan_cost_amount` | DECIMAL | 计划成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | plan_cost |
| `actual_cost_amount` | DECIMAL | 实际成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | actual_cost |
| `cost_variance_amount` | DECIMAL | 成本偏差 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | actual_cost - plan_cost |

---

## 5. `it_demo_dws_project_health`（SUMMARY / DWS）

粒度：一个项目在一个快照日期一行；粒度键：`project_health_id`；上游：已发布任务快照事实/项目维度/组织维度；物化：table / FULL。

```sql
create table it_demo_dws_project_health (
    project_health_id    varchar(32)  not null,
    snapshot_date        date         not null,
    project_code         varchar(32)  not null,
    project_name         varchar(200) not null,
    org_code             varchar(32)  not null,
    org_name             varchar(100) not null,
    project_manager_name varchar(100) not null,
    health_status_code   varchar(32)  not null,
    task_total           integer      not null,
    completed_task_count integer      not null,
    overdue_task_count   integer      not null,
    high_risk_task_count integer      not null,
    avg_progress_pct     numeric(7,4) not null,
    completion_rate      numeric(7,4) not null,
    overdue_rate         numeric(7,4) not null,
    plan_cost_amount     numeric(18,2) not null,
    actual_cost_amount   numeric(18,2) not null,
    cost_variance_amount numeric(18,2) not null,
    constraint pk_it_demo_dws_project_health primary key (project_health_id)
);

comment on table it_demo_dws_project_health is '项目健康汇总';
comment on column it_demo_dws_project_health.project_health_id is '项目健康快照标识（project_code+snapshot_date 哈希）';
comment on column it_demo_dws_project_health.snapshot_date is '快照日期';
comment on column it_demo_dws_project_health.project_code is '项目编码';
comment on column it_demo_dws_project_health.project_name is '项目名称';
comment on column it_demo_dws_project_health.org_code is '责任组织编码';
comment on column it_demo_dws_project_health.org_name is '责任组织名称';
comment on column it_demo_dws_project_health.project_manager_name is '项目负责人';
comment on column it_demo_dws_project_health.health_status_code is '项目健康状态（IT-RC-PROJECT-HEALTH）';
comment on column it_demo_dws_project_health.task_total is '任务总数';
comment on column it_demo_dws_project_health.completed_task_count is '已完成任务数';
comment on column it_demo_dws_project_health.overdue_task_count is '延期任务数';
comment on column it_demo_dws_project_health.high_risk_task_count is '高风险任务数';
comment on column it_demo_dws_project_health.avg_progress_pct is '平均任务进度';
comment on column it_demo_dws_project_health.completion_rate is '任务完成率';
comment on column it_demo_dws_project_health.overdue_rate is '任务延期率';
comment on column it_demo_dws_project_health.plan_cost_amount is '计划成本';
comment on column it_demo_dws_project_health.actual_cost_amount is '实际成本';
comment on column it_demo_dws_project_health.cost_variance_amount is '成本偏差';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `project_health_id` | STRING | 项目健康快照标识 | ✓ | ✓ | | IT-DE-RECORD-ID | hash(project_code \|\| snapshot_date) |
| `snapshot_date` | DATE | 快照日期 | | ✓ | | | snapshot_date |
| `project_code` | STRING | 项目编码 | | ✓ | | IT-DE-PROJECT-CODE | project_code |
| `project_name` | STRING | 项目名称 | | ✓ | | | lookup project_name |
| `org_code` | STRING | 责任组织编码 | | ✓ | | IT-DE-ORG-CODE | owner_org_code |
| `org_name` | STRING | 责任组织名称 | | ✓ | | | lookup org_name |
| `project_manager_name` | STRING | 项目负责人 | | ✓ | | | lookup manager_name |
| `health_status_code` | STRING | 项目健康状态 | | ✓ | | IT-DE-STATUS-CODE / IT-RC-PROJECT-HEALTH | 健康谓词 |
| `task_total` | INT | 任务总数 | | ✓ | | IT-DE-COUNT / COUNT | sum(task_count) |
| `completed_task_count` | INT | 已完成任务数 | | ✓ | | IT-DE-COUNT / COUNT | sum(completed_task_count) |
| `overdue_task_count` | INT | 延期任务数 | | ✓ | | IT-DE-COUNT / COUNT | sum(overdue_task_count) |
| `high_risk_task_count` | INT | 高风险任务数 | | ✓ | | IT-DE-COUNT / COUNT | sum(high_risk_task_count) |
| `avg_progress_pct` | DECIMAL | 平均任务进度 | | ✓ | | IT-DE-PERCENT / PERCENT | avg(progress_pct) |
| `completion_rate` | DECIMAL | 任务完成率 | | ✓ | | IT-DE-PERCENT / PERCENT | completed/task_total |
| `overdue_rate` | DECIMAL | 任务延期率 | | ✓ | | IT-DE-PERCENT / PERCENT | overdue/task_total |
| `plan_cost_amount` | DECIMAL | 计划成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | sum(plan_cost_amount) |
| `actual_cost_amount` | DECIMAL | 实际成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | sum(actual_cost_amount) |
| `cost_variance_amount` | DECIMAL | 成本偏差 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | actual - plan |

---

## 6. `it_demo_ads_project_overview`（APPLICATION / ADS）

粒度：一个项目在一个快照日期一行；粒度键：`project_overview_id`；上游：已发布项目健康汇总；物化：table / FULL。

```sql
create table it_demo_ads_project_overview (
    project_overview_id  varchar(32)  not null,
    snapshot_date        date         not null,
    project_code         varchar(32)  not null,
    project_name         varchar(200) not null,
    org_code             varchar(32)  not null,
    org_name             varchar(100) not null,
    project_manager_name varchar(100) not null,
    health_status_code   varchar(32)  not null,
    task_total           integer      not null,
    completed_task_count integer      not null,
    overdue_task_count   integer      not null,
    high_risk_task_count integer      not null,
    avg_progress_pct     numeric(7,4) not null,
    completion_rate      numeric(7,4) not null,
    overdue_rate         numeric(7,4) not null,
    plan_cost_amount     numeric(18,2) not null,
    actual_cost_amount   numeric(18,2) not null,
    cost_variance_amount numeric(18,2) not null,
    constraint pk_it_demo_ads_project_overview primary key (project_overview_id)
);

comment on table it_demo_ads_project_overview is '项目健康概览（BI/API/数据产品消费入口）';
comment on column it_demo_ads_project_overview.project_overview_id is '项目概览标识';
comment on column it_demo_ads_project_overview.snapshot_date is '快照日期';
comment on column it_demo_ads_project_overview.project_code is '项目编码';
comment on column it_demo_ads_project_overview.project_name is '项目名称';
comment on column it_demo_ads_project_overview.org_code is '责任组织编码';
comment on column it_demo_ads_project_overview.org_name is '责任组织名称';
comment on column it_demo_ads_project_overview.project_manager_name is '项目负责人';
comment on column it_demo_ads_project_overview.health_status_code is '项目健康状态';
comment on column it_demo_ads_project_overview.task_total is '任务总数';
comment on column it_demo_ads_project_overview.completed_task_count is '已完成任务数';
comment on column it_demo_ads_project_overview.overdue_task_count is '延期任务数';
comment on column it_demo_ads_project_overview.high_risk_task_count is '高风险任务数';
comment on column it_demo_ads_project_overview.avg_progress_pct is '平均任务进度';
comment on column it_demo_ads_project_overview.completion_rate is '任务完成率';
comment on column it_demo_ads_project_overview.overdue_rate is '任务延期率';
comment on column it_demo_ads_project_overview.plan_cost_amount is '计划成本';
comment on column it_demo_ads_project_overview.actual_cost_amount is '实际成本';
comment on column it_demo_ads_project_overview.cost_variance_amount is '成本偏差';
```

| 字段名称 | 页面类型 | 字段显示名 | 主键 | 非空 | 维度属性编码 | 数据元/单位 | 来源/表达式 |
|---|---|---|---|---|---|---|---|
| `project_overview_id` | STRING | 项目概览标识 | ✓ | ✓ | | IT-DE-RECORD-ID | project_health_id |
| `snapshot_date` | DATE | 快照日期 | | ✓ | | | snapshot_date |
| `project_code` | STRING | 项目编码 | | ✓ | | IT-DE-PROJECT-CODE | project_code |
| `project_name` | STRING | 项目名称 | | ✓ | | | project_name |
| `org_code` | STRING | 责任组织编码 | | ✓ | | IT-DE-ORG-CODE | org_code |
| `org_name` | STRING | 责任组织名称 | | ✓ | | | org_name |
| `project_manager_name` | STRING | 项目负责人 | | ✓ | | | project_manager_name |
| `health_status_code` | STRING | 项目健康状态 | | ✓ | | IT-DE-STATUS-CODE / IT-RC-PROJECT-HEALTH | health_status_code |
| `task_total` | INT | 任务总数 | | ✓ | | IT-DE-COUNT / COUNT | task_total |
| `completed_task_count` | INT | 已完成任务数 | | ✓ | | IT-DE-COUNT / COUNT | completed_task_count |
| `overdue_task_count` | INT | 延期任务数 | | ✓ | | IT-DE-COUNT / COUNT | overdue_task_count |
| `high_risk_task_count` | INT | 高风险任务数 | | ✓ | | IT-DE-COUNT / COUNT | high_risk_task_count |
| `avg_progress_pct` | DECIMAL | 平均任务进度 | | ✓ | | IT-DE-PERCENT / PERCENT | avg_progress_pct |
| `completion_rate` | DECIMAL | 任务完成率 | | ✓ | | IT-DE-PERCENT / PERCENT | completion_rate |
| `overdue_rate` | DECIMAL | 任务延期率 | | ✓ | | IT-DE-PERCENT / PERCENT | overdue_rate |
| `plan_cost_amount` | DECIMAL | 计划成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | plan_cost_amount |
| `actual_cost_amount` | DECIMAL | 实际成本 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | actual_cost_amount |
| `cost_variance_amount` | DECIMAL | 成本偏差 | | ✓ | | IT-DE-AMOUNT-CNY / CNY | cost_variance_amount |

## 质量规则对应字段

| 规则 | 涉及字段 |
|---|---|
| IT-QR-ORG-CODE-UNIQUE | `it_demo_dwd_dim_org.org_code` |
| IT-QR-PROJECT-ORG-REF | `it_demo_dwd_dim_project.owner_org_code` → `org_code` |
| IT-QR-TASK-SNAPSHOT-UNIQUE | `it_demo_dwd_fct_task_snapshot.task_snapshot_id` |
| IT-QR-TASK-PROJECT-REF | `it_demo_dwd_fct_task_snapshot.project_code` → `project_code` |
| IT-QR-TASK-STATUS | `task_status_code`（白名单） |
| IT-QR-RISK-LEVEL | `risk_level_code`（白名单） |
| IT-QR-PROGRESS-RANGE | `progress_pct` |
| IT-QR-COST-NONNEGATIVE | `plan_cost_amount` / `actual_cost_amount` |
| IT-QR-PLAN-DATE-ORDER | `plan_start_date` ≤ `plan_end_date` |
| IT-QR-SNAPSHOT-FRESHNESS | `snapshot_date` 最新快照周期 |
