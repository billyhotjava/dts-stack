# T04: 现场冒烟 Runbook

**优先级**: P1
**状态**: DONE
**依赖**: T01, T02, T03

## 目标

提供现场可执行的 OpenMetadata 采集冒烟步骤，覆盖容器、API、脚本、平台查询。

## 范围

- `docker compose ps` 检查。
- OpenMetadata `/api/v1/system/version` 或等价 API 检查。
- ingestion 脚本运行和日志检查。
- catalog 详情、血缘、质量页面验证。
- 失败排查表。

## 完成标准

- [x] runbook 不依赖开发者口头说明。
- [x] 每一步有预期结果和异常处理。
- [x] 证据文件位置写入 `it/README.md`。
- [x] 可被现场运维重复执行。

## Runbook

### 1. 服务状态

执行：

```sh
docker compose -f docker-compose-app.yml ps dts-airflow-webserver dts-airflow-scheduler dts-airflow-triggerer dts-openmetadata dts-openmetadata-ingestion dts-platform dts-ingestion
```

预期：

- `dts-openmetadata` 为 `Up`。
- `dts-airflow-webserver` 为 `Up ... (healthy)`。
- `dts-platform` 和 `dts-ingestion` 为 `Up`。
- `dts-openmetadata-ingestion` 是 one-shot 容器，正常情况下可不常驻；用 `ps -a` 看最近退出码。

### 2. OpenMetadata API

执行：

```sh
curl -sS http://127.0.0.1:18585/api/v1/system/version
```

预期返回版本号，例如当前环境为 `1.11.5`。

### 3. Airflow 依赖

执行：

```sh
docker compose -f docker-compose-app.yml exec -T dts-airflow-webserver python -c "import importlib.metadata as m; print(m.version('openmetadata-managed-apis')); print(m.version('openmetadata-ingestion'))"
docker compose -f docker-compose-app.yml exec -T dts-airflow-webserver airflow dags list
```

预期：

- 两个 Python 包版本与 OpenMetadata 版本线一致。
- `airflow dags list` 能列出 DAG。

### 4. 认证策略

若 OpenMetadata server 启用了认证，必须先准备 `ingestion-bot` 或等效 bot token，并写入 `.env`：

```sh
DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN=<redacted>
DTS_OPENMETADATA_AUTH_TOKEN=<redacted>
DTS_OPENMETADATA_ALLOW_NO_AUTH=false
```

若服务端明确无认证，才允许：

```sh
DTS_OPENMETADATA_ALLOW_NO_AUTH=true
```

注意：OpenMetadata ingestion 1.11.x 的配置枚举不支持 `authProvider: no-auth`。本仓库脚本在 no-auth 模式下保留 `authProvider: openmetadata` 并写空 JWT；如果服务端仍要求 token，会以 401 失败，这是预期保护。

### 5. PostgreSQL 元数据采集冒烟

约束：

- `DTS_OPENMETADATA_INGEST_DATABASE` 必须是数仓/分析侧数据库，默认建议使用 `biadmin` 或现场实际数仓库。
- 禁止把 `dts_platform`、`dts_admin`、`dts_common`、`dts_analytics` 等平台业务/内部库作为采集目标。
- 若误配到禁止库，脚本应在认证前返回退出码 2，并输出 `database '<name>' is forbidden for metadata ingestion`。
- 若 OpenMetadata 中已存在历史误采的 `hive.dts_platform.*` 等对象，属于历史污染数据；上线验收前必须通过 OpenMetadata UI/API 清理，不能继续作为合规样例或验收依据。

执行：

```sh
docker compose -f docker-compose-app.yml run --rm --entrypoint /bin/sh dts-openmetadata-ingestion -c /opt/openmetadata/ingestion/run-postgres-ingestion.sh
```

预期：

- 日志出现 `auth_mode=token`。
- `Workflow Postgres Summary` 和 `Workflow OpenMetadata Summary` 均为 `Success %: 100.0`。
- 可通过 OpenMetadata API 查到数仓/分析库样例表，FQN 形如 `hive.${DTS_OPENMETADATA_INGEST_DATABASE}.public.<table>`。

### 6. dbt 质量/血缘采集冒烟

执行：

```sh
docker compose -f docker-compose-app.yml run --rm dts-openmetadata-ingestion
```

预期：

- 若 `services/dts-dbt/target/manifest.json` 或 `run_results.json` 缺失，脚本输出 `status=skipped reason=dbt_artifacts_not_found` 并退出 0。
- 若 artifacts 存在且 token 有效，脚本输出 `Workflow dbt Summary` 与 `Workflow OpenMetadata Summary`。
- 若日志出现大量 `Unable to find the table ... in OpenMetadata`，说明 dbt manifest 中的 FQN 与已采集表不一致，需要先采集对应数据库服务或调整 FQN/service/database/schema 口径。
- dbt artifacts 中引用的 source/model 也必须遵守同一范围约束，不得引用平台业务/内部库表作为数仓分析平台采集对象；若引用禁止库，脚本会输出 `dbt artifacts reference forbidden platform business/internal databases` 并返回退出码 2。

### 7. 平台侧验证

使用已登录账号进入平台：

- 元数据采集页：检查“数据来源”标签，期望显示 `OpenMetadata` 或 `本地 Catalog`。
- 质量页：检查“当前数据来源”和缺失原因。
- 若 OpenMetadata 未命中，页面应显示 fallback reason，不应表现为空白成功。

未登录直接访问平台 API 返回 401 属于预期。

### 8. 常见异常

| 现象 | 处理 |
|---|---|
| `OPENMETADATA_AUTH_TOKEN empty; failing ingestion` | 当前环境未声明 token，也未显式 no-auth；补 token 或确认后设置 no-auth。 |
| `Invalid parameter value ... authProvider` | 不要使用 `authProvider: no-auth`；按本仓库脚本生成配置。 |
| `Not Authorized! Token not present` | 服务端启用了认证；配置有效 bot token。 |
| `pg_stat_statements does not exist` | PostgreSQL query usage 采集缺扩展；当前为非强制项，元数据采集可继续。 |
| `Unable to find the table ... in OpenMetadata` | dbt manifest 的 FQN 与已采集数据库表不一致；先采集对应库或调整 service/database/schema。 |
