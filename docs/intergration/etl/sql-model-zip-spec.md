# SQL 模型 ZIP 导入规范（Platform）

## 1. 目标

用于客户将 ODS/DWD/DWS/ADS 模型以 ZIP 包方式交付，平台在 `逻辑建模` 中批量导入并挂靠到指定项目空间（`planId`）。

对应接口：

- `POST /api/modeling/sql-models/import-zip`

## 2. 上传入口（前端）

页面：`Platform -> 逻辑建模 -> 模型 -> 批量导入 ZIP`

必填项：

- `项目空间（planId）`
- `来源数据源（sourceDataSourceId）`
- `ZIP 文件（.zip）`

可选项：

- `默认分层（defaultLayer）`
- `schemaName`
- `materialized`
- `tags`
- `description`
- `enabled`
- `status`
- `ownerDept`

## 3. ZIP 包目录结构

ZIP 根目录必须直接包含 dbt 标准目录，不要额外包一层父目录。

推荐结构：

```text
models/
  ods/
    ods_customer.sql
  dwd/
    dwd_customer_order_detail.sql
  dws/
    dws_customer_30d_metrics.sql
  ads/
    ads_customer_dashboard.sql
```

支持目录（与后端白名单一致）：

- `models/`
- `macros/`
- `seeds/`
- `tests/`
- `snapshots/`
- `analyses/`

支持文件后缀：

- `.sql`
- `.yml` / `.yaml`
- `.csv`
- `.md`
- `.txt`

## 4. 平台解析规则

### 4.1 模型文件识别

仅 `models/**/*.sql` 会被解析为 SQL 模型实体（入库到 `modeling_sql_model`）。

### 4.2 模型名

模型名取文件名（去掉 `.sql`），例如：

- `models/dwd/dwd_order_detail.sql` -> 模型名 `dwd_order_detail`

模型名校验：

- 必须匹配 `^[A-Za-z][A-Za-z0-9_]*$`

### 4.3 分层识别优先级

1. 目录分层：`models/ods|dwd|dws|ads/`
2. 模型名前缀：`ods_` / `dwd_` / `dws_` / `ads_`
3. 表单默认分层：`defaultLayer`

若以上都无法识别，则该模型跳过。

### 4.4 创建 / 更新规则

按 `(planId, modelName)` 作为唯一语义：

- 若项目内不存在同名模型 -> 创建
- 若项目内已存在同名模型 -> 更新

更新时会刷新：

- `sqlText`
- `modelPath`
- `layer`
- 以及表单里传入的可覆盖字段（如 `schemaName/materialized/tags/status/enabled/sourceDataSourceId`）

### 4.5 结果返回

接口返回：

- 文件维度统计：`totalFiles/newFiles/overwrittenFiles/skippedFiles/importedFiles`
- 模型维度统计：`modelsCreated/modelsUpdated/skippedModels/modelFiles`

## 5. 常见失败原因

- ZIP 内路径不是从 `models/` 开始（例如多了一层父目录）。
- 文件名不合法（不是字母开头，或包含非法字符）。
- 无法识别分层，且未提供 `defaultLayer`。
- 新建模型时未提供 `sourceDataSourceId`。
- SQL 文件为空。

## 6. 打包建议

在样例目录中执行：

```bash
cd docs/intergration/etl/sample-sql-model-zip
zip -r ../sample-sql-model-zip.zip models
```

注意：

- 使用 `zip` 时请确保 ZIP 根目录第一层就是 `models/`。
- 不要将 `sample-sql-model-zip` 这个父目录一起打进 ZIP。

## 7. 样例包

样例源码目录：

- `docs/intergration/etl/sample-sql-model-zip/`

可直接上传样例 ZIP：

- `docs/intergration/etl/sample-sql-model-zip.zip`

