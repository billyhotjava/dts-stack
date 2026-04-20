# Finance dbt Model

标准 dbt 项目，现场部署使用。

## 目录结构

```text
dbt_model/
├── dbt_project.yml          # dbt 项目配置（profile=dts）
├── model-governance.md      # 建模分层与命名治理规则
├── macros/                  # 通用宏
│   ├── nullif_placeholder.sql
│   └── parse_numeric_safe.sql
├── models/
│   ├── fin_sources.yml      # ODS 源表声明
│   ├── fin_stg.yml          # STG 层字段与测试
│   ├── fin_schema.yml       # DWD/DWS/ADS 层字段与测试
│   ├── stg/                 # 标准化层（view）
│   ├── dwd/                 # 明细 + 维度（table）
│   ├── dws/                 # 主题汇总（table）
│   └── ads/                 # 看板 KPI（table）
└── ods_ddl/
    └── create_finance_tables.sql  # ODS 建表 DDL（参考用）
```

## 分层约定

```text
ODS → STG → DWD → DWS → ADS
```

- `dwd/*`、`dws/*`、`ads/*` 禁止直接 `source(...)`，必须经 `stg_fin__*` 进入
- 详细规则见 `model-governance.md`

## 部署流程

1. 在目标库执行 `ods_ddl/create_finance_tables.sql`（如 ODS 已建则跳过）
2. 确认 `~/.dbt/profiles.yml` 中 `dts` profile 指向现场库
3. 在项目根目录执行：

```bash
dbt deps
dbt run --profile dts
dbt test --profile dts
```

## 模型清单

- STG (4)：`stg_fin__own_fund` / `stg_fin__project_fund` / `stg_fin__aux_balance` / `stg_fin__aux_balance_personal`
- DWD (8)：4 张维度 + 4 张业务明细
- DWS (4)：自有资金年度 / 项目经费汇总 / 辅助余额按部门 / 个人辅助余额按部门
- ADS (4)：对应四主题的看板 KPI
