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

本版 dbt 包已补齐 DTS CSV 训练快照契约模型：

- `metro_dwd_lstm_training_contract`：训练前门禁，输出 `passed` / `warning` / `blocked`。
- `metro_dwd_lstm_training_snapshot`：面向 metro-stack 的训练快照明细，可由 DTS 导出为 CSV 训练快照包。
- ODS 初始化 SQL 已包含 manifest、schema、quality、lineage 四类训练快照契约表。

业务 App 端口约定：

- App-Pack / 行业业务 App 本地演示端口统一使用 `50000+`。
- Metro AppPack 前端默认 `50073`，后端默认 `50080`。
- 避免占用 DTS 平台、PKI、Airflow、Traefik 等平台端口。
