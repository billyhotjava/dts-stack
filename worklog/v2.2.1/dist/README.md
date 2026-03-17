# 项目管理建模包 v2.2.1

## 包内容

| 文件 | 用途 |
|------|------|
| `99-build-all.sql` | **推荐**：纯 SQL 一键建表（不依赖 dbt） |
| `project-sql/` | 分层单独 SQL 文件 + KPI 卡片查询 |
| `project-kpi-queries.md` | 35 个 KPI 指标 SQL 查询文档 |
| `dts-dbt-runtime.tar.gz` | dbt 运行时目录（用 dbt 方式部署时使用） |
| `project-management-cli-deploy.zip` | CLI `dts-deploy` 导入包 |
| `project-management-ui-import.zip` | 页面 ZIP 上传导入包 |

### 数仓表清单（24 张）

| 层 | 数量 | 表 |
|----|------|------|
| Seed | 4 | pm_dim_delay_reason_seed, pm_dim_major_project_seed, pm_dim_subproject_seed, pm_map_node_subject_seed |
| DIM | 7 | dim_completion_status, dim_node_type, dim_risk_level, pm_dim_delay_reason, pm_dim_major_project, pm_dim_subproject, pm_map_node_subject |
| DWD | 2 | biz_dwd_project_node, biz_dwd_project_node_enriched |
| DWS | 4 | biz_dws_period_node_summary, biz_dws_period_node_type_summary, biz_dws_period_risk_summary, biz_dws_week_subproject_summary |
| ADS | 7 | biz_ads_project_kpi_overview, biz_ads_project_milestone_kpi, biz_ads_project_non_general_kpi, biz_ads_project_incomplete_risk, biz_ads_major_project_overview, biz_ads_major_project_tree_snapshot, biz_ads_delay_reason_trend |

---

## 方式一：纯 SQL 一键建表（推荐，最简单）

适用于所有环境，不依赖 dbt、Airflow、平台 API。

### 前提

ODS 表已通过入湖任务导入（Excel 导入），目标表名为 `ods_project_subject_domain`。

### 执行

```bash
# 本地环境
psql -U biadmin -d biadmin -v ods_table=ods_project_subject_domain -f 99-build-all.sql

# Docker 环境
docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain \
  -f - < 99-build-all.sql

# 远程环境（SSH）
ssh root@<IP> "cd /opt/prod/s10-stack && docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain -f -" < 99-build-all.sql
```

### 自定义 ODS 表名

如果入湖时用了其他表名，替换 `ods_table` 参数即可：

```bash
psql -U biadmin -d biadmin -v ods_table=my_custom_ods_table -f 99-build-all.sql
```

---

## 方式二：dbt 运行时部署

适用于需要使用平台建模功能（编辑、测试、发布）的场景。

```bash
# 1. 解压 dbt 运行时包
tar xzf dts-dbt-runtime.tar.gz -C /opt/prod/s10-stack/

# 2. Excel 入湖（目标表名用 ods_project_subject_domain）

# 3. dbt seed + run（全量重建）
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 seed --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev

docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 run --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev --threads 1 --full-refresh
```

**注意：** 必须加 `--full-refresh` 确保清除可能的残留重复数据。

---

## 方式三：CLI dts-deploy 部署

```bash
bin/dts-deploy \
  --user opadmin --password xxx \
  --package project-management-cli-deploy.zip \
  --plan-name "项目管理" \
  --skip-existing --run --insecure
```

---

## 数据重建（已有环境修复）

如果遇到以下问题，需要重建数仓表：
- 项目看板 OOM（`OutOfMemoryError: Java heap space`）
- `biz_dwd_project_node_enriched` 行数远超 ODS 表（笛卡尔积）
- 维度表有重复数据

### dbt 方式重建

```bash
cd /opt/prod/s10-stack

# 全量重建（清除重复数据）
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 run --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev --threads 1 --full-refresh

# 重启 analytics 服务（清除 OOM 状态）
docker restart s10-stack-dts-analytics-1
```

### 纯 SQL 方式重建

```bash
cd /opt/prod/s10-stack

# 一键重建
docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain \
  -f - < 99-build-all.sql

# 重启 analytics 服务
docker restart s10-stack-dts-analytics-1
```

---

## 故障排查

| 错误信息 | 原因 | 解决 |
|---------|------|------|
| `OutOfMemoryError: Java heap space` | enriched 表数据膨胀（笛卡尔积） | 执行数据重建（见上方），然后重启 analytics |
| `relation "ods_project_subject_domain" does not exist` | ODS 表未创建 | 先通过入湖任务导入 Excel |
| `function parse_date_safe does not exist` | 函数未创建 | 执行 `99-build-all.sql`（会自动创建函数） |
| `dbt found two models with the same name` | 同名 SQL 文件在不同目录 | 删除重复目录，用 `--full-refresh` 重建 |
| 项目看板"重大项目数"为 0 | seed 维度映射和客户数据不匹配 | 更新 seed CSV 或使用自动推导模型 |
| analytics 首页 500 错误 | analytics 用户记录缺失或服务 OOM | 重启 analytics：`docker restart s10-stack-dts-analytics-1` |

---

## 增加 Analytics 堆内存

如果数据量较大（>5000 行），建议增加 analytics 服务的堆内存。编辑 `.env`：

```bash
# 默认 1024m，建议改为 2048m
ANALYTICS_JAVA_TOOL_OPTIONS="-Xms512m -Xmx2048m"
```

然后重启：`docker compose restart dts-analytics`
