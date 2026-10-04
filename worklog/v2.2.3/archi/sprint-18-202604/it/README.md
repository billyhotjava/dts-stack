# Sprint-18 集成测试证据

## 自动化验证矩阵

| 场景 | 覆盖点 | 状态 |
|---|---|---|
| 数据库全量接入 | 自动建 ODS、源字段复制、`_dts_*` 技术字段、batch 一致性 | AUTO PASS |
| 数据库增量接入 | watermark、批次字段、重复运行、checkpoint 审计 | AUTO PARTIAL |
| Excel 上传接入 | sheet/header/schema 预检、文件血缘字段、坏行记录 | AUTO PASS |
| CSV 上传接入 | 引号/逗号/换行解析、schema 确认、行号与 hash | AUTO PASS |
| dbt source 刷新 | ODS source 表级/列级元数据生成 | AUTO PASS |
| 前端向导 | ODS 不提供业务计算配置，stg 边界提示 | AUTO PASS |

## 自动化证据（2026-04-27）

| 命令 | 结果 |
|---|---|
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -DskipTests compile` | PASS |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=IngestionTaskMapperTest test` | PASS |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=StagingTableServiceTest,IngestionExecutionQueryServiceTest test` | PASS |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile` | PASS |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -Dtest=DbtSourceServiceTest test` | PASS |
| `pnpm exec tsc --noEmit`（`source/dts-platform-webapp`） | PASS |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=CsvParseServiceTest,StagingTableServiceTest,IngestionPreCheckResourceTest test` | PASS（2026-04-30） |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -DskipTests compile` | PASS（2026-04-30） |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=IngestionTaskMapperTest,IngestionExecutionQueryServiceTest test` | PASS（2026-04-30） |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-platform/pom.xml -Dtest=DbtSourceServiceTest test` | PASS（2026-04-30） |
| `docker exec v223-dts-ingestion-1 sh -lc 'mvn -q -DskipTests compile'` | PASS（app 模式，2026-04-30） |
| `docker exec dts-dbt dbt parse --project-dir /opt/dbt --profiles-dir /opt/dbt/profiles` | PASS（app 模式，2026-04-30） |
| `pnpm --dir tests/web-e2e exec playwright test specs/biz/elt-ingestion-center-smoke.spec.ts specs/biz/elt-ingestion-edge-regression.spec.ts --project=chromium --config=playwright.config.ts` | PASS（app web，2026-04-30） |

## 现场证据（2026-04-30）

| 证据 | 路径 | 结论 |
|---|---|---|
| app 模式 CSV 直连预检 | `worklog/v2.2.3/sprint-18-202604/it/evidence/20260430-app-ingestion-direct/` | 上传、任务创建、CSV parse、暂存表、坏行摘要、staging metadata 均通过；`builtInErrorCount=0` |
| ODS 技术字段 SQL 抽样 | `worklog/v2.2.3/sprint-18-202604/it/evidence/20260430-app-ods-sql/` | `ods_risk_info_v2` 存在 `_dts_*` 技术字段，样本含 batch、execution、task、file hash、row number |
| execution 反查 | `worklog/v2.2.3/sprint-18-202604/it/evidence/20260430-app-ods-sql/04-execution-trace.json` | task、execution、batch、Airflow run id 可串联 |
| dbt parse | `worklog/v2.2.3/sprint-18-202604/it/evidence/20260430-app-dbt/` | `dbt parse` exit code 为 0 |
| 平台前端 E2E | `worklog/v2.2.3/sprint-18-202604/it/evidence/20260430-web-e2e/html-app-expert/index.html` | 接入中心 smoke 与边界回归 2 条用例通过 |

## 模式复核

| 模式 | 复核结论 |
|---|---|
| app | 已在 `docker-compose-app.yml` 运行环境验证 ingestion compile、CSV 上传/预检、ODS SQL 抽样、dbt parse 和平台前端 E2E。 |
| dev | 本次改动未修改 `.env`、`docker-compose.dev.yml` 或 dev mount；代码路径为服务内 Java/web E2E 辅助，按现有 dev 挂载复用。 |
| legacy | 本次改动未修改 `docker-compose.legacy.yml`、镜像构建参数或架构相关依赖；CSV parser 为纯 Java 实现，不引入平台相关 native 依赖。 |

说明：当前 `v223-dts-platform-1` 容器因非 Sprint-18 的平台后端改动启动失败，未通过 platform backend proxy 做端到端执行；本次 app 现场验证改为 direct ingestion API + ODS SQL + dbt + platform-webapp E2E。失败原因不归属 Sprint-18 接入链路。

## 手工验收步骤

### Step 1 - 数据库接入

1. 新建一个 PostgreSQL/MySQL/DM 测试数据源。
2. 选择 2 张表创建接入任务，执行 full refresh。
3. 检查 ODS 表存在源字段和 `_dts_*` 字段。
4. 抽样比对源表字段值，确认未做业务计算。
5. 检查同一次执行内 `_dts_batch_id` 一致。

### Step 2 - Excel/CSV 接入

1. 上传含多 sheet、日期、数字、空行和错误行的 Excel。
2. 执行预检并确认 schema。
3. 导入 ODS，检查 `_dts_source_file/_dts_source_sheet/_dts_row_number/_dts_file_hash`。
4. 上传 CSV，验证编码、分隔符和坏行记录。

### Step 3 - dbt stg 边界

1. 刷新 dbt source。
2. 检查 `ods_sources.yml` 包含接入任务生成的 ODS 表。
3. 新建或运行 stg 模型，确认业务字段标准化只发生在 stg。

## 产物留存

- `db-ods-contract.sql` - 数据库 ODS 字段检查 SQL
- `file-ingestion-samples/` - Excel/CSV 样本
- `batch-trace.md` - batch/execution/Airflow/ODS 串联证据
- `dbt-source-diff.md` - dbt source 刷新前后差异
