# 手工执行 ELT

当 dbt 构建失败或 Airflow 不可用时，可直接在数据库中手工执行 SQL 构建数仓。

## 前提

- ODS 表已存在（通过 `ods_create_tables.sql` 创建）
- ODS 表中已有数据（通过入湖任务导入）

## 使用方式

### 方式 1：在 PG 容器内执行

```bash
# 复制 SQL 到容器
docker cp run_all_models.sql s10-stack_dts-pg_1:/tmp/

# 执行（全量构建 40 个模型）
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -f /tmp/run_all_models.sql
```

### 方式 2：远程 psql 执行

```bash
PGPASSWORD='密码' psql -h 数据库地址 -U biadmin -d biadmin -f run_all_models.sql
```

## 文件说明

| 文件 | 内容 |
|------|------|
| `run_all_models.sql` | 40 个模型的合并 SQL，按依赖顺序排列，事务内执行 |

## 执行顺序

```
1. 维度表（7个）：dim_completion_status, dim_node_type, dim_risk_level, ...
2. DWD 事实表（12个）：biz_dwd_project_node, biz_dwd_quality_issue, ...
3. DWD 宽表（1个）：biz_dwd_project_node_enriched
4. DWS 汇总表（9个）：biz_dws_period_node_summary, ...
5. ADS 指标表（11个）：biz_ads_project_kpi_overview, ...
```

全部在一个事务内执行，任何一步失败则全部回滚。

## 注意事项

- 每个模型都是 `DROP TABLE CASCADE + CREATE TABLE AS SELECT`
- 会**覆盖**已有的数仓表
- **不会动 ODS 表**（只读取不修改）
- 执行时间取决于 ODS 数据量，通常几秒到几分钟
