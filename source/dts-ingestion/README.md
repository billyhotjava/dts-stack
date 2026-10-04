# dts-ingestion

本模块承载数据入湖执行侧能力，包括 JDBC/file Addax 任务、API 出站拉取执行器、Airflow DAG 生成与执行状态同步。

## API 入湖

Sprint-38 后，API 入湖采用 Java 执行器：

- Airflow DAG 只做瘦触发和轮询。
- API 凭据由 dts-platform 加密存储，dts-ingestion 运行时通过服务间接口读取。
- API raw landing 写入 ODS，并用 `dts_api_ingestion_checkpoint` 维护增量游标。
- 运行时默认值由 `dts.ingestion.api.*` / `DTS_INGESTION_API_*` 管理。

现场文档：

- `../../worklog/v2.2.3/sprint-38-202606/assets/api-ingestion-ops-guide.md`
- `../../worklog/v2.2.3/sprint-38-202606/assets/api-properties-reference.md`
- `../../worklog/v2.2.3/sprint-38-202606/assets/api-upgrade-checklist.md`

关键验证脚本：

```bash
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-secret-security.sh
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/api-dag-migration.sh
RUN_LIVE=1 worklog/v2.2.3/sprint-38-202606/it/scripts/jdbc-file-regression.sh
```
