# 预检与应用 API 契约

## 1. 预检

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

## 2. 应用

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

## 3. 查询

- `GET /api/modeling/model-spec-imports/{runId}`：读取预检/应用结果。
- `POST /api/modeling/model-spec-imports/{runId}/retry`：仅重试失败且输入未漂移的候选。
- 首版不提供“删除已导入模型”的批量回滚；错误导入按 ModelSpec 生命周期退役，避免破坏下游引用。

## 4. 稳定错误语义

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
