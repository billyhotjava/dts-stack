# Sprint-19 集成测试证据

## 当前基线

| 检查项 | 当前观察 | 结论 |
|---|---|---|
| OpenMetadata server | `dts-openmetadata` 已启动 | 已接入，需要继续验证 API 与认证 |
| OpenMetadata ingestion | token 模式 one-shot 已执行；PostgreSQL 采集新增业务/内部库阻断 | PostgreSQL 与 dbt 冒烟通过，原 `dts_platform` 样例不再作为合规验收依据 |
| ingestion 日志 | token 缺失会 fail fast；token 模式输出 `auth_mode=token` | 已修复静默 skip |
| FQN pattern | `.env` 中坏值已迁移为 `{service}.{database}.{table}` | 已修复 |
| lineage 注册 | 从 tableMapping 派生 source/target streams | 已修复空 streams 跳过 |
| 目标库配置解析 | 支持 nested `connection`、`jdbcUrl`、顶层字段 | 已修复 |

## 自动化验证矩阵

| 场景 | 覆盖点 | 状态 |
|---|---|---|
| 配置生成 | `init.sh` 默认 FQN pattern、旧 `.env` 迁移、坏 pattern 诊断 | AUTO PASS |
| OpenMetadata health | server API、token/no-auth、容器状态 | MANUAL PASS |
| 连接配置解析 | nested `connection`、`jdbcUrl`、顶层字段、多数据源类型 | AUTO PASS |
| service/pipeline 创建 | create/ensure/trigger 结果和错误处理 | AUTO PASS |
| 血缘注册 | reader/writer/tableMapping 生成真实 source/target streams | AUTO PASS |
| 平台读路径 | 元数据、血缘、质量 FQN 命中和 fallback reason | AUTO PASS |
| 前端状态 | OpenMetadata 命中、未命中、回退、失败展示 | BUILD PASS |
| 采集范围约束 | `DTS_OPENMETADATA_INGEST_DATABASE` 禁止配置到平台业务/内部库 | AUTO PASS |

## 预期命令

| 命令 | 目的 | 结果 |
|---|---|---|
| `docker compose -f docker-compose-app.yml ps dts-openmetadata dts-openmetadata-ingestion dts-platform dts-ingestion` | 检查运行时服务状态 | PASS（OpenMetadata server、platform、ingestion-service 运行中） |
| `docker compose -f docker-compose-app.yml ps -a dts-openmetadata-ingestion` | 检查 ingestion 容器历史状态 | BASELINE：Exited (0)，尚未用本次脚本重跑 |
| `docker logs dts-openmetadata-ingestion --tail 30` | 检查 ingestion 是否跳过或失败 | BASELINE：旧脚本因 token 为空 skip |
| `curl -sS http://127.0.0.1:18585/api/v1/system/version` | 检查 OpenMetadata API | PASS：`1.11.5` |
| `docker compose -f docker-compose-app.yml exec -T dts-airflow-webserver python -c "import importlib.metadata as m; ..."` | 检查 Airflow OpenMetadata 依赖 | PASS：`openmetadata-managed-apis=1.11.5.0`，`openmetadata-ingestion=1.11.5.0` |
| `docker compose -f docker-compose-app.yml exec -T dts-airflow-webserver airflow dags list` | 检查 Airflow DAG 列表 | PASS |
| `docker compose -f docker-compose-app.yml run --rm --entrypoint /bin/sh dts-openmetadata-ingestion -c /opt/openmetadata/ingestion/run-postgres-ingestion.sh` | PostgreSQL 元数据采集 | PASS：token 模式，workflow success 100%；目标库必须为 `DTS_OPENMETADATA_INGEST_DATABASE` 指定的数仓/分析库 |
| `OPENMETADATA_INGEST_DATABASE=dts_platform ... run-postgres-ingestion.sh` | 禁止平台业务库采集 | PASS：退出码 2，未进入认证和采集流程 |
| `docker compose -f docker-compose-app.yml run --rm dts-openmetadata-ingestion` | dbt 元数据/质量采集 | PASS：token 模式，workflow success 100%；存在 FQN 不一致告警 |
| dbt artifacts 禁止库预检 | 禁止间接采集平台业务库 | PASS：manifest/catalog 中出现禁止库会退出码 2，未发送 OpenMetadata ingestion |
| OpenMetadata API 查询数仓/分析库样例表 | 样例表可见性 | 需使用 `hive.${DTS_OPENMETADATA_INGEST_DATABASE}.public.<table>`；`hive.dts_platform.*` 不再作为合规样例 |
| `curl http://127.0.0.1:18082/api/catalog/metadata/tables?...` | 平台 API 未登录访问 | PASS：401，符合认证预期 |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=OpenMetadataAdapterTest test` | ingestion 侧单测 | PASS |
| `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -Dtest=OpenMetadataServiceTest test`（`source/dts-platform`） | platform 侧单测 | PASS |
| `bash -n init.sh` | 初始化脚本语法 | PASS |
| `sh -n services/dts-openmetadata/ingestion/run-dbt-ingestion.sh` | dbt ingestion 脚本语法 | PASS |
| `sh -n services/dts-openmetadata/ingestion/run-postgres-ingestion.sh` | postgres ingestion 脚本语法 | PASS |
| `docker compose -f docker-compose-app.yml config --quiet` | app compose 配置解析 | PASS |
| `docker compose -f docker-compose.dev.yml config --quiet` | dev compose 配置解析 | PASS |
| `docker compose -f docker-compose.legacy.yml config --quiet` | legacy compose 配置解析 | PASS（存在原有 `DTS_DBT_HOST_PROJECT_DIR` 未设置和 `version` obsolete 警告） |
| `pnpm build`（`source/dts-platform-webapp`） | 前端构建检查 | PASS |

## 自动化证据（2026-04-29）

| 范围 | 结果 |
|---|---|
| `init.sh` OpenMetadata FQN 默认值 | 修复 Bash 参数展开截断；坏值 `{service` 会迁移为 `{service}.{database}.{table}` |
| compose OpenMetadata 环境变量 | app/dev/legacy 均显式传递 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 和 `DTS_OPENMETADATA_ALLOW_NO_AUTH` |
| ingestion 脚本 | token 缺失且未显式 no-auth 时返回失败，不再静默 skip |
| OpenMetadata API | 当前运行时 `http://127.0.0.1:18585/api/v1/system/version` 返回 `1.11.5` |
| `OpenMetadataAdapterTest` | 覆盖 nested JDBC writer 配置解析、service connection config、显式 source/target 血缘映射、owner/domain/tags `not_supported` 回传 |
| `OpenMetadataServiceTest` | 覆盖坏 FQN pattern `{service` 自动回退并避免错误 lookup；覆盖 metadata source/fallback reason 透传 |
| PostgreSQL one-shot ingestion | 使用当前 `ingestion-bot` token（未落盘、未记录明文）完成；Postgres/OpenMetadata workflow success 100% |
| dbt ingestion | 使用当前 `ingestion-bot` token 完成；workflow success 100%，但 dbt manifest 中部分 `hive.biadmin.public.*` 表未先采集，产生 table-not-found 告警 |
| 采集范围防护 | PostgreSQL 配置层改为 `DTS_OPENMETADATA_INGEST_DATABASE`，默认 `biadmin`；PostgreSQL/dbt 脚本对 `dts_platform`、`dts_admin`、`dts_common`、`dts_analytics` 等禁止库返回退出码 2 |
| 前端构建 | `source/dts-platform-webapp/pnpm build` 通过；仅有既有 chunk size 和 Browserslist 过期提示 |

## 现场问题与结论（2026-04-29）

| 问题 | 结论 |
|---|---|
| `authProvider: no-auth` | OpenMetadata ingestion 1.11.5 不支持该枚举；脚本已改为显式 no-auth 时保留 `authProvider: openmetadata` 并写空 JWT。 |
| 当前服务端 no-auth | 当前 OpenMetadata server 仍要求 token；无 token 会返回 `Not Authorized! Token not present`。 |
| PostgreSQL `pg_stat_statements` | query usage 检查会告警，但为非 mandatory，元数据采集成功。 |
| 采集范围 | 平台业务/内部库不得进入数仓分析平台采集范围；原 `hive.dts_platform.public.*` 冒烟样例已废弃，只能用数仓/分析库样例验收。 |
| dbt FQN | dbt artifacts 使用 `hive.biadmin.public.*`；PostgreSQL ingestion 默认目标已切换为 `biadmin`，上线前仍需确认 dbt artifacts 与 OpenMetadata service/database/schema 口径一致。 |
| owner/domain/tags | 当前显式返回 `not_supported`，不静默写入或丢弃；表级写入作为后续增强。 |

## 手工验收步骤

### Step 1 - 配置验收

1. 重新执行初始化或读取当前 `.env`。
2. 确认 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 为完整 pattern。
3. 明确当前环境使用 token 还是 no-auth。
4. 检查 compose 中 server、ingestion、platform、ingestion-service 的配置一致。

### Step 2 - 接入任务验收

1. 创建数据库接入任务，选择至少 2 张源表。
2. 执行任务并确认 ODS 表生成。
3. 检查 `dts-ingestion` 日志中 OpenMetadata service/pipeline/lineage 结果。
4. 在 OpenMetadata API 或 UI 中检查 service、table、pipeline、lineage。

### Step 3 - 平台查询验收

1. 打开 catalog 数据集详情。
2. 检查元数据来源标识是否为 OpenMetadata 或本地 catalog。
3. 打开血缘和质量页，确认 FQN lookup 日志和结果一致。
4. 人为关闭 OpenMetadata 或使用不存在 FQN，确认 UI/API 显示 fallback reason。

## 产物留存

- `baseline-openmetadata-status.md` - 修复前容器和 API 基线。
- `fqn-pattern-migration.md` - `.env` 修复前后对比。
- `lineage-registration-sample.json` - 血缘注册请求与响应样例。
- `openmetadata-api-smoke.md` - OpenMetadata API 冒烟结果。
- `catalog-fallback-evidence.md` - 平台回退展示证据。
