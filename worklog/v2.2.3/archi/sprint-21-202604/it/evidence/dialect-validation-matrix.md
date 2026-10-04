# Schema Discover 方言验证矩阵

本矩阵用于关闭 F3 的工业级验证口径：同一套 Schema Discover/ODS 生成链路至少覆盖 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的基础元数据。

## 验证项

| 项 | 要求 |
|---|---|
| 连接测试 | 能成功建立 JDBC 连接并记录耗时、数据库版本和失败分类 |
| Schema 列表 | 能读取 schema/database 清单 |
| 表清单 | 能读取普通表，视图应标识 `view=true` 或 `type=VIEW` |
| 字段 | 字段名、JDBC 类型、原生类型、nullable、ordinal position 可用 |
| 主键 | 单列/联合主键能进入 `primaryKeys` |
| 索引 | 普通索引/唯一索引能进入 `indexes` |
| 增量候选 | `updated_at/update_time/modify_time/last_modified` 等时间字段能被识别 |
| 样本 | `sampleLimit > 0` 时能返回样本，敏感字段按权限脱敏 |
| ODS 预览 | 能生成 ODS 字段类型、技术字段、DDL、dbt source 和 Addax/Airflow 草稿 |
| 预检 | 能返回源端 SELECT、行数、主键、增量字段、目标写入和类型兼容规则 |

## 数据库矩阵

| 数据库 | 驱动/版本 | 连接测试 | Discover | ODS 预览 | 预检 | 证据目录 | 状态 |
|---|---|---|---|---|---|---|---|
| PostgreSQL | `org.postgresql.Driver` | 待归档 | 待归档 | 待归档 | 待归档 | `db-source-smoke/postgresql/` | 待现场执行 |
| MySQL | `com.mysql.cj.jdbc.Driver` | 待归档 | 待归档 | 待归档 | 待归档 | `db-source-smoke/mysql/` | 待现场执行 |
| Oracle | `oracle.jdbc.OracleDriver` | 待归档 | 待归档 | 待归档 | 待归档 | `db-source-smoke/oracle/` | 待现场执行 |
| SQL Server | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | 待归档 | 待归档 | 待归档 | 待归档 | `db-source-smoke/sqlserver/` | 待现场执行 |
| DM8 | `dm.jdbc.driver.DmDriver` | 待归档 | 待归档 | 待归档 | 待归档 | `db-source-smoke/dm8/` | 待现场执行 |

## 执行方式

每种数据库准备一张同构样例表，字段至少包含：

```text
project_id varchar primary key
project_name varchar
budget_amount decimal
progress numeric
update_time timestamp/datetime
active boolean/number
```

执行同一条 smoke 路径：

```bash
DTS_BASE_URL="https://dts.local" \
DTS_TOKEN="$ACCESS_TOKEN" \
DTS_DATA_SOURCE_ID="$DATA_SOURCE_ID" \
DTS_SCHEMA="$SOURCE_SCHEMA" \
DTS_TABLE_PATTERN="erp_project" \
DTS_SMOKE_OUT="worklog/v2.2.3/sprint-21-202604/it/evidence/20260430-rc1/db-source-smoke/postgresql" \
worklog/v2.2.3/sprint-21-202604/it/scripts/connector-center-smoke.sh
```

验证通过后，把矩阵中的状态更新为 `PASS`，并记录驱动版本、数据库版本和证据目录。验证失败时保留输出目录，状态填 `FAIL`，并在发布记录中给出失败分类和补救计划。
