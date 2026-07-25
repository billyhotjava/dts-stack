# 预检与应用 API 契约

## 1. ZIP 检查与内部转换

`POST /api/modeling/model-spec-imports/dbt/archive/inspect`

请求使用 `multipart/form-data`，字段 `archive` 只接受单个 dbt ZIP，最大 32 MiB。服务端必须校验 ZIP magic、路径、条目数和解压大小，在临时目录中只读解析并在请求结束后清理。

响应是现有 `dts.model-package/v1` 内部对象，供同源导入向导继续完成上下文映射。它不是要求业务用户制作或上传的公开文件格式。支持：

- dbt artifact ZIP：`manifest.json` 或 `target/manifest.json`，可选 catalog、schema YAML、SQL、macro 和 seed。
- legacy ZIP：`models.tsv + SQL`。缺少可证明的依赖和业务语义时返回结构化 issue/阻断，禁止猜测后写入。

稳定错误至少覆盖 `MODEL_IMPORT_ARCHIVE_INVALID`、`MODEL_IMPORT_ARCHIVE_TOO_LARGE`、`MODEL_IMPORT_ARCHIVE_UNSAFE_PATH`、`MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING` 和 `MODEL_IMPORT_ARCHIVE_SQL_MISSING`。

## 2. 预检

`POST /api/modeling/model-spec-imports/dbt/preview`

请求：

```json
{
  "package": {},
  "context": {
    "planId": "uuid",
    "domainMappings": {},
    "sourceMappings": {}
  },
  "selectedUniqueIds": []
}
```

响应：

```json
{
  "runId": "uuid",
  "previewHash": "sha256",
  "summary": {
    "total": 5,
    "ready": 4,
    "blocked": 1,
    "create": 4,
    "update": 0,
    "skip": 0,
    "conflict": 0
  },
  "items": [
    {
      "dbtUniqueId": "model.pm_analytics_v3.biz_dwd_budget_v2",
      "action": "CREATE",
      "conversionMode": "DBT_BACKED",
      "proposedModelSpec": {},
      "proposedImplementation": {},
      "issues": []
    }
  ]
}
```

预检必须：

- 不写 ModelSpec、implementation、artifact 或文件。
- 校验 package schema/version/size/checksum、dbt project/manifest 元数据和 node 类型。
- 使用服务端租户、账号、计划权限和 SourceBinding 版本。
- 运行 canonical ModelSpec 语义校验、依赖拓扑、所有权和循环检测。
- 返回结构化 issue code、字段路径、严重度、修复入口。

## 3. 应用

`POST /api/modeling/model-spec-imports/dbt/apply`

请求：

```json
{
  "runId": "uuid",
  "previewHash": "sha256",
  "selectedUniqueIds": [],
  "idempotencyKey": "client-generated-stable-key"
}
```

响应：

```json
{
  "summary": {
    "total": 4,
    "created": 4,
    "updated": 0,
    "replayed": 0,
    "failed": 0
  },
  "items": [
    {
      "dbtUniqueId": "model.pm_analytics_v3.biz_dwd_budget_v2",
      "status": "CREATED",
      "modelSpecId": "uuid",
      "revision": 1,
      "implementationRevision": 1,
      "artifactCount": 2,
      "issues": []
    }
  ]
}
```

apply 必须：

1. 重新验证 `previewHash` 和所有外部版本。
2. 按依赖拓扑创建或修订 ModelSpec。
3. 创建普通 implementation，或 claim `DBT_MANAGED` implementation。
4. 对 DBT_BACKED 候选复用现有 dbt artifact import。
5. 为每个候选建立独立原子事务；一个失败不得留下半个候选。
6. 批次返回逐项结果，允许仅重试失败项。

## 4. 查询

- `GET /api/modeling/model-spec-imports/{runId}`：读取预检/应用结果。
- `POST /api/modeling/model-spec-imports/{runId}/retry`：仅重试失败且输入未漂移的候选。
- 首版不提供“删除已导入模型”的批量回滚；错误导入按 ModelSpec 生命周期退役，避免破坏下游引用。

## 5. 稳定错误语义

至少覆盖：

- `MODEL_PACKAGE_SCHEMA_INVALID`
- `MODEL_PACKAGE_CHECKSUM_MISMATCH`
- `MODEL_PACKAGE_SEMANTIC_METADATA_REQUIRED`
- `MODEL_IMPORT_SOURCE_NOT_CONFIRMED`
- `MODEL_IMPORT_SOURCE_VERSION_STALE`
- `MODEL_IMPORT_DEPENDENCY_MISSING`
- `MODEL_IMPORT_DEPENDENCY_CYCLE`
- `MODEL_IMPORT_NODE_ALREADY_OWNED`
- `MODEL_IMPORT_PREVIEW_STALE`
- `MODEL_IMPORT_IDEMPOTENCY_CONFLICT`
- `MODEL_IMPORT_CONVERSION_BLOCKED`
