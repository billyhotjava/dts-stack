# 项目管理数仓 — 运维手册

## 1. 部署流程

### 1.1 首次部署

```bash
# 1) 手动建 ODS 表（9 张）
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -f /tmp/ods_create_tables.sql

# 2) 上传 UI 导入包到平台（逻辑建模 → 批量导入）
#    选择 project-management-ui-import.zip

# 3) 构建模型（界面点"构建"或手动执行）
#    见下方"手动构建"命令
```

### 1.2 更新模型（已有 ODS 数据）

```bash
# 1) 上传新 ZIP 包（勾选"清理旧版文件"）
# 2) 构建 → 不会动 ODS 表，只重建 DWD/DWS/ADS
```

---

## 2. 手动构建命令

### 2.1 全量构建（build = compile + run + test）

```bash
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 build \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev \
  --threads 1
```

### 2.2 只编译（不执行 SQL，只生成 manifest）

```bash
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 compile \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev
```

### 2.3 只运行模型（不跑测试）

```bash
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 run \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev \
  --threads 1
```

### 2.4 只跑测试

```bash
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 test \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev
```

### 2.5 只构建某个域（按 tag 选择）

```bash
# 只构建执行域
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 build \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev --threads 1 \
  --select tag:project-management
```

### 2.6 只构建某个模型及其下游

```bash
# 构建 biz_dwd_quality_issue 及所有依赖它的下游模型
docker run --rm --privileged \
  --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  -v /opt/prod/s10-stack/services/dts-dbt/profiles:/root/.dbt \
  -v /opt/prod/s10-stack/logs/dbt:/opt/dbt-logs \
  -e DBT_LOG_PATH=/opt/dbt-logs \
  dts-dbt:1.10.0 build \
  --project-dir /opt/dbt \
  --profiles-dir /root/.dbt \
  --target dev --threads 1 \
  --select biz_dwd_quality_issue+
```

---

## 3. ODS 表管理

### 3.1 手动建表（首次或重置）

```bash
# 将 ods_create_tables.sql 复制到 PG 容器内执行
docker cp ods_create_tables.sql s10-stack_dts-pg_1:/tmp/
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -f /tmp/ods_create_tables.sql
```

**注意**：此脚本会 DROP + CREATE，已有数据会丢失。

### 3.2 查看 ODS 表状态

```bash
# 查看所有 ODS 表及行数
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c "
SELECT tablename, 
  (SELECT count(*) FROM pg_catalog.pg_class c 
   JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace 
   WHERE c.relname = tablename AND n.nspname = 'public') 
FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'ods_%' ORDER BY 1;
"
```

简化版（逐表查行数）：

```bash
for t in ods_project_subject_domain ods_progress_measure ods_quality_issue ods_quality_measure ods_tech_state ods_tech_state_measure ods_risk_info ods_risk_measure ods_material_info; do
  echo -n "$t: "
  docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -t -c "SELECT count(*) FROM $t" 2>/dev/null || echo "不存在"
done
```

### 3.3 查看 ODS 表结构

```bash
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c "\d ods_project_subject_domain"
```

---

## 4. 数仓表验证

### 4.1 查看各层表行数

```bash
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c "
SELECT 'ODS' AS layer, count(*) AS tables FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'ods_%'
UNION ALL SELECT 'DWD', count(*) FROM pg_tables WHERE schemaname='public' AND (tablename LIKE 'biz_dwd_%' OR tablename LIKE 'dim_%' OR tablename LIKE 'pm_%')
UNION ALL SELECT 'DWS', count(*) FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'biz_dws_%'
UNION ALL SELECT 'ADS', count(*) FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'biz_ads_%'
ORDER BY 1;
"
```

### 4.2 验证执行域数据链路

```bash
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c "
SELECT 'ods_project_subject_domain' AS tbl, count(*) FROM ods_project_subject_domain
UNION ALL SELECT 'biz_dwd_project_node', count(*) FROM biz_dwd_project_node
UNION ALL SELECT 'biz_ads_major_project_overview', count(*) FROM biz_ads_major_project_overview
UNION ALL SELECT 'biz_ads_project_kpi_overview', count(*) FROM biz_ads_project_kpi_overview;
"
```

---

## 5. 排障

### 5.1 dbt 构建失败

```bash
# 查看 dbt 日志
cat /opt/prod/s10-stack/logs/dbt/dbt.log | tail -50

# 查看 target 产物
ls -la /opt/prod/s10-stack/services/dts-dbt/target/
```

### 5.2 检查 dbt 项目文件

```bash
# 查看 dbt_project.yml
cat /opt/prod/s10-stack/services/dts-dbt/dbt_project.yml

# 查看 models 目录
find /opt/prod/s10-stack/services/dts-dbt/models -type f | sort

# 查看 macros
ls /opt/prod/s10-stack/services/dts-dbt/macros/

# 查看 yml 文件（source 和 schema 定义）
find /opt/prod/s10-stack/services/dts-dbt/models -name "*.yml" | sort
```

### 5.3 source 定义冲突

如果报 "found two sources with the same name"：

```bash
# 查找所有 yml 文件
find /opt/prod/s10-stack/services/dts-dbt/models -name "*.yml" -exec grep -l "sources:" {} \;

# 删除旧的 source yml（只保留 pm_sources.yml）
find /opt/prod/s10-stack/services/dts-dbt/models -maxdepth 1 -name "*.yml" ! -name "pm_sources.yml" ! -name "pm_schema.yml" -delete
```

### 5.4 恢复出厂设置

```bash
/opt/prod/s10-stack/bin/dts-reset
```

然后重新上传 ZIP 包 → 构建。

### 5.5 同步 manifest 到平台

构建成功后如果平台状态未更新：

```bash
# 手动触发同步（在 Docker 网络内执行）
docker exec s10-stack_dts-platform_1 curl -sSf -X POST \
  -H "Content-Type: application/json" \
  -H "X-DTS-Service: dts-airflow" \
  "http://localhost:8080/api/etl/dbt/models/sync"
```

---

## 6. 数据模型清单

### ODS 源表（9 张，入湖流程创建，dbt 不管理）

| 表名 | 来源 | 字段数 |
|------|------|:---:|
| ods_project_subject_domain | project1.xlsx | 31 |
| ods_progress_measure | pmall.pdf | 23 |
| ods_quality_issue | pmall.pdf | 21 |
| ods_quality_measure | pmall.pdf | 33 |
| ods_tech_state | pmall.pdf | 33 |
| ods_tech_state_measure | pmall.pdf | 41 |
| ods_risk_info | pmall.pdf | 31 |
| ods_risk_measure | pmall.pdf | 42 |
| ods_material_info | pmall.pdf | 29 |

### dbt 模型（40 个）

| 层 | 数量 | 说明 |
|---|:---:|------|
| DWD | 20 | 10 事实表 + 10 维度/映射表 |
| DWS | 9 | 周期汇总 |
| ADS | 11 | KPI 指标 |

### 查询卡片（25 个）

| 域 | 数量 |
|---|:---:|
| 执行域 | 8 |
| 质量域 | 4 |
| 技术状态域 | 4 |
| 风险域 | 4 |
| 物料域 | 3 |
| 进度跟进域 | 2 |
