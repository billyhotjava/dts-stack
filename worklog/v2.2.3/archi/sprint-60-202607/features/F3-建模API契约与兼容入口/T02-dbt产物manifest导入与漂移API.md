# T02: dbt 产物、manifest 导入与漂移 API

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

支持高级开发把已有 dbt 项目登记进新模型台账，并提供可解释的 SQL/规格漂移结果。

## 技术设计

- `POST /api/modeling/vnext/dbt/import`：接收 manifest 元数据、项目标识和可选 SQL 内容。
- `GET /api/modeling/vnext/model-specs/{id}/artifacts`：查询 SQL、schema、tests、docs。
- `GET /api/modeling/vnext/model-specs/{id}/drift`：比较字段、粒度、来源、类型和 checksum。
- 导入失败使用 `DBT_MANIFEST_INVALID`、`DBT_MODEL_NOT_FOUND`、`DBT_ARTIFACT_UNREADABLE`。

## 影响范围

- dbt manifest DTO 和导入 service。
- 前端高级入口 API client。
- API contract tests。

## 验证

- [x] manifest 缺节点、缺字段或版本不支持时返回稳定错误码。
- [x] SQL 内容变化只产生 drift，不覆盖 ModelSpec 的规则已纳入契约。
- [x] 同一个 dbt unique id 重复导入使用幂等键的规则已纳入契约。
- [x] 后端 manifest 单模型导入解析器已覆盖字段、依赖和内容 checksum，并对缺节点/不可读 SQL 返回稳定错误码。

## 完成标准

- [ ] PJM 旧 dbt fixture 可成功导入并显示来源关系。
