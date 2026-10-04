# PJM 预算模型包黄金夹具

此目录保存 Sprint-70 共用的单文件模型包夹具，不是 ZIP 或目录包。

## 真值边界

- dbt 图、配置、字段说明与测试来自
  `worklog/v2.2.3/s10/v4/pjm/dbt_model/target/manifest.json`。
- SQL 正文优先读取同一 dbt 项目的 `models/**/*.sql`，其次才使用
  manifest 的 `compiled_code`、`raw_code`、`raw_sql`。
- `catalog.json` 当前缺失，因此黄金包保留 `CATALOG_MISSING` issue，不能宣称字段类型已验证。
- 业务类型、粒度、来源、消费场景等不可从 SQL 或名称可靠推断的内容，只来自
  `semantic-overrides.json`。
- 不包含 ODS 数据行、`target/`、`logs/` 或 dbt 运行成功声明。

## 生成

生成器输入固定选取以下 canonical/technical 链：

```text
stg_pm__budget_v2                  TECHNICAL_ONLY
biz_dwd_budget_v2                  FACT@DWD + DBT_BACKED
biz_dws_budget_v2                  SUMMARY@DWS + DBT_BACKED
biz_ads_budget_kpi_v2              APPLICATION@ADS + DBT_BACKED
biz_ads_budget_derived_v2          APPLICATION@ADS + DBT_BACKED
dim_node_type_v2                   DIMENSION@DWD + DBT_BACKED
```

repo-native main：

```text
com.yuzhi.dts.platform.service.modeling.imports.converter.DtsModelPackageGenerator
```

参数：

```text
--manifest worklog/v2.2.3/s10/v4/pjm/dbt_model/target/manifest.json
--project-root worklog/v2.2.3/s10/v4/pjm/dbt_model
--overrides worklog/v2.2.3/sprint-70-202607-dbt-model-package-import/fixtures/pjm-budget/semantic-overrides.json
--package-id pjm-budget-v1
--select model.pm_analytics_v3.stg_pm__budget_v2,model.pm_analytics_v3.biz_dwd_budget_v2,model.pm_analytics_v3.biz_dws_budget_v2,model.pm_analytics_v3.biz_ads_budget_kpi_v2,model.pm_analytics_v3.biz_ads_budget_derived_v2,model.pm_analytics_v3.dim_node_type_v2
--output worklog/v2.2.3/sprint-70-202607-dbt-model-package-import/fixtures/pjm-budget/dts-model-package.json
```

追加 `--validate-only` 会完成转换、checksum 与契约校验，但不写输出文件。两次相同输入应生成
相同 `packageChecksum`；生成时间、绝对路径和环境 UUID 均不进入包。
