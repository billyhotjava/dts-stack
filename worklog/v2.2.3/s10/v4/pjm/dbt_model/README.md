# Project Management dbt Model (v4)

标准 dbt 项目，现场部署使用。

## 目录结构

```text
dbt_model/
├── dbt_project.yml              # dbt 项目配置（profile=dts）
├── model-governance.md          # 建模分层与命名治理规则
├── macros/                      # 通用宏
│   ├── ensure_date_helpers.sql
│   ├── get_custom_schema.sql
│   ├── nullif_placeholder.sql
│   ├── parse_date_safe.sql
│   └── parse_numeric_safe.sql
├── models/
│   ├── pm_sources_v2.yml        # ODS 源表声明
│   ├── pm_stg_v2.yml            # STG 层字段与测试
│   ├── pm_schema_v2.yml         # DWD/DWS/ADS 层字段与测试
│   ├── stg/                     # 标准化层（view）
│   ├── dwd/                     # 明细 + 维度（table）
│   ├── dws/                     # 主题汇总（table）
│   └── ads/                     # 看板 KPI（table）
└── ods_ddl/
    ├── ods_create_tables_v2.sql # ODS 建表 DDL
    ├── ods-field-mapping/       # Excel 字段到 ODS 列的映射 CSV
    └── ods-verify/              # 各业务表字段口径核对文档
```

## 分层约定

```text
ODS → STG → DWD → DWS → ADS
```

- `dwd/*`、`dws/*`、`ads/*` 禁止直接 `source(...)`，必须经 `stg_pm__*` 进入
- STG 强制保留来源元数据：`source_row_id / source_table / source_system / source_file / source_sheet_name / source_batch_id / source_row_num / imported_at`
- ODS 物理表使用入湖统一技术字段 `_dts_source_system / _dts_import_time`；STG 负责映射为下游标准字段 `source_system / imported_at`
- 详细规则见 `model-governance.md`

## 部署流程

1. 在目标库执行 `ods_ddl/ods_create_tables_v2.sql`（如 ODS 已建则跳过）
2. 确认 `~/.dbt/profiles.yml` 中 `dts` profile 指向现场库
3. 在项目根目录执行：

```bash
dbt deps
dbt run --profile dts
dbt test --profile dts
```

## MySQL 客户源系统模拟

`ods_ddl/mysql/` 中的 10 张测试源表都包含两个客户应用业务字段：

- `classification varchar(32) not null`：仅允许
  `PUBLIC / INTERNAL / SECRET / CONFIDENTIAL`
- `owner_dept varchar(64) not null`：所属部门编码，格式
  `^[A-Za-z0-9._-]{1,64}$`

它们与 DTS 入湖时追加的 `_dts_*` 技术字段不同，JDBC 直连和
`tests/dbapi` API 都应原样采集。

全新建库按以下顺序执行：

1. `mysql/01_create_ods_source_tables.sql`
2. `mysql/02_seed_golden.sql`
3. `mysql/03_verify_golden.sql`

已有测试库升级按以下顺序执行：

1. 备份 `dts_pjm_test`
2. `mysql/04_add_source_security_columns.sql`
3. `mysql/05_backfill_source_security_values.sql`
4. `mysql/06_enforce_source_security_columns.sql`
5. `mysql/03_verify_golden.sql`

该升级是前向迁移；删除这两列会丢失安全归属数据，不提供自动
`DROP COLUMN` 回滚。需要回退时恢复升级前备份。

## 当前纳入语义链路的 ODS 表

- `ods_project_subject_domain_v2`
- `ods_quality_issue_v2`
- `ods_tech_state_v2`
- `ods_risk_info_v2`
- `ods_budget_v2`

对应 STG 表：

- `stg_pm__project_subject_domain_v2`
- `stg_pm__quality_issue_v2`
- `stg_pm__tech_state_v2`
- `stg_pm__risk_info_v2`
- `stg_pm__budget_v2`
