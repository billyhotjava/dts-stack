# 项目管理建模包 v2.2.2

## 包内容

### dist/

| 文件 | 用途 |
|------|------|
| `99-build-all.sql` | **推荐**：纯 SQL 一键建表（不依赖 dbt，最简单） |
| `README.md` | 本文档 |

### dist/pm/

| 文件 | 用途 |
|------|------|
| `ODS表字段映射说明.md` | ODS 表字段说明及入湖映射要求 |
| `指标计算原理说明.md` | KPI 指标计算逻辑说明 |
| `project-management-cli-deploy.zip` | CLI `dts-deploy` 导入包 |
| `project-management-ui-import.zip` | 页面 ZIP 上传导入包 |
| `project-management-test-data.xlsx` | 测试数据（可用于验证建模效果） |

### 数仓表清单（20 张）

| 层 | 数量 | 表 |
|----|------|----|
| DIM | 7 | dim_completion_status, dim_node_type, dim_risk_level, pm_dim_delay_reason, pm_dim_major_project, pm_dim_subproject, pm_map_node_subject |
| DWD | 2 | biz_dwd_project_node, biz_dwd_project_node_enriched |
| DWS | 4 | biz_dws_period_node_summary, biz_dws_period_node_type_summary, biz_dws_period_risk_summary, biz_dws_week_subproject_summary |
| ADS | 7 | biz_ads_project_kpi_overview, biz_ads_project_milestone_kpi, biz_ads_project_non_general_kpi, biz_ads_project_incomplete_risk, biz_ads_major_project_overview, biz_ads_major_project_tree_snapshot, biz_ads_delay_reason_trend |

> **v2.2.2 变更**：移除了 v2.2.1 的 4 张 seed 表，全量改为 ODS 自动推导；
> `biz_dwd_project_node` 新增字段 `deliverable`、`last_update_week`。

---

## 方式一：纯 SQL 一键建表（最简单，万一其他方式都失败时用）

适用于所有环境，不依赖 dbt、平台 API，只需 psql 客户端。

### 前提

ODS 表已通过入湖任务导入（Excel 导入），目标表名为 `ods_project_subject_domain`。

### 执行

```bash
# 本地环境
psql -U biadmin -d biadmin -v ods_table=ods_project_subject_domain -f 99-build-all.sql

# Docker 容器内执行
docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain \
  -f - < 99-build-all.sql

# 远程环境（SSH 传入）
ssh root@<IP> "cd /opt/prod/s10-stack && docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain -f -" < 99-build-all.sql
```

### 自定义 ODS 表名

如果入湖时使用了自定义表名，替换 `ods_table` 参数即可：

```bash
psql -U biadmin -d biadmin -v ods_table=my_custom_ods_table -f 99-build-all.sql
```

---

## 方式二：dbt 运行时部署（docker run dbt）

适用于需要在平台建模功能中查看、编辑、测试、发布模型的场景。

### 前提

1. 先从部署包中将 macros 拷贝到 dbt 目录：

```bash
# 解压 pm/ 下的 CLI 包，将 macros 复制到 dbt 工作目录
# （如 macros/ 已存在同名文件，先备份）
cp -r pm/macros/* services/dts-dbt/macros/
```

2. Excel 已通过入湖任务导入，目标表名为 `ods_project_subject_domain`。

### 执行

```bash
cd /opt/prod/s10-stack

# Step 1：dbt seed（如有 seed 文件）
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 seed --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev

# Step 2：dbt run 全量重建
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 run --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev --threads 1 --full-refresh
```

**注意：** 必须加 `--full-refresh` 确保清除可能的残留重复数据。

---

## 方式三：CLI dts-deploy 部署

适用于通过命令行批量导入建模包到平台的场景。

```bash
bin/dts-deploy \
  --user opadmin \
  --password xxx \
  --package pm/project-management-cli-deploy.zip \
  --plan-name "项目管理" \
  --skip-existing --run --insecure
```

> 参数说明：
> - `--skip-existing`：跳过已存在的同名模型，避免覆盖已修改的线上版本
> - `--run`：导入后立即触发执行
> - `--insecure`：跳过 HTTPS 证书验证（内网部署时使用）

---

## 方式四：UI 页面 ZIP 上传

1. 登录平台，进入 **建模中心 → 导入**
2. 上传 `pm/project-management-ui-import.zip`
3. 填写计划名称（如"项目管理"），确认导入
4. 导入成功后，在建模中心执行全量刷新（Full Refresh）

---

## 数据重建（修复 OOM/笛卡尔积等）

如果遇到以下问题，需要重建数仓表：

- 项目看板 OOM（`OutOfMemoryError: Java heap space`）
- `biz_dwd_project_node_enriched` 行数远超 ODS 表（笛卡尔积）
- 维度表有重复数据
- ODS 数据更新后需要重刷

### dbt 方式重建

```bash
cd /opt/prod/s10-stack

# 全量重建（--full-refresh 会先 DROP 再 CREATE，清除重复数据）
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 run --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev --threads 1 --full-refresh

# 重建完成后重启 analytics 服务（清除 OOM 状态）
docker restart s10-stack-dts-analytics-1
```

### 纯 SQL 方式重建

```bash
cd /opt/prod/s10-stack

# 一键重建（99-build-all.sql 内含 DROP IF EXISTS CASCADE，幂等安全）
docker exec -i s10-stack-dts-pg-1 psql -U biadmin -d biadmin \
  -v ods_table=ods_project_subject_domain \
  -f - < 99-build-all.sql

# 重建完成后重启 analytics 服务
docker restart s10-stack-dts-analytics-1
```

---

## ODS 表字段要求

完整字段说明请参阅 `pm/ODS表字段映射说明.md`。

**ODS 表名**：`ods_project_subject_domain`（默认；可通过 `ods_table` 参数覆盖）

**必须包含的字段（最小集合）**：

| 字段名 | 说明 | 类型 |
|--------|------|------|
| `project_no` | 项目编号（重大项目标识） | text |
| `subsystem` | 子系统/子项目名称 | text |
| `node_task` | 节点任务名称 | text |
| `plan_date` | 计划完成日期（支持多种格式，如 2026-03-18、20260318、2026/03/18） | text |
| `completion_status` | 完成状态（如"按时完成"、"超期未完成未变更"等） | text |
| `node_type` | 节点类型（如"一般节点"、"里程碑节点"等） | text |
| `risk_level` | 风险等级（高/中/低） | text |

**推荐包含的字段**：

| 字段名 | 说明 |
|--------|------|
| `actual_date` | 实际完成日期 |
| `owner` | 责任人 |
| `dept` | 责任部门 |
| `dept_leader` | 部门领导 |
| `project_manager` | 项目经理 |
| `incomplete_reason` | 未完成原因（用于自动推导延期原因分类） |
| `risk_content` | 风险内容 |
| `deliverable` | 交付物（v2.2.2 新增） |
| `last_update_week` | 最后更新周数（v2.2.2 新增，数值型） |

> 字段值中的 `/`、`-`、`N/A`、`#VALUE!` 等占位符会自动清除为 NULL，无需预处理。

---

## 故障排查表

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `OutOfMemoryError: Java heap space` | enriched 表数据膨胀（笛卡尔积） | 执行数据重建（见上方），然后重启 analytics |
| `relation "ods_project_subject_domain" does not exist` | ODS 表未创建 | 先通过入湖任务导入 Excel；或用 `-v ods_table=实际表名` 指定正确的 ODS 表名 |
| `column "deliverable" does not exist` | ODS 表缺少 v2.2.2 新增字段 | 在 ODS 表上执行 `ALTER TABLE ods_project_subject_domain ADD COLUMN deliverable text;` |
| `column "last_update_week" does not exist` | ODS 表缺少 v2.2.2 新增字段 | 在 ODS 表上执行 `ALTER TABLE ods_project_subject_domain ADD COLUMN last_update_week text;` |
| `function parse_date_safe does not exist` | 函数未创建 | 执行 `99-build-all.sql`（会自动创建所有函数） |
| `function nullif_placeholder does not exist` | 函数未创建 | 同上，执行 `99-build-all.sql` |
| `dbt found two models with the same name` | 同名 SQL 文件在不同目录 | 删除重复目录，用 `--full-refresh` 重建 |
| 项目看板数据全为 0 | 数仓表未构建或 ODS 表为空 | 检查 ODS 表是否有数据，再执行重建 |
| analytics 首页 500 错误 | analytics 用户记录缺失或服务 OOM | 重启：`docker restart s10-stack-dts-analytics-1` |
| `ERROR: value too long for type character varying` | ODS 某字段超长 | 检查 ODS 表对应字段长度，或将该字段列类型改为 `text` |

---

## 堆内存配置

如果数据量较大（ODS 超过 5000 行），建议增加 analytics 服务的 JVM 堆内存。

编辑项目根目录下的 `.env` 文件：

```bash
# 默认 1024m，建议改为 2048m 或更大
ANALYTICS_JAVA_TOOL_OPTIONS="-Xms512m -Xmx2048m"
```

> 注意：`.env` 文件中的值不要加引号（Docker Compose 不会自动去除引号）。

然后重启服务：

```bash
docker compose restart dts-analytics
```

for f in *.tar *.tar.gz; do docker load -i "$f"; done
