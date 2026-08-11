# Finance dbt Model

面向研究所财务管理域的标准 dbt 项目，可独立运行，也可打包后从 DTS“逆向建模”页面导入。

## 目录结构

```text
dbt_model/
├── dbt_project.yml          # dbt 项目配置（profile=dts，数据域=FINANCE）
├── model-governance.md      # 建模分层、编码与规划关系规则
├── reverse-modeling-import-guide.md # DTS 逆向建模导入手册
├── models/
│   ├── fin_sources.yml      # ODS 源表声明
│   ├── fin_stg.yml          # STG 层字段、契约与测试
│   ├── fin_dimensions.yml   # 维度/映射表字段、契约与测试
│   ├── fin_dwd.yml          # DWD 事实表字段、契约与测试
│   ├── fin_dws.yml          # DWS 汇总表字段、契约与测试
│   ├── fin_ads.yml          # ADS 应用表字段、契约与测试
│   ├── stg/                 # 标准化层（view）
│   ├── dwd/                 # 明细事实 + 维度（table）
│   ├── dws/                 # 主题汇总（table）
│   └── ads/                 # 应用 KPI（table）
└── ods_ddl/
    └── create_finance_tables.sql  # ODS 建表 DDL（参考用）
```

## 分层约定

```text
ODS → STG → DWD → DWS → ADS
```

- 所有模型继承 `domain:FINANCE` 标签，逆向建模时映射到“财务管理域”。
- `dwd/*`、`dws/*`、`ads/*` 禁止直接 `source(...)`，必须经 `stg_fin__*` 进入。
- 24 个 dbt 模型均启用列级契约；详细规则见 `model-governance.md`。

## 运行与测试

1. 在目标库执行 `ods_ddl/create_finance_tables.sql`（ODS 已存在时跳过）。
2. 确认 `~/.dbt/profiles.yml` 中 `dts` profile 指向目标库。
3. 如 ODS 不在 `public` schema，设置 `DTS_FIN_ODS_SCHEMA`。
4. 在本目录执行：

```bash
export DTS_FIN_ODS_SCHEMA=public
dbt build --profile dts
```

## 生成逆向建模导入包

在 `finance/` 目录执行：

```bash
./build-deploy.sh
```

输出文件为 `finance-dbt-model-reverse-import.zip`。页面导入步骤及业务过程映射见 `reverse-modeling-import-guide.md`。

## 模型清单

- STG（4）：4 个源表标准化视图；导入时作为技术节点，不生成业务模型。
- DWD（12）：8 张维度/映射表 + 4 张业务明细事实表。
- DWS（4）：自有资金年度、项目经费、合同辅助余额、个人辅助余额汇总。
- ADS（4）：对应四个财务主题的应用 KPI 表。

逆向建模共识别 20 个业务模型；4 个 STG 仅用于保留依赖链路。
