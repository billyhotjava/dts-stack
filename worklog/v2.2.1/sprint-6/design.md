# CLI 工具抽象 + 批量导入设计

日期：2026-03-11
Sprint：v2.2.1 / sprint-5（延续）

## 背景

`bin/project-progress/deploy.sh` 将 Plan 创建和模型导入逻辑硬编码在单个领域目录中。新增业务域时需要复制整个脚本。前端导入弹窗只支持单个模型，无法批量操作。

## 目标

1. 将部署脚本抽象为通用 CLI 工具集，新业务域零脚本接入
2. 前端新增批量导入功能，支持 ZIP 上传和多文件在线编辑
3. `dts-manifest-gen` 从 dbt 模型自动生成 TSV manifest

---

## Part 1：CLI 工具架构

### 目标结构

```
bin/
├── lib/dts-common.sh           # 共享函数库
├── dts-plan                    # Plan 管理 CLI
├── dts-deploy                  # 编排器
├── dts-manifest-gen            # TSV manifest 生成器
└── dts-dbt-import              # 已有，最小改造

services/dts-dbt/
├── models/{dim,dwd,dws,ads}/   # dbt 模型（不变）
├── deploy/                     # 部署产物（按领域组织）
│   ├── project-progress/
│   │   ├── deploy.conf
│   │   ├── manifest/models.tsv
│   │   ├── seed-ods-project-progress.sql
│   │   ├── validate-indicators.sql
│   │   └── screen-template-project-progress.json
│   └── patent/
│       └── ...
```

### lib/dts-common.sh

从 deploy.sh 和 dts-dbt-import 中提取重复代码：

- 颜色输出：`red()` / `green()` / `yellow()` / `info()` / `warn()` / `die()`
- 环境校验：`require_env "API_BASE" "TOKEN"`
- curl 封装：`dts_curl_get <url>` / `dts_curl_post_json <url> <data>`
- JSON 提取：`json_extract <file> <field>`（jq 优先，python3 fallback）

### dts-plan

Plan CRUD CLI，核心逻辑从 deploy.sh 提取。

```bash
bin/dts-plan create --name "项目进度分析" --domain "project-management" \
  --scope "描述" --tags "tag1,tag2"
# 输出：plan_id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

bin/dts-plan find --name "项目进度分析"
# 输出：plan_id 或空

bin/dts-plan list [--keyword "项目"]
```

- `create` 自动处理重名后缀（name-2, name-3, ...）
- 支持 `--dry-run`、`--insecure`
- 环境变量：`API_BASE`、`TOKEN`、`ACTIVE_DEPT`（可选）

### dts-manifest-gen

扫描 `services/dts-dbt/models/` 下的 .sql 文件，解析 dbt config() 块生成 TSV。

```bash
bin/dts-manifest-gen --tag project-management
bin/dts-manifest-gen --path models/ads/
bin/dts-manifest-gen --tag biz --path models/dws/ --output out.tsv
```

解析规则：

| TSV 字段 | 来源 |
|----------|------|
| name | 文件名去 .sql |
| layer | 目录名（ads/ → ADS）或 tags 中的层级标识 |
| sql_path | 相对于 services/dts-dbt/ 的路径 |
| materialized | config 中的 materialized 值 |
| tags | config 中的 tags 数组，逗号分隔 |
| status | 默认 DRAFT |
| enabled | 默认 true |

过滤：`--tag` 和 `--path` 组合时取交集。默认输出到 stdout。

### dts-deploy

编排器：create plan → import models。

```bash
# 方式1：领域配置文件
bin/dts-deploy --config services/dts-dbt/deploy/project-progress/deploy.conf

# 方式2：直接参数
bin/dts-deploy --plan-name "专利分析" --manifest path/models.tsv --domain patent
```

deploy.conf 格式：

```ini
PLAN_NAME="项目进度分析"
PLAN_DOMAIN="project-management"
PLAN_SCOPE="项目主体域数据建模与指标分析"
PLAN_TAGS="project-management,sprint-5"
PLAN_CONTENT="Sprint-5 项目主体域数据建模"
MANIFEST_PATH="manifest/models.tsv"
```

MANIFEST_PATH 相对于 deploy.conf 所在目录解析。

### dts-dbt-import 改造

最小改动：替换内联颜色函数为 `source lib/dts-common.sh`，其余逻辑不变。

---

## Part 2：后端批量导入 API

### Endpoint

```
POST /api/modeling/sql-models/batch-import
Content-Type: multipart/form-data
```

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | UUID | 是 | 目标项目空间 |
| sourceDataSourceId | UUID | 是 | 默认数据源（TSV 行级可覆盖） |
| archive | MultipartFile (ZIP) | 是 | 包含 models.tsv + SQL 文件 |
| skipExisting | Boolean | 否 | 同名模型跳过，默认 false |

ZIP 内部结构：

```
archive.zip
├── models.tsv
├── dim_completion_status.sql
├── biz_dwd_project_node.sql
└── ...
```

返回 DTO：

```java
record BatchImportResult(
    int total,
    int imported,
    int skipped,
    int failed,
    List<BatchImportDetail> details
)

record BatchImportDetail(
    String name,
    String layer,
    String status,       // "imported" | "skipped" | "failed"
    String message
)
```

实现要点：
- 解压 ZIP 到临时目录，查找 TSV（首个 .tsv 文件）
- 逐行解析 TSV，复用 ModelingSqlModelService.importFromFiles()
- 每行独立事务，失败不影响后续行
- 完成后清理临时文件

代码位置：
- Controller: ModelingSqlModelResource.batchImport()
- Service: ModelingSqlModelService.batchImportFromArchive()

---

## Part 3：前端批量导入 UI

### 入口

SqlModelingPage.tsx 工具栏新增"批量导入"菜单项，位于"导入模型"之后。

### Modal 设计

两个 Tab：

**Tab 1 - ZIP 上传：**
- 项目空间下拉、数据源下拉、跳过同名开关
- 拖拽区域上传 ZIP
- "下载 TSV 模板"链接

**Tab 2 - 文件选择：**
- 项目空间下拉、数据源下拉、跳过同名开关
- 多选 SQL 文件按钮
- 可编辑表格：模型名称（自动从文件名推断）、分层（从前缀推断，可修改）、物化方式、标签
- 提交时前端用 JSZip 打包为 ZIP 调用同一 API

### 交互细节

- 文件名推断：去 .sql 后缀为模型名，ads_ → ADS，dws_ → DWS，dwd_/dim_ → DWD
- 导入完成后 Modal 切换为结果视图，表格显示每行状态
- 新增前端依赖：jszip（~10KB gzip）

### API

platformApi.ts 新增：

```typescript
export const batchImportSqlModels = (data: FormData) =>
    api.post({ url: "/modeling/sql-models/batch-import", data });
```

---

## Part 4：迁移计划

### 文件迁移

| 原位置 | 新位置 |
|--------|--------|
| bin/project-progress/deploy.sh | 删除（功能由 dts-deploy + deploy.conf 替代） |
| bin/project-progress/manifest/models.tsv | services/dts-dbt/deploy/project-progress/manifest/models.tsv |
| bin/project-progress/seed-ods-*.sql | services/dts-dbt/deploy/project-progress/ |
| bin/project-progress/validate-*.sql | services/dts-dbt/deploy/project-progress/ |
| bin/project-progress/screen-template-*.json | services/dts-dbt/deploy/project-progress/ |
| bin/project-progress/ | 删除（空目录） |

### worklog 更新

worklog/v2.2.1/sprint-5/README.md 和 it/README.md 中的路径引用同步更新到新位置。

---

## Part 5：离线打包与部署（dts-pack + dts-deploy --package）

### 背景

最终部署环境为离线环境，`services/` 目录只随大版本更新，不能每次都更新。增量更新的 dbt 模型需要打包成 ZIP，拷贝到现场后通过 CLI 或 UI 导入。

### 概念澄清

- **项目空间 (ModelingPlan)** = DTS 平台自身的模型组织单元
- **业务模型** = 客户业务数据的 dbt 模型（如项目进度 Excel → ODS → ADS）
- `dts-pack` 打包的是业务模型，部署时导入到某个 DTS 项目空间中

### dts-pack

从 deploy 配置目录打包自包含 ZIP。

```bash
# 从 deploy.conf 打包
bin/dts-pack --config services/dts-dbt/deploy/project-progress/deploy.conf \
  --output worklog/v2.2.1/sprint-5/dist/ --include-seeds

# 从 manifest 直接打包
bin/dts-pack --manifest path/models.tsv --output dist/
```

ZIP 结构（扁平化）：

```
project-management-20260312.zip
├── deploy.conf           # Plan 元数据
├── models.tsv            # 模型清单
├── dim_completion_status.sql
├── biz_dwd_project_node.sql
├── ...
└── extras/               # 种子数据、验证脚本、大屏模板等
    ├── seed-ods-project-progress.sql
    ├── validate-indicators.sql
    └── screen-template-project-progress.json
```

选项：

| 选项 | 说明 |
|------|------|
| `--config <path>` | deploy.conf 路径，读取 Plan 元数据和 MANIFEST_PATH |
| `--manifest <path>` | 直接指定 TSV 路径 |
| `--output <dir>` | 输出目录（默认当前目录） |
| `--name <filename>` | ZIP 文件名（默认自动生成） |
| `--include-seeds` | 包含 seed-*.sql / validate-*.sql / screen-template-*.json |
| `--extras <glob>` | 指定额外文件模式（可重复） |
| `--dry-run` | 仅列出会打包的文件 |

### dts-deploy --package

从 ZIP 包部署：

```bash
# 从 ZIP 包创建新项目空间并导入
bin/dts-deploy --package dist/project-management-20260312.zip

# 导入到已有项目空间
bin/dts-deploy --package dist/project-management-20260312.zip \
  --plan-id "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

工作流程：
1. 解压 ZIP 到临时目录
2. 读取 deploy.conf 获取 Plan 元数据
3. 使用根目录 models.tsv 作为清单
4. 设置 SQL_BASE_DIR 指向解压目录（SQL 文件扁平化存放）
5. 如有 --plan-id 则跳过 Plan 创建，直接导入
6. 清理临时文件

### 离线部署工作流

```
开发环境                              现场（离线）
┌──────────────────┐                ┌──────────────────┐
│ 修改 dbt 模型     │                │                  │
│ dts-pack → ZIP    │  ─── 拷贝 ──→ │ dts-deploy        │
│ 产出: dist/*.zip  │                │   --package *.zip │
│                  │                │   [--plan-id xxx] │
└──────────────────┘                └──────────────────┘

或通过 UI：
  前端 BatchImportModal → ZIP 上传 Tab → 选择 dts-pack 产出的 ZIP 文件
```
