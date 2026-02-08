# 外部 LLM 结果离线导入规范（ETL / 逻辑建模）

## 1. 目标

适用场景：
- 客户在外部环境使用 LLM 生成了 DWD/DWS/ADS 的复杂 SQL（含分层、指标、聚合）。
- 生产环境为离线环境，不能在线调用 LLM。
- 需要通过命令行将模型导入平台，并进入后续 dbt 执行与发布流程。

本规范替代原 ZIP 导入规范。当前平台已移除 ZIP 导入能力。

## 2. 当前可用导入路径

### 路径 A（推荐）：导入到逻辑建模

使用接口：`POST /api/modeling/sql-models/import`

特点：
- 模型会写入 `modeling_sql_model`，可在 `逻辑建模` 页面管理。
- 同时落地 SQL 文件到 dbt 工作区。
- 可继续走平台内的同步、运行、上线流程。

### 路径 B：仅写入 dbt 项目文件

特点：
- 只影响 dbt 执行。
- 不会自动出现在 `逻辑建模` 模型列表。

建议：除临时排障外，优先使用路径 A。

## 3. 与 ODS 一键生模的关系

不冲突，但必须遵守命名隔离规则：
- 外部 LLM 模型统一前缀：`biz_dwd_` / `biz_dws_` / `biz_ads_`。
- 自动生模保留前缀：`dwd_` / `dws_` / `ads_`。
- 文件目录分离：外部模型放在 `models/custom/...`。

注意：
- `POST /api/modeling/sql-models/import` 是创建语义，重复执行会新增记录。
- 建议批量导入前先确认命名唯一，避免同项目出现同名模型多条记录。

## 4. 接口参数约束（/import）

必填：
- `planId`：项目空间 ID（UUID）
- `name`：模型名（`^[A-Za-z][A-Za-z0-9_]*$`）
- `layer`：`ODS|DWD|DWS|ADS`
- `sourceDataSourceId`：来源数据源 ID（UUID）
- `sql`：SQL 文件

可选：
- `alias` `schemaName` `materialized` `tags` `description` `enabled` `status` `ownerDept`
- `csv`：字段样例 CSV（可选）

认证头：
- `Authorization: Bearer <token>`
- `X-Active-Dept: <dept>`（按租户组织策略可选）

## 5. 样例包结构

目录：`docs/intergration/etl/sample-cli-model-import/`

```text
sample-cli-model-import/
  manifest/
    models.tsv
  models/
    dwd/biz_dwd_patent_base.sql
    dws/biz_dws_patent_dept_metrics.sql
    ads/biz_ads_patent_dashboard.sql
  scripts/
    import_models.sh
  README.md
```

## 6. 导入步骤（命令行）

1. 准备环境变量：

```bash
export API_BASE="https://<platform-domain>"
export TOKEN="<bearer-token>"
export PLAN_ID="<project-space-uuid>"
export SOURCE_DATA_SOURCE_ID="<source-datasource-uuid>"
export ACTIVE_DEPT="<optional-dept-code>"
```

2. 执行批量导入（推荐使用仓库命令 `bin/dts-dbt-import`）：

```bash
bin/dts-dbt-import \
  --manifest docs/intergration/etl/sample-cli-model-import/manifest/models.tsv \
  --api-base "$API_BASE" \
  --token "$TOKEN" \
  --plan-id "$PLAN_ID" \
  --source-data-source-id "$SOURCE_DATA_SOURCE_ID"
```

3. 校验结果：
- 平台页面：`逻辑建模` 查看模型是否挂靠到目标项目空间。
- 文件侧：`dbt 文件浏览器` 查看 SQL 是否写入。
- 运行侧：执行 `dbt run`，再执行平台 `同步`。

## 7. 常见问题

1. `sourceDataSourceId` 缺失报错
- `/import` 接口要求必填，必须传有效 UUID。

2. 同名模型重复
- 批量导入脚本会按清单逐条创建；重复执行会重复创建。
- 请在导入前保证模型名唯一，或先清理后重导。

3. 模型可运行但建模页看不到
- 说明模型仅写入了 dbt 文件目录，没有走 `/import`。

## 8. 版本说明

- 文档时间：2026-02-08
- 规范状态：有效
- 替代文档：`sql-model-zip-spec.md`（已废弃并删除）
