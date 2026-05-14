# Thales Metro v1

交付物：

- `dbt_model/`：可检查的 dbt 项目目录。
- `thales-metro-dbt-model.zip`：DTS UI 导入包。
- `build-deploy.sh`：重新打包脚本。

导入前先在目标库执行：

```bash
dbt_model/ods_ddl/ods_create_tables.sql
```

导入演示数据可选执行：

```bash
dbt_model/ods_ddl/ods_seed_demo_data.sql
```
