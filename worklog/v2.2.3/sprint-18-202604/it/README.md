# Sprint-18 集成测试证据

## 自动化验证矩阵

| 场景 | 覆盖点 | 状态 |
|---|---|---|
| 数据库全量接入 | 自动建 ODS、源字段复制、`_dts_*` 技术字段、batch 一致性 | AUTO PASS |
| 数据库增量接入 | watermark、批次字段、重复运行、checkpoint 审计 | AUTO PARTIAL |
| Excel 上传接入 | sheet/header/schema 预检、文件血缘字段、坏行记录 | AUTO PASS |
| CSV 上传接入 | 编码、分隔符、schema 确认、行号与 hash | AUTO PARTIAL |
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

## 待补现场证据

- 数据库全量/增量实际执行后的 ODS 抽样 SQL。
- Excel/CSV 样本文件导入后的坏行下载截图或 CSV 留存。
- `dbt parse` 在现场 dbt 项目目录中的执行结果。
- 浏览器 E2E 覆盖数据库、Excel、CSV 三条 happy path。

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
