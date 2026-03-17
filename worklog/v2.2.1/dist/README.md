# 项目管理建模包 v2.2.1

## 包内容

| 文件 | 用途 |
|------|------|
| `project-management-cli-deploy.zip` | CLI 命令行部署（含 macros） |
| `project-management-ui-import.zip` | 页面 ZIP 上传导入 |
| `models.tsv` | 模型清单（21个模型的元数据） |

### 模型清单（21个）

| 层 | 数量 | 模型 |
|----|------|------|
| ODS | 1 | ods_project_subject_domain |
| DWD | 9 | biz_dwd_project_node, biz_dwd_project_node_enriched, dim_completion_status, dim_node_type, dim_risk_level, pm_dim_delay_reason, pm_dim_major_project, pm_dim_subproject, pm_map_node_subject |
| DWS | 4 | biz_dws_period_node_summary, biz_dws_period_node_type_summary, biz_dws_period_risk_summary, biz_dws_week_subproject_summary |
| ADS | 7 | biz_ads_project_kpi_overview, biz_ads_project_milestone_kpi, biz_ads_project_non_general_kpi, biz_ads_project_incomplete_risk, biz_ads_major_project_overview, biz_ads_major_project_tree_snapshot, biz_ads_delay_reason_trend |

### ODS 表名约定

模型 SQL 通过 `{{ source('pm_ods', 'project_subject_domain') }}` 引用 ODS 表。
**客户现场导入 Excel 时，入湖任务的目标表名必须使用以下约定名称：**

| 逻辑名（模型引用） | 说明 | Excel 来源 |
|-------------------|------|-----------|
| `project_subject_domain` | 项目节点台账 | 项目管理主数据 Excel |

> 表名在 `models.tsv` 的 ODS 行中也有记录，现场操作时以此为准。

## 现场部署流程（离线环境）

### 准备物料

带到现场的文件：
- `project-management-cli-deploy.zip` — 模型包
- 客户的 Excel 源数据文件

### Step 1: 导入 Excel（使用约定表名）

在平台页面操作：

1. **数据接入中心 → 新建入湖任务 → Excel 导入**
2. 目标表名填写 `project_subject_domain`（必须和模型包约定一致）
3. 上传客户的 Excel 文件
4. 执行入湖任务，等待完成

如果有多个 Excel 对应多张 ODS 表，每个都按约定表名创建入湖任务：

```
客户Excel文件          →  入湖任务目标表名（约定）
────────────────────      ─────────────────────
项目节点台账.xlsx      →  project_subject_domain
```

### Step 2: 配置专题绑定

1. **数据开发中心 → 专题绑定中心**
2. 找到 `project-management` 专题
3. 点击"绑定"，选择数据源 `pg-lake`，表名填 `project_subject_domain`
4. 保存绑定

或通过 API：
```bash
curl -sk -X POST "${API_BASE}/api/topic-bindings/ods" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "templateCode": "project-management",
    "entityCode": "project_subject_domain",
    "schemaName": "public",
    "tableName": "project_subject_domain"
  }'
```

### Step 3: 导入模型包 + 建表

```bash
# 加载环境（会提示输入用户名和密码）
source bin/dts-deploy-env.sh

# 导入模型 + 建表
bin/dts-deploy \
  --package project-management-cli-deploy.zip \
  --plan-name "项目管理" \
  --skip-existing \
  --run \
  --insecure
```

或通过页面：
1. **逻辑建模（SQL） → 批量归档 → ZIP 上传** `project-management-ui-import.zip`
2. 流水线点击 **编译 → 测试 → 发布上线**

### 一步到位（ODS 表已存在且绑定已配好）

```bash
source bin/dts-deploy-env.sh
bin/dts-deploy \
  --package project-management-cli-deploy.zip \
  --plan-name "项目管理" \
  --skip-existing \
  --run \
  --insecure
```

## CLI 参数说明

| 参数 | 说明 |
|------|------|
| `--package <zip>` | 模型包路径 |
| `--plan-name <name>` | 项目空间名称（自动创建） |
| `--plan-id <uuid>` | 导入到已有项目空间（替代 --plan-name） |
| `--skip-existing` | 跳过同名已有模型 |
| `--run` | 导入后自动触发 dbt run 建表 + sync 目录 |
| `--run-selector <tag>` | 指定 dbt selector（默认用包内 tags 或 all） |
| `--insecure` | 跳过自签名 SSL 证书验证 |
| `--dry-run` | 仅验证，不实际执行 |

## 环境变量（可预设，免交互）

```bash
export DTS_USERNAME=opadmin
export DTS_PASSWORD=xxx
source bin/dts-deploy-env.sh
```

## 方式四：直接替换 dbt 目录（推荐，最可靠）

适用于现场离线部署，跳过平台导入，直接替换 dbt 运行时目录。

```bash
# 1. 解压 dbt 运行时包（覆盖 services/dts-dbt/，保留 profiles/）
tar xzf dts-dbt-runtime.tar.gz -C /opt/prod/s10/s10-stack/

# 2. Excel 入湖（目标表名用 ods_project_subject_domain）
#    通过页面操作：数据接入中心 → 新建入湖任务 → Excel

# 3. 运行 dbt seed + run 建表
docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 seed --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev

docker run --rm --network dts-core --privileged \
  -v $(pwd)/services/dts-dbt:/opt/dbt \
  -v $(pwd)/services/dts-dbt/profiles:/root/.dbt \
  dts-dbt:1.10.0 run --project-dir /opt/dbt --profiles-dir /root/.dbt --target dev --threads 1
```

**优点：** 不经过平台 API，不会重生成 SQL，不产生重复目录，不需要处理权限。
**注意：** `profiles/` 目录不在包中，由平台自动生成（或手动配置）。

### 包内容

| 文件 | 说明 |
|------|------|
| `dts-dbt-runtime.tar.gz` | 9.7K，完整 dbt 项目（排除 profiles/target/.git） |

```
services/dts-dbt/
├── dbt_project.yml          # dbt 项目配置
├── macros/                  # parse_date_safe 等宏
├── seeds/                   # 维度映射 CSV（4个）
├── models/
│   ├── dwd/prjtest1/        # 9 个 DWD 模型
│   ├── dws/prjtest1/        # 4 个 DWS 模型
│   ├── ads/prjtest1/        # 7 个 ADS 模型
│   ├── pm_ods_sources.yml   # source 定义
│   └── project_cockpit_schema.yml  # 测试定义
└── profiles/                # [不在包中] 由平台生成
```

## 故障排查

| 错误信息 | 原因 | 解决 |
|---------|------|------|
| `relation "project_subject_domain" does not exist` | ODS 表未创建或表名不匹配 | 检查入湖任务是否用了约定表名 |
| `function parse_date_safe does not exist` | macros 未部署 | 检查 `services/dts-dbt/macros/` 是否有该文件 |
| `目标数仓数据源不存在` | dbt 工作区未配置 target 数据源 | 页面"逻辑建模"齿轮图标配置数据源 |
| `dbt found two models with the same name` | 同名 SQL 文件在不同目录 | 删除重复文件，保留一份 |
